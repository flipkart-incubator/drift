package com.flipkart.drift.commons.model.waitConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.commons.model.enums.WaitType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnEventConfig extends WaitConfig {

    /**
     * Ordered list of event type labels the workflow expects to receive before continuing.
     * Single-event callers pass a list with one entry.
     */
    private List<String> expectedEventTypes;

    /**
     * ANY (default): first event unblocks the workflow.
     * ALL: every event in expectedEventTypes must arrive before continuing.
     */
    private WaitSemantics waitSemantics = WaitSemantics.ALL;

    /**
     * Dot-path reference into the HBase context for a List<String> already stored by a previous node.
     * Use this when the event list is only known at runtime.
     * Format: "<nodeInstanceName>.<fieldName>" e.g. "fetchVendors.vendorIds"
     * Takes priority over expectedEventTypes when both are set.
     */
    private String expectedEventTypesVar;

    public OnEventConfig() {
        super(WaitType.ON_EVENT);
    }
}
