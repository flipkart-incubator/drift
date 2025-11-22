package com.drift.worker.activities;

import com.drift.commons.model.client.request.WorkflowStartRequest;
import com.drift.commons.model.node.Workflow;
import com.drift.commons.model.node.WorkflowNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "fetchWorkflow")
public interface FetchWorkflowActivity {
    Workflow fetchWorkflow(String workflowId, String version, String tenant);
    Workflow fetchWorkflowBasedOnRequest(WorkflowStartRequest workflowStartRequest);
    Workflow fetchWorkflowBasedOnIssueId(String issueId, String tenant);
    WorkflowNode fetchWorkflowNode(String issueId, String nodeName, String tenant);
}