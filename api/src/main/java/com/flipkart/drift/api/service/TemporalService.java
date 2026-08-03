package com.flipkart.drift.api.service;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.service.idempotency.IdempotencyMetrics;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.workflows.GenericWorkflow;
import com.google.inject.Inject;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.*;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import java.time.Duration;

import static com.flipkart.drift.commons.utils.Constants.Workflow.WORKFLOW_EXCEPTION;


@Slf4j
public class TemporalService {
    private final WorkflowServiceStubsOptions stubsOptions;
    // Create a stub that accesses a Temporal Service
    private final WorkflowServiceStubs serviceStub;
    private final WorkflowClient client;
    private final RedisPubSubService redisPubSubService;
    public static final String START = "start";
    public static final String RESUME = "resume";
    private final Utility utility;
    private final DriftConfiguration driftConfiguration;
    private final IdempotencyMetrics idempotencyMetrics;

    @Inject
    public TemporalService(RedisPubSubService redisPubSubService,
                           DriftConfiguration driftConfiguration,
                           Utility utility,
                           IdempotencyMetrics idempotencyMetrics) {
        this.stubsOptions = WorkflowServiceStubsOptions
                .newBuilder()
                .setTarget(driftConfiguration.getTemporalFrontEnd())
                .build();
        this.serviceStub = WorkflowServiceStubs.newServiceStubs(stubsOptions);
        this.redisPubSubService = redisPubSubService;
        this.client = WorkflowClient.newInstance(serviceStub);
        this.utility = utility;
        this.driftConfiguration = driftConfiguration;
        this.idempotencyMetrics = idempotencyMetrics;
    }

    public WorkflowResponse startWorkflow(WorkflowStartRequest workflowStartRequest) {
        String resolvedWorkflowId = RequestThreadContext.get().getResolvedWorkflowId();
        if (StringUtils.isNotBlank(resolvedWorkflowId)) {
            workflowStartRequest.setWorkflowId(resolvedWorkflowId);
        } else if (workflowStartRequest.getWorkflowId() == null || workflowStartRequest.getWorkflowId().isBlank()) {
            workflowStartRequest.setWorkflowId(utility.generateWorkflowId(null, false));
        }
        workflowStartRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
        return executeWorkflow(workflowStartRequest);
    }

    public WorkflowResponse executeWorkflow(WorkflowStartRequest workflowStartRequest) {
        return executeWorkflow(workflowStartRequest, true);
    }

    /**
     * @param allowPurgeRetry whether an already-started-but-history-purged race should
     *                        be resolved by retrying the start once as a fresh workflow. Set to
     *                        {@code false} on the retry attempt itself to guarantee termination
     *                        (at most one retry per request, never unbounded recursion).
     */
    private WorkflowResponse executeWorkflow(WorkflowStartRequest workflowStartRequest, boolean allowPurgeRetry) {
        String workflowId = workflowStartRequest.getWorkflowId();
        boolean idempotent = StringUtils.isNotBlank(RequestThreadContext.get().getResolvedWorkflowId());
        WorkflowIdReusePolicy reusePolicy = idempotent
                ? WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY
                : WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;
        GenericWorkflow workflow;
        try {
            workflow = client.newWorkflowStub(
                    GenericWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(workflowId)
                            .setWorkflowExecutionTimeout(Duration.ofMinutes(1440))
                            .setTaskQueue(driftConfiguration.getTemporalTaskQueue())
                            .setWorkflowIdReusePolicy(reusePolicy)
                            .build()
            );
            redisPubSubService.subscribeAndExecute(workflowId, () -> {
                WorkflowClient.start(workflow::startWorkflow, workflowStartRequest);
                return null;
            }, START);
            return buildResponseAndReturn(workflow);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // Most-specific-exception-first: WorkflowExecutionAlreadyStarted extends
            // WorkflowException, so this catch MUST precede catch(WorkflowException) below,
            // otherwise it is unreachable dead code.
            return resolveAlreadyStartedWorkflow(workflowStartRequest, allowPurgeRetry);
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow start: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to start workflow: " + e.getMessage());
        }
    }

