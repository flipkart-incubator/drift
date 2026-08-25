# HLD: Dynamic `expectedEventTypes` Resolution for ON_EVENT WaitNode

---

## 1. Problem

`expectedEventTypes` in `OnEventConfig` is a static list declared at DSL authoring time:

```json
{
  "waitType": "ON_EVENT",
  "expectedEventTypes": ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM"],
  "waitSemantics": "ALL"
}
```

This breaks for workflows where the set of events to wait for is only known at runtime —
for example when a preceding HTTP node fetches a dynamic list of vendors, approvers, or
downstream systems that must all callback before the workflow continues.

---

## 2. What the execution context already contains

Every time a node executes, Drift fetches the workflow's HBase context and hands it to the
activity. This context is a JSON bag that grows as the workflow progresses:

```
HBase context (ObjectNode) — what every activity already receives
┌──────────────────────────────────────────────────────────────┐
│  globalParams      → { threadContext: {...}, params: {...} } │
│  nodeParameters    → evaluated parameters for this node      │
│                                                              │
│  fetchVendors      → { vendorIds: ["v1","v2","v3"] }        │  ← response of
│  notifyOrder       → { orderId: "ORD-99", status: "SENT" }  │    each past node
│  ...               → ...                                     │    keyed by
│                                                              │    instance name
└──────────────────────────────────────────────────────────────┘
```

`OnEventExecutor` already receives this context via `activityRequest.getContext()`. The
dynamic event list is simply read from a field that a previous node already stored there —
no scripting engine needed.

---

## 3. Design

### 3.1 New field on `OnEventConfig`

```
OnEventConfig (addition):

  expectedEventTypesVar: String   (optional, nullable)

    A dot-path into the HBase context: "<nodeInstanceName>.<fieldName>"
    The referenced value must already be a JSON array of strings — stored there
    by a previous node's response.

    Examples:
      "fetchVendors.vendorIds"           → context["fetchVendors"]["vendorIds"]
      "getApprovers.approvalEventTypes"  → context["getApprovers"]["approvalEventTypes"]

    Takes priority over expectedEventTypes when both are set.
    When null/empty, expectedEventTypes is used as-is (static list — existing behaviour).
```

DSL examples:

```json
// Dynamic: HTTP node "fetchVendors" returned { "vendorIds": ["v1","v2","v3"] }
// Wait for each vendor's event to arrive
{
  "waitType": "ON_EVENT",
  "expectedEventTypesVar": "fetchVendors.vendorIds",
  "waitSemantics": "ALL"
}

// Static (existing, unchanged)
{
  "waitType": "ON_EVENT",
  "expectedEventTypes": ["ORDER_PAID", "STOCK_RESERVED"],
  "waitSemantics": "ALL"
}
```

---

### 3.2 Resolution priority

```
┌──────────────────────────────────────────────────────────────────┐
│  Resolution order in OnEventExecutor.resolveExpectedEventTypes() │
├──────────────────────────────────────────────────────────────────┤
│  1. expectedEventTypesVar != null && !isEmpty()                   │
│       → split on "." → context[node][field]                      │
│       → validate: value is JSON array (else activity throws)     │
│       → warn if resolved list is empty                           │
│       → use as resolvedExpectedEventTypes                        │
│                                                                  │
│  2. expectedEventTypes != null && !isEmpty()  (static list)      │
│       → use as-is                                                │
│                                                                  │
│  3. both absent → Activity.wrap(IllegalStateException) thrown    │
│       workflow node fails with clear DSL validation message      │
└──────────────────────────────────────────────────────────────────┘
```

---

### 3.3 How the resolved list travels from activity → workflow thread

```
ActivityResponse
  + resolvedExpectedEventTypes: List<String>    ← populated by OnEventExecutor

BaseNodeActivityImpl.execute()  [thin-response builder]
  .resolvedExpectedEventTypes(response.getResolvedExpectedEventTypes())

ActivityThinResponse
  + resolvedExpectedEventTypes: List<String>    ← received by WorkflowNodeExecutor

WorkflowNodeExecutor.applyOnEventWaitState(currentNode, nodeDefinition, response)
  → initOnEventState(config, response.getResolvedExpectedEventTypes())

WorkflowNodeExecutor.initOnEventState(config, List<String> resolvedTypes)
  effective = resolvedTypes != null && !isEmpty() ? resolvedTypes : config.getExpectedEventTypes()
  workflowState.setExpectedEventTypes(effective)
```

---

## 4. Flow Diagrams

### 4.1  Static vs Dynamic path — decision inside `OnEventExecutor`

