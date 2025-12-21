package com.flipkart.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flipkart.drift.commons.model.enums.NodeType;
import com.flipkart.drift.commons.model.enums.WaitType;
import com.flipkart.drift.commons.model.waitConfig.WaitConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class WaitNode extends NodeDefinition {

    @Valid
    @NotNull(message = "config can't be null")
    private WaitConfig config;

    @Override
    public NodeType getType() {
        return NodeType.WAIT;
    }

    @Override
    public void validateWFNodeFields() {
        super.validateWFNodeFields();
        //additional validation can be added here
    }

    @Override
    public void mergeRequestToEntity(NodeDefinition sourceNode) {
        WaitNode sourceWaitNode = (WaitNode) sourceNode;
        if (sourceWaitNode.config != null) {
            this.setConfig(sourceWaitNode.config);
        }
    }

    public <T extends WaitConfig> T getTypedConfig(Class<T> clazz) {
        return clazz.cast(config);
    }


    @JsonIgnore
    public WaitType getWaitType() {
        return config != null ? config.getWaitType() : null;
    }
}

