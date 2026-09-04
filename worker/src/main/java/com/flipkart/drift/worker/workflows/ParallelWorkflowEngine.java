package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.enums.NodeStatus;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.NodeState;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.commons.model.waitConfig.SchedulerWaitConfig;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;
import com.flipkart.drift.commons.parallel.ParallelWaitConditions;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.worker.activities.BranchSchedulerWaitActivity;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import com.flipkart.drift.worker.temporal.OptionsStore;
import io.temporal.workflow.Async;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG-based parallel workflow engine. Independent nodes run concurrently via Temporal Async;
 * fan-in nodes wait for all predecessors; each branch gets its own event wait via NodeState.
 */
public class ParallelWorkflowEngine {

    private final Logger logger = io.temporal.workflow.Workflow.getLogger(ParallelWorkflowEngine.class);
    private final WorkflowState workflowState;
    private final WorkflowNodeExecutor nodeExecutor;

    private Map<String, Set<String>> pendingDeps = new LinkedHashMap<>();
    private Map<String, List<String>> successors = new LinkedHashMap<>();
    private Set<String> terminalNodes = new LinkedHashSet<>();
    private Set<String> inFlight = new LinkedHashSet<>();
    private Set<String> completed = new LinkedHashSet<>();
    private Set<String> failedNodes = new LinkedHashSet<>();
    private Set<String> skipped = new LinkedHashSet<>();
    private Map<String, String> branchSelections = new LinkedHashMap<>();
    private Map<String, List<String>> branchTargetGroups = new LinkedHashMap<>();
    private Set<String> completedTerminals = new LinkedHashSet<>();
    private Map<String, Boolean> pausedNodes = new LinkedHashMap<>();

    private Workflow workflow;
    private WorkflowStartRequest startRequest;
    private Map<String, String> threadContext;

    public ParallelWorkflowEngine(WorkflowState workflowState, WorkflowNodeExecutor nodeExecutor) {
        this.workflowState = workflowState;
        this.nodeExecutor = nodeExecutor;
    }

    public void execute(Workflow workflow, WorkflowStartRequest startRequest, Map<String, String> threadContext) {
        this.workflow = workflow;
        this.startRequest = startRequest;
        this.threadContext = threadContext;
        initDag(workflow);
        workflowState.setStatus(WorkflowStatus.RUNNING);
        workflowState.setSkippedNodes(skipped);
        workflowState.setBranchSelections(branchSelections);
        workflowState.setNodeStates(new LinkedHashMap<>());

        dispatchReadyNodes();

        io.temporal.workflow.Workflow.await(() ->
                isGlobalTerminal() || (inFlight.isEmpty() && !hasRemainingWork()));

        finalizeStatus();
    }

    private void initDag(Workflow workflow) {
        Map<String, WorkflowNode> states = workflow.getStates();
        // defaultFailureNode is a reactive side node run only via runFallbackNode() when
        // another node pauses for unsideline — it must not be part of the dependency graph,
        // or (having no dependsOn of its own) it would be dispatched unconditionally at start.
        String fallbackNodeName = workflow.getDefaultFailureNode();
        for (WorkflowNode node : states.values()) {
            String name = node.getInstanceName();
            if (name.equals(fallbackNodeName)) {
                continue;
            }
            List<String> deps = node.getDependsOn() != null ? node.getDependsOn() : Collections.emptyList();
            pendingDeps.put(name, new LinkedHashSet<>(deps));
            for (String dep : deps) {
                successors.computeIfAbsent(dep, k -> new ArrayList<>()).add(name);
                WorkflowNode depNode = states.get(dep);
                if (depNode != null && depNode.getNodeDefinition() != null
                        && depNode.getNodeDefinition().getType() == NodeType.BRANCH) {
                    branchTargetGroups.computeIfAbsent(dep, k -> new ArrayList<>()).add(name);
                }
            }
        }
        for (String name : states.keySet()) {
            if (name.equals(fallbackNodeName)) {
                continue;
            }
            if (!successors.containsKey(name) || successors.get(name).isEmpty()) {
                terminalNodes.add(name);
            }
        }
    }

