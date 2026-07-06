package com.flipkart.drift.commons.validation;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.ExecutionType;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;

import javax.ws.rs.core.Response;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ParallelWorkflowValidator {

    private ParallelWorkflowValidator() {
    }

    public static void validate(Workflow workflow) {
        if (!workflow.isParallel()) {
            validateSequential(workflow);
            return;
        }
        validateParallel(workflow);
    }

    private static void validateSequential(Workflow workflow) {
        for (WorkflowNode node : workflow.getStates().values()) {
            if (node.getDependsOn() != null && !node.getDependsOn().isEmpty()) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "SEQUENTIAL workflow must not use dependsOn on node: " + node.getInstanceName());
            }
        }
    }

    private static void validateParallel(Workflow workflow) {
        Map<String, WorkflowNode> states = workflow.getStates();
        boolean hasDependsOn = false;
        Set<String> allEventTypes = new HashSet<>();

        for (WorkflowNode node : states.values()) {
            String name = node.getInstanceName();

            if (node.getNextNode() != null && !node.getNextNode().isEmpty()) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "PARALLEL workflow must not use nextNode on node: " + name);
            }
            if (node.getNextNode() != null && node.getDependsOn() != null && !node.getDependsOn().isEmpty()) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "Node must not have both nextNode and dependsOn: " + name);
            }

            List<String> deps = node.getDependsOn() != null ? node.getDependsOn() : List.of();
            if (!deps.isEmpty()) {
                hasDependsOn = true;
            }
            for (String dep : deps) {
                if (!states.containsKey(dep)) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "dependsOn references unknown node '" + dep + "' from node: " + name);
                }
            }

            collectEventTypes(node, name, allEventTypes);
        }

        if (!hasDependsOn) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "PARALLEL workflow requires at least one non-empty dependsOn");
        }

        detectCycle(states);
        validateStartNode(workflow, states);
        validateSinks(states);
    }

    private static void collectEventTypes(WorkflowNode node, String name, Set<String> allEventTypes) {
        WaitConfig waitConfig = resolveWaitConfig(node);
        if (waitConfig == null || waitConfig.getWaitType() != WaitType.ON_EVENT) {
            return;
        }
        OnEventConfig config = (OnEventConfig) waitConfig;
        if (config.getExpectedEventTypes() != null) {
            for (String eventType : config.getExpectedEventTypes()) {
                if (!allEventTypes.add(eventType)) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "expectedEventTypes must be unique workflow-wide; duplicate: " + eventType
                                    + " on node: " + name);
                }
            }
        }
    }

    private static WaitConfig resolveWaitConfig(WorkflowNode node) {
        if (node.getWaitConfig() != null) {
            return node.getWaitConfig();
        }
        NodeDefinition def = node.getNodeDefinition();
        if (def != null && def.getType() == NodeType.WAIT) {
            WaitNode waitNode = (WaitNode) def;
            if (waitNode.getWaitType() == WaitType.ON_EVENT) {
                return waitNode.getTypedConfig(OnEventConfig.class);
            }
        }
        return null;
    }

    private static void detectCycle(Map<String, WorkflowNode> states) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeName : states.keySet()) {
            if (dfs(nodeName, states, visiting, visited)) {
                throw new ApiException(Response.Status.BAD_REQUEST, "PARALLEL workflow DAG contains a cycle");
            }
        }
    }

    private static boolean dfs(String nodeName, Map<String, WorkflowNode> states,
                               Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeName)) {
            return false;
        }
        if (!visiting.add(nodeName)) {
            return true;
        }
        WorkflowNode node = states.get(nodeName);
        List<String> deps = node.getDependsOn() != null ? node.getDependsOn() : List.of();
        for (String dep : deps) {
            if (dfs(dep, states, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeName);
        visited.add(nodeName);
        return false;
    }

    private static void validateStartNode(Workflow workflow, Map<String, WorkflowNode> states) {
        WorkflowNode start = states.get(workflow.getStartNode());
        if (start == null) {
            return;
        }
        if (start.getDependsOn() != null && !start.getDependsOn().isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "Start node must have empty dependsOn: " + start.getInstanceName());
        }
    }

    private static void validateSinks(Map<String, WorkflowNode> states) {
        Set<String> referenced = new HashSet<>();
        for (WorkflowNode node : states.values()) {
            if (node.getDependsOn() != null) {
                referenced.addAll(node.getDependsOn());
            }
        }
        Set<String> sinks = new HashSet<>(states.keySet());
        sinks.removeAll(referenced);
        if (sinks.isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST,
                    "PARALLEL workflow must have at least one sink node");
        }
    }
}
