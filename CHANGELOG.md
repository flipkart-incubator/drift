# Changelog

## Unreleased

### Added
- `SIDELINED` workflow status (`java-sdk/src/main/java/com/flipkart/drift/sdk/model/enums/WorkflowStatus.java`), representing a parallel-execution node paused pending an external unsideline signal.
  - **Consumer action required:** any code that switches on `WorkflowStatus` must add explicit `SIDELINED` handling. There is no default/fallback behavior — serial workflow execution (`WorkflowNodeExecutor.handleNodeResponseStatus`) rejects `SIDELINED` outright with `SIDELINED_NOT_SUPPORTED_SERIAL` rather than coercing it to `COMPLETED` or silently dropping it. Parallel execution (`ParallelWorkflowEngine`) is the only supported path for pause/unsideline semantics today.
- `idempotency-key` request header support, mapped to the Temporal workflow ID, in the drift service.
