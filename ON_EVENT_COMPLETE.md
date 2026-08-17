# ON_EVENT — Complete Feature Reference

This document is the single source of truth for the ON_EVENT wait feature in Drift.
No prior context needed — everything is here.

---

## 1. Key Terms

Understanding these terms is enough to follow the rest of the document.

**Temporal**
The workflow orchestration engine Drift runs on. It manages workflow execution, retries,
signals, and durability. Think of it as the runtime that keeps workflows alive even across
process restarts.

**Workflow (coroutine)**
A workflow in Temporal is a long-running piece of code that runs as a deterministic
coroutine — like a thread that can be suspended, replayed, and resumed. Workflow code must
NOT do I/O directly (no DB calls, no HTTP calls). It can only orchestrate activities and
react to signals.

**Activity**
A regular Java method that does actual work — fetches from HBase, makes HTTP requests,
runs Groovy scripts, etc. Activities run outside the workflow coroutine in a worker thread,
can be retried by Temporal on failure, and return their result back to the workflow.
In Drift, every node execution (HTTP, GROOVY, WAIT, etc.) is dispatched as an activity.

```
Workflow coroutine (orchestrator — no I/O allowed)
      │
      ├── dispatch activity → "fetch HBase context, run HTTP node, return result"
      │         └── runs in worker thread, can call any external system
      │
      ├── dispatch activity → "run WaitNode, resolve expected event types"
      │         └── runs in worker thread → returns WAITING + resolved event list
      │
      └── park at Workflow.await() until a signal arrives
```

**Signal**
A message sent from outside to a running workflow. Drift uses signals to resume parked
workflows — an external system calls `PUT /workflow/resume/{workflowId}`, which internally
sends a `resumeWorkflow` signal to Temporal. Signals are queued and delivered reliably by
Temporal even if the workflow is sleeping.

**`Workflow.await(condition)`**
A Temporal API that suspends the workflow coroutine until the condition lambda returns
`true`. While suspended, the workflow consumes no threads. Signals can still arrive and
modify `WorkflowState` — the condition is re-evaluated after each signal.

**WorkflowState**
A plain Java object that lives in memory inside the workflow coroutine. It holds the
current status, the node the workflow is on, and ON_EVENT tracking fields
(`expectedEventTypes`, `receivedEventTypes`, `waitSemantics`). It is never persisted
directly — Temporal rebuilds it by replaying the workflow history on restart.

**HBase context**
A JSON object (ObjectNode) stored in HBase that accumulates the response of every node
as the workflow progresses. It is fetched at the start of each activity, updated with
the node's output, and written back. This is what Groovy scripts and node parameters
read when referencing previous node responses.

```
HBase context grows as workflow executes:
  { globalParams: {...}, fetchVendors: {vendorIds:[...]}, notifyOrder: {...}, ... }
         ↑ always there        ↑ added after fetchVendors runs   ↑ added after notifyOrder runs
```

**DSL**
The JSON workflow definition authored by the workflow designer. It declares nodes, their
types, connections (`nextNode`), and configs like `waitType`, `expectedEventTypes`, etc.

**Thin request / Thin response**
Temporal serializes everything it sends to and receives from activities. To avoid
serializing large objects like the full HBase context over the wire on every dispatch,
Drift uses "thin" versions:

- `ActivityThinRequest` — what the workflow sends to the activity: node definition,
  workflow ID, thread context. No HBase data — the activity fetches that itself.
- `ActivityThinResponse` — what comes back to the workflow: status, next node, resolved
  event types. No HBase data.

---

## 2. What Problem This Solves

Drift workflows sometimes need to **pause and wait for external systems to callback**
before continuing. Examples:

- Send a notification to 3 vendors → wait for all 3 to confirm before proceeding
- Trigger a payment gateway → wait for the async payment event
- Dispatch to multiple approval systems → wait for any one approval to arrive

Before this feature, `resumeWorkflow` existed but it always unblocked on the first signal,
with no concept of event identity or waiting for N events.

