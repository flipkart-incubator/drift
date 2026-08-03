# Execution Log: Business-Key Idempotency for Drift

## Summary

All 10 subtasks implemented, statically validated (execute.md) and runtime-validated (validate.md).
`validate_verdict: LGTM` recorded in `business-key-idempotency_till_done.json`. cleanup.md ran
post-LGTM to strip debug instrumentation, re-verify tests, and archive the plan.

## Key Deviations from Plan (discovered during execute/validate)

1. **`RedisPubSubService` `WorkflowException` propagation fix** — `subscribeAndExecute` was not
   correctly propagating `WorkflowException` (including `WorkflowExecutionAlreadyStarted`) out of
   the callback in all cases; fixed so `TemporalService.executeWorkflow`'s catch-ordering
   (`WorkflowExecutionAlreadyStarted` before `WorkflowException`) actually gets exercised end-to-end.
2. **Bounded retry-as-fresh-start for the history-purged race (LLD §9)** — when an existing
   workflow's history has been purged by Temporal namespace retention and cannot be queried,
   `resolveAlreadyStartedWorkflow` now retries the start exactly once as a fresh workflow
   (`allowPurgeRetry=false` on the retry) instead of surfacing a 502, guaranteeing termination
   (no unbounded recursion).
3. **No `jersey-test-framework` on the classpath** — filter and integration tests mock the JAX-RS
   `ContainerRequestContext`/`UriInfo`/`MultivaluedMap` interfaces directly rather than bootstrapping
   a Jersey test container.
4. **Docker was down in the sandbox** — `validate.md` could not boot the local stack until
   `colima start` was run manually to bring up the Docker daemon; noted here since it blocked
   runtime validation until resolved.
5. **`_till_done.json` subtask-2 status bug** — a stale/incorrect status value on subtask-2 was
   found during validation and corrected so the tracker accurately reflected `STATIC_PASS` +
   `validate_status: PASS`.

## Cleanup Summary

- **Cleanup agent ran:** 2026-08-03T14:03:00+05:30
- **Probes found:** 7 (P001, P002, P004, P005, P006, P007 — P007 covers 3 throw sites)
- **Stripped:** 7/7 (all — comment markers + `log.debug` lines removed entirely, not converted;
  these were debug-only instrumentation per the plan's "Strip After: VALIDATED" designation)
- **Retained (converted to permanent):** 0 — the one permanent, non-probe log-related artifact
  (`MDC.put("idempotencyKey", rawKey)` in `IdempotencyFilter.java`) was left untouched, as it backs
  the `%X{idempotencyKey}` logFormat token and `ResponseFilter`'s `MDC.remove` cleanup, not
  validation-only debugging.
- **Verification:** `grep -rn "PROBE::" api/src` → 0; `grep -rn "_probe_" api/src` → 0;
  `grep -rn "feature=business-key-idempotency" api/src/main` → 0
- **Tests after stripping:** `mvn -o -pl api -am test -Dgpg.skip=true` → 32/32 PASS
- **Arch tests after stripping:** `mvn -o -pl arch-test -am test -Dgpg.skip=true` → 6/6 PASS, 0 violations
- **Docs:** `docs/_pages/08-API-CONTRACTS.md` (subtask-9) reviewed — accurately documents the
  request header, error codes, and response headers (`X-Drift-Workflow-Id`,
  `X-Drift-Idempotent-Replay`); no further changes required.
- **Plan archived to:** `harness-docs/plans/completed/business-key-idempotency_execution_plan.md`
- **Till-done archived to:** `harness-docs/plans/completed/business-key-idempotency_till_done.json`

Feature is production-ready.
