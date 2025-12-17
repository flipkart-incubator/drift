package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

@Data
public class SuccessNode extends NodeDefinition {
    private String comment;
    @NotNull(message = "executionMode can't be null")
    private ExecutionMode executionMode;

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
