package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ChildNode extends NodeDefinition {

    @NotNull(message = "executionMode can't be null")
    private ExecutionMode executionMode;
    @NotBlank(message = "childWorkflowId can't be blank")
    private String childWorkflowId;
    @NotBlank(message = "childWorkflowVersion can't be empty")
    private String childWorkflowVersion;

    @Override
    public NodeType getType() {
        return NodeType.CHILD;
    }


    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        ChildNode sourceChildNode = (ChildNode) sourceNode;
        if (sourceChildNode.getExecutionMode() != null) {
            this.setExecutionMode(sourceChildNode.getExecutionMode());
        }
        if (sourceChildNode.getChildWorkflowId() != null) {
            this.setChildWorkflowId(sourceChildNode.getChildWorkflowId());
        }
        if (sourceChildNode.getChildWorkflowVersion() != null) {
            this.setChildWorkflowVersion(sourceChildNode.getChildWorkflowVersion());
        }
    }

}
