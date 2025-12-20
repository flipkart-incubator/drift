---
title: "Low-Level Design"
permalink: /05-LOW-LEVEL-DESIGN/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - Low-Level Design (LLD)

## Table of Contents
1. [Code Structure & Modules](#code-structure--modules)
2. [Data Structures](#data-structures)
3. [Sequence Diagrams](#sequence-diagrams)
4. [Database Schema](#database-schema)
5. [Configuration Management](#configuration-management)

---
## Code Structure & Modules

The project is organized as a multi-module Maven project, designed to separate public contracts, shared internal logic, and service implementations.

### 1. Java SDK (`java-sdk`)
**Purpose**: A lightweight, purely functional library containing public contracts and Service Provider Interfaces (SPIs). This module allows clients to interact with Drift and developers to build extensions without pulling in heavy service dependencies.
*   **Contracts**: Request/Response models (`WorkflowStartRequest`, `WorkflowResponse`, `WorkflowUtilityRequest`).
*   **SPIs**: Interfaces for extending platform capabilities.
    *   `TokenProvider`: Interface for injecting authentication tokens into HTTP nodes.
    *   `ABTestingProvider`: Interface for resolving A/B testing experiments.
    *   `SchedulerProvider`: Interface to plug external schedulers used by WAIT nodes.
*   **Factories**: Thread-safe factories (`TokenProviderFactory`, `ABTestingProviderFactory`) that use `ServiceLoader` to discover implementations at runtime.
    *   `SchedulerProviderFactory`: Discovers `SchedulerProvider` via `ServiceLoader`, defaults to `NoOpSchedulerProvider`.
*   **Client Models**:
    *   `ScheduleRequest`: `{ scheduleTimeInMillis, workflowId }` payload passed to `SchedulerProvider.addSchedule`.

### 2. Commons (`commons`)
**Purpose**: The shared internal core containing domain models, business logic for node types, and the persistence layer.
*   **Domain Models**: Core definitions for `Workflow`, `NodeDefinition`, and `WorkflowState`.
*   **Node Implementations**: Class hierarchy for all node types:
    *   `HttpNode`: HTTP API call configuration.
    *   `GroovyNode`: Dynamic script execution.
    *   `BranchNode`: Conditional routing logic.
    *   `InstructionNode`: UI/Widget definitions.
*   **Persistence Layer**: (Merged from `hbase-entities`)
    *   **Entities**: `WorkflowHB`, `NodeHB`, `WorkflowContextHB`.
    *   **DAOs**: `AbstractEntityDao` and concrete implementations for HBase access.

### 3. API Service (`api`)
**Purpose**: The RESTful gateway to the platform, built using Dropwizard.
*   **Resources**: JAX-RS resources exposing endpoints (`WorkflowResource`, `NodeDefinitionResource`, `WorkflowDefinitionResource`).
*   **Services**: `TemporalService` which acts as the bridge between REST requests and Temporal workflow stubs.
*   **Validation**: Request validation logic using Hibernate Validator.

### 4. Worker Service (`worker`)
**Purpose**: The execution engine running as a Temporal Worker.
*   **Workflow Implementation**: `GenericWorkflowImpl` - the main state machine that traverses the workflow graph.
*   **Activities**: Discrete units of work corresponding to node types:
    *   `HttpNodeActivityImpl`: Executes HTTP requests.
    *   `GroovyNodeActivityImpl`: Compiles and runs Groovy scripts.
    *   `InstructionNodeActivityImpl`: Processes instruction nodes.
*   **Executors**: `WorkflowNodeExecutor` manages the transition logic between nodes.
*   **SPI Loading**: Bootstraps the `java-sdk` factories to load any present extensions (e.g., `worker-flipkart`).

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
```

---

## Sequence Diagrams

### Workflow Start & Resume Sequence
<img width="1703" height="1864" alt="drift_flow drawio" src="https://github.com/user-attachments/assets/f8001019-66e9-423d-ad9f-e8e069246e0d" />

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

**Next**: [API Contracts](/06-DSL-CONTRACTS/)
