package com.flipkart.drift.worker.workflows;

import com.flipkart.drift.commons.model.enums.NodeStatus;
import com.flipkart.drift.commons.model.enums.WorkflowNodeType;
import com.flipkart.drift.commons.model.node.InstructionNode;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.node.WorkflowNode;
import com.flipkart.drift.commons.model.temporal.NodeState;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.worker.model.activity.ActivityThinResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for ParallelWorkflowEngine's SIDELINED / pauseForUnsideline / unsideline bookkeeping
 * (PR #61 review comment). Drives the real engine inside a Temporal test workflow coroutine
 * (TestWorkflowEnvironment) via a minimal harness workflow, since Workflow.await/getLogger
 * require a coroutine context. WorkflowNodeExecutor is mocked via TestFixtures since it is
 * instantiated directly (not an activity) and its executeParallelNode/executeNodeWithoutStatusUpdate
 * calls are what we need to control across retries.
 */
class ParallelWorkflowEngineSidelineTest {

    /** Static handoff of per-test fixtures into the workflow-thread-constructed engine. */
    static final class TestFixtures {
        static WorkflowNodeExecutor nodeExecutor;
        static Workflow workflow;
    }

    @WorkflowInterface
    public interface HarnessWorkflow {
        // Returns the terminal WorkflowState as the workflow result. Querying a workflow
        // immediately after signal-driven completion is unreliable in temporal-testing 1.22.2
        // (spurious "Signal received after workflow is closed" INVALID_ARGUMENT on direct-query
        // replay), so post-completion assertions read the result instead of querying afterwards.
        @WorkflowMethod
        WorkflowState run();

        @SignalMethod
        void unsideline(String nodeId);

        @io.temporal.workflow.QueryMethod
        WorkflowState getState();
    }

    public static class HarnessWorkflowImpl implements HarnessWorkflow {
        private final WorkflowState workflowState = new WorkflowState();
        private ParallelWorkflowEngine engine;

        @Override
        public WorkflowState run() {
            workflowState.setWorkflowId("test-wf-id");
            engine = new ParallelWorkflowEngine(workflowState, TestFixtures.nodeExecutor);
            engine.execute(TestFixtures.workflow, new WorkflowStartRequest(), new HashMap<>());
            return workflowState;
        }

        @Override
        public void unsideline(String nodeId) {
            engine.unsideline(nodeId);
        }

        @Override
        public WorkflowState getState() {
            return workflowState;
        }
    }

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(HarnessWorkflowImpl.class);
        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private WorkflowNode instructionNode(String name, List<String> dependsOn) {
        WorkflowNode node = new WorkflowNode();
        node.setInstanceName(name);
        node.setType(WorkflowNodeType.NODE);
        node.setDependsOn(dependsOn);
        node.setNodeDefinition(new InstructionNode());
        return node;
    }

    private Workflow buildWorkflow(Map<String, WorkflowNode> states, String defaultFailureNode) {
        Workflow workflow = new Workflow();
        workflow.setId("wf-dsl-id");
        workflow.setVersion("1");
        workflow.setStartNode("A");
        workflow.setExecutionType(com.flipkart.drift.commons.model.enums.ExecutionType.PARALLEL);
        workflow.setStates(states);
        workflow.setDefaultFailureNode(defaultFailureNode);
        return workflow;
    }

    private HarnessWorkflow startHarness() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .build();
        HarnessWorkflow stub = client.newWorkflowStub(HarnessWorkflow.class, options);
        WorkflowClient.start(stub::run);
        return stub;
    }

    // Scenario 1: single node fails -> pauses (SIDELINED, PAUSED NodeState) -> unsideline -> retry succeeds -> COMPLETED.
    @Test
    void singleNodeSidelinedThenUnsidelinedResumesAndCompletes() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        states.put("A", instructionNode("A", List.of()));
        TestFixtures.workflow = buildWorkflow(states, null);

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        AtomicInteger callCount = new AtomicInteger();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.SIDELINED).build();
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.COMPLETED).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();

        awaitQuiescence(wf);
        WorkflowState mid = wf.getState();
        assertEquals(WorkflowStatus.SIDELINED, mid.getStatus());
        assertNotNull(mid.getNodeStates().get("A"));
        assertEquals(NodeStatus.PAUSED, mid.getNodeStates().get("A").getStatus());
        assertEquals("Error message: Node returned SIDELINED status", mid.getErrorMessage());

        wf.unsideline("A");
        WorkflowState finalState = untypedStub(wf).getResult(WorkflowState.class);

        assertEquals(WorkflowStatus.COMPLETED, finalState.getStatus());
        assertNull(finalState.getErrorMessage());
        assertFalse(finalState.getNodeStates().containsKey("A"));
        assertEquals(2, callCount.get());
    }

    // Scenario 2: pauseForUnsideline runs the configured fallback node before parking.
    @Test
    void pauseForUnsidelineRunsFallbackNodeBeforeParking() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        states.put("A", instructionNode("A", List.of()));
        states.put("fallback", instructionNode("fallback", List.of()));
        TestFixtures.workflow = buildWorkflow(states, "fallback");

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        AtomicInteger callCount = new AtomicInteger();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.FAILED).build();
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.COMPLETED).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();
        awaitQuiescence(wf);

        verify(executor).executeNodeWithoutStatusUpdate(any(), any());
        assertEquals(WorkflowStatus.SIDELINED, wf.getState().getStatus());

        wf.unsideline("A");
        WorkflowState finalState = untypedStub(wf).getResult(WorkflowState.class);
        assertEquals(WorkflowStatus.COMPLETED, finalState.getStatus());
    }

    // Scenario 2b: no defaultFailureNode configured -> no fallback invocation, still pauses correctly.
    @Test
    void pauseForUnsidelineWithoutFallbackNodeConfigured() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        states.put("A", instructionNode("A", List.of()));
        TestFixtures.workflow = buildWorkflow(states, null);

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        AtomicInteger callCount = new AtomicInteger();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                throw new RuntimeException("boom");
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.COMPLETED).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();
        awaitQuiescence(wf);

        assertEquals(WorkflowStatus.SIDELINED, wf.getState().getStatus());
        assertEquals("Error message: boom", wf.getState().getErrorMessage());
        // Fallback is absent (defaultFailureNode == null) -> executeNodeWithoutStatusUpdate must
        // never be invoked; only the one executeParallelNode interaction (the failing attempt) exists.
        verify(executor, org.mockito.Mockito.never()).executeNodeWithoutStatusUpdate(any(), any());

        wf.unsideline("A");
        WorkflowState finalState = untypedStub(wf).getResult(WorkflowState.class);
        assertEquals(WorkflowStatus.COMPLETED, finalState.getStatus());
    }

    // Scenario 3: unsideline signal for an unknown/not-currently-paused nodeId is a no-op —
    // must not create a stale pausedNodes entry (which would block the real pause's resolution).
    @Test
    void unsidelineForUnknownNodeIdIsNoOpAndDoesNotBlockRealResolution() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        states.put("A", instructionNode("A", List.of()));
        TestFixtures.workflow = buildWorkflow(states, null);

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        AtomicInteger callCount = new AtomicInteger();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.SIDELINED).build();
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.COMPLETED).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();
        awaitQuiescence(wf);
        assertEquals(WorkflowStatus.SIDELINED, wf.getState().getStatus());

        // Bogus signal: unrelated/unknown node id -> should be ignored, not consumed as "A"'s resolution.
        wf.unsideline("does-not-exist");
        awaitQuiescence(wf);
        assertEquals(WorkflowStatus.SIDELINED, wf.getState().getStatus());
        assertEquals(NodeStatus.PAUSED, wf.getState().getNodeStates().get("A").getStatus());

        // Now the real signal resolves it.
        wf.unsideline("A");
        WorkflowState finalState = untypedStub(wf).getResult(WorkflowState.class);
        assertEquals(WorkflowStatus.COMPLETED, finalState.getStatus());
    }

    // Scenario 4: two branches pause concurrently; status/errorMessage only reset to
    // RUNNING/null once BOTH are unsidelined (pausedNodes.isEmpty()), and each resolves independently.
    @Test
    void multipleConcurrentlyPausedNodesResolveIndependentlyAndOnlyClearOnceAllResolved() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        states.put("A", instructionNode("A", List.of()));
        states.put("B", instructionNode("B", List.of()));
        TestFixtures.workflow = buildWorkflow(states, null);

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        Map<String, AtomicInteger> callCounts = new java.util.concurrent.ConcurrentHashMap<>();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            WorkflowNode node = invocation.getArgument(0);
            AtomicInteger count = callCounts.computeIfAbsent(node.getInstanceName(), k -> new AtomicInteger());
            if (count.getAndIncrement() == 0) {
                return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.SIDELINED).build();
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.COMPLETED).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();
        awaitQuiescence(wf);

        WorkflowState mid = wf.getState();
        assertEquals(WorkflowStatus.SIDELINED, mid.getStatus());
        assertEquals(NodeStatus.PAUSED, mid.getNodeStates().get("A").getStatus());
        assertEquals(NodeStatus.PAUSED, mid.getNodeStates().get("B").getStatus());

        // Resolve only A -> still SIDELINED overall since B is still paused.
        wf.unsideline("A");
        awaitQuiescence(wf);
        WorkflowState afterA = wf.getState();
        assertFalse(afterA.getNodeStates().containsKey("A"));
        assertTrue(afterA.getNodeStates().containsKey("B"));
        assertEquals(WorkflowStatus.SIDELINED, afterA.getStatus());

        // Resolve B -> both resolved -> status/errorMessage reset, then workflow completes.
        wf.unsideline("B");
        WorkflowState finalState = untypedStub(wf).getResult(WorkflowState.class);
        assertEquals(WorkflowStatus.COMPLETED, finalState.getStatus());
    }

    // Scenario 5: BRANCH node failing to resolve a next node also pauses for unsideline.
    @Test
    void branchNodeNotResolvingNextNodePausesForUnsideline() {
        Map<String, WorkflowNode> states = new LinkedHashMap<>();
        WorkflowNode branch = new WorkflowNode();
        branch.setInstanceName("A");
        branch.setType(WorkflowNodeType.NODE);
        branch.setDependsOn(List.of());
        branch.setNodeDefinition(new com.flipkart.drift.commons.model.node.BranchNode());
        states.put("A", branch);
        TestFixtures.workflow = buildWorkflow(states, null);

        WorkflowNodeExecutor executor = mock(WorkflowNodeExecutor.class);
        AtomicInteger callCount = new AtomicInteger();
        when(executor.executeParallelNode(any(), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                // BRANCH with no nextNode resolved.
                return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.RUNNING).nextNode(null).build();
            }
            return ActivityThinResponse.builder().workflowStatus(WorkflowStatus.RUNNING).nextNode(null).build();
        });
        TestFixtures.nodeExecutor = executor;

        HarnessWorkflow wf = startHarness();
        awaitQuiescence(wf);
        assertEquals(WorkflowStatus.SIDELINED, wf.getState().getStatus());
        assertEquals(NodeStatus.PAUSED, wf.getState().getNodeStates().get("A").getStatus());
    }

    /** Helper to get an untyped WorkflowStub for result-waiting from a typed stub. */
    private io.temporal.client.WorkflowStub untypedStub(HarnessWorkflow wf) {
        return io.temporal.client.WorkflowStub.fromTyped(wf);
    }

    /**
     * Real-time settle-and-query helper. This harness workflow has no timers, so
     * TestWorkflowEnvironment#sleep (virtual-clock advance) does not apply and, worse,
     * combining it with a just-delivered signal/query triggers a TestWorkflowEnvironment
     * replay quirk ("Signal received after workflow is closed" / INVALID_ARGUMENT on direct
     * query) in SDK 1.22.2. Polling on real wall-clock time avoids that interaction entirely.
     */
    private void awaitQuiescence(HarnessWorkflow wf) {
        WorkflowState last = null;
        for (int i = 0; i < 50; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            WorkflowState current = wf.getState();
            if (last != null && statesEqual(last, current)) {
                return;
            }
            last = current;
        }
    }

    private boolean statesEqual(WorkflowState a, WorkflowState b) {
        return a.getStatus() == b.getStatus()
                && java.util.Objects.equals(a.getErrorMessage(), b.getErrorMessage())
                && java.util.Objects.equals(a.getNodeStates().keySet(), b.getNodeStates().keySet());
    }

    private static void assertNotNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNotNull(o);
    }
}