---

## 2. Core Concepts

### 2.1 How a workflow parks

Drift workflows run as Temporal coroutines. When a workflow needs to wait, it calls:

```java
Workflow.await(() -> workflowState.getStatus() != WAITING)
```

This suspends the coroutine. It unblocks when the lambda returns `true` — i.e. when status
changes from `WAITING` to something else (typically `RUNNING`). External systems unblock
the workflow by sending a `resumeWorkflow` signal to Temporal.

### 2.2 WorkflowState — shared mutable state in the coroutine

```
WorkflowState (lives in memory inside the Temporal coroutine)
┌─────────────────────────────────────────────────────────────┐
│  status               WorkflowStatus (RUNNING/WAITING/...)  │
│  workflowExecutionMode  SYNC / ASYNC                        │
│  currentNodeRef       instance name of the current node     │
│                                                             │
│  — ON_EVENT tracking —                                      │
│  expectedEventTypes   List<String>  set at park time        │
│  waitSemantics        ANY / ALL                             │
│  receivedEventTypes   Set<String>   grows as signals arrive │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 SYNC vs ASYNC execution mode

| Mode | Behaviour when parked |
|------|----------------------|
| SYNC | Calls `ReturnControlActivity` which publishes to Redis; API caller blocks on Redis and gets the workflow result when it resumes |
| ASYNC | Skips `ReturnControlActivity`; API caller gets `200 OK` immediately; workflow silently resumes when events arrive |

ON_EVENT waits are typically used with **ASYNC** mode since external systems cannot block.

---

## 3. Two Ways to Declare an ON_EVENT Wait

Both approaches funnel into the same `Workflow.await()` mechanism.

### Approach 1 — Dedicated WaitNode

A standalone `WAIT` node in the DSL whose only job is to park the workflow.

```json
{
  "states": {
    "notifyVendors": {
      "instanceName": "notifyVendors",
      "type": "HTTP",
      "nextNode": "waitForCallbacks"
    },
    "waitForCallbacks": {
      "instanceName": "waitForCallbacks",
      "type": "WAIT",
      "nextNode": "processResults",
      "nodeDefinition": {
        "waitType": "ON_EVENT",
        "expectedEventTypes": ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM"],
        "waitSemantics": "ALL"
      }
    },
    "processResults": { ... }
  }
}
```

**Graph:**
```
[HTTP: notifyVendors] → [WAIT: waitForCallbacks] → [HTTP: processResults]
                              ↑ standalone park node
```

**When to use:** pure wait with no preceding action (entry-point wait, branch-conditional
wait, scheduler pause), or when explicit graph visibility is preferred.

---

### Approach 2 — Inline `waitConfig` on Any Node

An optional `waitConfig` field on `WorkflowNode`. The node executes its own logic and then
parks inline — no separate WaitNode needed.

```json
{
  "states": {
    "notifyVendors": {
      "instanceName": "notifyVendors",
      "type": "HTTP",
      "nextNode": "processResults",
      "waitConfig": {
        "waitType": "ON_EVENT",
        "expectedEventTypes": ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM"],
        "waitSemantics": "ALL"
      }
    },
    "processResults": { ... }
  }
}
```

**Graph:**
```
[HTTP: notifyVendors (+wait)] → [HTTP: processResults]
    ↑ does work AND waits — one less node
