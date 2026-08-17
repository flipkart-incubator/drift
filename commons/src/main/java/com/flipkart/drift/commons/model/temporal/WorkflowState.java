package com.flipkart.drift.commons.model.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import com.flipkart.drift.sdk.model.client.IssueDetail;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowState implements Serializable {
    private String workflowId;
    private String incidentId;
    private WorkflowStatus status;
    private String disposition;
    private String errorMessage;
    private View view;
    private String currentNodeRef;
    private IssueDetail issueDetail;
    // Resolved workflow DSL identity. Used to execute disconnected nodes when issueDetail is absent.
    private String workflowDslId;
    private String workflowVersion;
    /** Workflow-level execution mode set when the workflow was started; null for legacy workflows (treated as SYNC). */
    private WorkflowExecutionMode workflowExecutionMode;

    // --- ON_EVENT wait tracking (populated when a WaitNode or inline waitConfig parks the workflow) ---

    /** Event types the workflow is waiting for; copied from OnEventConfig at park time. */
    private List<String> expectedEventTypes;

    /** Semantics for the wait condition; ANY (default) or ALL. */
    private WaitSemantics waitSemantics;

    /** Accumulates event types received via resumeWorkflow signals while parked. */
    private Set<String> receivedEventTypes = new HashSet<>();
}
