package com.flipkart.drift.commons.model.node;

import com.flipkart.drift.commons.exception.ApiException;
import com.flipkart.drift.commons.model.enums.ExecutionMode;
import com.flipkart.drift.commons.model.enums.NodeType;
import lombok.Data;

import javax.ws.rs.core.Response;

@Data
public class SuccessNode extends NodeDefinition {
    private String comment;
    private ExecutionMode executionMode;

    @Override
    public NodeType getType() {
        return NodeType.SUCCESS;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        if (executionMode == null) {
            throw new ApiException(Response.Status.BAD_REQUEST, "executionMode shouldn't be null");
        }
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
