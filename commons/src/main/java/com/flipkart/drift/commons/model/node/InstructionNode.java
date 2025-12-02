package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.model.clientComponent.VariableAttributeComponent;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.sdk.model.instruction.Option;
import lombok.Data;

import java.util.List;

@Data
public class InstructionNode extends NodeDefinition {
    private List<Option> inputOptions;
    private VariableAttributeComponent disposition;
    private VariableAttributeComponent workflowStatus;
    private VariableAttributeComponent layoutId;

    @Override
    public NodeType getType() {
        return NodeType.INSTRUCTION;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        InstructionNode sourceInstructionNode = (InstructionNode) sourceNode;
        if (sourceInstructionNode.getInputOptions() != null) {
            this.setInputOptions(sourceInstructionNode.getInputOptions());
        }
        if (sourceInstructionNode.getDisposition() != null) {
            this.setDisposition(sourceInstructionNode.getDisposition());
        }
        if (sourceInstructionNode.getWorkflowStatus() != null) {
            this.setWorkflowStatus(sourceInstructionNode.getWorkflowStatus());
        }
        if (sourceInstructionNode.getLayoutId() != null) {
            this.setLayoutId(sourceInstructionNode.getLayoutId());
        }
    }
}
