package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class SuccessNode extends NodeDefinition {
    private String comment;

    @Override
    public NodeType getType() {
        return NodeType.SUCCESS;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        SuccessNode sourceSuccessNode = (SuccessNode) sourceNode;
        if (sourceSuccessNode.getComment() != null) {
            this.setComment(sourceSuccessNode.getComment());
        }
    }
}
