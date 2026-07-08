package com.flipkart.drift.commons.validation;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.ExecutionType;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.waitConfig.OnEventConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelWorkflowValidatorTest {

    @Test
    void validParallelFanOutFanIn_passes() {
        Workflow workflow = parallelWorkflow(Map.of(
                "fetch", node("fetch", null),
                "notifyA", node("notifyA", List.of("fetch")),
                "notifyB", node("notifyB", List.of("fetch")),
                "process", node("process", List.of("notifyA", "notifyB"))
        ));
        assertDoesNotThrow(() -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void parallelWithNextNode_rejected() {
        WorkflowNode withNext = node("a", null);
        withNext.setNextNode("b");
        Workflow workflow = parallelWorkflow(Map.of("a", withNext, "b", node("b", List.of("a"))));

        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void sequentialWithDependsOn_rejected() {
        Workflow workflow = new Workflow();
        workflow.setId("seq-wf");
        workflow.setStartNode("a");
        workflow.setExecutionType(ExecutionType.SEQUENTIAL);
        workflow.setStates(Map.of(
                "a", node("a", List.of("missing"))
        ));

        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void cycleInDependsOn_rejected() {
        Workflow workflow = parallelWorkflow(Map.of(
                "a", node("a", List.of("b")),
                "b", node("b", List.of("a"))
        ));

        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void duplicateExpectedEventTypes_rejected() {
        OnEventConfig config = new OnEventConfig();
        config.setExpectedEventTypes(List.of("SAME_EVENT"));
        config.setWaitSemantics(WaitSemantics.ALL);

        WorkflowNode a = node("a", null);
        a.setWaitConfig(config);
        WorkflowNode b = node("b", List.of("a"));
        b.setWaitConfig(config);

        Workflow workflow = parallelWorkflow(Map.of("a", a, "b", b));
        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void startNodeWithDependsOn_rejected() {
        Workflow workflow = parallelWorkflow(Map.of(
                "fetch", node("fetch", List.of("other")),
                "other", node("other", null)
        ));
        workflow.setStartNode("fetch");

        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void duplicateInstanceName_rejected() {
        WorkflowNode a = node("a", null);
        WorkflowNode b = node("b", List.of("a"));
        b.setInstanceName("a");
        Workflow workflow = parallelWorkflow(Map.of("a", a, "b", b));
        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    @Test
    void mustacheRefWithoutDependsOn_rejected() {
        WorkflowNode fetch = node("fetch", null);
        WorkflowNode aggregate = node("aggregate", List.of("fetch"));
        aggregate.setParameters(Map.of("orderId", "{{context.notifyVendorA.orderId}}"));
        Workflow workflow = parallelWorkflow(Map.of(
                "fetch", fetch,
                "notifyVendorA", node("notifyVendorA", List.of("fetch")),
                "aggregate", aggregate
        ));
        assertThrows(ApiException.class, () -> ParallelWorkflowValidator.validate(workflow));
    }

    private static Workflow parallelWorkflow(Map<String, WorkflowNode> states) {
        Workflow workflow = new Workflow();
        workflow.setId("test-parallel-wf");
        workflow.setStartNode("fetch");
        workflow.setExecutionType(ExecutionType.PARALLEL);
        workflow.setStates(new HashMap<>(states));
        if (!states.containsKey("fetch")) {
            workflow.setStartNode(states.keySet().iterator().next());
        }
        return workflow;
    }

    private static WorkflowNode node(String name, List<String> dependsOn) {
        WorkflowNode n = new WorkflowNode();
        n.setInstanceName(name);
        n.setDependsOn(dependsOn);
        return n;
    }
}
