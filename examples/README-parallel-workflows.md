# Parallel Workflow Examples

This directory contains example DSL definitions for Drift parallel workflow execution.

## `parallel-vendor-notify-workflow.json`

Fan-out / fan-in pattern from the [Parallel Workflow Execution design doc](https://flipkart.atlassian.net/wiki/spaces/RET/pages/489472616):

```
         fetchOrder
        /    |    |    \
 notifyA  notifyB  notifyC  delayedAudit (SCHEDULER_WAIT)
        \    |    |    /
         processResults (sink)
```

| Node | Wait type | Notes |
|------|-----------|-------|
| `notifyVendorA` | ON_EVENT (ALL) | Waits for `CALLBACK_A1` and `CALLBACK_A2` |
| `notifyVendorB` | ON_EVENT (ANY) | Waits for `CALLBACK_B` |
| `notifyVendorC` | none | Completes after HTTP POST |
| `delayedAudit` | SCHEDULER_WAIT | Parks until scheduler fires `SCHEDULER_WAIT_delayedAudit` |
| `processResults` | fan-in sink | Starts only after all four branches complete |

### Resume external events

```http
PUT /v3/workflow/resume/{workflowId}
Content-Type: application/json

{
  "eventType": "CALLBACK_A1",
  "params": { "orderId": "ORD-99" },
  "workflowExecutionMode": "ASYNC"
}
```

Scheduler resume uses event type `SCHEDULER_WAIT_{instanceName}` (e.g. `SCHEDULER_WAIT_delayedAudit`).

### Key DSL fields

- `executionType: "PARALLEL"` — enables DAG engine (`dependsOn` graph)
- `workflowExecutionMode: "ASYNC"` — API returns immediately; required for multi-branch event waits
- `dependsOn` — list of predecessor `instanceName` values (replaces `nextNode` in parallel workflows)
