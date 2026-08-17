package com.flipkart.drift.commons.model.temporal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.NodeStatus;
import com.flipkart.drift.commons.model.enums.WaitSemantics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeState implements Serializable {
    private String instanceName;
    private NodeStatus status;
    private List<String> expectedEventTypes;
    private WaitSemantics waitSemantics;
}