```

**When to use:** any async operation where the node that triggers the action should also
own the wait for its callback. Works on HTTP, PROCESSOR, GROOVY, DELEGATE, CHILD.

Dynamic `expectedEventTypesVar` is also supported here — the dot-path is resolved inside
the node's activity (which already has the full HBase context) and the result is passed
back via the same `resolvedExpectedEventTypes` pipeline used by Approach 1.

---

### Comparison

```
┌──────────────────────────────┬──────────────────────┬────────────────────────┐
│                              │  Approach 1          │  Approach 2            │
│                              │  Dedicated WaitNode  │  Inline waitConfig     │
├──────────────────────────────┼──────────────────────┼────────────────────────┤
│ DSL nodes for async op       │  2 (action + wait)   │  1 (action with wait)  │
├──────────────────────────────┼──────────────────────┼────────────────────────┤
│ Applicable to which nodes    │  Only WAIT type      │  Any node type         │
├──────────────────────────────┼──────────────────────┼────────────────────────┤
│ Dynamic expectedEventTypes   │  Yes (dot-path var)  │  Yes (dot-path var)    │
├──────────────────────────────┼──────────────────────┼────────────────────────┤
│ Graph readability            │  Explicit wait node  │  Compact, less noise   │
├──────────────────────────────┼──────────────────────┼────────────────────────┤
│ Backward compatible          │  Yes                 │  Yes (field optional)  │
└──────────────────────────────┴──────────────────────┴────────────────────────┘
```

---

## 4. Wait Semantics — ANY vs ALL

`waitSemantics` controls how many events must arrive before the workflow unblocks.

| Semantics | Unblocks when |
|-----------|---------------|
| `ANY` | First event from `expectedEventTypes` arrives |
| `ALL` | Every event in `expectedEventTypes` has arrived at least once |

```json
// ANY — first vendor to confirm unblocks the workflow
{
  "expectedEventTypes": ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM", "VENDOR_C_CONFIRM"],
  "waitSemantics": "ANY"
}

// ALL — every vendor must confirm
{
  "expectedEventTypes": ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM", "VENDOR_C_CONFIRM"],
  "waitSemantics": "ALL"
}
```

Default is `ALL` when `waitSemantics` is omitted.

---

## 5. Dynamic Expected Event Types (`expectedEventTypesVar`)

### 5.1 The Problem

Static `expectedEventTypes` must be known at DSL authoring time. This fails when the list
is only known at runtime — for example when a preceding HTTP node fetches a dynamic list of
vendor IDs that must all callback before the workflow continues.

### 5.2 How It Works

`OnEventConfig` has an optional `expectedEventTypesVar` field — a dot-path string
(`"nodeInstanceName.fieldName"`) that navigates into the HBase context to read a
`List<String>` already stored there by a previous node.

```
HBase context — accumulated as workflow executes
┌──────────────────────────────────────────────────────────────┐
│  globalParams    → { threadContext: {...}, params: {...} }   │
│  nodeParameters  → evaluated parameters for this node        │
│  fetchVendors    → { vendorIds: ["v1","v2","v3"] }  ← prev  │
│  notifyOrder     → { orderId: "ORD-99", ... }        ← node │
│  ...             → ...                               responses│
└──────────────────────────────────────────────────────────────┘
```

Dot-path navigates: `"fetchVendors.vendorIds"` → `context["fetchVendors"]["vendorIds"]`
→ `["v1","v2","v3"]` (must already be a JSON array of strings).

DSL examples:

```json
// Dynamic: HTTP node "fetchVendors" returned { "vendorIds": ["v1","v2","v3"] }
// Wait for each vendor to confirm
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

### 5.3 Resolution Priority

```
1. expectedEventTypesVar present and non-empty?
      → split on "." → navigate context["node"]["field"]
      → value must be a JSON array (else activity throws)
      → use as the expected event list

2. expectedEventTypes present and non-empty?  (static list)
      → use as-is

3. both absent
      → activity throws: "OnEventConfig must have either
        expectedEventTypesVar or expectedEventTypes"
```

---

## 6. Resume API

External systems resume a parked workflow via:

```
PUT /v3/workflow/resume/{workflowId}

{
  "eventType": "VENDOR_A_CONFIRM",       ← which event arrived
  "params": { "orderId": "ORD-99" },     ← event payload (written to HBase)
  "workflowExecutionMode": "ASYNC"       ← ASYNC: return 200 immediately
}
```

### 6.1 What happens on each signal

