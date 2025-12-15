package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ChildInvokeNode extends NodeDefinition {

    @NotNull(message = "modeOfSpawn can't be null")
    private ExecutionMode modeOfSpawn;
    @NotBlank(message = "childWorkflowId can't be blank")
    private String childWorkflowId;
    @NotBlank(message = "childWorkflowVersion can't be empty")
    private String childWorkflowVersion;

    @Override
    public NodeType getType() {
        return NodeType.CHILD_INVOKE;
    }


    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        ChildInvokeNode sourceChildInvokeNode = (ChildInvokeNode) sourceNode;
        if (sourceChildInvokeNode.getModeOfSpawn() != null) {
            this.setModeOfSpawn(sourceChildInvokeNode.getModeOfSpawn());
        }
        if (sourceChildInvokeNode.getChildWorkflowId() != null) {
            this.setChildWorkflowId(sourceChildInvokeNode.getChildWorkflowId());
        }
        if (sourceChildInvokeNode.getChildWorkflowVersion() != null) {
            this.setChildWorkflowVersion(sourceChildInvokeNode.getChildWorkflowVersion());
        }
    }

}
