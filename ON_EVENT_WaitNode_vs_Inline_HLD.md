# HLD: ON_EVENT Wait Behaviour — Approach Comparison & Decision

## 1. Context

Drift workflows need to support pausing at a node and waiting for N external events
before continuing. Two approaches exist for where this "wait behaviour" lives in the
system. This document defines both approaches, compares them, and picks the better one.

---

## 2. Approach 1 — Dedicated WaitNode (Extend Existing)

### 2.1 What It Is

`WaitNode` is a standalone node in the DSL whose sole purpose is to pause the workflow.
The existing `WaitType.ON_EVENT` skeleton is completed to support N external events.

### 2.2 DSL Shape

```json
{
  "states": {
    "notifyServiceA": {
      "instanceName": "notifyServiceA",
      "type": "HTTP",
      "nextNode": "waitForCallback"
    },
    "waitForCallback": {
      "instanceName": "waitForCallback",
      "type": "WAIT",
      "nextNode": "processResults",
      "nodeDefinition": {
        "waitType": "ON_EVENT",
        "expectedEventTypes": ["CALLBACK_FROM_A", "CALLBACK_FROM_B"],
        "waitSemantics": "ALL"
      }
    },
    "processResults": {
      "instanceName": "processResults",
      "type": "HTTP",
      "nextNode": "successNode"
    }
  }
}
```

### 2.3 Execution Flow

```
While loop iteration 1:
    [HTTP Node — notifyServiceA]
          │
          │  executes, returns COMPLETED
          │
          ▼
    nextNode = "waitForCallback"

While loop iteration 2:
    [WaitNode — waitForCallback]
          │
          │  OnEventExecutor runs:
          │    copies expectedEventTypes, waitSemantics → WorkflowState
          │    returns WAITING
          │
          ▼
    handleWaitingState()
          │
          ├── (ASYNC mode) skip ReturnControlActivity
          └── Workflow.await(status != WAITING)  ◄── PARKED

[External events arrive — Signal 1, Signal 2]
    resumeWorkflow signal handler:
          │
          ├── received={CALLBACK_FROM_A}  → NOT YET → stays WAITING
          └── received={CALLBACK_FROM_A, CALLBACK_FROM_B} → ALL ✓ → RUNNING

    Workflow.await() unblocks
          │
          ▼
    nextNode = "processResults"

While loop iteration 3:
    [HTTP Node — processResults]
```

### 2.4 Graph Shape

```
[HTTP: notifyServiceA]
          │
          ▼
[WAIT: waitForCallback]   ←── standalone node, sole purpose is to park
          │
          ▼
[HTTP: processResults]
```

### 2.5 Component Changes

```
commons
  └── OnEventConfig.java
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics  (ANY / ALL)

  └── WorkflowState.java
        ADD  receivedEventTypes: Set<String>
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics

worker
  └── OnEventExecutor.java
        IMPLEMENT: copy config → WorkflowState, return WAITING

  └── GenericWorkflowImpl.resumeWorkflow()
        ADD  N-event condition check before setting status = RUNNING

  └── WorkflowNodeExecutor.handleWaitingState()
        ADD  ASYNC mode guard: skip ReturnControlActivity if ASYNC

java-sdk
  └── WorkflowResumeRequest.java
        ADD  eventType: String
        ADD  workflowExecutionMode: WorkflowExecutionMode
```

### 2.6 Pros and Cons

```
PROS:
  ✓  Minimal change — ON_EVENT skeleton already exists (WaitType, OnEventConfig,
     OnEventExecutor, WaitNodeNodeActivityImpl, WaitTypeExecutor MapBinder)
  ✓  Clear separation of concerns — node does work, WaitNode waits
  ✓  No change to WorkflowNode DSL wrapper
  ✓  WaitNode behaviour is explicit and visible in the graph
  ✓  Existing SCHEDULER and ABSOLUTE wait types unaffected

CONS:
  ✗  Every async operation requires two nodes in DSL (action + wait)
  ✗  Graph becomes verbose — WaitNode adds an extra hop with no logic
  ✗  WaitNode is semantically empty — it does nothing except park
  ✗  Callers building DSLs must always remember to add a WaitNode after
     every node that needs async callback
  ✗  Cannot express "this HTTP call itself waits for callback" cleanly
```