```
  DSL config
       │
       ├── expectedEventTypesVar present?
       │         │
       │        YES ──► resolveFromContext(dotPath, context, workflowId)
       │                  split "fetchVendors.vendorIds" → ["fetchVendors","vendorIds"]
       │                  context.get("fetchVendors").get("vendorIds")
       │                        │
       │                 isArray?
       │                        │
       │                    NO  ├──► Activity.wrap(IllegalStateException)
       │                        │    "must point to a JSON array, got: <NodeType>"
       │                       YES
       │                        │
       │                 list.isEmpty()?
       │                        │
       │                    YES ├──► log.warn (proceed anyway — DSL author's choice)
       │                        │
       │                        └──► resolvedList = List<String> from array
       │
        NO ──► expectedEventTypes present?
                      │
                  NO  ├──► Activity.wrap(IllegalStateException)
                      │    "OnEventConfig must have either expectedEventTypesVar
                      │     or expectedEventTypes"
                     YES
                      │
                      └──► resolvedList = config.getExpectedEventTypes()
       │
       ▼
  ActivityResponse.builder()
    .workflowStatus(WAITING)
    .resolvedExpectedEventTypes(resolvedList)
    .build()
```

---

### 4.2  Full data-flow: DSL → WorkflowState

```
  DSL (JSON)
    "expectedEventTypesVar": "prevNode.items"
       │
       │  (workflow starts, nodes execute sequentially)
       │
       ▼
  [HTTP node "prevNode" executes]
  HBase context += { "prevNode": { "items": ["X_EVENT","Y_EVENT","Z_EVENT"] } }
       │
       ▼
  [WaitNode "waitApprovals" dispatched as Temporal activity]
       │
       ▼
  BaseNodeActivityImpl.executeWithContextManagement()
    ├── fetch HBase context (ObjectNode)
    │     └── { prevNode: {items:[X_EVENT,Y_EVENT,Z_EVENT]}, globalParams: {...}, ... }
    ├── build ActivityRequest<WaitNode>
    │     └── activityRequest.context = ^ ObjectNode
    └── call WaitNodeActivityImpl.executeNode(activityRequest)
              │
              ▼
         WaitNodeActivityImpl → delegates to OnEventExecutor.executeWait()
              │
              ▼
         resolveExpectedEventTypes(config, activityRequest)
              │
              ├── config.getExpectedEventTypesVar() = "prevNode.items"
              │
              └── resolveFromContext("prevNode.items", context, workflowId)
                      split → ["prevNode", "items"]
                      context.get("prevNode") → { items: [X_EVENT,Y_EVENT,Z_EVENT] }
                      .get("items")           → JSON array node
                      forEach → ["X_EVENT","Y_EVENT","Z_EVENT"]
              │
              ▼
         ActivityResponse {
           workflowStatus: WAITING,
           resolvedExpectedEventTypes: ["X_EVENT","Y_EVENT","Z_EVENT"]
         }
              │
              ▼
  BaseNodeActivityImpl.execute()  [maps to thin response]
    ActivityThinResponse {
      workflowStatus: WAITING,
      resolvedExpectedEventTypes: ["X_EVENT","Y_EVENT","Z_EVENT"]
    }
              │
              ▼
  WorkflowNodeExecutor.executeNode()  [private]
    updateWorkflowState(response, currentNode)   → status = WAITING
    applyOnEventWaitState(currentNode, nodeDefinition, response)
              │
              ▼
  applyOnEventWaitState()
    nodeDefinition.type == WAIT && waitType == ON_EVENT  ✓
    initOnEventState(config, ["X_EVENT","Y_EVENT","Z_EVENT"])
              │
              ▼
  initOnEventState(config, resolvedTypes)
    effective = ["X_EVENT","Y_EVENT","Z_EVENT"]      ← resolvedTypes wins
    workflowState.expectedEventTypes  = ["X_EVENT","Y_EVENT","Z_EVENT"]
    workflowState.waitSemantics       = ALL
    workflowState.receivedEventTypes  = {} (or preserved if already populated)
              │
              ▼
  isOnEventConditionMet()?
    received.containsAll(["X_EVENT","Y_EVENT","Z_EVENT"]) → false
    → status stays WAITING
              │
              ▼
  handleNodeResponseStatus() → case WAITING
    Workflow.await(() -> status != WAITING)     ← PARKED
```

---

### 4.3  End-to-end workflow: N events arrive, workflow unblocks

