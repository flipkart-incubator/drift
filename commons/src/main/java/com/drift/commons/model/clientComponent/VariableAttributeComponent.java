package com.drift.commons.model.clientComponent;

import com.drift.commons.model.componentDetail.ComponentDetail;
import com.drift.commons.model.value.StringValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VariableAttributeComponent extends ClientComponents {
    private ComponentDetail<StringValue, String> attribute;
    @Override
    protected String getComponentSpecificHashInput() {
        StringBuilder input = new StringBuilder();
        if (attribute != null) input.append(attribute);
        return input.toString();
    }
}