---

## 3. Approach 2 — Inline `waitConfig` on Any Node

### 3.1 What It Is

An optional `waitConfig` field is added to `WorkflowNode` (the DSL wrapper around any
node definition). When present, `WorkflowNodeExecutor` applies wait behaviour **after**
the node's activity completes — without a separate WaitNode in the graph.

### 3.2 DSL Shape

```json
{
  "states": {
    "notifyServiceA": {
      "instanceName": "notifyServiceA",
      "type": "HTTP",
      "nextNode": "processResults",
      "waitConfig": {
        "waitType": "ON_EVENT",
        "expectedEventTypes": ["CALLBACK_FROM_A", "CALLBACK_FROM_B"],
        "waitSemantics": "ALL"
      }
    },
    "processResults": {
      "instanceName": "processResults",
      "type": "HTTP",
      "nextNode": "successNode"
    }
  }
}
```

The WaitNode is gone. `notifyServiceA` fires the HTTP call and then waits inline.

### 3.3 Execution Flow

```
While loop iteration 1:
    [HTTP Node — notifyServiceA]
          │
          │  HTTP activity executes, returns COMPLETED
          │
          ├── WorkflowNodeExecutor checks:
          │       currentNode.waitConfig != null?  → YES
          │
          ├── copies waitConfig → WorkflowState
          │       expectedEventTypes = [CALLBACK_FROM_A, CALLBACK_FROM_B]
          │       waitSemantics = ALL
          │       receivedEventTypes = {}
          │
          └── sets status = WAITING
                    │
              handleWaitingState()
                    │
                    ├── (ASYNC mode) skip ReturnControlActivity
                    └── Workflow.await(status != WAITING)  ◄── PARKED

[External events arrive — Signal 1, Signal 2]
    resumeWorkflow signal handler:
          │
          ├── received={CALLBACK_FROM_A}  → NOT YET → stays WAITING
          └── received={CALLBACK_FROM_A, CALLBACK_FROM_B} → ALL ✓ → RUNNING

    Workflow.await() unblocks
          │
          ▼
    nextNode = "processResults"   ← from notifyServiceA.nextNode directly

While loop iteration 2:
    [HTTP Node — processResults]
```

### 3.4 Graph Shape

```
[HTTP: notifyServiceA]   ←── does work AND waits inline
          │
          ▼
[HTTP: processResults]   ←── one less node in the graph
```

### 3.5 Component Changes

```
commons
  └── WorkflowNode.java
        ADD  waitConfig: WaitConfig  (optional, nullable)

  └── OnEventConfig.java
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics  (ANY / ALL)

  └── WorkflowState.java
        ADD  receivedEventTypes: Set<String>
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics

worker
  └── WorkflowNodeExecutor.executeNode()
        ADD  after activity completes:
               if currentNode.waitConfig != null:
                   copy waitConfig → WorkflowState
                   set status = WAITING
                   handleWaitingState()

  └── GenericWorkflowImpl.resumeWorkflow()
        ADD  N-event condition check before setting status = RUNNING

  └── WorkflowNodeExecutor.handleWaitingState()
        ADD  ASYNC mode guard: skip ReturnControlActivity if ASYNC

java-sdk
  └── WorkflowResumeRequest.java
        ADD  eventType: String
        ADD  workflowExecutionMode: WorkflowExecutionMode

  NOTE: WaitNodeNodeActivityImpl, OnEventExecutor, WaitTypeExecutor
        still exist for standalone WaitNode use cases.
        No deletion of existing code.
```

### 3.6 Pros and Cons