```
  Temporal worker                         External system
  (workflow coroutine)                    (e.g. vendor callback API)
        │                                        │
        │  [parked at Workflow.await()]           │
        │                                        │
        │◄─── resumeWorkflow signal ─────────────│
        │     { eventType: "X_EVENT" }           │
        │                                        │
  GenericWorkflowImpl.resumeWorkflow()            │
    receivedEventTypes.add("X_EVENT")            │
    isResumeConditionMet()?                      │
      received={X_EVENT}                         │
      expected=[X,Y,Z], semantics=ALL            │
      containsAll? → false → status stays WAITING│
        │                                        │
        │  [still parked]                        │
        │◄─── resumeWorkflow signal ─────────────│
        │     { eventType: "Y_EVENT" }           │
        │                                        │
    receivedEventTypes.add("Y_EVENT")            │
    isResumeConditionMet()? → false              │
        │                                        │
        │  [still parked]                        │
        │◄─── resumeWorkflow signal ─────────────│
        │     { eventType: "Z_EVENT" }           │
        │                                        │
    receivedEventTypes.add("Z_EVENT")            │
    isResumeConditionMet()?                      │
      received={X,Y,Z}, expected=[X,Y,Z]         │
      containsAll? → TRUE                        │
      drain: removeAll([X,Y,Z]) → received={}    │
      workflowState.status = RUNNING             │
        │                                        │
  Workflow.await() unblocks                      │
        │                                        │
  handleNodeResponseStatus() → case RUNNING      │
    (no-op — execution continues)                │
        │                                        │
  [advance to next node]                         │
```

---

### 4.4  Early signal scenario (signal arrives before WaitNode is reached)

```
  Temporal worker                         External system
        │                                        │
        │  [executing node A — HTTP call]        │
        │                                        │
        │◄─── resumeWorkflow signal ─────────────│
        │     { eventType: "X_EVENT" }           │
        │                                        │
  GenericWorkflowImpl.resumeWorkflow()            │
    receivedEventTypes.add("X_EVENT")            │
    isResumeConditionMet()?                      │
      workflowState.expectedEventTypes = null    │
      (WaitNode not reached yet)                 │
      → semantics null → legacy: status = RUNNING│
      but workflow is not parked, signal is stored│
        │                                        │
        │  [node A completes, node B executes...] │
        │                                        │
        │  [WaitNode "waitApprovals" executes]   │
        │                                        │
  OnEventExecutor resolves via expectedEventTypesVar
    → ["X_EVENT","Y_EVENT"]
  applyOnEventWaitState()
    initOnEventState(config, ["X_EVENT","Y_EVENT"])
      receivedEventTypes != null (has "X_EVENT") → preserved as-is
      workflowState.expectedEventTypes = ["X_EVENT","Y_EVENT"]
        │
  isOnEventConditionMet()?
    received={X_EVENT}, expected=[X,Y], ALL
    containsAll? → false → status stays WAITING
        │
  [parked waiting for Y_EVENT]
        │
        │◄─── resumeWorkflow signal ─────────────│
        │     { eventType: "Y_EVENT" }           │
        │
  isResumeConditionMet()? → received={X,Y} → TRUE
  drain → received={}
  status = RUNNING → Workflow.await() unblocks
```

---

## 5. Component Changes

```
commons
  └── OnEventConfig.java
        ADD  expectedEventTypesVar: String   (optional, nullable)
        REM  expectedEventTypesScript: String (removed — no Groovy)

worker
  └── model/activity/ActivityResponse.java
        ADD  resolvedExpectedEventTypes: List<String>

  └── model/activity/ActivityThinResponse.java
        ADD  resolvedExpectedEventTypes: List<String>

  └── activities/BaseNodeActivityImpl.java
        MODIFIED  execute()  → builder maps resolvedExpectedEventTypes
        MODIFIED  resolveInlineWaitConfigVar() — dot-path resolution for Approach 2

  └── executor/WaitTypeExecutor/OnEventExecutor.java
        ADDED  resolveFromContext(dotPath, context, workflowId) — static helper
                 — navigates dot-path into ObjectNode
                 — validates result is JSON array
                 — package-accessible (used by BaseNodeActivityImpl)
        MODIFIED executeWait() → calls resolveExpectedEventTypes(), returns result in
                  ActivityResponse.resolvedExpectedEventTypes

  └── workflows/WorkflowNodeExecutor.java
        MODIFIED  applyOnEventWaitState(WorkflowNode, NodeDefinition, ActivityThinResponse)
        MODIFIED  initOnEventState(OnEventConfig, List<String> resolvedTypes)
        MODIFIED  executeNode() private — threads response into applyOnEventWaitState()

NOT CHANGING:
  WorkflowState             — no new fields needed
  WorkflowResumeRequest     — no changes
  GenericWorkflowImpl       — no changes to resume signal handler
  HBase schema              — no changes
```

