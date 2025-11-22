package com.drift.commons.model.node;

import com.drift.commons.model.enums.NodeType;
import lombok.Data;

@Data
public class ProcessorNode extends NodeDefinition {
    private String instructionNodeRef;

    @Override
    public NodeType getType() {
        return NodeType.PROCESSOR;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        ProcessorNode sourceProcessorNode = (ProcessorNode) sourceNode;
        if (sourceProcessorNode.getInstructionNodeRef() != null) {
            this.setInstructionNodeRef(sourceProcessorNode.getInstructionNodeRef());
        }
    }
}