```
PROS:
  ✓  DSL is concise — one node does work and waits, no extra hop
  ✓  Any node type can declare wait behaviour (HTTP, PROCESSOR,
     GROOVY, DELEGATE, CHILD)
  ✓  Graph reflects real intent — fewer phantom nodes
  ✓  Single point of wait handling in WorkflowNodeExecutor
  ✓  Works alongside existing WaitNode — both coexist
  ✓  Callers cannot forget to add a WaitNode — wait is declared
     on the node itself

CONS:
  ✗  WorkflowNode DSL wrapper changes (minor, additive, backward compatible)
  ✗  Wait behaviour is implicit — reader must look at waitConfig field
     to know a node will park after executing
  ✗  Slightly harder to reason about node graph — a node's "done" is
     not the same as "moved to nextNode"
```

---

## 4. Full Comparison

```
┌──────────────────────────────┬──────────────────────────┬────────────────────────────┐
│                              │  Approach 1              │  Approach 2                │
│                              │  Dedicated WaitNode      │  Inline waitConfig         │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ DSL nodes for async op       │  2 (action + wait)       │  1 (action with wait)      │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Applicable to which nodes    │  Only WAIT node          │  Any node type             │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Graph readability            │  Explicit wait node      │  Compact, less noise       │
│                              │  visible in graph        │                            │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Backward compatibility       │  Full — no DSL change    │  Full — waitConfig is      │
│                              │                          │  optional, null by default │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Code change surface          │  Small — inside          │  Small — WorkflowNode +    │
│                              │  WaitNode pipeline only  │  WorkflowNodeExecutor      │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Standalone wait (no          │  Yes — WaitNode alone    │  Yes — WaitNode still      │
│ preceding activity)          │                          │  exists for this           │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Existing WaitNode preserved  │  Yes                     │  Yes                       │
├──────────────────────────────┼──────────────────────────┼────────────────────────────┤
│ Risk                         │  Low                     │  Low                       │
└──────────────────────────────┴──────────────────────────┴────────────────────────────┘
```

---

## 5. Decision — Approach 2 (Inline waitConfig) with Approach 1 as Fallback

### Why Approach 2 wins

**Real-world usage pattern:**
In practice, a wait almost never exists without a preceding action. The most common
pattern is "fire HTTP call → wait for its async callback." Approach 1 forces two nodes
for this. Approach 2 expresses it in one.

**Graph reflects intent:**
```
Approach 1 graph:
  [notifyServiceA] → [waitForCallback] → [processResults]
                          ↑
                    phantom node — does no work,
                    just parks

Approach 2 graph:
  [notifyServiceA] → [processResults]
  (notifyServiceA internally waits for callback)
```

**Scales to multiple async hops:**
```
Approach 1 (3 async hops = 6 nodes):
  [HTTP-A] → [WAIT-A] → [HTTP-B] → [WAIT-B] → [HTTP-C] → [WAIT-C] → [SUCCESS]

Approach 2 (3 async hops = 4 nodes):
  [HTTP-A(+wait)] → [HTTP-B(+wait)] → [HTTP-C(+wait)] → [SUCCESS]
```

**Single place to handle waiting:**
All wait logic lives in `WorkflowNodeExecutor.executeNode()`. No duplication.
WaitNode activity is only invoked for pure standalone wait cases.

### Why Approach 1 is kept (not removed)

Approach 1 (WaitNode) still covers valid standalone wait use cases:

```
- Pure scheduler pause:  [NODE-A] → [WAIT: scheduler 10min] → [NODE-B]
- Entry-point wait:      start → [WAIT: ON_EVENT] → first action node
- Conditional wait:      BRANCH routes to WaitNode in one branch only
```

These cannot be expressed with inline `waitConfig` since there is no "preceding node"
whose `waitConfig` to attach to. WaitNode stays.

### Chosen Model — Both Coexist

