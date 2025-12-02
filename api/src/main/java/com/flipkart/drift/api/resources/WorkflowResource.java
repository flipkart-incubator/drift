package com.flipkart.drift.api.resources;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.api.service.TemporalService;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.google.inject.Inject;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkflowResource {
    private final TemporalService temporalService;

    @Inject
    public WorkflowResource(TemporalService temporalService) {
        this.temporalService = temporalService;
    }

    @POST
    @Timed
    @Path("/workflow/start")
    @ExceptionMetered
    public WorkflowResponse startWorkflow(@Valid WorkflowStartRequest workflowStartRequest) {
        return temporalService.startWorkflow(workflowStartRequest);
    }

    @PUT
    @Timed
    @Path("/workflow/resume/{workflowId}")
    @ExceptionMetered
    public WorkflowResponse resumeWorkflow(@Valid WorkflowResumeRequest workflowResumeRequest, @NotEmpty @PathParam("workflowId") String workflowId) {
        workflowResumeRequest.setWorkflowId(workflowId);
        return temporalService.resumeWorkflow(workflowResumeRequest);
    }

    @DELETE
    @Timed
    @Path("/workflow/terminate/{workflowId}")
    @ExceptionMetered
    public Response terminateWorkflow(@Valid WorkflowTerminateRequest workflowTerminateRequest, @NotEmpty @PathParam("workflowId") String workflowId) {
        if (workflowTerminateRequest == null) {
            workflowTerminateRequest = new WorkflowTerminateRequest("fallback: Workflow Completed");
        }
        workflowTerminateRequest.setWorkflowId(workflowId);
        temporalService.terminateWorkflow(workflowTerminateRequest);
        return Response.ok().status(200).build();
    }

    @GET
    @Timed
    @Path("/workflow/{workflowId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ExceptionMetered
    public WorkflowState getWorkflowState(@PathParam("workflowId") String workflowId) {
        return temporalService.getWorkflowState(workflowId);
    }

    @POST
    @Timed
    @Path("/workflow/{workflowId}/disconnected-node/execute")
    @ExceptionMetered
    public WorkflowUtilityResponse executeDisconnectedNode(@Valid WorkflowUtilityRequest workflowUtilityRequest, @PathParam("workflowId") String workflowId) {
        workflowUtilityRequest.setWorkflowId(workflowId);
        return temporalService.executeDisconnectedNode(workflowUtilityRequest);
    }
}