---

## 6. Inline waitConfig — Dynamic Var Support (Approach 2)

`expectedEventTypesVar` is fully supported for inline `waitConfig` (Approach 2).

Resolution happens inside `BaseNodeActivityImpl.resolveInlineWaitConfigVar()` — called right
after `executeNode()` returns, while the activity still holds the full HBase context
(including the just-executed node's own response).

```
BaseNodeActivityImpl.executeWithContextManagement()
  ├── executeNode(activityRequest)              ← node's own logic runs
  ├── resolveInlineWaitConfigVar(...)           ← resolve dot-path if present
  │     workflowNode.waitConfig has expectedEventTypesVar?
  │       → OnEventExecutor.resolveFromContext(var, context, workflowId)
  │       → response.setResolvedExpectedEventTypes(result)
  └── persist context to HBase

ActivityThinResponse.resolvedExpectedEventTypes  ← carries the resolved list back

WorkflowNodeExecutor.applyOnEventWaitState()
  inline path: initOnEventState(config, response.getResolvedExpectedEventTypes())
                                               ↑ carries resolved types
```

---

## 7. Error Handling

```
┌──────────────────────────────────┬──────────────────────────────────────────────────┐
│ Error                            │ Behaviour                                        │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Dot-path node key not found      │ Activity.wrap(IllegalStateException):            │
│ in context                       │ "key '<node>' not found in context"              │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Dot-path field not found in      │ Activity.wrap(IllegalStateException):            │
│ node response                    │ "field '<field>' not found"                      │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Dot-path points to non-array     │ Activity.wrap(IllegalStateException):            │
│                                  │ "must point to a JSON array, got: <NodeType>"   │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Resolved list is empty           │ log.warn emitted, empty list stored;            │
│                                  │ ANY stays WAITING forever;                       │
│                                  │ ALL unblocks immediately (vacuous truth);        │
│                                  │ DSL author's responsibility to avoid            │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Both var and static list set     │ expectedEventTypesVar takes priority             │
├──────────────────────────────────┼──────────────────────────────────────────────────┤
│ Both null/absent                 │ Activity.wrap(IllegalStateException):            │
│                                  │ "OnEventConfig must have either                  │
│                                  │  expectedEventTypesVar or expectedEventTypes"    │
└──────────────────────────────────┴──────────────────────────────────────────────────┘
```

---

## 8. Multi-WaitNode — Drain on Unblock

`receivedEventTypes` is a flat `Set<String>` shared across the workflow lifecycle. Without
cleanup, events stored for WaitNode-1 could pollute WaitNode-2's condition check.

**Fix:** when a WaitNode's condition is met and the workflow unblocks, the events consumed
by that node are drained from `receivedEventTypes` via `removeAll(expectedEventTypes)`.
Events that arrived early for a *future* WaitNode remain in the set untouched.

```
WaitNode-1 expects [A, B] — ALL
WaitNode-2 expects [C, D] — ALL

Timeline:
  signal C arrives early  → received = {C}
  signal A arrives        → received = {A, C}
  signal B arrives        → received = {A, B, C}  → WaitNode-1 condition met
  drain removeAll([A,B])  → received = {C}         ← C preserved for WaitNode-2
  WaitNode-2 executes     → expected=[C,D], received={C} → still parked
  signal D arrives        → received = {C, D}      → WaitNode-2 condition met
  drain removeAll([C,D])  → received = {}
```

Drain happens in two places:
- `GenericWorkflowImpl.isResumeConditionMet()` — when a signal triggers unblock
- `WorkflowNodeExecutor.isOnEventConditionMet()` — when early signals already satisfy the condition at WaitNode init

**DSL contract — event type names must be unique per WaitNode across the entire workflow.**

Each event type string belongs to exactly one WaitNode. If two WaitNodes in the same
workflow declare the same event type name, the drain will consume the early-arrived event
for the first node and the second node will park forever.

```
✅ Correct DSL
  WaitNode-1: expectedEventTypes = ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM"]
  WaitNode-2: expectedEventTypes = ["PAYMENT_CAPTURED", "STOCK_RESERVED"]
  → no overlap → drain is safe

❌ Incorrect DSL
  WaitNode-1: expectedEventTypes = ["ORDER_CONFIRMED"]
  WaitNode-2: expectedEventTypes = ["ORDER_CONFIRMED"]   ← same name reused
  → if ORDER_CONFIRMED arrives early, WaitNode-1 drains it, WaitNode-2 parks forever
```

For dynamic event types resolved via `expectedEventTypesVar`, the same rule applies — the
list read from context must not overlap with any other WaitNode's expected events in the
same workflow.