```
resumeWorkflow signal arrives
      │
      ├── persist params to HBase
      │     viewResponse key:   {currentNodeRef}_viewResponse
      │     event payload key:  {currentNodeRef}_{eventType}_payload
      │
      ├── receivedEventTypes.add(eventType)
      │
      ├── isResumeConditionMet()?
      │     semantics == null (legacy)  → always true
      │     semantics == ANY            → received not empty
      │     semantics == ALL            → received.containsAll(expected)
      │
      ├── condition met?
      │     YES → drain consumed events (see §8), status = RUNNING
      │           → Workflow.await() unblocks
      │     NO  → status stays WAITING
      │           → Workflow.await() stays blocked
      │
      └── ASYNC mode: return 200 OK immediately
          SYNC mode:  block on Redis until workflow publishes result
```

### 6.2 Existing resume vs ON_EVENT resume

Both use the same `resumeWorkflow` signal and the same endpoint. The difference is in
how many calls are needed and whether the caller blocks:

```
┌─────────────────────────┬────────────────────────┬──────────────────────────┐
│                         │ Existing (SYNC/human)  │ New ON_EVENT (ASYNC)     │
├─────────────────────────┼────────────────────────┼──────────────────────────┤
│ Who calls               │ Human / UI / agent     │ External system / bus    │
├─────────────────────────┼────────────────────────┼──────────────────────────┤
│ Caller blocks?          │ Yes — waits on Redis   │ No — 200 OK immediately  │
├─────────────────────────┼────────────────────────┼──────────────────────────┤
│ Calls to unpark         │ 1 — always unblocks    │ 1 (ANY) or N (ALL)       │
├─────────────────────────┼────────────────────────┼──────────────────────────┤
│ Event identity          │ None                   │ eventType field          │
├─────────────────────────┼────────────────────────┼──────────────────────────┤
│ Signal handler          │ Always status=RUNNING  │ Status=RUNNING only when │
│                         │                        │ condition is met         │
└─────────────────────────┴────────────────────────┴──────────────────────────┘
```

---

## 7. Complete Execution Flow

### 7.1 WaitNode path (Approach 1) with dynamic event types

```
Workflow starts — nodes execute in while loop
      │
      ▼
[HTTP node "fetchVendors" executes]
  HBase context += { fetchVendors: { vendorIds: ["v1","v2","v3"] } }
      │
      ▼
[WaitNode "waitCallbacks" dispatched as Temporal activity]
      │
      ▼
BaseNodeActivityImpl.executeWithContextManagement()
  ├── fetch HBase context (ObjectNode)
  └── call WaitNodeNodeActivityImpl.executeNode()
            │
            ▼
       OnEventExecutor.executeWait()
         resolveExpectedEventTypes():
           expectedEventTypesVar = "fetchVendors.vendorIds"
           resolveFromContext("fetchVendors.vendorIds", context)
             context["fetchVendors"]["vendorIds"] → ["v1","v2","v3"]
         return ActivityResponse {
           status: WAITING,
           resolvedExpectedEventTypes: ["v1","v2","v3"]
         }
            │
            ▼
BaseNodeActivityImpl.execute() maps to ActivityThinResponse {
  status: WAITING,
  resolvedExpectedEventTypes: ["v1","v2","v3"]
}
            │
            ▼
WorkflowNodeExecutor.executeNode() [private]
  updateWorkflowState()          → status = WAITING
  applyOnEventWaitState()
    initOnEventState(config, ["v1","v2","v3"])
      workflowState.expectedEventTypes = ["v1","v2","v3"]
      workflowState.waitSemantics      = ALL
      workflowState.receivedEventTypes preserved (not reset)
    isOnEventConditionMet()? → false → status stays WAITING
            │
            ▼
handleNodeResponseStatus() → case WAITING
  (ASYNC mode) skip ReturnControlActivity
  Workflow.await(status != WAITING)   ◄──── PARKED
```

### 7.2 N signals arrive, workflow unblocks

