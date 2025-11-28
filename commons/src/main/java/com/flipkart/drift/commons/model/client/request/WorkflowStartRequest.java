package com.flipkart.drift.commons.model.client.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.client.Customer;
import com.flipkart.drift.commons.model.client.IssueDetail;
import com.flipkart.drift.commons.model.client.OrderDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowStartRequest extends WorkflowRequest {
    @NotNull(message = "issueDetail cannot be null")
    @Deprecated
    private IssueDetail issueDetail;
    @Deprecated
    private Customer customer;
    @Deprecated
    private Set<OrderDetail> orderDetails;
    private Map<String, Object> config;
}