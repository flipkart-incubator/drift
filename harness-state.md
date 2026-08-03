# Harness State

## Pipeline

pipeline-stage: DONE
task-type: DESIGN_FIRST
session-id: business-key-idempotency
repo-type: SERVICE
last-updated-by: cleanup
plan-doc: harness-docs/plans/completed/business-key-idempotency_execution_plan.md
till-done-doc: harness-docs/plans/completed/business-key-idempotency_till_done.json
plan-revision: 2

## Feature

feature-tag: business-key-idempotency
feature-branch: feature/business-key-idempotency
feature-branch-base: main
feature-branch-created-at: 2026-08-03

## Design Doc Handoff (LLD already exists — designer.md and hld.md are SKIPPED)

design-doc-source: confluence-existing-lld
design-doc: harness-docs/design/active/business-key-idempotency-lld.md
lld-confluence-page-id: 474316866
lld-confluence-page-title: "LLD: Business-Key Idempotency Implementation for Drift"
lld-confluence-parent-page-id: 474382401
lld-confluence-parent-page-type: folder
lld-confluence-space: RET
lld-confluence-url: https://flipkart.atlassian.net/wiki/spaces/RET/pages/474316866/LLD+Business-Key+Idempotency+Implementation+for+Drift
prd-source: embedded-in-lld
hld-source: embedded-in-lld
note: >
  This is a feature addition to the already-migrated Drift codebase (NOT a
  Flux->Drift migration — mart-drift-deployment/migration-skills/ does not
  apply). An LLD already exists on Confluence (page 474316866) with PRD/HLD
  context embedded (see LLD sections 1-2). designer.md and hld.md are
  intentionally skipped. Next step: planner.md should read
  harness-docs/design/active/business-key-idempotency-lld.md (local cache of
  the Confluence LLD, fetched 2026-08-03) as its design input, decompose into
  subtasks, generate _till_done.json, and proceed through the normal
  evaluator -> execute -> validate -> cleanup pipeline. Open decision flagged
  in LLD section 14.1: ship the Redis-backed variant (3.2) or the Redis-free
  variant (3.4) — LLD recommends 3.4; planner should surface this as an
  explicit assumption/question if not already resolved by the user.

## Confluence

confluence-parent-page: 474382401
confluence-parent-page-type: folder
confluence-base-url: https://flipkart.atlassian.net/wiki
confluence-space: RET
confluence-review: ENABLED
confluence-auth: OK   # verified via test-auth 2026-08-03 (Ojesav Srivastava)
confluence-plan-page: 512088238
confluence-plan-published-at: 2026-08-03T07:16:46Z
confluence-plan-url: https://flipkart.atlassian.net/wiki/pages/viewpage.action?pageId=512088238

## Jira

jira-integration: SKIP

## GitHub

github-integration: ENABLED
auto-pr: true
github-base-url: https://github.com
github-repo: flipkart-incubator/drift
github-auth: PENDING_SETUP   # user deferred github.sh setup on 2026-08-03; run before validate.md attempts a push/PR

## SonarQube

# sonar-project-key intentionally absent — Sonar is SKIPPED for this feature.
# validate.md / sonar-agent RUN_GATE calls will emit SONAR_GATE_SKIPPED.

## Runtime

app-runtime: docker
docker-services: api,worker,vector,victorialogs,redis-master,redis-sentinel

## Tooling

mmdc: CONFIRMED

## Scaffold Status (first-time, this session)

harness-docs: CREATED
docker-compose: CREATED
harness-scripts: CREATED
connections.md: CREATED (Temporal frontend + HBase Zookeeper quorum left PENDING — see connections.md)
arch-enforcement: CREATED (arch-test module, ArchUnit)
validate-guard-hook: NOT WIRED (blocked by permission system as self-modification of .claude/settings.json — apply manually if desired)

## pending-sync

pending-sync:

## Stage Completion Log

