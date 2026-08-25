package com.flipkart.drift.commons.model.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.ExecutionType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Set at start for parallel workflows so API/clients can detect execution model. */
    private ExecutionType executionType;

    // --- ON_EVENT wait tracking (serial path: global; parallel path: per-node in nodeStates) ---

    /** Event types the workflow is waiting for; copied from OnEventConfig at park time (serial path). */
    private List<String> expectedEventTypes;

    /** Semantics for the wait condition; ANY or ALL. */
    private WaitSemantics waitSemantics;

    /** Accumulates event types received via resumeWorkflow signals while parked. */
    private Set<String> receivedEventTypes = new HashSet<>();

    // --- Parallel execution tracking ---

    /** Per-node state for nodes in WAITING, SCHEDULER_WAITING, RUNNING, or SKIPPED. */
    private Map<String, NodeState> nodeStates = new HashMap<>();

    /** Nodes pruned by BRANCH selection (non-selected arms). */
    private Set<String> skippedNodes = new HashSet<>();

    /** BRANCH instanceName → selected next node. */
    private Map<String, String> branchSelections = new HashMap<>();
}