```
External system                   Temporal (signal handler)
      │                                   │
      │── PUT /resume {v1_CONFIRM} ──────►│
      │                                   │  receivedEventTypes = {v1_CONFIRM}
      │                                   │  containsAll([v1,v2,v3])? → false → parked
      │
      │── PUT /resume {v2_CONFIRM} ──────►│
      │                                   │  receivedEventTypes = {v1_CONFIRM, v2_CONFIRM}
      │                                   │  containsAll? → false → parked
      │
      │── PUT /resume {v3_CONFIRM} ──────►│
      │                                   │  receivedEventTypes = {v1,v2,v3}
      │                                   │  containsAll? → TRUE
      │                                   │  drain: removeAll([v1,v2,v3]) → received={}
      │                                   │  status = RUNNING
      │                                   │
      │                            Workflow.await() unblocks
      │                            handleNodeResponseStatus() → RUNNING → no-op
      │                            advance to next node
```

---

## 8. Multi-WaitNode — Drain on Unblock

`receivedEventTypes` is a flat `Set<String>` shared across the full workflow lifetime.
Signals can arrive for a future WaitNode before it is reached — they are stored in the set.
When the current WaitNode unblocks, its consumed events are **drained** so they don't
contaminate the next WaitNode's condition.

```
WaitNode-1 expects [A, B] — ALL
WaitNode-2 expects [C, D] — ALL

  signal C arrives (WaitNode-1 still parked) → received = {C}
  signal A arrives                           → received = {A, C}
  signal B arrives → WaitNode-1 condition met
  drain removeAll([A,B])                     → received = {C}  ← preserved
  WaitNode-2 executes, expected=[C,D], received={C} → parked
  signal D arrives → WaitNode-2 condition met
  drain removeAll([C,D])                     → received = {}
```

Drain is applied in two code paths:
- `GenericWorkflowImpl.isResumeConditionMet()` — when a signal satisfies the condition
- `WorkflowNodeExecutor.isOnEventConditionMet()` — when early signals already satisfy the
  condition at WaitNode init time (workflow skips parking)

### DSL Contract — event type names must be globally unique per workflow

Each event type string belongs to exactly one WaitNode. Reusing the same name across two
WaitNodes causes the drain of the first node to silently consume events intended for
the second.

```
✅ Correct
  WaitNode-1: ["VENDOR_A_CONFIRM", "VENDOR_B_CONFIRM"]
  WaitNode-2: ["PAYMENT_CAPTURED", "STOCK_RESERVED"]

❌ Incorrect — same string reused
  WaitNode-1: ["ORDER_CONFIRMED"]
  WaitNode-2: ["ORDER_CONFIRMED"]   ← drained by WaitNode-1, WaitNode-2 parks forever
```

This applies equally to event types resolved via `expectedEventTypesVar` — the list read
from context must not overlap with any other WaitNode's expected events in the same workflow.

---

## 9. Early Signal Handling

A signal can arrive before the WaitNode that expects it is reached. This is valid and
handled correctly.

```
[Node A executing]
      │◄── signal "X_EVENT" arrives
      │
  receivedEventTypes.add("X_EVENT")
  isResumeConditionMet(): waitSemantics=null (WaitNode not yet reached)
    → legacy path: return true → status=RUNNING
    → but workflow is not parked, so no unblock — just stored
      │
[Node A completes, Node B executes...]
      │
[WaitNode "waitApprovals" reached]
      │
  OnEventExecutor resolves expectedEventTypes = ["X_EVENT", "Y_EVENT"]
  initOnEventState():
    receivedEventTypes != null (already has X_EVENT) → preserved as-is
    workflowState.expectedEventTypes = ["X_EVENT", "Y_EVENT"]
      │
  isOnEventConditionMet(): received={X_EVENT}, expected=[X,Y], ALL
    containsAll? → false → status stays WAITING → park normally
      │
  [signal "Y_EVENT" arrives]
    condition met → drain → unblock
```

If ALL events arrived before the WaitNode is reached, `isOnEventConditionMet()` returns
true immediately, drain runs, and `Workflow.await()` unblocks without parking at all.

