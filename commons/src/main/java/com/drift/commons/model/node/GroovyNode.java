package com.drift.commons.model.node;

import com.drift.commons.model.componentDetail.ScriptedComponentDetail;
import com.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class GroovyNode extends NodeDefinition {
    private ScriptedComponentDetail transformer;

    @Override
    public NodeType getType() {
        return NodeType.GROOVY;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        GroovyNode sourceGroovyNode = (GroovyNode) sourceNode;
        if (sourceGroovyNode.getTransformer() != null) {
            this.setTransformer(sourceGroovyNode.getTransformer());
        }
    }
}
