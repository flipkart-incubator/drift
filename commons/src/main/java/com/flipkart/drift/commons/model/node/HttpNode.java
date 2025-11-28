package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.clientComponent.HttpComponents;
import com.flipkart.drift.commons.model.clientComponent.TransformerComponents;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HttpNode extends NodeDefinition {
    private HttpComponents httpComponents;
    private TransformerComponents transformerComponents;

    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        HttpNode sourceHttpNode = (HttpNode) sourceNode;
        if (sourceHttpNode.getHttpComponents() != null) {
            this.setHttpComponents(sourceHttpNode.getHttpComponents());
        }
        if (sourceHttpNode.getTransformerComponents() != null) {
            this.setTransformerComponents(sourceHttpNode.getTransformerComponents());
        }
    }
}