- SCAFFOLD | 2026-08-03 | harness-docs, docker-compose, harness-scripts, arch-enforcement, connections.md created
- DOCS_LOADED | 2026-08-03 | AGENTS.md, ARCHITECTURE.md generated from repo analysis + existing docs/_pages/*
- LLD_FETCHED | 2026-08-03 | Cached Confluence page 474316866 to harness-docs/design/active/business-key-idempotency-lld.md; resolved parent page 474382401
- CONFLUENCE_AUTH_OK | 2026-08-03 | test-auth verified (Ojesav Srivastava); credentials stored in .confluence-credentials (gitignored, 600 perms)
- GITHUB_AUTH_DEFERRED | 2026-08-03 | User deferred github.sh setup; auto-pr will require setup before validate.md pushes/opens PR
- LLD_REFETCHED | 2026-08-03 | Re-pulled page 474316866 (v2, 33411 chars) on user request; handing off to planner.md
- PLAN_CREATED | 2026-08-03 | planner.md generated business-key-idempotency_execution_plan.md (10 subtasks, 5 layers) + _till_done.json; defaulted to LLD's recommended §3.4 (Redis-free) variant per LLD §14.1, flagged as unconfirmed assumption to user; submitting to evaluator round 1
- PLAN_REVISED | 2026-08-03 | Evaluator round 1: PLAN REVISION REQUIRED (1 FAIL: unowned response-header implementation; 2 WEAK: resolvedFromExistingWorkflow field ownership, logFormat edit ownership). Fixed: subtask-6 now owns ResponseFilter.java + response headers; subtask-2 owns resolvedFromExistingWorkflow; subtask-1 sole owner of logFormat. Re-submitting to evaluator round 2
- PLAN_APPROVED | 2026-08-03 | Evaluator round 2: PLAN LGTM (7/7 PASS, 1 non-blocking wording nit fixed). Jira SKIPPED per harness-state (jira-integration: SKIP) — no Jira operations attempted. Instrumentation Registry already drafted in plan (Phase 3). Proceeding to Phase 3B: Confluence publish + stakeholder LGTM gate
- CONFLUENCE_PLAN_PUBLISHED | 2026-08-03T07:16:46Z | Published business-key-idempotency_execution_plan.md (+ _till_done.json appendix) to Confluence page 512088238 under parent 474382401 (space RET). pipeline-stage set to AWAITING_PLAN_LGTM — hard stop until stakeholder LGTM (comment "LGTM"/"Approved" on the page, or tell planner "Plan approved" here)
- PLAN_LGTM_RECEIVED | 2026-08-03 | User approved plan directly ("plan approved") and explicitly confirmed the §3.4 (Redis-free) variant — no plan changes needed, matches evaluator-approved + published version. pipeline-stage set to IMPLEMENTING. Dispatching execute.md against business-key-idempotency_till_done.json (10 subtasks, 5 layers). Reminders carried forward: github-auth PENDING_SETUP (local commits only to feature/business-key-idempotency, no push/PR); jira-integration SKIP (no Jira ops); confluence-auth OK (available if any Confluence updates needed along the way, e.g. integration-tests.md)
- ALL_SUBTASKS_STATIC_PASS | 2026-08-03 | execute.md completed all 10 subtasks across 5 layers, each with build+test+arch-test static gates passing and a local commit on feature/business-key-idempotency (no push, per github-auth PENDING_SETUP). Notable deviations logged in commit messages: (1) RedisPubSubService.waitForRedisResponse fixed to unwrap WorkflowException (not just WorkflowNotFoundException) so WorkflowExecutionAlreadyStarted reaches TemporalService's new catch block; (2) history-purged race implemented as one bounded retry-as-fresh-start; (3) IdempotencyFilterTest/BusinessKeyIdempotencyIT mock JAX-RS context interfaces directly instead of using a live Jersey test container (no such dependency exists in this repo); (4) AC-7-3 (boot.sh Docker verification) left unmet at the static layer — Docker unavailable in this execution sandbox, explicitly a validate.md runtime gate per the plan. Docker is also required for validate.md's own Phase 2 boot gate — if unavailable there too, validate.md is expected to report BLOCKED rather than fabricate a pass. pipeline-stage set to STATIC_VALIDATED. Triggering validate.md.
- RUNTIME_VALIDATED | 2026-08-03 | validate.md retry: Docker up (Colima). api booted HEALTHY in Docker (200 /healthcheck) with full feature DI wiring — AC-7-3 PASS, no Guice CreationException (createInjector at DriftApplication:67 succeeded; earlier failure was a null JedisSentinelPool = infra, fixed once redis-sentinel was healthy). Infra substitutions (validate-owned): public redis:7-alpine retagged for unreachable jfrog image + `sentinel resolve-hostnames yes` (reverted after); eclipse-temurin:17-jdk-jammy retagged for the removed bullseye base. Runtime-verified: POST /v3/workflow/start with X_DRIFT_IDEMPOTENCY_KEY -> X-Drift-Workflow-Id = WF-<tenant>-<client>-<sha256(key)> (byte-exact, 2 distinct keys), no X-Drift-Idempotent-Replay on fresh resolve; legacy no-header request -> neither header (back-compat); malformed key -> HTTP 400 ApiException via ApiExceptionMapper; drift.idempotency.miss=2 on admin /metrics (same injected MetricRegistry). NOT runtime-exercisable in this sandbox (pre-existing infra, not a feature regression): Temporal-arbitrated duplicate dedup + X-Drift-Idempotent-Replay:true and drift.idempotency.temporal.* metrics (require a live Temporal cluster — not containerized; covered by TemporalServiceTest + BusinessKeyIdempotencyIT/TestWorkflowEnvironment, static PASS). VictoriaLogs/Vector not wired in this repo's compose -> log-pipeline verification N/A, used direct API/metrics/response-header checks per instruction. Verdict: LGTM (runtime-observable scope). pipeline-stage: VALIDATED. github-auth PENDING -> no push/PR.
- CLEANED_UP | 2026-08-03 | cleanup.md stripped all 7 debug probes (P001/P002 IdempotencyFilter.java ENTRY/RESULT; P004/P005/P006 TemporalService.java ENTRY/BRANCH×2; P007 IdempotencyKeyResolver.java ERROR×3) — removed entirely (marker comments + log.debug lines), not converted, per plan's "Strip After: VALIDATED" designation. Left the permanent MDC.put("idempotencyKey", rawKey) in IdempotencyFilter.java untouched (backs logFormat %X{idempotencyKey} + ResponseFilter's MDC.remove — not a probe). Verified zero PROBE::/_probe_/feature=business-key-idempotency* remain in api/src. Re-ran tests: mvn -pl api -am test -> 32/32 PASS; mvn -pl arch-test -am test -> 6/6 PASS, 0 violations. docs/_pages/08-API-CONTRACTS.md (subtask-9) reviewed, no further changes needed. Execution plan finalized (Instrumentation Status + Done Criteria all checked, Status: COMPLETE) and execution log created documenting deviations (RedisPubSubService WorkflowException propagation fix, bounded retry-as-fresh-start for history-purged race, no jersey-test-framework so tests mock JAX-RS interfaces directly, Docker required colima start in sandbox, till_done.json subtask-2 status bug found+fixed). Archived business-key-idempotency_execution_plan.md, _till_done.json, and _execution_log.md to harness-docs/plans/completed/. Committed as a single local commit on feature/business-key-idempotency (no push — github-auth still PENDING_SETUP). pipeline-stage: DONE.
confluence-plan-page: 512088238
confluence-plan-published-at: 2026-08-03T07:16:46Z
