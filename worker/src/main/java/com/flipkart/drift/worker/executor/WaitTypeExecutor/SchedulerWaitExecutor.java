package com.flipkart.drift.worker.executor.WaitTypeExecutor;

import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.node.WaitNode;
import com.flipkart.drift.commons.model.waitConfig.SchedulerWaitConfig;
import com.flipkart.drift.sdk.model.client.ScheduleRequest;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.sdk.spi.scheduler.SchedulerProvider;
import com.flipkart.drift.worker.Utility.SchedulerInitializer;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SchedulerWaitExecutor implements WaitTypeExecutor {

    private final SchedulerProvider schedulerProvider;


    @Inject
    public SchedulerWaitExecutor(SchedulerInitializer schedulerInitializer) {
        // Get the SchedulerProvider from the initialized SchedulerInitializer
        this.schedulerProvider = schedulerInitializer.getProvider();
        log.debug("Using schedulerProvider from SchedulerInitializer: {}",
                schedulerProvider.getClass().getSimpleName());
    }


    @Override
    public ActivityResponse executeWait(ActivityRequest<WaitNode> activityRequest) throws Exception {
        try {
            SchedulerWaitConfig config = activityRequest.getNodeDefinition().getTypedConfig(SchedulerWaitConfig.class);
            String workflowId = activityRequest.getWorkflowId();
            long scheduleTimeMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(config.getDuration());

            ScheduleRequest schedulerData = ScheduleRequest.builder()
                    .scheduleTimeInMillis(scheduleTimeMillis)
                    .workflowId(workflowId)
                    .build();

            Map<String, String> threadContext = activityRequest.getThreadContext();
            String perfFlagStr = threadContext != null ? threadContext.get("perfFlag") : null;
            boolean perfFlag = "true".equalsIgnoreCase(perfFlagStr);

            if (!perfFlag) {
                schedulerProvider.addSchedule(schedulerData);
            }

            return ActivityResponse.builder()
                    .workflowStatus(config.getExecutionMode() == ExecutionMode.SYNC
                            ? WorkflowStatus.WAITING
                            : WorkflowStatus.SCHEDULER_WAITING).build();
        } catch (Exception e) {
            throw new Exception("Exception while executing scheduler wait node", e);
        }
    }
}