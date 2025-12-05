---
title: "Low-Level Design"
permalink: /05-LOW-LEVEL-DESIGN/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - Low-Level Design (LLD)

## Table of Contents
1. [Data Structures](#data-structures)
2. [Sequence Diagrams](#sequence-diagrams)
3. [Database Schema](#database-schema)
4. [Configuration Management](#configuration-management)

---
## Data Structures

### Workflow Context Structure

The workflow context is a JSON object that maintains the state of a workflow execution. It consists of two main sections:

*   **`_global`**: Contains the running context of the workflow. It stores the output of each executed node, keyed by the node's instance name (e.g., `"node002"`). This data persists throughout the workflow's lifecycle and is accessible by subsequent nodes for decision-making or data transformation.
*   **`_enum_store`**: Loads and maintains access to all defined lookup keys (ex: service VIPs, configs etc.) required by the workflow. This ensures consistent access to static values across the workflow execution.

```json
{
  "_global": {
    "incidentId": "INC-12345",
    "workflowId": "wf-abc-123",
    "issueDetail": {
      "issueId": "return_request",
      "issueType": "REFUND",
      "priority": "HIGH"
    },
    "customer": {
      "customerId": "CUST-001",
      "name": "John Doe",
      "email": "john@example.com"
    },
    "orderDetail": {
      "orderId": "ORD-789",
      "amount": 1500.00
    },
     "return_possible_options_view:viewResponse": {
        "selectedOptions": {
           "possible_actions": "REFUND"
        }
     },
    "node002": {
      "refundAmount": 1500.00,
      "refundMethod": "BANK"
    }
  },
  "_enum_store": {
    "ISSUE_TYPES": ["REFUND", "REPLACEMENT", "CANCELLATION"],
    "STATUS_CODES": ["NEW", "IN_PROGRESS", "COMPLETED"],
    "OMS_VIP": "127.0.0.1"
  }
}
```

### Activity Request/Response Structure

**ActivityRequest<T extends NodeDefinition>**
- `nodeDefinition` - Node configuration (typed)
- `context` - Workflow context (_global)
- `threadContext` - Thread-specific data (tenant, client, perf flags)
- `nodeInstanceId` - Unique node execution ID

**ActivityResponse**
- `workflowStatus` - RUNNING, WAITING, COMPLETED, FAILED
- `nodeResponse` - Node execution result (JsonNode)
- `nextNode` - Next node to execute (for BRANCH nodes)
- `errorMessage` - Error details if failed

**ActivityThinRequest** (Lightweight version)
- `nodeId` - Node identifier
- `version` - Node version
- `context` - Workflow context
- `threadContext` - Thread data

**ActivityThinResponse** (Lightweight version)
- `status` - Workflow status
- `nextNode` - Next node ID
- `result` - Execution result

### HBase Row Key Design

```
┌─────────────────────────────────────────────────────────────────┐
│                   HBASE ROW KEY PATTERNS                        │
└─────────────────────────────────────────────────────────────────┘

1. WORKFLOW DEFINITION TABLE (WorkflowHB)
   ─────────────────────────────────────────────
   Row Key: {workflowId}_{version}
   
   Examples:
   - "refund_workflow_v1"
   - "return_workflow_v2"
   - "order_cancellation_v1"
   
   Columns (Family: main):
   - workflowData: JSON serialized Workflow object
   
   Access Pattern: Point lookup by workflowId + version

2. NODE DEFINITION TABLE (NodeHB)
   ─────────────────────────────────────────────
   Row Key: {nodeId}_{version}
   
   Examples:
   - "validate_order_http_v1"
   - "calculate_refund_groovy_v2"
   - "check_eligibility_branch_v1"
   
   Columns (Family: main):
   - nodeData: JSON serialized NodeDefinition object
   
   Access Pattern: Point lookup by nodeId + version

3. WORKFLOW CONTEXT TABLE (WorkflowContextHB)
   ─────────────────────────────────────────────
   Row Key: {workflowExecutionId}
   
   Examples:
   - "wf-abc-123-2025-11-21-10-00-00"
   - "wf-xyz-456-2025-11-21-11-30-15"
   
   Columns (Family: main):
   - context: JSON serialized workflow context
   - currentNode: Current executing node
   - status: Workflow status
   - createdAt: Timestamp
   - updatedAt: Timestamp
   
   Access Pattern: Point lookup by workflow execution ID

DESIGN CONSIDERATIONS:
- Composite keys for versioning support
- Lexicographic ordering for range scans
- Avoid hotspotting (no timestamp prefix)
- Efficient point lookups
```

---

## Sequence Diagrams

### Workflow Start & Resume Sequence

> **Note**: The above design illustrates the sync mode of workflow interaction. Drift also supports an async mode, in which Redis Pub Sub is skipped and response is returned on a webhook, and the client is notified via webhook triggered at I/O or terminal nodes.

---
## Database Schema

### HBase Table Schemas

```
┌─────────────────────────────────────────────────────────────────┐
│                     WORKFLOWHB TABLE                            │
├─────────────────────────────────────────────────────────────────┤
│ Table Name: WorkflowHB                                          │
│ Column Family: main                                             │
│                                                                 │
│ Row Structure:                                                  │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Row Key: "{workflowId}_{version}"                           ││
│ │ ┌───────────────────────────────────────────────────────────┤│
│ │ │ Column Family: main                                       ││
│ │ │ ┌─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: workflowKey                                  ││
│ │ │ │ Value: "refund_workflow_v1" (String)                    ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: workflowData                                 ││
│ │ │ │ Value: "{\"id\":\"refund_workflow\",                    ││
│ │ │ │         \"startNode\":\"validate_order\",               ││
│ │ │ │         \"states\":{...}}" (JSON String)                ││
│ │ │ └─────────────────────────────────────────────────────────┤│
│ │ └───────────────────────────────────────────────────────────┤│
│ └─────────────────────────────────────────────────────────────┤│
│                                                                 │
│ Example Row:                                                    │
│   Row Key: "refund_workflow_v1"                                 │
│   main:workflowKey = "refund_workflow_v1"                       │
│   main:workflowData = "{...Workflow JSON...}"                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        NODEHB TABLE                             │
├─────────────────────────────────────────────────────────────────┤
│ Table Name: NodeHB                                              │
│ Column Family: main                                             │
│                                                                 │
│ Row Structure:                                                  │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Row Key: "{nodeId}_{version}"                               ││
│ │ ┌───────────────────────────────────────────────────────────┤│
│ │ │ Column Family: main                                       ││
│ │ │ ┌─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: nodeKey                                      ││
│ │ │ │ Value: "validate_order_http_v1" (String)                ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: nodeData                                     ││
│ │ │ │ Value: "{\"id\":\"validate_order_http\",               ││
│ │ │ │         \"type\":\"HTTP\",                              ││
│ │ │ │         \"httpComponents\":{...}}" (JSON String)        ││
│ │ │ └─────────────────────────────────────────────────────────┤│
│ │ └───────────────────────────────────────────────────────────┤│
│ └─────────────────────────────────────────────────────────────┤│
│                                                                 │
│ Example Row:                                                    │
│   Row Key: "validate_order_http_v1"                             │
│   main:nodeKey = "validate_order_http_v1"                       │
│   main:nodeData = "{...HttpNode JSON...}"                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  WORKFLOWCONTEXTHB TABLE                        │
├─────────────────────────────────────────────────────────────────┤
│ Table Name: WorkflowContextHB                                   │
│ Column Family: main                                             │
│                                                                 │
│ Row Structure:                                                  │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Row Key: "{workflowExecutionId}"                            ││
│ │ ┌───────────────────────────────────────────────────────────┤│
│ │ │ Column Family: main                                       ││
│ │ │ ┌─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: contextKey                                   ││
│ │ │ │ Value: "wf-abc-123" (String)                            ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: context                                      ││
│ │ │ │ Value: "{\"_global\":{...},                             ││
│ │ │ │         \"_enum_store\":{...}}" (JSON String)           ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: currentNode                                  ││
│ │ │ │ Value: "node003" (String)                               ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: status                                       ││
│ │ │ │ Value: "RUNNING" (String)                               ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: createdAt                                    ││
│ │ │ │ Value: 1700567890000 (Long - epoch millis)             ││
│ │ │ ├─────────────────────────────────────────────────────────┤│
│ │ │ │ Qualifier: updatedAt                                    ││
│ │ │ │ Value: 1700567920000 (Long - epoch millis)             ││
│ │ │ └─────────────────────────────────────────────────────────┤│
│ │ └───────────────────────────────────────────────────────────┤│
│ └─────────────────────────────────────────────────────────────┤│
│                                                                 │
│ Example Row:                                                    │
│   Row Key: "wf-abc-123"                                         │
│   main:contextKey = "wf-abc-123"                                │
│   main:context = "{...context JSON...}"                         │
│   main:currentNode = "node003"                                  │
│   main:status = "RUNNING"                                       │
│   main:createdAt = 1700567890000                                │
│   main:updatedAt = 1700567920000                                │
└─────────────────────────────────────────────────────────────────┘

HBASE CONFIGURATION:
- Replication Factor: 3
- Compression: SNAPPY
- Block Cache: Enabled
- Bloom Filter: ROW (for point lookups)
- TTL: None (permanent storage)
```

---

## Configuration Management

### Environment-based Configuration

```yaml
┌─────────────────────────────────────────────────────────────────┐
│           api/src/main/resources/config/configuration.yaml      │
├─────────────────────────────────────────────────────────────────┤
server:
  applicationConnectors:
    - type: http
      port: 8000
  adminConnectors:
    - type: http
      port: 8001

redisConfiguration:
  password: ${REDIS_PASSWORD}
  master: ${REDIS_MASTER}
  sentinels: ${REDIS_SENTINELS}
  prefix: ${REDIS_PREFIX}
  maxTotal: 50
  maxWaitMillis: 100
  maxIdle: 25
  minIdle: 25
  testOnBorrow: true
  blockWhenExhausted: true

temporalFrontEnd: ${TEMPORAL_FRONTEND}
temporalTaskQueue: ${TEMPORAL_TASK_QUEUE}

driftConfigBucket: ${DRIFT_CONFIG_BUCKET}
hbaseConfigBucket: ${HBASE_CONFIG_BUCKET}

hadoopUserName: ${HADOOP_USERNAME}
hadoopLoginUser: ${HADOOP_LOGIN_USER}
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│          worker/src/main/resources/config/configuration.yaml    │
├─────────────────────────────────────────────────────────────────┤
server:
  applicationConnectors:
    - type: http
      port: 7200
  adminConnectors:
    - type: http
      port: 7201

redisConfiguration:
  password: ${REDIS_PASSWORD}
  master: ${REDIS_MASTER}
  sentinels: ${REDIS_SENTINELS}
  prefix: ${REDIS_PREFIX}
  maxTotal: 50
  maxWaitMillis: 100
  maxIdle: 25
  minIdle: 25

enumStoreBucket: ${ENUM_STORE_BUCKET}

prometheusConfig:
  port: 9090
  path: "/metrics"

workerDynamicOptions:
  workflowTaskPoller: 20
  activityTaskPoller: 50
  workflowCacheSize: 600
  maxWorkflowThreadCount: 800

abConfiguration:
  isABEnabled: ${AB_ENABLED}
  clientId: ${AB_CLIENT_ID}
  tenantId: ${AB_TENANT_ID}
  endpoint: ${AB_ENDPOINT}
  clientSecretKey: ${AB_CLIENT_SECRET_KEY}

hadoopUserName: ${HADOOP_USERNAME}
hadoopLoginUser: ${HADOOP_LOGIN_USER}

authClientName: ${AUTH_CLIENT_NAME}
authClientUrl: ${AUTH_CLIENT_URL}
authClientSecret: ${AUTH_CLIENT_SECRET}

temporalTaskQueue: ${TEMPORAL_TASK_QUEUE}
temporalFrontEnd: ${TEMPORAL_FRONTEND}
└─────────────────────────────────────────────────────────────────┘
```

---

**Next**: [API Contracts](/06-CONTRACTS/)
