package com.drift.worker.activities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.drift.commons.model.client.request.ViewResponse;
import com.drift.commons.model.client.response.View;
import com.drift.commons.model.enums.WorkflowStatus;
import com.drift.commons.model.node.ProcessorNode;
import com.drift.worker.model.activity.ActivityRequest;
import com.drift.worker.model.activity.ActivityResponse;
import com.drift.worker.service.WorkflowContextHBService;
import com.drift.commons.utils.ObjectMapperUtil;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;

import static com.drift.commons.utils.Constants.MAPPER;

@Slf4j
public class ProcessorNodeNodeActivityImpl extends BaseNodeActivityImpl<ProcessorNode> implements ProcessorNodeNodeActivity {

    @Inject
    public ProcessorNodeNodeActivityImpl(WorkflowContextHBService workflowContextHBService) {
        super(workflowContextHBService);
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<ProcessorNode> activityRequest) {
        try {
            View view = ObjectMapperUtil.INSTANCE.getObj(activityRequest.getContext().get(activityRequest.getNodeDefinition().getInstructionNodeRef()), View.class);
            ViewResponse viewResponse = ObjectMapperUtil.INSTANCE.getObj(activityRequest.getContext().get(activityRequest.getNodeDefinition().getInstructionNodeRef() + ":viewResponse"), ViewResponse.class);
            ObjectNode processorResponse = MAPPER.createObjectNode();
            processorResponse.put("isValidResponse", view.getInputOptions().size() == viewResponse.getSelectedOptions().size());
            return ActivityResponse.builder()
                    .nodeResponse(processorResponse)
                    .workflowStatus(WorkflowStatus.RUNNING).build();
        } catch (Exception e) {
            log.error("Exception while executing processor node {}", e.getMessage());
            throw Activity.wrap(e);
        }
    }
}
