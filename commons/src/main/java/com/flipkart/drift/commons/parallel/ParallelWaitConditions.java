package com.flipkart.drift.commons.parallel;

import com.flipkart.drift.commons.model.enums.NodeStatus;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.temporal.NodeState;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Pure wait-condition helpers for parallel workflow branches.
 * Used by {@code ParallelWorkflowEngine} and unit-tested without Temporal.
 */
public final class ParallelWaitConditions {

    public static final String SCHEDULER_WAIT_PREFIX = "SCHEDULER_WAIT_";

    private ParallelWaitConditions() {
    }

    public static String schedulerEventType(String instanceName) {
        return SCHEDULER_WAIT_PREFIX + instanceName;
    }

    public static boolean isConditionMet(NodeState nodeState, Set<String> receivedEventTypes) {
        if (nodeState == null || receivedEventTypes == null || receivedEventTypes.isEmpty()) {
            return false;
        }
        if (nodeState.getStatus() == NodeStatus.SCHEDULER_WAITING) {
            return receivedEventTypes.contains(schedulerEventType(nodeState.getInstanceName()));
        }
        if (nodeState.getStatus() == NodeStatus.WAITING) {
            return isOnEventConditionMet(nodeState, receivedEventTypes);
        }
        return false;
    }

    public static boolean isOnEventConditionMet(NodeState nodeState, Set<String> receivedEventTypes) {
        List<String> expected = nodeState.getExpectedEventTypes();
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        WaitSemantics semantics = nodeState.getWaitSemantics() != null
                ? nodeState.getWaitSemantics()
                : WaitSemantics.ALL;
        if (semantics == WaitSemantics.ANY) {
            return !Collections.disjoint(receivedEventTypes, expected);
        }
        return receivedEventTypes.containsAll(expected);
    }

    /** Removes event types consumed by the parked branch from the global set. */
    public static void drainConsumedEvents(NodeState nodeState, Set<String> receivedEventTypes) {
        if (receivedEventTypes == null || nodeState == null) {
            return;
        }
        if (nodeState.getStatus() == NodeStatus.SCHEDULER_WAITING) {
            receivedEventTypes.remove(schedulerEventType(nodeState.getInstanceName()));
            return;
        }
        if (nodeState.getExpectedEventTypes() != null) {
            receivedEventTypes.removeAll(nodeState.getExpectedEventTypes());
        }
    }

    /**
     * Resolves which node instance should receive an event payload write for parallel workflows.
     * Matches ON_EVENT parked branches by event type; matches SCHEDULER_WAIT by synthetic type.
     */
    public static String resolveNodeForEventType(String eventType, Iterable<NodeState> nodeStates) {
        if (eventType == null || nodeStates == null) {
            return null;
        }
        for (NodeState ns : nodeStates) {
            if (ns.getStatus() == NodeStatus.SCHEDULER_WAITING
                    && eventType.equals(schedulerEventType(ns.getInstanceName()))) {
                return ns.getInstanceName();
            }
            if (ns.getStatus() == NodeStatus.WAITING && ns.getExpectedEventTypes() != null
                    && ns.getExpectedEventTypes().contains(eventType)) {
                return ns.getInstanceName();
            }
        }
        return null;
    }
}
