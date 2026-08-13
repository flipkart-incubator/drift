package com.flipkart.drift.sdk.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.sdk.model.client.Customer;
import com.flipkart.drift.sdk.model.client.IssueDetail;
import com.flipkart.drift.sdk.model.client.OrderDetail;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowStartRequest extends WorkflowRequest {
    // Optional/deprecated. Prefer params.workflowId + params.version for workflow resolution.
    // Retained for backward compatibility: when params do not carry workflowId/version,
    // issueDetail.issueId is used to look up the workflow mapping.
    @Deprecated
    private IssueDetail issueDetail;
    @Deprecated
    private Customer customer;
    @Deprecated
    private Set<OrderDetail> orderDetails;
    private Map<String, Object> config;
    /**
     * Controls whether the API call blocks waiting for a terminal state (SYNC) or returns immediately (ASYNC).
     * Defaults to SYNC for backward compatibility.
     * SYNC requires Redis to be enabled; use ASYNC in Redis-free environments.
     */
    private WorkflowExecutionMode workflowExecutionMode = WorkflowExecutionMode.SYNC;
}
