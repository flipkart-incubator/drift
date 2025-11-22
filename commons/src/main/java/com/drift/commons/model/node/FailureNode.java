package com.drift.commons.model.node;

import com.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class FailureNode extends NodeDefinition {
    private String error;

    @Override
    public NodeType getType() {
        return NodeType.FAILURE;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        FailureNode sourceFailureNode = (FailureNode) sourceNode;
        if (sourceFailureNode.getError() != null) {
            this.setError(sourceFailureNode.getError());
        }
    }
}
