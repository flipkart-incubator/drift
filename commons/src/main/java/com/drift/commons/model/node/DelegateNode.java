package com.drift.commons.model.node;

import com.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class DelegateNode extends NodeDefinition {

    @Override
    public NodeType getType() {
        return NodeType.DELEGATE;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        // No specific fields to merge for DelegateNode
    }
}
