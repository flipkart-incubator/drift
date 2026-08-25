package com.flipkart.drift.commons.model.enums;

public enum WaitSemantics {
    /** First event to arrive unblocks the workflow. Equivalent to legacy single-event behaviour. */
    ANY,
    /** All declared event types must arrive before the workflow continues. Order does not matter. */
    ALL
}
