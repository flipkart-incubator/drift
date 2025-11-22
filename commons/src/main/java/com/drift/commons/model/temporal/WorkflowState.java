package com.drift.commons.model.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.drift.commons.model.client.IssueDetail;
import com.drift.commons.model.client.response.View;
import com.drift.commons.model.enums.WorkflowStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

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
}
