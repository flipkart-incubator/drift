package com.flipkart.drift.commons.parallel;

import com.flipkart.drift.commons.model.enums.NodeStatus;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.temporal.NodeState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelWaitConditionsTest {

    @Test
    void schedulerEventType_isPrefixedWithInstanceName() {
        assertEquals("SCHEDULER_WAIT_delayedAudit",
                ParallelWaitConditions.schedulerEventType("delayedAudit"));
    }

    @Test
    void onEventAll_unblocksWhenAllExpectedPresent() {
        NodeState ns = new NodeState("notifyA", NodeStatus.WAITING,
                List.of("A", "B"), WaitSemantics.ALL);
        Set<String> received = new HashSet<>(Set.of("A"));

        assertFalse(ParallelWaitConditions.isConditionMet(ns, received));

        received.add("B");
        assertTrue(ParallelWaitConditions.isConditionMet(ns, received));
    }

    @Test
    void onEventAny_unblocksOnFirstMatchingEvent() {
        NodeState ns = new NodeState("notifyB", NodeStatus.WAITING,
                List.of("X", "Y", "Z"), WaitSemantics.ANY);
        Set<String> received = new HashSet<>(Set.of("Y"));

        assertTrue(ParallelWaitConditions.isConditionMet(ns, received));
    }

    @Test
    void schedulerWait_unblocksOnSyntheticEventType() {
        NodeState ns = new NodeState("delayedAudit", NodeStatus.SCHEDULER_WAITING,
                List.of("SCHEDULER_WAIT_delayedAudit"), null);
        Set<String> received = new HashSet<>();

        assertFalse(ParallelWaitConditions.isConditionMet(ns, received));

        received.add("SCHEDULER_WAIT_delayedAudit");
        assertTrue(ParallelWaitConditions.isConditionMet(ns, received));
    }

    @Test
    void drainConsumedEvents_removesOnEventTypes() {
        NodeState ns = new NodeState("notifyA", NodeStatus.WAITING,
                List.of("A", "B"), WaitSemantics.ALL);
        Set<String> received = new HashSet<>(Set.of("A", "B", "C"));

        ParallelWaitConditions.drainConsumedEvents(ns, received);

        assertEquals(Set.of("C"), received);
    }

    @Test
    void drainConsumedEvents_removesSchedulerSyntheticType() {
        NodeState ns = new NodeState("delayedAudit", NodeStatus.SCHEDULER_WAITING, null, null);
        Set<String> received = new HashSet<>(Set.of("SCHEDULER_WAIT_delayedAudit", "OTHER"));

        ParallelWaitConditions.drainConsumedEvents(ns, received);

        assertEquals(Set.of("OTHER"), received);
    }

    @Test
    void resolveNodeForEventType_findsOnEventAndSchedulerBranches() {
        NodeState onEvent = new NodeState("notifyA", NodeStatus.WAITING,
                List.of("CALLBACK_A"), WaitSemantics.ALL);
        NodeState scheduler = new NodeState("delayedAudit", NodeStatus.SCHEDULER_WAITING, null, null);

        assertEquals("notifyA",
                ParallelWaitConditions.resolveNodeForEventType("CALLBACK_A", List.of(onEvent, scheduler)));
        assertEquals("delayedAudit",
                ParallelWaitConditions.resolveNodeForEventType("SCHEDULER_WAIT_delayedAudit", List.of(onEvent, scheduler)));
        assertNull(ParallelWaitConditions.resolveNodeForEventType("UNKNOWN", List.of(onEvent, scheduler)));
    }
}
