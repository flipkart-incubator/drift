package com.flipkart.drift.persistence.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.hbaseobjectmapper.Family;
import com.flipkart.hbaseobjectmapper.HBColumn;
import com.flipkart.hbaseobjectmapper.HBRecord;
import com.flipkart.hbaseobjectmapper.HBTable;
import com.flipkart.drift.persistence.annotations.PrimaryKey;
import com.flipkart.drift.commons.model.node.Workflow;
import lombok.Data;

@Data
@HBTable(name = "WorkflowHB", families = {@Family(name = "main")})
public class WorkflowHB implements HBRecord<String> {
    private String id;
    @PrimaryKey
    @HBColumn(family = "main", column = "workflowKey")
    private String workflowKey;
    @HBColumn(family = "main", column = "workflowData")
    private String workflowData; // Change to String

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String composeRowKey() {
        return workflowKey;
    }

    @Override
    public void parseRowKey(String rowKey) {
        id = rowKey;
    }

    public void setWorkflowData(Workflow workflowData) {
        try {
            this.workflowData = objectMapper.writeValueAsString(workflowData);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing Workflow", e);
        }
    }

    public Workflow getWorkflowData() {
        try {
            return objectMapper.readValue(this.workflowData, Workflow.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing Workflow", e);
        }
    }
}

