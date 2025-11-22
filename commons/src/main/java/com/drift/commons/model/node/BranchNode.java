package com.drift.commons.model.node;

import com.drift.commons.model.clientComponent.BranchComponents;
import com.drift.commons.model.enums.NodeType;
import lombok.Data;

import java.util.List;

@Data
public class BranchNode extends NodeDefinition {
    private List<BranchComponents> choices;
    private String defaultNode;

    @Override
    public NodeType getType() {
        return NodeType.BRANCH;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        BranchNode sourceBranchNode = (BranchNode) sourceNode;
        if (sourceBranchNode.getChoices() != null) {
            this.setChoices(sourceBranchNode.getChoices());
        }
        if (sourceBranchNode.getDefaultNode() != null) {
            this.setDefaultNode(sourceBranchNode.getDefaultNode());
        }
    }
}
