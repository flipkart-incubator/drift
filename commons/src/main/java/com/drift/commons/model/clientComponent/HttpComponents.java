package com.drift.commons.model.clientComponent;

import com.drift.commons.model.componentDetail.ComponentDetail;
import com.drift.commons.model.enums.HttpContentTypeEnum;
import com.drift.commons.model.enums.HttpMethod;
import com.drift.commons.model.value.StringValue;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@ToString
public class HttpComponents extends ClientComponents {
    private ComponentDetail<StringValue, String> url;
    private ComponentDetail<StringValue, Map> headers;
    private ComponentDetail<StringValue, Map> queryParams;
    private ComponentDetail<StringValue, Map> body;
    private HttpMethod method;
    private HttpContentTypeEnum contentType;
    private String targetClientId;

    @Override
    protected String getComponentSpecificHashInput() {
        StringBuilder input = new StringBuilder();
        if (url != null) input.append(url);
        if (headers != null) input.append(headers);
        if (queryParams != null) input.append(queryParams);
        if (body != null) input.append(body);
        if (method != null) input.append(method);
        if (contentType != null) input.append(contentType);
        if (targetClientId != null) input.append(targetClientId);
        return input.toString();
    }
}