    /**
     * Resolves a duplicate start signaled by Temporal via {@code WorkflowExecutionAlreadyStarted}
     * (the primary, server-arbitrated de-dup mechanism). Fetches and returns the existing
     * workflow's state, marking the request as an idempotent replay. Handles the narrow
     * history-purged race: if the existing workflow's state can no longer be queried
     * because Temporal namespace retention has purged its history, meters that fact and retries
     * the start once as a fresh workflow rather than surfacing a 502.
     */
    private WorkflowResponse resolveAlreadyStartedWorkflow(WorkflowStartRequest workflowStartRequest,
                                                            boolean allowPurgeRetry) {
        String workflowId = workflowStartRequest.getWorkflowId();
        String tenant = RequestThreadContext.get().getTenant();
        log.info("Workflow already started for idempotent wfId={}", workflowId);
        idempotencyMetrics.alreadyStarted(tenant);
        try {
            GenericWorkflow existing = client.newWorkflowStub(GenericWorkflow.class, workflowId);
            WorkflowResponse response = buildResponseAndReturn(existing);
            RequestThreadContext.get().setResolvedFromExistingWorkflow(true);
            return response;
        } catch (WorkflowNotFoundException | WorkflowQueryException notFound) {
            // History-purged edge case: the existing execution's history is gone by the time we
            // query it. Not an error the caller should see -- meter it and treat the request as
            // a fresh start (Temporal no longer has state under this workflowId to conflict with).
            idempotencyMetrics.historyPurged(tenant);
            if (!allowPurgeRetry) {
                throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR,
                        "Workflow " + workflowId + " could not be started or resolved after history-purge retry");
            }
            return executeWorkflow(workflowStartRequest, false);
        }
    }

    public WorkflowResponse resumeWorkflow(WorkflowResumeRequest workflowResumeRequest) {
        try {
            workflowResumeRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowResumeRequest.getWorkflowId());
            redisPubSubService.subscribeAndExecute(workflowResumeRequest.getWorkflowId(), () -> {
                workflow.resumeWorkflow(workflowResumeRequest);
                return null;
            }, RESUME);
            return buildResponseAndReturn(workflow);
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow resume: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to resume workflow: " + e.getMessage());
        }
    }

    public void terminateWorkflow(WorkflowTerminateRequest workflowTerminateRequest) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowTerminateRequest.getWorkflowId());
            workflow.terminateWorkflow(workflowTerminateRequest);
            WorkflowStub untyped = WorkflowStub.fromTyped(workflow);
            untyped.cancel();
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during workflow termination: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to terminate workflow: " + e.getMessage());
        }
    }

    public WorkflowState getWorkflowState(String workflowId) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowId);
            return workflow.getWorkflowState();
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause().getMessage());
        }
    }

    public WorkflowUtilityResponse executeDisconnectedNode(WorkflowUtilityRequest workflowUtilityRequest) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowUtilityRequest.getWorkflowId());
            workflowUtilityRequest.setThreadContext(RequestThreadContext.get().getLegacyThreadContext());
            return workflow.executeDisconnectedNode(workflowUtilityRequest);
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause().getMessage());
        }
    }

    private WorkflowResponse buildResponseAndReturn(GenericWorkflow workflow) {
        WorkflowState workflowState = workflow.getWorkflowState();
        View view = workflowState.getView();
        return WorkflowResponse.builder()
                .disposition(workflowState.getDisposition())
                .errorMessage(workflowState.getErrorMessage())
                .incidentId(workflowState.getIncidentId())
                .workflowId(workflowState.getWorkflowId())
                .workflowStatus(workflowState.getStatus())
                .view(view)
                .build();
    }
}