---

## 10. Payload Access in Downstream Nodes

Each resume signal's payload is written to HBase under two keys:

```
{currentNodeRef}_viewResponse                ← full params (legacy key, always written)
{currentNodeRef}_{eventType}_payload         ← per-event payload (new, for N-event waits)
```

Downstream nodes can reference individual event payloads via node parameters:
```
{{waitCallbacks_VENDOR_v1_CONFIRM_payload.orderId}}
{{waitCallbacks_VENDOR_v2_CONFIRM_payload.status}}
```

---

## 11. Error Handling

```
┌────────────────────────────────┬───────────────────────────────────────────────────┐
│ Error                          │ Behaviour                                         │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ expectedEventTypesVar path     │ Activity throws with clear message:               │
│ key not found in context       │ "key '<node>' not found in context"               │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ expectedEventTypesVar field    │ Activity throws: "field '<field>' not found"      │
│ not found in node response     │                                                   │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ expectedEventTypesVar points   │ Activity throws: "must point to a JSON array,     │
│ to non-array value             │ got: <NodeType>"                                  │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ Resolved list is empty         │ log.warn; ANY parks forever; ALL unblocks         │
│                                │ immediately (vacuous truth)                       │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ Both var and static list set   │ expectedEventTypesVar takes priority              │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ Both absent                    │ Activity throws: "OnEventConfig must have either  │
│                                │ expectedEventTypesVar or expectedEventTypes"      │
├────────────────────────────────┼───────────────────────────────────────────────────┤
│ WaitNode is terminal node      │ BaseNodeActivityImpl overrides WAITING →          │
│ (end=true)                     │ COMPLETED — workflow ends without parking         │
└────────────────────────────────┴───────────────────────────────────────────────────┘
```

---

## 12. All Changed Files

```
commons
  WaitSemantics.java          NEW   enum ANY / ALL
  OnEventConfig.java          MOD   + expectedEventTypes, waitSemantics,
                                      expectedEventTypesVar
  WorkflowState.java          MOD   + expectedEventTypes, waitSemantics,
                                      receivedEventTypes, workflowExecutionMode
  WorkflowNode.java           MOD   + waitConfig (optional, Approach 2)

java-sdk
  WorkflowResumeRequest.java  MOD   + eventType, workflowExecutionMode
  WorkflowStartRequest.java   MOD   + workflowExecutionMode
  WorkflowExecutionMode.java  NEW   enum SYNC / ASYNC

worker
  OnEventExecutor.java        MOD   resolveExpectedEventTypes() — dot-path var + static paths
  ActivityResponse.java       MOD   + resolvedExpectedEventTypes
  ActivityThinResponse.java   MOD   + resolvedExpectedEventTypes
  BaseNodeActivityImpl.java   MOD   maps resolvedExpectedEventTypes to thin response
  WorkflowNodeExecutor.java   MOD   applyOnEventWaitState, initOnEventState,
                                    isOnEventConditionMet (with drain)
  GenericWorkflowImpl.java    MOD   resumeWorkflow tracks eventType,
                                    isResumeConditionMet (ANY/ALL/legacy + drain)
  WorkflowContextManagerActivityImpl.java
                              MOD   null-guard on viewResponse,
                                    per-event payload key written to HBase

NOT CHANGED:
  WaitNodeNodeActivityImpl    — unchanged, delegates to OnEventExecutor via map
  SchedulerWaitExecutor       — unchanged
  AbsoluteWaitExecutor        — unchanged
  GroovyTranslator            — used as-is
  HBase schema                — no changes
  GenericWorkflow interface   — no changes
  WorkflowResource endpoints  — no changes
```

---

## 13. Known Limitations

| Limitation | Detail |
|-----------|--------|
| Event type names must be unique across all WaitNodes | See §8. Overlapping names cause drain to consume events meant for a later WaitNode. Convention enforced by DSL authors, not runtime. |
