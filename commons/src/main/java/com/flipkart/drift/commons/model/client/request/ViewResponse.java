package com.flipkart.drift.commons.model.client.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewResponse {
    Map<String, Object> selectedOptions;
}
