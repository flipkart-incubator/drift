package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.drift.commons.model.clientComponent.VariableAttributeComponent;
import com.flipkart.drift.commons.model.enums.WorkflowStatus;
import com.flipkart.drift.commons.model.instruction.Option;
import com.flipkart.drift.commons.model.node.InstructionNode;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.commons.model.resolvedDetails.AttributeDetails;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.worker.stringResolver.StringResolver;
import com.flipkart.drift.worker.translator.ClientResolvedDetailBuilder;
import com.google.inject.Inject;
import io.temporal.activity.Activity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import com.flipkart.drift.worker.service.WorkflowConfigStoreService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.flipkart.drift.commons.utils.Constants.MAPPER;
import static com.flipkart.drift.commons.utils.Constants.Workflow.GLOBAL;
import static com.flipkart.drift.commons.utils.Constants.Workflow.ENUM_STORE;

@Slf4j
public class InstructionNodeActivityImpl extends BaseNodeActivityImpl<InstructionNode> implements InstructionNodeActivity {

    private final WorkflowConfigStoreService workflowConfigStoreService;

    @Inject
    public InstructionNodeActivityImpl(StringResolver stringResolver, WorkflowContextHBService workflowContextHBService,
                                       WorkflowConfigStoreService workflowConfigStoreService) {
        super(workflowContextHBService);
        this.workflowConfigStoreService = workflowConfigStoreService;
    }

    @Override
    public ActivityResponse executeNode(ActivityRequest<InstructionNode> activityRequest) {
        try {
            JsonNode response = buildInstructionResponse(activityRequest);
            //resolving disposition
            AttributeDetails attributeDetails = new AttributeDetails("");
            if (Optional.ofNullable(activityRequest.getNodeDefinition().getDisposition()).isPresent()) {
                attributeDetails = resolveAttributeDetails(activityRequest.getNodeDefinition().getDisposition(),
                        activityRequest.getContext(),
                        activityRequest.getNodeDefinition().getVersion(),
                        true);
            }
            //resolving workflowStatus
            AttributeDetails workflowAttributeDetails = null;
            if (Optional.ofNullable(activityRequest.getNodeDefinition().getWorkflowStatus()).isPresent()) {
                workflowAttributeDetails = resolveAttributeDetails(activityRequest.getNodeDefinition().getWorkflowStatus(),
                        activityRequest.getContext(),
                        activityRequest.getNodeDefinition().getVersion(),
                        false);
            }

            WorkflowStatus workflowStatus;
            if (workflowAttributeDetails != null) {
                workflowStatus = WorkflowStatus.valueOf(workflowAttributeDetails.getAttribute());
            } else {
                workflowStatus = activityRequest.getIsTerminal() ? WorkflowStatus.COMPLETED : WorkflowStatus.WAITING;
            }

            return ActivityResponse.builder()
                    .disposition(attributeDetails.getAttribute())
                    .workflowStatus(workflowStatus)
                    .nodeResponse(response).build();
        } catch (Exception e) {
            log.error("Exception while executing instruction node", e);
            throw Activity.wrap(e);
        }
    }

    private AttributeDetails resolveAttributeDetails(VariableAttributeComponent variableAttributeComponent, JsonNode context, String version, Boolean includeEnumMap) {
        AttributeDetails attributeDetails = null;
        ObjectNode contextWrapper = MAPPER.createObjectNode();
        contextWrapper.set(GLOBAL,context);
        if (includeEnumMap) {
            contextWrapper.set(ENUM_STORE, MAPPER.valueToTree(workflowConfigStoreService.getEnumMapping()));
        }
        attributeDetails = ClientResolvedDetailBuilder
                .evaluateGroovy(variableAttributeComponent,
                        version,
                        contextWrapper.set(GLOBAL, context), AttributeDetails.class);
        return attributeDetails;
    }

    private JsonNode buildInstructionResponse(ActivityRequest<InstructionNode> activityRequest) {
        // 1. resolve instructions -- irrelevant since templates are not handled at client
//        List<Instruction> resolvedInstructions = resolveInstructions(activityRequest.getNodeDefinition().getInstructions(), activityRequest.getContext());

        // 2. resolve options
        List<Option> resolvedOptions = resolveOptions(activityRequest.getNodeDefinition().getInputOptions(), activityRequest.getContext());

        //resolving layoutId
        AttributeDetails layoutAttributeDetails = null;
        if (Optional.ofNullable(activityRequest.getNodeDefinition().getLayoutId()).isPresent()) {
            layoutAttributeDetails = resolveAttributeDetails(activityRequest.getNodeDefinition().getLayoutId(),
                    activityRequest.getContext(),
                    activityRequest.getNodeDefinition().getVersion(),
                    false);
        }

        ObjectNode response = MAPPER.createObjectNode();
        response.putPOJO("inputOptions", resolvedOptions);

        // Add layoutId to response if resolved
        if (layoutAttributeDetails != null) {
            response.put("layoutId", layoutAttributeDetails.getAttribute());
        }

        return response;
    }

    // NOTE : Spec only supports static / dynamic options individually, not a mix of both
    // If it is dynamic, then only one context variable is allowed
    private List<Option> resolveOptions(List<Option> inputOptions,
                                        JsonNode context) {
        List<Option> resolvedOptions = new ArrayList<>();
        if (inputOptions == null || inputOptions.isEmpty()) {
            return resolvedOptions;
        }
        boolean isDynamic = StringUtils.isNotEmpty(inputOptions.get(0).getPossibleDynamicValues());

       if (isDynamic) {
            String dynamicValueField = inputOptions.get(0).getPossibleDynamicValues();
            JsonNode currentNode = context;

            // Split by dots and traverse the nested structure
            String[] fields = dynamicValueField.split("\\.");
            for (String field : fields) {
                currentNode = currentNode.get(field);
                if (currentNode == null) {
                    return new ArrayList<>();  // Return empty list if path doesn't exist
                }
            }

            return MAPPER.convertValue(currentNode, new TypeReference<List<Option>>() {});
        }
        return inputOptions;
    }

//    private List<Instruction> resolveInstructions(List<Instruction> instructions,
//                                                  JsonNode context) {
//        List<Instruction> resolvedInstructions = new ArrayList<>();
//        Map<String, Object> variableMapping = MAPPER.convertValue(context.get(variableMap), Map.class);
//
//        for (Instruction instruction : instructions) {
//            String resolvedMsg = stringResolver.resolve(instruction.getMessage(), variableMapping);
//            resolvedInstructions.add(new Instruction(instruction.getId(), resolvedMsg));
//        }
//        return resolvedInstructions;
//    }

}
