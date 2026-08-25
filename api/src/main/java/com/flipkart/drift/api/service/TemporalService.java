package com.flipkart.drift.api.service;

import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.api.config.RedisConfiguration;
import com.flipkart.drift.api.exception.ApiException;
import com.flipkart.drift.api.filters.RequestThreadContext;
import com.flipkart.drift.api.service.builder.WorkflowDefinitionService;
import com.flipkart.drift.api.service.idempotency.IdempotencyMetrics;
import com.flipkart.drift.api.service.utils.Utility;
import com.flipkart.drift.commons.model.enums.ExecutionType;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.model.temporal.WorkflowState;
import com.flipkart.drift.sdk.model.enums.WorkflowExecutionMode;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.model.request.WorkflowResumeRequest;
import com.flipkart.drift.sdk.model.request.WorkflowStartRequest;
import com.flipkart.drift.sdk.model.request.WorkflowTerminateRequest;
import com.flipkart.drift.sdk.model.request.WorkflowUtilityRequest;
import com.flipkart.drift.sdk.model.response.View;
import com.flipkart.drift.sdk.model.response.WorkflowResponse;
import com.flipkart.drift.sdk.model.response.WorkflowUtilityResponse;
import com.flipkart.drift.workflows.GenericWorkflow;
import com.google.inject.Inject;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowQueryException;
import io.temporal.client.WorkflowStub;
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
    private final WorkflowServiceStubs serviceStub;
    private final WorkflowClient client;
    private final RedisPubSubService redisPubSubService;
    public static final String START = "start";
    public static final String RESUME = "resume";
    private final Utility utility;
    private final DriftConfiguration driftConfiguration;
    private final IdempotencyMetrics idempotencyMetrics;
    private final WorkflowDefinitionService workflowDefinitionService;

    private static final String WORKFLOW_ID_PARAM = "workflowId";
    private static final String VERSION_PARAM = "version";

    @Inject
    public TemporalService(RedisPubSubService redisPubSubService,
                           DriftConfiguration driftConfiguration,
                           Utility utility,
                           IdempotencyMetrics idempotencyMetrics,
                           WorkflowDefinitionService workflowDefinitionService) {
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
        this.workflowDefinitionService = workflowDefinitionService;
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

    private WorkflowResponse executeWorkflow(WorkflowStartRequest workflowStartRequest, boolean allowPurgeRetry) {
        String workflowId = workflowStartRequest.getWorkflowId();
        boolean idempotent = StringUtils.isNotBlank(RequestThreadContext.get().getResolvedWorkflowId());
        WorkflowIdReusePolicy reusePolicy = idempotent
                ? WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY
                : WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;
        WorkflowExecutionMode executionMode = resolveStartExecutionMode(workflowStartRequest);
        workflowStartRequest.setWorkflowExecutionMode(executionMode);

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

            if (executionMode == WorkflowExecutionMode.ASYNC) {
                WorkflowClient.start(workflow::startWorkflow, workflowStartRequest);
                if (idempotent) {
                    idempotencyMetrics.miss(RequestThreadContext.get().getTenant(), RequestThreadContext.get().getClientId());
                }
                return WorkflowResponse.builder()
                        .workflowId(workflowId)
                        .workflowStatus(WorkflowStatus.RUNNING)
                        .build();
            } else {
                // SYNC mode: block until workflow reaches a terminal state via Redis
                RedisConfiguration redisConfiguration = driftConfiguration.getRedisConfiguration();
                if (redisConfiguration != null && !redisConfiguration.isRedisEnabled()) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "SYNC execution mode requires Redis to be enabled. " +
                            "Set executionMode=ASYNC or enable Redis (redisEnabled=true).");
                }
                redisPubSubService.subscribeAndExecute(workflowId, () -> {
                    WorkflowClient.start(workflow::startWorkflow, workflowStartRequest);
                    return null;
                }, START);
                if (idempotent) {
                    idempotencyMetrics.miss(RequestThreadContext.get().getTenant(), RequestThreadContext.get().getClientId());
                }
                return buildResponseAndReturn(workflow);
            }
        } catch (WorkflowExecutionAlreadyStarted e) {
            return resolveAlreadyStartedWorkflow(workflowStartRequest, allowPurgeRetry);
        } catch (ApiException e) {
            throw e;
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

    private WorkflowResponse resolveAlreadyStartedWorkflow(WorkflowStartRequest workflowStartRequest,
                                                            boolean allowPurgeRetry) {
        String workflowId = workflowStartRequest.getWorkflowId();
        String tenant = RequestThreadContext.get().getTenant();
        String clientId = RequestThreadContext.get().getClientId();
        log.info("Workflow already started for idempotent wfId={}", workflowId);
        idempotencyMetrics.alreadyStarted(tenant, clientId);
        try {
            GenericWorkflow existing = client.newWorkflowStub(GenericWorkflow.class, workflowId);
            WorkflowResponse response = buildResponseAndReturn(existing);
            RequestThreadContext.get().setResolvedFromExistingWorkflow(true);
            return response;
        } catch (WorkflowNotFoundException | WorkflowQueryException notFound) {
            idempotencyMetrics.historyPurged(tenant, clientId);
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

            // Request-level mode takes priority (external event callers set ASYNC here).
            // Falls back to the mode persisted when the workflow was started.
            WorkflowState currentState = workflow.getWorkflowState();
            WorkflowExecutionMode executionMode = resolveResumeExecutionMode(
                    workflowResumeRequest, currentState);

            if (executionMode == WorkflowExecutionMode.ASYNC) {
                workflow.resumeWorkflow(workflowResumeRequest);
                return buildResponseAndReturn(workflow);
            } else {
                RedisConfiguration redisConfiguration = driftConfiguration.getRedisConfiguration();
                if (redisConfiguration != null && !redisConfiguration.isRedisEnabled()) {
                    throw new ApiException(Response.Status.BAD_REQUEST,
                            "SYNC execution mode requires Redis to be enabled. " +
                            "Set executionMode=ASYNC or enable Redis (redisEnabled=true).");
                }
                redisPubSubService.subscribeAndExecute(workflowResumeRequest.getWorkflowId(), () -> {
                    workflow.resumeWorkflow(workflowResumeRequest);
                    return null;
                }, RESUME);
                return buildResponseAndReturn(workflow);
            }
        } catch (ApiException e) {
            throw e;
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

    public void unsidelineWorkflow(String workflowId, String nodeId) {
        try {
            GenericWorkflow workflow = client.newWorkflowStub(GenericWorkflow.class, workflowId);
            workflow.unsidelineWorkflow(nodeId);
        } catch (WorkflowNotFoundException e) {
            throw new ApiException(Response.Status.NOT_FOUND, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (WorkflowException e) {
            log.error(WORKFLOW_EXCEPTION, e.getMessage(), e);
            throw new ApiException(Response.Status.EXPECTATION_FAILED, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during node unsideline: {}", e.getMessage(), e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to unsideline node: " + e.getMessage());
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

    private WorkflowExecutionMode resolveStartExecutionMode(WorkflowStartRequest request) {
        Workflow workflow = loadWorkflowForRequest(request);
        if (workflow != null && workflow.isParallel()) {
            return workflow.getWorkflowExecutionMode() != null
                    ? workflow.getWorkflowExecutionMode()
                    : WorkflowExecutionMode.ASYNC;
        }
        return request.getWorkflowExecutionMode() != null
                ? request.getWorkflowExecutionMode()
                : WorkflowExecutionMode.SYNC;
    }

    private WorkflowExecutionMode resolveResumeExecutionMode(WorkflowResumeRequest request,
                                                               WorkflowState state) {
        if (state.getExecutionType() == ExecutionType.PARALLEL) {
            return state.getWorkflowExecutionMode() != null
                    ? state.getWorkflowExecutionMode()
                    : WorkflowExecutionMode.ASYNC;
        }
        if (request.getWorkflowExecutionMode() != null) {
            return request.getWorkflowExecutionMode();
        }
        return state.getWorkflowExecutionMode() != null
                ? state.getWorkflowExecutionMode()
                : WorkflowExecutionMode.SYNC;
    }

    private Workflow loadWorkflowForRequest(WorkflowStartRequest request) {
        try {
            if (request.getParams() != null) {
                Object wfId = request.getParams().get(WORKFLOW_ID_PARAM);
                Object version = request.getParams().get(VERSION_PARAM);
                if (wfId != null && version != null) {
                    return workflowDefinitionService.getWorkflowById(
                            wfId.toString(), version.toString(), true);
                }
            }
        } catch (Exception e) {
            log.debug("Could not pre-load workflow DSL for execution mode: {}", e.getMessage());
        }
        return null;
    }
}
