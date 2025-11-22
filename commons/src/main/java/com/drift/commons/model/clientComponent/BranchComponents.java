package com.drift.commons.model.clientComponent;

import com.drift.commons.model.componentDetail.ScriptedComponentDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BranchComponents extends ClientComponents {
    private ScriptedComponentDetail rule;
    private String nextNode;
    @Override
    protected String getComponentSpecificHashInput() {
        StringBuilder input = new StringBuilder();
        if (rule != null) input.append(rule);
        if (nextNode != null) input.append(nextNode);
        return input.toString();
    }
}