    private void dispatchReadyNodes() {
        List<String> ready = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : pendingDeps.entrySet()) {
            String nodeName = entry.getKey();
            if (entry.getValue().isEmpty() && !completed.contains(nodeName)
                    && !skipped.contains(nodeName) && !failedNodes.contains(nodeName)
                    && !inFlight.contains(nodeName)) {
                ready.add(nodeName);
            }
        }
        for (String nodeName : ready) {
            dispatchNode(nodeName);
        }
    }

    private void dispatchNode(String nodeName) {
        inFlight.add(nodeName);
        Async.procedure(() -> {
            try {
                executeNodeBranch(nodeName);
            } finally {
                inFlight.remove(nodeName);
                if (!isGlobalTerminal()) {
                    dispatchReadyNodes();
                    checkTermination();
                }
            }
        });
    }

    private void executeNodeBranch(String nodeName) {
        if (isGlobalTerminal() || skipped.contains(nodeName)) {
            return;
        }
        WorkflowNode node = workflow.getStates().get(nodeName);
        if (node == null) {
            failedNodes.add(nodeName);
            checkTermination();
            return;
        }

        while (true) {
            logger.info("WfId: {} Parallel dispatch node: {}", workflowState.getWorkflowId(), nodeName);
            ActivityThinResponse response;
            try {
                response = nodeExecutor.executeParallelNode(node, threadContext, startRequest);
            } catch (Exception e) {
                logger.error("WfId: {} Node {} failed: {}", workflowState.getWorkflowId(), nodeName, e.getMessage());
                if (!pauseForUnsideline(nodeName, e.getMessage())) {
                    return;
                }
                continue;
            }

            if (response == null) {
                completed.add(nodeName);
                onNodeComplete(nodeName, node, response);
                return;
            }

            if (response.getWorkflowStatus() == WorkflowStatus.FAILED) {
                logger.error("WfId: {} Node {} returned FAILED status", workflowState.getWorkflowId(), nodeName);
                if (!pauseForUnsideline(nodeName, "Node returned FAILED status")) {
                    return;
                }
                continue;
            }

            if (node.getNodeDefinition() != null && node.getNodeDefinition().getType() == NodeType.BRANCH) {
                if (response.getNextNode() == null) {
                    logger.error("WfId: {} BRANCH node {} did not resolve a next node",
                            workflowState.getWorkflowId(), nodeName);
                    if (!pauseForUnsideline(nodeName, "BRANCH node did not resolve a next node")) {
                        return;
                    }
                    continue;
                }
                handleBranchCompletion(nodeName, response);
                return;
            }

            WaitKind waitKind = resolveWaitKind(node, response);
            if (waitKind == WaitKind.ON_EVENT) {
                parkForOnEvent(node, response);
                return;
            }
            if (waitKind == WaitKind.SCHEDULER_WAIT) {
                parkForScheduler(node, response);
                return;
            }

            completed.add(nodeName);
            onNodeComplete(nodeName, node, response);
            return;
        }
    }

    /**
     * Runs the fallback node (if configured), parks the node's coroutine in PAUSED state,
     * and blocks until an unsideline signal for this node or a workflow-terminal event.
     * @return true if the node should be retried, false if the caller should give up (workflow going terminal).
     */
    private boolean pauseForUnsideline(String nodeName, String errorMessage) {
        if (isGlobalTerminal()) {
            return false;
        }
        runFallbackNode(nodeName);

        workflowState.setErrorMessage("Error message: " + errorMessage);
        workflowState.getNodeStates().put(nodeName, new NodeState(nodeName, NodeStatus.PAUSED, null, null));
        workflowState.setStatus(WorkflowStatus.SIDELINED);

        logger.info("WfId: {} Node: {} paused — awaiting unsideline signal", workflowState.getWorkflowId(), nodeName);
        pausedNodes.putIfAbsent(nodeName, false);
        io.temporal.workflow.Workflow.await(() ->
                Boolean.TRUE.equals(pausedNodes.get(nodeName)) || isGlobalTerminal());

        workflowState.getNodeStates().remove(nodeName);
        if (isGlobalTerminal()) {
            return false;
        }

        pausedNodes.remove(nodeName);
        workflowState.setErrorMessage(null);
        if (pausedNodes.isEmpty()) {
            workflowState.setStatus(WorkflowStatus.RUNNING);
        }
        logger.info("WfId: {} Node: {} received unsideline signal — retrying", workflowState.getWorkflowId(), nodeName);
        return true;
    }

    private void runFallbackNode(String failedNodeId) {
        WorkflowNode fallbackNode = workflow.getStates().get(workflow.getDefaultFailureNode());
        if (fallbackNode == null) {
            return;
        }
        try {
            logger.info("WfId: {} Running fallback node: {} for failed node: {}",
                    workflowState.getWorkflowId(), fallbackNode.getInstanceName(), failedNodeId);
            // Tag this one invocation's request payload with the node that triggered it —
            // a copy, not a mutation of the shared DSL object — purely so the DAG view can
            // recover the causal edge (failedNode -> fallback) the same way it recovers
            // real dependsOn edges, without needing its own fallback-specific concept.
            WorkflowNode taggedFallbackNode = new WorkflowNode(
                    fallbackNode.getInstanceName(), fallbackNode.getResourceId(), fallbackNode.getResourceVersion(),
                    fallbackNode.getType(), fallbackNode.getParameters(), fallbackNode.getContextOverrideKey(),
                    fallbackNode.getNextNode(), fallbackNode.isEnd(), fallbackNode.getTimeoutSeconds(),
                    fallbackNode.getRetryConfig(), fallbackNode.getNodeDefinition(),
                    fallbackNode.getWaitConfig(), List.of(failedNodeId));
            nodeExecutor.executeNodeWithoutStatusUpdate(taggedFallbackNode, threadContext);
        } catch (Exception e) {
            logger.error("WfId: {} Fallback node: {} failed for node: {} — {}",
                    workflowState.getWorkflowId(), fallbackNode.getInstanceName(), failedNodeId, e.getMessage(), e);
        }
    }

    /**
     * Signalled via GenericWorkflow.unsidelineWorkflow — unblocks the coroutine parked in
     * pauseForUnsideline() for this node so it retries.
     */
    public void unsideline(String nodeId) {
        pausedNodes.put(nodeId, true);
    }

    private enum WaitKind {
        NONE, ON_EVENT, SCHEDULER_WAIT
    }

    private void handleBranchCompletion(String branchName, ActivityThinResponse response) {
        String selectedNext = response.getNextNode();
        branchSelections.put(branchName, selectedNext);
        workflowState.setBranchSelections(branchSelections);

        List<String> armTargets = branchTargetGroups.getOrDefault(branchName, Collections.emptyList());
        for (String arm : armTargets) {
            pendingDeps.computeIfPresent(arm, (k, deps) -> {
                deps.remove(branchName);
                return deps;
            });
            if (!arm.equals(selectedNext)) {
                skipSubtree(arm);
            }
        }

        completed.add(branchName);
        WorkflowNode branchNode = workflow.getStates().get(branchName);
        onNodeComplete(branchName, branchNode, response);
    }

    private void skipSubtree(String root) {
        Deque<String> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String nodeName = queue.poll();
            if (skipped.contains(nodeName) || completed.contains(nodeName)) {
                continue;
            }
            skipped.add(nodeName);
            pendingDeps.remove(nodeName);
            NodeState ns = new NodeState(nodeName, NodeStatus.SKIPPED, null, null);
            workflowState.getNodeStates().put(nodeName, ns);
            for (String succ : successors.getOrDefault(nodeName, Collections.emptyList())) {
                pendingDeps.computeIfPresent(succ, (k, deps) -> {
                    deps.remove(nodeName);
                    return deps;
                });
                if (shouldSkipAsUnreachable(succ)) {
                    queue.add(succ);
                }
            }
        }
        workflowState.setSkippedNodes(skipped);
    }

    /**
     * A fan-in successor is skipped only when every predecessor is already skipped, completed, or failed.
     * Do not skip while any predecessor is still active (e.g. parallel branch still running).
     */
    private boolean shouldSkipAsUnreachable(String nodeName) {
        if (skipped.contains(nodeName) || completed.contains(nodeName)) {
            return false;
        }
        WorkflowNode node = workflow.getStates().get(nodeName);
        if (node == null) {
            return true;
        }
        List<String> allDeps = node.getDependsOn() != null ? node.getDependsOn() : Collections.emptyList();
        if (allDeps.isEmpty()) {
            return true;
        }
        for (String dep : allDeps) {
            if (!skipped.contains(dep) && !completed.contains(dep) && !failedNodes.contains(dep)) {
                return false;
            }
        }
        return true;
    }

    private WaitKind resolveWaitKind(WorkflowNode node, ActivityThinResponse response) {
        WaitConfig inline = resolveInlineWaitConfig(node);
        if (inline != null) {
            if (inline.getWaitType() == WaitType.ON_EVENT) {
                return WaitKind.ON_EVENT;
            }
            if (inline.getWaitType() == WaitType.SCHEDULER_WAIT) {
                return WaitKind.SCHEDULER_WAIT;
            }
        }
        if (node.getNodeDefinition() != null && node.getNodeDefinition().getType() == NodeType.WAIT) {
            WaitNode waitNode = (WaitNode) node.getNodeDefinition();
            if (waitNode.getWaitType() == WaitType.ON_EVENT) {
                return WaitKind.ON_EVENT;
            }
            if (waitNode.getWaitType() == WaitType.SCHEDULER_WAIT) {
                return WaitKind.SCHEDULER_WAIT;
            }
        }
        if (response.getWorkflowStatus() == WorkflowStatus.WAITING
                || response.getWorkflowStatus() == WorkflowStatus.SCHEDULER_WAITING) {
            // Activity returned a wait status without explicit config — infer from status.
            return response.getWorkflowStatus() == WorkflowStatus.SCHEDULER_WAITING
                    ? WaitKind.SCHEDULER_WAIT
                    : WaitKind.ON_EVENT;
        }
        return WaitKind.NONE;
    }

    private WaitConfig resolveInlineWaitConfig(WorkflowNode node) {
        return node.getWaitConfig();
    }
    private void parkForOnEvent(WorkflowNode node, ActivityThinResponse response) {
        OnEventConfig config = resolveOnEventConfig(node);
        if (config == null) {
            completed.add(node.getInstanceName());
            onNodeComplete(node.getInstanceName(), node, response);
            return;
        }

        List<String> expected = (response.getResolvedExpectedEventTypes() != null
                && !response.getResolvedExpectedEventTypes().isEmpty())
                ? response.getResolvedExpectedEventTypes()
                : config.getExpectedEventTypes();
        WaitSemantics semantics = config.getWaitSemantics() != null ? config.getWaitSemantics() : WaitSemantics.ALL;

        NodeState nodeState = new NodeState(node.getInstanceName(), NodeStatus.WAITING, expected, semantics);
        parkUntilConditionMet(node, response, nodeState);
    }

    private void parkForScheduler(WorkflowNode node, ActivityThinResponse response) {
        if (needsInlineSchedulerRegistration(node)) {
            long duration = resolveSchedulerDuration(node);
            BranchSchedulerWaitActivity schedulerActivity = io.temporal.workflow.Workflow.newActivityStub(
                    BranchSchedulerWaitActivity.class, OptionsStore.activityOptions);
            schedulerActivity.registerSchedulerWait(
                    workflowState.getWorkflowId(), node.getInstanceName(), duration, threadContext);
        }
        String syntheticType = ParallelWaitConditions.schedulerEventType(node.getInstanceName());
        NodeState nodeState = new NodeState(node.getInstanceName(), NodeStatus.SCHEDULER_WAITING,
                List.of(syntheticType), null);
        parkUntilConditionMet(node, response, nodeState);
    }

    private boolean needsInlineSchedulerRegistration(WorkflowNode node) {
        WaitConfig inline = node.getWaitConfig();
        return inline != null && inline.getWaitType() == WaitType.SCHEDULER_WAIT;
    }

    private long resolveSchedulerDuration(WorkflowNode node) {
        WaitConfig inline = node.getWaitConfig();
        if (inline != null && inline.getWaitType() == WaitType.SCHEDULER_WAIT) {
            SchedulerWaitConfig config = (SchedulerWaitConfig) inline;
            return config.getDuration();
        }
        if (node.getNodeDefinition() != null && node.getNodeDefinition().getType() == NodeType.WAIT) {
            WaitNode waitNode = (WaitNode) node.getNodeDefinition();
            if (waitNode.getWaitType() == WaitType.SCHEDULER_WAIT) {
                return waitNode.getTypedConfig(SchedulerWaitConfig.class).getDuration();
            }
        }
        return 0L;
    }

    private void parkUntilConditionMet(WorkflowNode node, ActivityThinResponse response, NodeState nodeState) {
        workflowState.getNodeStates().put(node.getInstanceName(), nodeState);

        if (ParallelWaitConditions.isConditionMet(nodeState, workflowState.getReceivedEventTypes())) {
            finishParkedNode(node, response, nodeState);
            return;
        }

        io.temporal.workflow.Workflow.await(() ->
                ParallelWaitConditions.isConditionMet(nodeState, workflowState.getReceivedEventTypes())
                        || isGlobalTerminal());

        if (isGlobalTerminal()) {
            return;
        }

        finishParkedNode(node, response, nodeState);
    }

    private void finishParkedNode(WorkflowNode node, ActivityThinResponse response, NodeState nodeState) {
        ParallelWaitConditions.drainConsumedEvents(nodeState, workflowState.getReceivedEventTypes());
        workflowState.getNodeStates().remove(node.getInstanceName());
        completed.add(node.getInstanceName());
        onNodeComplete(node.getInstanceName(), node, response);
    }

    private OnEventConfig resolveOnEventConfig(WorkflowNode node) {
        if (node.getWaitConfig() != null && node.getWaitConfig().getWaitType() == WaitType.ON_EVENT) {
            return (OnEventConfig) node.getWaitConfig();
        }
        if (node.getNodeDefinition() != null && node.getNodeDefinition().getType() == NodeType.WAIT) {
            WaitNode waitNode = (WaitNode) node.getNodeDefinition();
            if (waitNode.getWaitType() == WaitType.ON_EVENT) {
                return waitNode.getTypedConfig(OnEventConfig.class);
            }
        }
        return null;
    }

    private void onNodeComplete(String nodeName, WorkflowNode node, ActivityThinResponse response) {
        if (terminalNodes.contains(nodeName)) {
            completedTerminals.add(nodeName);
        }

        for (String succ : successors.getOrDefault(nodeName, Collections.emptyList())) {
            pendingDeps.computeIfPresent(succ, (k, deps) -> {
                deps.remove(nodeName);
                return deps;
            });
            Set<String> succDeps = pendingDeps.get(succ);
            if (succDeps != null && succDeps.isEmpty() && !completed.contains(succ)
                    && !skipped.contains(succ) && !failedNodes.contains(succ)
                    && !inFlight.contains(succ)) {
                dispatchNode(succ);
            }
        }
        checkTermination();
    }

    private void checkTermination() {
        if (isGlobalTerminal()) {
            return;
        }
        Set<String> nonSkippedTerminals = new HashSet<>(terminalNodes);
        nonSkippedTerminals.removeAll(skipped);

        if (!nonSkippedTerminals.isEmpty() && completedTerminals.containsAll(nonSkippedTerminals)) {
            workflowState.setStatus(WorkflowStatus.COMPLETED);
            return;
        }

        if (inFlight.isEmpty() && !hasRemainingWork()) {
            workflowState.setStatus(WorkflowStatus.FAILED);
            if (workflowState.getErrorMessage() == null) {
                workflowState.setErrorMessage(buildFailureMessage(nonSkippedTerminals));
            }
        }
    }

    private void finalizeStatus() {
        if (workflowState.getStatus() == WorkflowStatus.RUNNING) {
            checkTermination();
        }
        if (workflowState.getStatus() == WorkflowStatus.RUNNING && inFlight.isEmpty()) {
            Set<String> nonSkippedTerminals = new HashSet<>(terminalNodes);
            nonSkippedTerminals.removeAll(skipped);
            if (completedTerminals.containsAll(nonSkippedTerminals)) {
                workflowState.setStatus(WorkflowStatus.COMPLETED);
            } else {
                workflowState.setStatus(WorkflowStatus.FAILED);
            }
        }
        if (workflowState.getStatus() == WorkflowStatus.COMPLETED
                && workflow.getPostWorkflowCompletionNodes() != null
                && !workflow.getPostWorkflowCompletionNodes().isEmpty()) {
            logger.info("WfId: {} Running post-workflow completion nodes", workflowState.getWorkflowId());
            nodeExecutor.runPostWorkflowCompletionNodes(workflow, threadContext);
        }
    }

    private boolean hasRemainingWork() {
        for (Map.Entry<String, Set<String>> entry : pendingDeps.entrySet()) {
            String nodeName = entry.getKey();
            if (!skipped.contains(nodeName) && !completed.contains(nodeName)
                    && !failedNodes.contains(nodeName) && !entry.getValue().isEmpty()) {
                Set<String> deps = entry.getValue();
                boolean blockedByFailure = deps.stream().anyMatch(failedNodes::contains);
                if (!blockedByFailure) {
                    return true;
                }
            }
        }
        return !inFlight.isEmpty();
    }

    private boolean isGlobalTerminal() {
        WorkflowStatus status = workflowState.getStatus();
        return status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED
                || status == WorkflowStatus.TERMINATED || status == WorkflowStatus.ASYNC_COMPLETE;
    }

    private String buildFailureMessage(Set<String> nonSkippedTerminals) {
        if (!failedNodes.isEmpty()) {
            return "Parallel branch failure(s): " + failedNodes;
        }
        Set<String> missing = new HashSet<>(nonSkippedTerminals);
        missing.removeAll(completedTerminals);
        return "Not all required terminals succeeded; unreachable or failed: " + missing;
    }

    public String resolveNodeForEventType(String eventType) {
        if (workflowState.getNodeStates() == null || workflowState.getNodeStates().isEmpty()) {
            return null;
        }
        return ParallelWaitConditions.resolveNodeForEventType(
                eventType, workflowState.getNodeStates().values());
    }
}
