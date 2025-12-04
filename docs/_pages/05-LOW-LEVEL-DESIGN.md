---
title: "Low-Level Design"
permalink: /05-LOW-LEVEL-DESIGN/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - Low-Level Design (LLD)

## Table of Contents
1. [Class Diagrams](#class-diagrams)
2. [Detailed Component Design](#detailed-component-design)
3. [Algorithms & Logic](#algorithms--logic)
4. [Data Structures](#data-structures)
5. [Sequence Diagrams](#sequence-diagrams)
6. [Database Schema](#database-schema)
7. [Configuration Management](#configuration-management)

---

## Class Diagrams

### Core Domain Model

```
┌─────────────────────────────────────────────────────────────────┐
│                    WORKFLOW DOMAIN MODEL                         │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────┐
│      Workflow            │
├──────────────────────────┤
│ - id: String             │
│ - comment: String        │
│ - startNode: String      │
│ - version: String        │
│ - defaultFailureNode: String │
│ - states: Map<String,    │
│           WorkflowNode>  │
│ - postWorkflowCompletion │
│   Nodes: List<String>    │
├──────────────────────────┤
│ + validateWFFields()     │
└──────────────────────────┘
            │
            │ 1..*
            ▼
┌──────────────────────────┐
│    WorkflowNode          │
├──────────────────────────┤
│ - instanceName: String   │
│ - nextNode: String       │
│ - version: String        │
│ - comment: String        │
└──────────────────────────┘
            │
            │ references
            ▼
┌────────────────────────────────────────────────────────────────┐
│                  NodeDefinition (Abstract)                     │
├────────────────────────────────────────────────────────────────┤
│ # id: String                                                   │
│ # name: String                                                 │
│ # type: NodeType                                               │
│ # parameters: List<String>                                     │
│ # version: String                                              │
├────────────────────────────────────────────────────────────────┤
│ + validateWFNodeFields()                                       │
│ + getType(): NodeType {abstract}                              │
│ + mergeRequestToEntity(NodeDefinition) {abstract}             │
└──────────┬─────────────────┬─────────────────┬────────────────┘
           │                 │                 │
           ▼                 ▼                 ▼
    ┌────────────┐    ┌────────────┐    ┌────────────┐
    │  HttpNode  │    │ GroovyNode │    │ BranchNode │
    └────────────┘    └────────────┘    └────────────┘
           │                 │                 │
           ▼                 ▼                 ▼
    ┌────────────┐    ┌────────────┐    ┌────────────┐
    │Instruction │    │ Processor  │    │ Success    │
    │    Node    │    │    Node    │    │   Node     │
    └────────────┘    └────────────┘    └────────────┘
                          │
                          ▼
                    ┌────────────┐
                    │  Failure   │
                    │    Node    │
                    └────────────┘
```

### Node Type Inheritance

```
┌─────────────────────────────────────────────────────────────────┐
│                  NODE TYPE HIERARCHY                            │
└─────────────────────────────────────────────────────────────────┘

              ┌──────────────────────┐
              │   NodeDefinition     │◄────────────────────┐
              │   (Abstract Base)    │                     │
              └──────────┬───────────┘                     │
                         │ implements                      │
                         ▼                                 │
         ┌───────────────────────────────┐               │
         │  Common Methods:              │               │
         │  • validate()                 │               │
         │  • getType()                  │               │
         │  • mergeRequestToEntity()     │               │
         └───────────────────────────────┘               │
                         │                                 │
        ┌────────────────┼────────────────┐               │
        │                │                │               │
        ▼                ▼                ▼               │
┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  HttpNode    │  │ GroovyNode   │  │ BranchNode   │    │
├──────────────┤  ├──────────────┤  ├──────────────┤    │
│ + httpCompo  │  │ + script     │  │ + choices[]  │    │
│   nents      │  │ + scriptType │  │ + defaultNode│    │
│ + transformer│  │              │  │              │    │
│   Components │  │              │  │              │    │
├──────────────┤  ├──────────────┤  ├──────────────┤    │
│ getType():   │  │ getType():   │  │ getType():   │    │
│   HTTP       │  │   GROOVY     │  │   BRANCH     │    │
└──────────────┘  └──────────────┘  └──────────────┘    │
                                                         │
        ┌────────────────┼────────────────┐             │
        │                │                │             │
        ▼                ▼                ▼             │
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│Instruction   │  │ Processor    │  │ Success      │  │
│    Node      │  │    Node      │  │   Node       │  │
├──────────────┤  ├──────────────┤  ├──────────────┤  │
│ + instruction│  │ + processor  │  │ + message    │  │
│              │  │   Components │  │ + status     │  │
│              │  │ + transformer│  │              │  │
│              │  │   Components │  │              │  │
├──────────────┤  ├──────────────┤  ├──────────────┤  │
│ getType():   │  │ getType():   │  │ getType():   │  │
│  INSTRUCTION │  │  PROCESSOR   │  │  SUCCESS     │  │
└──────────────┘  └──────────────┘  └──────────────┘  │
                         │                              │
                         ▼                              │
                  ┌──────────────┐                     │
                  │  Failure     │                     │
                  │    Node      │                     │
                  ├──────────────┤                     │
                  │ + message    │                     │
                  │ + errorCode  │                     │
                  ├──────────────┤                     │
                  │ getType():   │                     │
                  │  FAILURE     │                     │
                  └──────────────┘                     │
                                                       │
┌───────────────────────────────────────────────────────────────┐
│                POLYMORPHIC BEHAVIOR                          │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  NodeDefinition node = fetchNodeDefinition(id, version);     │
│                                                               │
│  switch (node.getType()) {                                   │
│    case HTTP:                                                │
│      HttpNode httpNode = (HttpNode) node;                    │
│      executeHttpNode(httpNode);                              │
│      break;                                                  │
│    case GROOVY:                                              │
│      GroovyNode groovyNode = (GroovyNode) node;              │
│      executeGroovyNode(groovyNode);                          │
│      break;                                                  │
│    // ... other cases                                        │
│  }                                                           │
└───────────────────────────────────────────────────────────────┘
```

---

## Detailed Component Design

### 1. HTTP Node Activity Implementation

**HttpNodeNodeActivityImpl**
- Dependencies: WorkflowContextHBService, WorkflowConfigStoreService
- Main method: `executeNode(ActivityRequest<HttpNode>): ActivityResponse`
- Helper method: `executeHttp(...): JsonNode`

**Execution Flow:**
1. Extract HttpComponents from node definition
2. Build context wrapper with global context and enum store
3. Evaluate HTTP details using Groovy (resolve dynamic values like URLs, headers)
4. Add performance test headers if in perf mode
5. Get HTTP executor singleton for the base URL
6. Execute HTTP call with retry logic
7. Transform response using transformer components (Groovy scripts)
8. Return activity response with transformed data

**Error Handling:**
- Network exceptions → Temporal auto-retry (3 attempts)
- 4xx HTTP errors → Wrap and throw ApplicationFailure
- Script evaluation errors → Wrap and throw ApplicationFailure

### 2. Groovy Node Activity Implementation

**GroovyNodeNodeActivityImpl**
- Dependencies: WorkflowContextHBService, GroovyTranslator
- Main method: `executeNode(ActivityRequest<GroovyNode>): ActivityResponse`
- Helper method: `evaluateScript(...): JsonNode`

**Execution Flow:**
1. Fetch script content (inline or from file reference)
2. Prepare bindings with context variables (_global, _enum_store, _thread)
3. Create Groovy shell with bindings
4. Evaluate script and capture result
5. Convert result to JsonNode
6. Return activity response with result

**Security Considerations:**
- Sandbox Groovy execution (restrict System, File I/O access)
- Set timeout for script execution (prevent infinite loops)
- Validate script before execution (optional)

**Error Handling:**
- Script syntax error → GroovyException → ApplicationFailure
- Runtime error → Wrap and propagate
- Variable not found → NullPointerException → ApplicationFailure

### 3. Branch Node Activity Implementation

**BranchNodeNodeActivityImpl**
- Dependencies: WorkflowContextHBService
- Main method: `executeNode(ActivityRequest<BranchNode>): ActivityResponse`
- Helper method: `evaluateConditions(...): String`

**Execution Flow:**
1. Prepare context wrapper with global context and enum store
2. Iterate through choices in order
3. Evaluate condition script using Groovy for each choice
4. If condition is true, return next node ID (short-circuit evaluation)
5. If no condition matched, return default node

**Optimization:**
- Short-circuit evaluation (stop at first match)
- Cache compiled Groovy scripts for reuse

**Error Handling:**
- Invalid condition script → Log error and use defaultNode
- Missing defaultNode → ApplicationFailure

### 4. Instruction Node Activity Implementation

**InstructionNodeNodeActivityImpl**
- Dependencies: WorkflowContextHBService
- Main method: `executeNode(ActivityRequest<InstructionNode>): ActivityResponse`
- Helper method: `buildInstructionView(...): View`

**Execution Flow:**
1. Extract instruction definition from node
2. Resolve dynamic values in instruction using context (e.g., "Order #${orderId}")
3. Build View object for UI rendering with resolved values
4. Mark workflow status as WAITING_FOR_INPUT
5. Return activity response with view data

**Workflow Pause Behavior:**
- Workflow enters WAITING state after instruction node
- Client must call `resumeWorkflow()` with user input
- User input is merged into workflow context under `_global`
- Workflow state changes to RUNNING
- Execution continues from next node

**Resume Flow:**
- `resumeWorkflow()` called with user selections
- Input merged into context
- Signal triggers workflow continuation
- Next node executes with updated context

---

## Algorithms & Logic

### Workflow Execution Algorithm

**MAIN WORKFLOW EXECUTION ALGORITHM**

**Function:** `executeWorkflowNodes(workflow, currentNode, workflowId, threadContext)`

**Initialization:**
- Set workflowState = RUNNING
- Initialize errorCount = 0
- Define maxErrors = 10

**Main Loop:**
```
WHILE (currentNode != null) DO
  TRY:
    1. Log node start
    2. Execute node via nodeExecutor
    3. Handle response based on status:
       - RUNNING → Move to next node
       - WAITING_FOR_INPUT → Pause workflow (exit loop)
       - COMPLETED → Mark complete, exit loop
       - FAILED → Mark failed, exit loop
    4. Reset error count on success
    
  CATCH (Exception):
    5. Log error and increment error count
    6. If errorCount > maxErrors → Fail workflow
    7. Jump to failure node if available
    8. Otherwise, fail workflow
END WHILE
```

**Finalization:**
- Execute post-workflow completion nodes (optional)
- Update final workflow state
- Log completion

**Complexity:**
- Time: O(n) where n = number of nodes
- Space: O(1) constant memory (context in HBase)

### Dynamic Value Resolution Algorithm

**FUNCTION:** `resolveValue(template, context)`

**Purpose:** Resolve Mustache/Groovy templates in configuration strings

**Template Types:**
1. **Groovy**: `${...}` syntax - e.g., `${_global.orderId}`
2. **Mustache**: `{{...}}` syntax - e.g., `{{orderId}}`

**Algorithm Steps:**
1. Detect template type by pattern
2. Extract all placeholders from template
3. For each placeholder:
   - Extract expression
   - Evaluate using context
   - Replace in template
4. Handle nested resolution (inner expressions first)
5. Return resolved string

**Example:**
```
Input: "https://api.example.com/orders/${_global.orderId}"
Context: {_global: {orderId: "12345"}}
Output: "https://api.example.com/orders/12345"
```

**Error Handling:**
- Variable not found → Return placeholder or throw exception
- Invalid syntax → Return original template
- Cyclic reference → Detect and throw exception

**Complexity:**
- Time: O(p * e) where p = placeholders, e = evaluation cost
- Space: O(p) for storing placeholders

---

## Data Structures

### Workflow Context Structure

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
      "amount": 1500.00,
      "items": [...]
    },
    "node001:httpResponse": {
      "statusCode": 200,
      "body": {...}
    },
    "node002:processedData": {
      "refundAmount": 1500.00,
      "refundMethod": "BANK"
    }
  },
  "_enum_store": {
    "ISSUE_TYPES": ["REFUND", "REPLACEMENT", "CANCELLATION"],
    "STATUS_CODES": ["NEW", "IN_PROGRESS", "COMPLETED"],
    "REFUND_METHODS": ["BANK", "WALLET", "CARD"]
  },
  "_metadata": {
    "createdAt": "2025-11-21T10:00:00Z",
    "lastUpdatedAt": "2025-11-21T10:05:30Z",
    "currentNode": "node003",
    "executedNodes": ["node001", "node002"]
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

### Workflow Start Sequence

```
Client    API Service   TemporalService   Temporal      Worker     HBase
  │             │              │           Cluster        │          │
  │─POST────────>│              │              │          │          │
  │/workflow/   │              │              │          │          │
  │start        │              │              │          │          │
  │             │──validate──> │              │          │          │
  │             │   request    │              │          │          │
  │             │              │──start────> │          │          │
  │             │              │  workflow   │          │          │
  │             │              │             │          │          │
  │             │              │             │─add to───>│          │
  │             │              │             │ task queue│          │
  │             │<─return──────│             │          │          │
  │             │  workflowId  │             │          │          │
  │<─200 OK─────│              │             │          │          │
  │{workflowId} │              │             │          │          │
  │             │              │             │          │          │
  │             │              │             │          │─poll────>│
  │             │              │             │          │ queue    │
  │             │              │             │<─────────┤          │
  │             │              │             │ workflow │          │
  │             │              │             │   task   │          │
  │             │              │             │          │          │
  │             │              │             │          │──persist─>│
  │             │              │             │          │ initial  │
  │             │              │             │          │ context  │
  │             │              │             │          │<─────────┤
  │             │              │             │          │          │
  │             │              │             │          │──fetch───>│
  │             │              │             │          │ workflow │
  │             │              │             │          │   DSL    │
  │             │              │             │          │<─────────┤
  │             │              │             │          │          │
  │             │              │             │          │          │
  │             │              │             │          │─execute──>│
  │             │              │             │          │  nodes   │
  │             │              │             │          │<─────────┤
  │             │              │             │<─────────┤          │
  │             │              │             │ complete │          │
  │             │              │             │          │          │

Timeline:
  t0: Client sends request
  t1: API validates and starts workflow (returns immediately)
  t2: Worker polls and receives workflow task
  t3: Worker executes workflow logic
  t4: Worker completes execution

Total latency from client perspective: ~50-100ms (just workflow start)
Actual execution happens asynchronously in worker
```

### Node Execution Sequence (HTTP Node)

```
Worker     NodeExecutor  HttpActivity  GroovyTranslator  HttpExecutor  ExternalAPI  HBase
  │             │              │              │               │             │          │
  │──execute───>│              │              │               │             │          │
  │   node      │              │              │               │             │          │
  │             │──fetch────> │              │               │             │          │
  │             │  node def   │              │               │             │          │
  │             │<───────────┤              │               │             │          │
  │             │             │              │               │             │          │
  │             │──create────>│              │               │             │          │
  │             │  activity   │              │               │             │          │
  │             │  stub       │              │               │             │          │
  │             │             │              │               │             │          │
  │             │──execute───>│              │               │             │          │
  │             │   HTTP      │              │               │             │          │
  │             │   activity  │──resolve────>│               │             │          │
  │             │             │  URL/headers │               │             │          │
  │             │             │<─────────────┤               │             │          │
  │             │             │  (evaluated) │               │             │          │
  │             │             │              │               │             │          │
  │             │             │──get────────────────────────>│             │          │
  │             │             │  executor    │               │             │          │
  │             │             │<─────────────────────────────┤             │          │
  │             │             │              │               │             │          │
  │             │             │──execute────────────────────>│             │          │
  │             │             │  HTTP call   │               │──POST────> │          │
  │             │             │              │               │            │          │
  │             │             │              │               │<─response──┤          │
  │             │             │<─────────────────────────────┤            │          │
  │             │             │  httpResponse│               │            │          │
  │             │             │              │               │            │          │
  │             │             │──transform──>│               │            │          │
  │             │             │  response    │               │            │          │
  │             │             │<─────────────┤               │            │          │
  │             │             │  (transformed)               │            │          │
  │             │             │                              │            │          │
  │             │             │──persist────────────────────────────────────────────>│
  │             │             │  context     │               │            │          │
  │             │             │<─────────────────────────────────────────────────────┤
  │             │             │              │               │            │          │
  │             │<────────────┤              │               │            │          │
  │             │  response   │              │               │            │          │
  │<────────────┤             │              │               │            │          │
  │  result     │             │              │               │            │          │

Timeline:
  t0: Worker requests node execution
  t1-t2: Fetch node definition (cache hit: ~5ms)
  t3-t4: Resolve URL/headers using Groovy (~10ms)
  t5-t6: Execute HTTP call (~50-200ms depending on external API)
  t7-t8: Transform response (~10ms)
  t9-t10: Persist context to HBase (~20ms)

Total: ~100-250ms per HTTP node
```

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
