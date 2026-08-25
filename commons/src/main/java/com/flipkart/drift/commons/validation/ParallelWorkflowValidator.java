package com.flipkart.drift.commons.validation;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.ExecutionType;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.BranchNode;
import com.flipkart.drift.commons.model.node.NodeDefinition;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParallelWorkflowValidator {

    private static final Pattern CONTEXT_REF = Pattern.compile("\\{\\{context\\.([a-zA-Z0-9_]+)");

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
        validateUniqueInstanceNames(states);
        validateSinkNotInDependsOn(states);
        Map<String, List<String>> branchArms = buildBranchTargetGroups(states);
        validateBranchNodes(states, branchArms);
        validateMustacheDependsOn(states);
    }

    /** INV-3: map keys match instanceName and all instance names are unique. */
    private static void validateUniqueInstanceNames(Map<String, WorkflowNode> states) {
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, WorkflowNode> entry : states.entrySet()) {
            String key = entry.getKey();
            WorkflowNode node = entry.getValue();
            String instanceName = node.getInstanceName();
            if (instanceName == null || instanceName.isBlank()) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "Every node must have a non-empty instanceName; state key: " + key);
            }
            if (!key.equals(instanceName)) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "State map key must match instanceName; key='" + key + "' instanceName='" + instanceName + "'");
            }
            if (!seen.add(instanceName)) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "Duplicate instanceName in workflow: " + instanceName);
            }
        }
    }

    /** INV-6: sink nodes must not appear in any node's dependsOn. */
    private static void validateSinkNotInDependsOn(Map<String, WorkflowNode> states) {
        Set<String> referenced = new HashSet<>();
        for (WorkflowNode node : states.values()) {
            if (node.getDependsOn() != null) {
                referenced.addAll(node.getDependsOn());
            }
        }
        Set<String> sinks = new HashSet<>(states.keySet());
        sinks.removeAll(referenced);

        for (WorkflowNode node : states.values()) {
            List<String> deps = node.getDependsOn() != null ? node.getDependsOn() : List.of();
            for (String dep : deps) {
                if (sinks.contains(dep)) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "Sink node '" + dep + "' must not appear in dependsOn of node: "
                                    + node.getInstanceName());
                }
            }
        }
    }

    /** INV-7: BRANCH nodes require defaultNode; each arm must dependOn the branch. */
    private static void validateBranchNodes(Map<String, WorkflowNode> states,
                                              Map<String, List<String>> branchArms) {
        for (WorkflowNode node : states.values()) {
            NodeDefinition def = node.getNodeDefinition();
            if (def == null || def.getType() != NodeType.BRANCH) {
                continue;
            }
            BranchNode branchNode = (BranchNode) def;
            if (branchNode.getDefaultNode() == null || branchNode.getDefaultNode().isBlank()) {
                throw new ApiException(Response.Status.BAD_REQUEST,
                        "BRANCH node must declare defaultNode: " + node.getInstanceName());
            }
            List<String> arms = branchArms.getOrDefault(node.getInstanceName(), List.of());
            for (String arm : arms) {
                WorkflowNode armNode = states.get(arm);
                if (armNode == null) {
                    continue;
                }
                List<String> armDeps = armNode.getDependsOn() != null ? armNode.getDependsOn() : List.of();
                if (!armDeps.contains(node.getInstanceName())) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "BRANCH arm '" + arm + "' must list branch node '" + node.getInstanceName()
                                    + "' in dependsOn");
                }
            }
        }
    }

    /** INV-12: Mustache context refs to another node's output require that node in dependsOn. */
    private static void validateMustacheDependsOn(Map<String, WorkflowNode> states) {
        Set<String> instanceNames = states.keySet();
        for (WorkflowNode node : states.values()) {
            if (node.getParameters() == null) {
                continue;
            }
            Set<String> refs = new HashSet<>();
            for (String value : node.getParameters().values()) {
                if (value == null) {
                    continue;
                }
                Matcher matcher = CONTEXT_REF.matcher(value);
                while (matcher.find()) {
                    refs.add(matcher.group(1));
                }
            }
            Set<String> deps = new HashSet<>(
                    node.getDependsOn() != null ? node.getDependsOn() : List.of());
            String self = node.getInstanceName();
            for (String ref : refs) {
                String sourceNode = resolveSourceNodeName(ref, instanceNames);
                if (sourceNode != null && !sourceNode.equals(self) && !deps.contains(sourceNode)) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "Node '" + self + "' references context." + ref
                                    + " but does not list '" + sourceNode + "' in dependsOn");
                }
            }
        }
    }

    private static String resolveSourceNodeName(String ref, Set<String> instanceNames) {
        if (instanceNames.contains(ref)) {
            return ref;
        }
        for (String name : instanceNames) {
            if (ref.startsWith(name + "_")) {
                return name;
            }
        }
        return null;
    }

    private static Map<String, List<String>> buildBranchTargetGroups(Map<String, WorkflowNode> states) {
        Map<String, List<String>> branchArms = new HashMap<>();
        for (WorkflowNode node : states.values()) {
            NodeDefinition def = node.getNodeDefinition();
            if (def == null || def.getType() != NodeType.BRANCH) {
                continue;
            }
            BranchNode branchNode = (BranchNode) def;
            List<String> targets = new ArrayList<>();
            if (branchNode.getChoices() != null) {
                branchNode.getChoices().forEach(choice -> {
                    if (choice.getNextNode() != null) {
                        targets.add(choice.getNextNode());
                    }
                });
            }
            if (branchNode.getDefaultNode() != null) {
                targets.add(branchNode.getDefaultNode());
            }
            branchArms.put(node.getInstanceName(), targets);
        }
        return branchArms;
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
