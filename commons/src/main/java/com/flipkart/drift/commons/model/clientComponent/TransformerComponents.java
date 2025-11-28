package com.flipkart.drift.commons.model.clientComponent;

import com.flipkart.drift.commons.model.componentDetail.ScriptedComponentDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@ToString
public class TransformerComponents extends ClientComponents {
    private ScriptedComponentDetail transformer;
    @Override
    protected String getComponentSpecificHashInput() {
        StringBuilder input = new StringBuilder();
        if (transformer != null) input.append(transformer);
        return input.toString();
    }
}

