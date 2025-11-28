package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.clientComponent.TransformerComponents;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class ContextOverrideNode extends NodeDefinition {
    private TransformerComponents transformerComponents;

    @Override
    public NodeType getType() {
        return NodeType.CONTEXT_OVERRIDE;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        ContextOverrideNode sourceContextOverrideNode = (ContextOverrideNode) sourceNode;
        if (sourceContextOverrideNode.getTransformerComponents() != null) {
            this.setTransformerComponents(sourceContextOverrideNode.getTransformerComponents());
        }
    }
}
