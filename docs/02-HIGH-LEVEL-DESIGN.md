# Drift Platform - High-Level Design (HLD)

## Table of Contents
1. [System Overview](#system-overview)
2. [Component Design](#component-design)
3. [Workflow Execution Model](#workflow-execution-model)
4. [Node Type System](#node-type-system)
5. [State Management](#state-management)
6. [Caching Strategy](#caching-strategy)
7. [Error Handling & Retry Logic](#error-handling--retry-logic)
8. [Performance Considerations](#performance-considerations)

---

## System Overview

Drift is designed as a microservices-based architecture with clear separation of concerns:

### Service Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│                     API Service                             │
│  Responsibilities:                                          │
│  • REST API exposure                                        │
│  • Request validation                                       │
│  • Workflow orchestration commands                          │
│  • Definition management (Workflows & Nodes)                │
│  • Client request filtering                                 │
│                                                             │
│  Does NOT:                                                  │
│  • Execute workflow logic                                   │
│  • Run activities                                           │
│  • Directly access HBase (only through cache)               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   Worker Service                            │
│  Responsibilities:                                          │
│  • Poll Temporal task queues                                │
│  • Execute workflow implementations                         │
│  • Run node activities (HTTP, Groovy, etc.)                 │
│  • Manage workflow state                                    │
│  • Persist context to HBase                                 │
│  • Dynamic script evaluation                                │
│  • External API interactions                                │
│                                                             │
│  Does NOT:                                                  │
│  • Expose REST APIs                                         │
│  • Handle client authentication                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Component Design

### 1. API Service Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                      API Service                               │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              HTTP Layer (Jetty/Dropwizard)               │  │
│  │                                                          │  │
│  │  ┌────────────────┐  ┌────────────────┐                │  │
│  │  │  Request       │  │  Response      │                │  │
│  │  │  Filter        │  │  Filter        │                │  │
│  │  │  • Tenant      │  │  • Headers     │                │  │
│  │  │  • Client ID   │  │  • Metrics     │                │  │
│  │  │  • Headers     │  │                │                │  │
│  │  └────────────────┘  └────────────────┘                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                 │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │              Resource Layer (JAX-RS)                    │  │
│  │                                                         │  │
│  │  ┌───────────────┐ ┌──────────────┐ ┌──────────────┐  │  │
│  │  │  Workflow     │ │   Node       │ │  Workflow    │  │  │
│  │  │  Resource     │ │ Definition   │ │ Definition   │  │  │
│  │  │               │ │  Resource    │ │  Resource    │  │  │
│  │  │ • start()     │ │ • create()   │ │ • create()   │  │  │
│  │  │ • resume()    │ │ • update()   │ │ • update()   │  │  │
│  │  │ • terminate() │ │ • get()      │ │ • get()      │  │  │
│  │  │ • getState()  │ │ • delete()   │ │ • delete()   │  │  │
│  │  └───────────────┘ └──────────────┘ └──────────────┘  │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼────────────────────────────────┐  │
│  │              Service Layer                                │  │
│  │                                                           │  │
│  │  ┌───────────────┐  ┌──────────────┐  ┌──────────────┐  │  │
│  │  │  Temporal     │  │    Node      │  │   Workflow   │  │  │
│  │  │  Service      │  │ Definition   │  │  Definition  │  │  │
│  │  │               │  │   Service    │  │   Service    │  │  │
│  │  │ • startWF()   │  │ • persist()  │  │ • persist()  │  │  │
│  │  │ • signalWF()  │  │ • retrieve() │  │ • retrieve() │  │  │
│  │  │ • queryWF()   │  │ • validate() │  │ • validate() │  │  │
│  │  │ • terminateWF│  │              │  │              │  │  │
│  │  └───────────────┘  └──────────────┘  └──────────────┘  │  │
│  └───────────────────────────┬────────────────────────────────┘  │
│                              │                                   │
│  ┌───────────────────────────▼───────────────────────────────┐  │
│  │              Integration Layer                            │  │
│  │                                                           │  │
│  │  ┌────────────────┐  ┌────────────┐  ┌──────────────┐   │  │
│  │  │   Temporal     │  │   HBase    │  │    Redis     │   │  │
│  │  │   Client       │  │   DAO      │  │   Client     │   │  │
│  │  │   (gRPC)       │  │            │  │  (Jedis)     │   │  │
│  │  └────────────────┘  └────────────┘  └──────────────┘   │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

#### Key API Service Classes

**DriftApplication** (Entrypoint)
- Initializes Dropwizard
- Configures Jackson ObjectMapper
- Registers Guice modules
- Registers resources and filters
- Enables environment variable substitution

**WorkflowResource** (REST Controller)
- `POST /v3/workflow/start` - Start new workflow
- `PUT /v3/workflow/resume/{workflowId}` - Resume paused workflow
- `DELETE /v3/workflow/terminate/{workflowId}` - Terminate workflow
- `GET /v3/workflow/{workflowId}` - Get workflow state
- `POST /v3/workflow/{workflowId}/disconnected-node/execute` - Execute standalone node

**TemporalService** (Business Logic)
- Manages Temporal workflow client
- Generates unique workflow IDs
- Creates workflow stubs with proper options
- Starts workflows asynchronously
- Signals workflows for resume/terminate operations
- Queries workflow state

**RequestFilter** (HTTP Filter)
- Extracts request headers (tenant, client ID, perf flags)
- Stores context in ThreadLocal for request scope
- Validates tenant and client credentials
- Ensures proper authentication before processing

---

### 2. Worker Service Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                    Worker Service                              │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         Temporal Worker (Polling Loop)                   │  │
│  │                                                          │  │
│  │  while (true) {                                          │  │
│  │    task = pollTaskQueue();                               │  │
│  │    if (task != null) {                                   │  │
│  │      executeTask(task);                                  │  │
│  │    }                                                     │  │
│  │  }                                                       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                 │
│  ┌──────────────────────────▼──────────────────────────────┐  │
│  │           Workflow Implementation Layer                 │  │
│  │                                                         │  │
│  │  ┌───────────────────────────────────────────────────┐  │  │
│  │  │         GenericWorkflowImpl                       │  │  │
│  │  │  @WorkflowMethod                                  │  │  │
│  │  │  startWorkflow(WorkflowStartRequest)              │  │  │
│  │  │    ├─ Initialize workflow state                   │  │  │
│  │  │    ├─ Persist initial context                     │  │  │
│  │  │    ├─ Fetch workflow DSL                          │  │  │
│  │  │    └─ Execute node loop                           │  │  │
│  │  │                                                    │  │  │
│  │  │  @SignalMethod                                    │  │  │
│  │  │  resumeWorkflow(WorkflowResumeRequest)            │  │  │
│  │  │    └─ Update context and continue                 │  │  │
│  │  │                                                    │  │  │
│  │  │  @QueryMethod                                     │  │  │
│  │  │  getWorkflowState(): WorkflowState                │  │  │
│  │  │    └─ Return current state                        │  │  │
│  │  └───────────────────────────────────────────────────┘  │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────▼────────────────────────────────┐  │
│  │           Node Execution Layer                            │  │
│  │                                                           │  │
│  │  ┌───────────────────────────────────────────────────┐   │  │
│  │  │        WorkflowNodeExecutor                       │   │  │
│  │  │                                                   │   │  │
│  │  │  executeNode(WorkflowNode currentNode)            │   │  │
│  │  │    ├─ Fetch node definition from cache/HBase     │   │  │
│  │  │    ├─ Determine node type                        │   │  │
│  │  │    ├─ Create activity stub                       │   │  │
│  │  │    ├─ Execute activity                           │   │  │
│  │  │    ├─ Handle response                            │   │  │
│  │  │    └─ Determine next node                        │   │  │
│  │  └───────────────────────────────────────────────────┘   │  │
│  └───────────────────────────┬───────────────────────────────┘  │
│                              │                                  │
│  ┌───────────────────────────▼──────────────────────────────┐  │
│  │             Activity Layer (Node Executors)              │  │
│  │                                                          │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │  │
│  │  │   HTTP      │  │   Groovy    │  │   Branch    │     │  │
│  │  │  Activity   │  │  Activity   │  │  Activity   │     │  │
│  │  │             │  │             │  │             │     │  │
│  │  │ • Resolve   │  │ • Load      │  │ • Evaluate  │     │  │
│  │  │   params    │  │   script    │  │   conditions│     │  │
│  │  │ • Execute   │  │ • Execute   │  │ • Determine │     │  │
│  │  │   HTTP call │  │   groovy    │  │   next node │     │  │
│  │  │ • Transform │  │ • Return    │  │             │     │  │
│  │  │   response  │  │   result    │  │             │     │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │  │
│  │                                                          │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │  │
│  │  │Instruction  │  │  Processor  │  │  Success/   │     │  │
│  │  │  Activity   │  │  Activity   │  │  Failure    │     │  │
│  │  │             │  │             │  │  Activity   │     │  │
│  │  │ • Generate  │  │ • Transform │  │ • Mark WF   │     │  │
│  │  │   UI form   │  │   data      │  │   complete  │     │  │
│  │  │ • Wait for  │  │ • Update    │  │ • Set final │     │  │
│  │  │   user input│  │   context   │  │   status    │     │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│  ┌───────────────────────────▼──────────────────────────────┐  │
│  │              Support Services                            │  │
│  │                                                          │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │  │
│  │  │  Workflow   │  │    Groovy    │  │      HTTP      │  │  │
│  │  │  Context    │  │   Translator │  │    Executor    │  │  │
│  │  │   HBase     │  │              │  │   (Retrofit)   │  │  │
│  │  │  Service    │  │ • Dynamic    │  │                │  │  │
│  │  │             │  │   script     │  │ • URL          │  │  │
│  │  │ • Persist   │  │   execution  │  │   resolution   │  │  │
│  │  │ • Retrieve  │  │ • Variable   │  │ • Header       │  │  │
│  │  │ • Update    │  │   resolution │  │   injection    │  │  │
│  │  └─────────────┘  └──────────────┘  └────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

#### Key Worker Classes

**GenericWorkflowImpl** (Temporal Workflow)
- Implements @WorkflowInterface with methods for start, resume, terminate, query
- Maintains workflow state throughout execution
- Initializes workflow with request data
- Fetches workflow DSL from HBase/cache
- Executes nodes in sequence until completion
- Handles errors and jumps to failure nodes when needed
- Supports signals for pause/resume operations

**WorkflowNodeExecutor** (Node Orchestrator)
- Fetches node definitions from cache or HBase
- Creates appropriate activity stubs based on node type
- Prepares activity requests with context
- Executes activities and handles responses
- Determines next node based on execution result
- Manages error handling and retry logic

---

## Workflow Execution Model

### Execution Lifecycle

```
[CREATED] ──startWorkflow()──> [RUNNING]
                                    │
                     ┌──────────────┼──────────────┐
                     │              │              │
              executeNode()   executeNode()   executeNode()
                     │              │              │
                     │              │              ▼
                     │              │       [WAITING_FOR_INPUT]
                     │              │              │
                     │              │      resumeWorkflow()
                     │              │              │
                     │              └──────────────┘
                     │
                     ├────> Success Node ──> [COMPLETED]
                     │
                     └────> Failure Node ──> [FAILED]
                     
         terminateWorkflow() ──────────────> [TERMINATED]
```

### Node Execution States

```
┌─────────────┐
│  Node Queue │
└──────┬──────┘
       │
       │ Fetch next node
       ▼
┌─────────────────────┐
│  Fetch Definition   │
│  from Cache/HBase   │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  Create Activity    │
│  Stub               │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│  Execute Activity   │
│  (with retry)       │
└──────┬──────────────┘
       │
       ├──Success──> Update Context
       │                    │
       │                    ▼
       │             Get Next Node ID
       │                    │
       │                    └──> Loop
       │
       └──Failure──> Error Handler
                           │
                           ├──Retry──> Execute Again
                           │
                           └──Fatal──> Failure Node
```

### Context Propagation

```
┌────────────────────────────────────────────────────────────┐
│                    Workflow Context                         │
│                                                            │
│  {                                                         │
│    "_global": {                                            │
│      "issueDetail": {...},                                 │
│      "customer": {...},                                    │
│      "orderDetail": {...},                                 │
│      "node001:httpResponse": {...},                        │
│      "node002:transformedData": {...}                      │
│    },                                                      │
│    "_enum_store": {                                        │
│      "ISSUE_TYPES": [...],                                 │
│      "STATUS_CODES": [...]                                 │
│    }                                                       │
│  }                                                         │
└────────────────────────────────────────────────────────────┘
           │                            │
           │ Passed to each node        │ Persisted to HBase
           │                            │ after each node
           ▼                            ▼
    ┌────────────────┐         ┌────────────────┐
    │  Node Activity │         │  Workflow      │
    │   Execution    │         │  Context       │
    │                │         │  HBase Service │
    └────────────────┘         └────────────────┘
```

---

## Node Type System

### Node Type Hierarchy

```
                    NodeDefinition (Abstract)
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
    HttpNode           GroovyNode          BranchNode
    │                      │                   │
    ├─ httpComponents      ├─ script           ├─ choices[]
    └─ transformerComponents└─ scriptType       └─ defaultNode

        │                   │                   │
  ProcessorNode      InstructionNode       SuccessNode
    │                      │                   │
    ├─ processorComponents ├─ instruction      ├─ message
    └─ transformerComponents└─ options[]        └─ status

        │                   │
   FailureNode         (Future node types...)
    │
    ├─ errorMessage
    └─ errorCode
```

### Node Execution Contracts

Each node type must implement:
- **INodeActivity** interface with `executeNode()` method
- **BaseNodeActivityImpl** abstract class providing:
  - Node definition validation
  - Node-specific logic execution
  - Context update after execution
  - Response building with status
  - Template method pattern for extensibility

---

## State Management

### Workflow State Model

**WorkflowState** contains:
- `workflowId` - Temporal workflow ID
- `incidentId` - Business entity ID
- `issueDetail` - Business context
- `status` - CREATED, RUNNING, COMPLETED, FAILED, etc.
- `currentNodeRef` - Current executing node
- `errorMessage` - Error details if failed
- `context` - Execution context (JsonNode)

### State Persistence Strategy

```
┌─────────────────────────────────────────────────────────┐
│              State Persistence Layers                    │
│                                                         │
│  Layer 1: Temporal (Workflow State)                     │
│  ┌────────────────────────────────────────────────────┐ │
│  │  • Workflow execution history                      │ │
│  │  • Event log (activities executed)                 │ │
│  │  • Query responses cached                          │ │
│  │  • Automatic persistence                           │ │
│  └────────────────────────────────────────────────────┘ │
│                                                         │
│  Layer 2: HBase (Business Context)                      │
│  ┌────────────────────────────────────────────────────┐ │
│  │  • Workflow context (execution data)               │ │
│  │  • Node execution results                          │ │
│  │  • Customer/order/issue details                    │ │
│  │  • Manual persistence after each node              │ │
│  └────────────────────────────────────────────────────┘ │
│                                                         │
│  Layer 3: Redis (Cache)                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  • Workflow definitions (hot data)                 │ │
│  │  • Node definitions (hot data)                     │ │
│  │  • Enum mappings                                   │ │
│  │  • TTL-based eviction                              │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## Caching Strategy

### Cache Hierarchy

```
Request for Node Definition (nodeId: "node001", version: "v1")
    │
    ▼
┌──────────────────┐
│  L1: Redis Cache │  ◄── Hot data, TTL: 5 min
└────────┬─────────┘
         │ Cache HIT? ──Yes──> Return
         │
         No
         │
         ▼
┌──────────────────┐
│ L2: HBase (NodeHB)│ ◄── Cold storage, permanent
└────────┬─────────┘
         │
         │ Found? ──Yes──> Store in Redis + Return
         │
         No
         │
         ▼
    Return NULL / Error
```

### Cache Invalidation

```
┌─────────────────────────────────────────────────────────┐
│            Cache Invalidation Strategy                   │
│                                                         │
│  Event: Node Definition Updated                         │
│    ├─ Update HBase                                      │
│    ├─ Publish Redis INVALIDATE message                  │
│    └─ Remove from Redis cache                           │
│                                                         │
│  Listeners:                                             │
│    ┌─ RedisCacheInvalidator (Worker Service)           │
│    │   └─ Subscribes to Redis pub/sub channel          │
│    │   └─ Removes local cache entries                  │
│    └─ All worker instances get notified                 │
└─────────────────────────────────────────────────────────┘
```

### Cache Configuration

```java
public class StaticCacheRefreshConfig {
    private int nodeDefinitionConfig = 5;    // 5 minutes
    private int workflowConfig = 5;          // 5 minutes
}

public class CacheMaxEntriesConfig {
    private int nodeDefinitionConfig = 1000; // Max 1000 nodes
    private int workflowConfig = 1000;       // Max 1000 workflows
}
```

---

## Error Handling & Retry Logic

### Activity Retry Configuration

**Activity Options** configured with:
- Start-to-close timeout: 5 minutes
- Heartbeat timeout: 30 seconds
- Retry policy:
  - Initial interval: 1 second
  - Maximum interval: 60 seconds
  - Backoff coefficient: 2.0 (exponential)
  - Maximum attempts: 3

### Error Propagation

```
Node Execution Error
    │
    ├─ Retryable Error (Network timeout, 5xx)
    │   └─ Temporal Auto-Retry (3 attempts)
    │       │
    │       ├─ Success ──> Continue
    │       │
    │       └─ Max attempts exceeded ──> Fatal Error
    │
    └─ Non-Retryable Error (4xx, validation error)
        │
        └─ Fatal Error
            │
            ├─ Has defaultFailureNode? ──Yes──> Jump to Failure Node
            │
            └─ No ──> Mark Workflow FAILED
```

### Failure Node Pattern

Workflows support global and node-level failure handlers:
- **defaultFailureNode** - Global fallback for any node failure
- **Node-specific failure handler** - Custom error handling per node
- Failure nodes log errors and terminate workflow gracefully
- Context preserved for debugging and audit

---

## Performance Considerations

### Throughput Optimization

**API Service**:
- **Async Workflow Start**: `WorkflowClient.start()` returns immediately
- **Connection Pooling**: Reuse Temporal gRPC connections
- **Request Validation**: Fail fast on invalid requests

**Worker Service**:
- **Parallel Activity Execution**: Configure multiple activity pollers
- **Worker Thread Pool**: Tune based on workload
  ```yaml
  workerDynamicOptions:
    workflowTaskPoller: 20
    activityTaskPoller: 50
    maxWorkflowThreadCount: 800
  ```

### Latency Optimization

```
┌─────────────────────────────────────────────────────────┐
│            Latency Breakdown (per node)                 │
│                                                         │
│  1. Fetch Node Definition: ~5ms (Redis cache hit)      │
│  2. Activity Stub Creation: ~1ms                        │
│  3. Node Execution:                                     │
│     ├─ HTTP Node: ~50-200ms (external API)             │
│     ├─ Groovy Node: ~10-50ms (script execution)        │
│     └─ Branch Node: ~5ms (condition evaluation)        │
│  4. Context Update: ~20ms (HBase write)                │
│  5. Next Node Determination: ~1ms                       │
│                                                         │
│  Total per node: ~100-300ms                             │
│  Workflow with 10 nodes: ~1-3 seconds                   │
└─────────────────────────────────────────────────────────┘
```

### Scalability Limits

```
┌─────────────────────────────────────────────────────────┐
│               Scalability Characteristics               │
│                                                         │
│  Component         │ Scale Dimension  │ Bottleneck     │
│  ─────────────────────────────────────────────────────  │
│  API Service       │ Horizontal       │ None (stateless│
│  Worker Service    │ Horizontal       │ Task queue depth
│  Temporal Cluster  │ Horizontal       │ History service │
│  HBase             │ Horizontal       │ Region servers  │
│  Redis             │ Horizontal       │ Memory          │
│                                                         │
│  Expected Throughput:                                   │
│  • API Requests: 10,000 req/sec (10 pods)              │
│  • Workflow Starts: 1,000 wf/sec                        │
│  • Node Executions: 10,000 nodes/sec (50 workers)      │
└─────────────────────────────────────────────────────────┘
```

---

**Next**: [Low-Level Design (LLD)](./03-LOW-LEVEL-DESIGN.md)