```
┌─────────────────────────────────────────────────────────────┐
│                     Final Model                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  WorkflowNode.waitConfig (optional)                         │
│    → use when: a node does work AND must wait after         │
│    → supported on: HTTP, PROCESSOR, GROOVY, DELEGATE, CHILD │
│                                                             │
│  WaitNode (existing, unchanged)                             │
│    → use when: pure wait with no preceding action           │
│    → supported: SCHEDULER, ABSOLUTE, ON_EVENT               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

DSL authors choose based on their use case. Both paths funnel into the same
`handleWaitingState()` and `Workflow.await()` mechanism in the worker.

---

## 6. Unified Execution Flow (Both Approaches)

```
WorkflowNodeExecutor.executeNode(currentNode)
          │
          ├── activity runs (HTTP / GROOVY / WAIT / etc.)
          │   returns ActivityThinResponse
          │
          ├── if NodeType == WAIT:
          │       WaitNodeNodeActivityImpl already set status = WAITING
          │       (handled by OnEventExecutor / SchedulerWaitExecutor)
          │
          ├── if currentNode.waitConfig != null  (Approach 2 path):
          │       copy waitConfig → WorkflowState
          │       override status = WAITING
          │
          └── handleNodeResponseStatus()
                    │
                    └── WAITING → handleWaitingState()
                                      │
                                      ├── ASYNC: skip ReturnControlActivity
                                      └── Workflow.await(status != WAITING)
                                                ◄── PARKED

[N external events arrive via resumeWorkflow Signal]
          │
          ├── each signal: add eventType to receivedEventTypes
          ├── check condition (ANY / ALL)
          └── when met: status = RUNNING → Workflow.await() unblocks
                    │
                    ▼
          currentNode = workflow.getStates().get(currentNode.getNextNode())
          while loop continues
```

---

## 7. Final Code Changes Summary

```
commons
  └── WorkflowNode.java
        ADD  waitConfig: WaitConfig  (optional)          ← Approach 2 entry point

  └── OnEventConfig.java
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics

  └── WorkflowState.java
        ADD  receivedEventTypes: Set<String>
        ADD  expectedEventTypes: List<String>
        ADD  waitSemantics: WaitSemantics
        ADD  workflowExecutionMode: WorkflowExecutionMode

worker
  └── WorkflowNodeExecutor.executeNode()
        ADD  inline waitConfig check after activity completes

  └── WorkflowNodeExecutor.handleWaitingState()
        ADD  ASYNC mode guard: skip ReturnControlActivity

  └── OnEventExecutor.java
        IMPLEMENT (was UnsupportedOperationException stub)

  └── GenericWorkflowImpl.resumeWorkflow()
        ADD  N-event condition check (ANY / ALL)
        ADD  receivedEventTypes tracking

java-sdk
  └── WorkflowResumeRequest.java
        ADD  eventType: String
        ADD  workflowExecutionMode: WorkflowExecutionMode (nullable)

  └── WorkflowStartRequest.java
        ADD  workflowExecutionMode: WorkflowExecutionMode (default SYNC)

  └── WorkflowExecutionMode.java  (new enum)
        SYNC / ASYNC

NOT CHANGING:
  WaitNodeNodeActivityImpl    — unchanged
  WaitTypeExecutor / MapBinder — unchanged
  SchedulerWaitExecutor       — unchanged
  WorkflowResource endpoints  — unchanged
  HBase schema                — unchanged
  GenericWorkflow interface   — unchanged (resumeWorkflow signal stays as-is)
