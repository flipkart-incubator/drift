package com.flipkart.drift.worker.resources;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.flipkart.drift.persistence.entity.WorkflowContextHB;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.google.inject.Inject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/v3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)

public class DriftWorkerResource {
    private final WorkflowContextHBService workflowContextHBService;

    @Inject
    public DriftWorkerResource(WorkflowContextHBService workflowContextHBService) {
        this.workflowContextHBService = workflowContextHBService;
    }

    @GET
    @Timed
    @Path("/workflow/context/{workflowId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ExceptionMetered
    public WorkflowContextHB getWorkflowState(@PathParam("workflowId") String workflowId) {
        Map<String, String> threadContext = new HashMap<>();
        threadContext.put("tenant", "cs");
        threadContext.put("userName", "ims-worker-context-api");
        threadContext.put("perfFlag", "false");
        return workflowContextHBService.getEntityById(workflowId, threadContext);
    }
}
