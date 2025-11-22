package com.drift.persistence.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.hbaseobjectmapper.Family;
import com.flipkart.hbaseobjectmapper.HBColumn;
import com.flipkart.hbaseobjectmapper.HBRecord;
import com.flipkart.hbaseobjectmapper.HBTable;
import com.drift.persistence.annotations.PrimaryKey;
import com.drift.commons.model.node.NodeDefinition;
import lombok.Data;

@Data
@HBTable(name = "NodeHB", families = {@Family(name = "main")})
public class NodeHB implements HBRecord<String> {
    private String id;
    @PrimaryKey
    @HBColumn(family = "main", column = "nodeKey")
    private String nodeKey;
    @HBColumn(family = "main", column = "nodeData")
    private String nodeData; // Change to String

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String composeRowKey() {
        return nodeKey;
    }

    @Override
    public void parseRowKey(String rowKey) {
        id = rowKey;
    }

    public void setNodeData(NodeDefinition nodeData) {
        try {
            this.nodeData = objectMapper.writeValueAsString(nodeData);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing NodeDefinition", e);
        }
    }

    public NodeDefinition getNodeData() {
        try {
            return objectMapper.readValue(this.nodeData, NodeDefinition.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing NodeDefinition", e);
        }
    }
}

