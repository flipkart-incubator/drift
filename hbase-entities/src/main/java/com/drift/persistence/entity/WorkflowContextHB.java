package com.drift.persistence.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.hbaseobjectmapper.Family;
import com.flipkart.hbaseobjectmapper.HBColumn;
import com.flipkart.hbaseobjectmapper.HBRecord;
import com.flipkart.hbaseobjectmapper.HBTable;
import com.drift.persistence.annotations.PrimaryKey;
import lombok.Data;

import java.util.Date;

@Data
@HBTable(name = "WorkflowContextHB", families = {@Family(name = "main")})
public class WorkflowContextHB implements HBRecord<String> {
    private String id;
    @PrimaryKey
    @HBColumn(family = "main", column = "workflowId")
    private String workflowId;
    @HBColumn(family = "main", column = "context")
    private ObjectNode context;
    @HBColumn(family = "main", column = "currentNodeRef")
    private Date createdAt;
    @HBColumn(family = "main", column = "updatedBy")
    private Date updatedAt;

    @Override
    public String composeRowKey() {
        return workflowId;
    }

    @Override
    public void parseRowKey(String rowKey) {
        id = rowKey;
    }
}