```

---

## 8. Existing Resume Capability vs New ON_EVENT Resume

### 8.1 Overview

Both paths use the same signal (`resumeWorkflow`) and the same endpoint
(`PUT /workflow/resume/{workflowId}`). The difference is **who calls it, why they call
it, whether they block, and how many calls are needed to unpark the workflow**.

---

### 8.2 Capability Comparison

```
┌─────────────────────────────────┬───────────────────────────────┬────────────────────────────────┐
│                                 │  Existing Resume              │  New ON_EVENT Resume           │
│                                 │  (Human-in-the-loop)          │  (External Event / Async)      │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Who triggers resume             │  Human (agent, ops, UI)       │  External system / event bus   │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Execution mode                  │  SYNC (default)               │  ASYNC                         │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Caller blocks?                  │  Yes — blocks on Redis        │  No — returns 200 immediately  │
│                                 │  pub/sub until workflow       │  with no wait                  │
│                                 │  publishes a response         │                                │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Redis required                  │  Yes — pub/sub channel        │  No — signal sent directly     │
│                                 │  opened per request           │  to Temporal, zero Redis       │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ How many resume calls to        │  1 — first resume always      │  1 (ANY) or N (ALL) — workflow │
│ unpark the workflow             │  unparks immediately          │  stays parked until condition  │
│                                 │                               │  is met                        │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Event identity                  │  None — resume carries form   │  eventType field identifies    │
│                                 │  data (viewResponse) but no   │  which event arrived; used for │
│                                 │  semantic event type          │  ALL-condition tracking        │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Payload access in downstream    │  {{currentNodeRef_view        │  {{currentNodeRef_             │
│ nodes                           │    Response.field}}           │    <eventType>_payload.field}} │
│                                 │  {{globalParams.params.field}}│  {{globalParams.params.field}} │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Caller receives workflow result │  Yes — final WorkflowResponse │  No — caller gets 200 OK with  │
│ in resume response              │  (status, view, disposition)  │  status=RUNNING only           │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ WaitNode type used              │  Any WaitType (SCHEDULER,     │  ON_EVENT specifically         │
│                                 │  ABSOLUTE, or ON_EVENT)       │                                │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Signal sent to Temporal         │  Yes (same resumeWorkflow)    │  Yes (same resumeWorkflow)     │
│                                 │                               │                                │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Signal handler behaviour        │  Always sets status=RUNNING   │  Sets status=RUNNING only when │
│                                 │  immediately                  │  ANY / ALL condition is met    │
├─────────────────────────────────┼───────────────────────────────┼────────────────────────────────┤
│ Typical use cases               │  Human approval, form submit, │  Async callback from downstream│
│                                 │  agent decision, ops override │  service, fan-in, multi-system │
│                                 │                               │  coordination, event bus       │
└─────────────────────────────────┴───────────────────────────────┴────────────────────────────────┘
```

---

### 8.3 What Is the Same

```
┌────────────────────────────────────────────────────────────┐
│               Shared Infrastructure (Unchanged)            │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Endpoint:   PUT /v3/workflow/resume/{workflowId}          │
│  Signal:     resumeWorkflow (same @SignalMethod)           │
│  Await:      Workflow.await(status != WAITING)             │
│  HBase:      params written to context on every resume     │
│  DSL:        WaitNode definition unchanged                 │
│  Temporal:   signal queue, determinism, replay — same      │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

The **only internal divergence** is in `GenericWorkflowImpl.resumeWorkflow()`:

```
Existing:                           New (ON_EVENT):

resumeWorkflow(request)             resumeWorkflow(request)
    │                                   │
    ├── persist to HBase                ├── persist to HBase
    │                                   │
    └── status = RUNNING  ← always      ├── receivedEventTypes.add(request.eventType)
                                        │
                                        ├── check condition (ANY / ALL)
                                        │
                                        ├── condition met?
                                        │     YES → status = RUNNING
                                        │     NO  → status stays WAITING
                                        │
                                        └── Workflow.await() re-evaluates
```

Everything above and below this point — the signal delivery, `Workflow.await()`,
`WorkflowNodeExecutor`, `handleWaitingState()` — is identical for both paths.

---

### 8.4 Decision: When to Use Each

```
┌──────────────────────────────────────────────────────────────────┐
│  Use existing SYNC resume when:                                  │
│    - A human or UI needs to know the workflow result immediately  │
│    - Redis is available                                          │
│    - Single event always unblocks the workflow                   │
│    - Caller expects a WorkflowResponse (status, view, etc.)      │
│                                                                  │
│  Use new ASYNC ON_EVENT resume when:                             │
│    - Caller is an external system / event bus                    │
│    - Caller cannot or should not block                           │
│    - Workflow must wait for N events (ANY or ALL)                │
│    - Redis is unavailable or undesirable                         │
│    - Fire-and-forget semantics are acceptable                    │
└──────────────────────────────────────────────────────────────────┘
```
