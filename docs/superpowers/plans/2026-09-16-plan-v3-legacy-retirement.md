# plan-v3 Legacy Plan Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every newly created supported task explicitly `plan-v3`, archive existing `plan-v1`/`plan-v2` tasks without deleting their artifacts, extract logic that is currently shared by legacy builders and `plan-v3`, and then remove only the legacy execution/build branches.

**Architecture:** The system will have one build-plan policy for new tasks: `requestedPlanVersion` must be exactly `plan-v3`; missing or legacy versions will fail fast. Historical tasks will retain their immutable `plan_snapshot`, stages, events, and artifacts, but a durable archive marker will make them read-only and prevent dispatch/retry. Product-specific validation and parameter parsing will move into neutral helpers so the `plan-v3` builders no longer call legacy `build_plan`/`build_integrated_plan` functions. Legacy worker routes will be removed only after the database archive gate proves no old task can be dispatched.

**Tech Stack:** Java 17, Spring Boot, MyBatis/PostgreSQL, Python 3 worker, pytest, Maven.

**Spec:** Existing plan-v3 staged backend design in `docs/superpowers/plans/2026-09-09-multisource-staged-backend.md`, plus the current database inventory recorded during the 2026-09-16 analysis.

## Global Constraints

- New task creation must send `requestedPlanVersion="plan-v3"`; no missing/legacy request may silently downgrade.
- Historical `plan-v1`/`plan-v2` task snapshots and artifacts are retained for read-only detail, quality-report, preview, and download operations.
- Do not rewrite historical `plan_snapshot` JSON to `plan-v3`.
- Do not delete output files or `rs_artifact` rows as part of plan retirement.
- The current local database contains 102 tasks: 11 `plan-v1`, 23 `plan-v2`, and 68 `plan-v3`; it also contains old non-terminal rows and must be rechecked immediately before archive execution.
- A deployment rollback must be possible by restoring the previous Java/worker build without restoring deleted data.

---

### Task 1: Freeze the inventory and define the retirement gate

**Files:**
- Create: `script/sql/maintenance/20260916_plan_v3_inventory.sql`
- Create: `docs/superpowers/plans/2026-09-16-plan-v3-retirement-runbook.md`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/PlanRetirementInventoryTest.java`

**Interfaces:**
- Consumes: `rs_processing_task.plan_snapshot`, `rs_task_stage`, `rs_artifact`.
- Produces: repeatable inventory queries and a release gate that can prove there are no dispatchable legacy tasks.

- [ ] **Step 1: Add read-only inventory SQL.**

The SQL must report counts by plan version and lifecycle, old non-terminal task IDs, old running stages with lease expiry, and artifact counts. It must not update or delete rows.

```sql
SELECT plan_snapshot->>'version' AS plan_version,
       lifecycle_status,
       count(*)
FROM rs_processing_task
GROUP BY 1, 2
ORDER BY 1, 2;

SELECT t.task_id,
       t.lifecycle_status,
       t.plan_snapshot->>'version' AS plan_version,
       s.stage_id,
       s.stage_key,
       s.execution_status,
       s.lease_expire_at
FROM rs_processing_task t
JOIN rs_task_stage s ON s.task_id = t.task_id
WHERE t.plan_snapshot->>'version' IN ('plan-v1', 'plan-v2')
  AND (t.lifecycle_status IN ('CREATED', 'QUEUED', 'RUNNING', 'RECOVERY_PENDING', 'CANCELING')
       OR s.execution_status IN ('RUNNING', 'RETRYING', 'RECOVERY_PENDING'))
ORDER BY t.task_id, s.stage_id;

SELECT t.plan_snapshot->>'version' AS plan_version,
       count(a.artifact_id) AS artifact_count
FROM rs_processing_task t
LEFT JOIN rs_artifact a ON a.task_id = t.task_id
WHERE t.plan_snapshot->>'version' IN ('plan-v1', 'plan-v2')
GROUP BY 1;
```

- [ ] **Step 2: Define the hard archive gate.**

The retirement deployment is blocked unless the inventory returns zero old tasks in `CREATED`, `QUEUED`, `RUNNING`, `RECOVERY_PENDING`, or `CANCELING`, and zero old stages in `RUNNING`, `RETRYING`, or `RECOVERY_PENDING`. Expired leases still count as non-terminal until explicitly canceled/archived.

- [ ] **Step 3: Add inventory integration coverage.**

Use the PostgreSQL migration test fixture to insert one old terminal task and one old non-terminal task. Assert that the inventory distinguishes them and that a non-terminal old task fails the retirement gate.

- [ ] **Step 4: Commit the read-only baseline.**

```bash
git add script/sql/maintenance/20260916_plan_v3_inventory.sql docs/superpowers/plans/2026-09-16-plan-v3-retirement-runbook.md htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/PlanRetirementInventoryTest.java
git commit -m "chore: add plan retirement inventory gate"
```

### Task 2: Enforce plan-v3 for all new build-plan requests

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/PlanRequest.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinator.java`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`
- Test: `rs-algorithm-worker/tests/test_registry.py`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinatorTest.java`

**Interfaces:**
- Consumes: `PlanRequest.requestedPlanVersion` and the Worker `build-plan` envelope.
- Produces: `PLAN_VERSION_REQUIRED`/`PLAN_VERSION_RETIRED` failures for missing or legacy versions; only `plan-v3` reaches a new task builder.

- [ ] **Step 1: Write failing tests for the policy.**

Cover all three inputs: `plan-v3` succeeds, missing version fails, and `plan-v1`/`plan-v2` fail with a stable machine-readable error. Add a coordinator test proving the request sent during task creation contains `plan-v3`.

- [ ] **Step 2: Remove the implicit Java compatibility default.**

Delete the four-argument `PlanRequest` constructor that currently defaults to `plan-v2`. Keep the explicit record constructor and validate that the version equals `plan-v3` for new planning requests. Update all production callers to pass the explicit version.

- [ ] **Step 3: Fail fast in the Worker registry.**

At the beginning of the `build-plan` branch in `registry.py`, reject every value except the exact string `plan-v3` before selecting an adapter. Do not change historical `execute-stage` routing yet; that remains available until Task 7.

```python
requested_version = payload.get("requestedPlanVersion")
if requested_version is None:
    raise ValueError("PLAN_VERSION_REQUIRED: new tasks must request plan-v3")
if requested_version != "plan-v3":
    raise ValueError("PLAN_VERSION_RETIRED: only plan-v3 may create a task")
```

- [ ] **Step 4: Run focused Java and Python tests.**

Run `mvn -pl htht-modules/htht-multisource -Dtest=WorkerContractCodecTest,TaskCreationCoordinatorTest test` and `pytest rs-algorithm-worker/tests/test_registry.py -q`. The expected result is that no new task can silently fall back to an old builder.

- [ ] **Step 5: Commit the new-request gate.**

```bash
git add htht-modules/htht-multisource/src/main/java rs-algorithm-worker/src/rs_algorithm_worker/registry.py htht-modules/htht-multisource/src/test rs-algorithm-worker/tests/test_registry.py
git commit -m "feat: require plan-v3 for new task planning"
```

### Task 3: Add durable legacy-task archive markers without deleting data

**Files:**
- Create: `script/sql/migrations/V20260916_03__create_legacy_plan_archive.sql`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/LegacyPlanArchive.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/LegacyPlanArchiveRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArchiveLegacyPlanTasksService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/ProcessingTaskMyBatisMapper.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/LegacyPlanArchiveMyBatisMapper.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisLegacyPlanArchiveRepository.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/ArchiveLegacyPlanTasksServiceTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/PostgreSqlMigrationIntegrationTest.java`

**Interfaces:**
- Consumes: old task IDs selected by plan snapshot version and lifecycle status.
- Produces: an idempotent archive record and a service that leaves `rs_processing_task`, `rs_task_stage`, `rs_task_event`, `rs_artifact`, and output files intact.

- [ ] **Step 1: Create the archive table migration.**

Use a foreign key that does not cascade-delete the task, and make `(task_id)` unique:

```sql
CREATE TABLE rs_legacy_plan_archive (
    task_id bigint PRIMARY KEY REFERENCES rs_processing_task(task_id),
    plan_version varchar(32) NOT NULL CHECK (plan_version IN ('plan-v1', 'plan-v2')),
    archived_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason varchar(255) NOT NULL,
    UNIQUE (task_id)
);
```

- [ ] **Step 2: Implement idempotent archival.**

The service must archive terminal old tasks directly. For old `CREATED`, `QUEUED`, `RUNNING`, `RECOVERY_PENDING`, or `CANCELING` tasks, it must first transition the task and pending/running stages through the same cancellation rules used by `CancelProcessingTaskService`; an expired lease is not treated as proof that a worker is still absent. Then insert the archive marker with `ON CONFLICT DO NOTHING` semantics.

- [ ] **Step 3: Add an admin-only maintenance entry point.**

Expose a one-shot command or protected maintenance endpoint that supports `dryRun=true` and `apply=true`. `dryRun` returns task IDs and stage states; `apply` requires an explicit reason and refuses to run if a fresh inventory detects a non-expired old lease.

- [ ] **Step 4: Verify preservation.**

Integration tests must assert that archiving does not change `plan_snapshot`, artifact count, artifact URIs, event count, or input assets. A second `apply` run must change no rows.

- [ ] **Step 5: Commit archive support.**

```bash
git add script/sql/migrations htht-modules/htht-multisource/src/main/java htht-modules/htht-multisource/src/test htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java
git commit -m "feat: archive legacy plan tasks without deleting artifacts"
```

### Task 4: Prevent archived and legacy tasks from re-entering dispatch

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/RetryFailedStageService.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/PlanExecutionPolicy.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/RetryFailedStageServiceTest.java`

**Interfaces:**
- Consumes: `ProcessingTask.plan().version()` and `LegacyPlanArchiveRepository.isArchived(taskId)`.
- Produces: no dispatch or retry for archived tasks; a stable `LEGACY_PLAN_ARCHIVED` domain failure.

- [ ] **Step 1: Add failing dispatch/retry tests.**

Assert that an archived `plan-v1`/`plan-v2` task cannot dispatch or retry, while an unarchived `plan-v3` task keeps the existing dependency and lease behavior.

- [ ] **Step 2: Implement the policy.**

`PlanExecutionPolicy` must expose `assertNewPlanVersion(String)` and `assertDispatchable(ProcessingTask, boolean archived)`. Call it before claiming a stage and before changing a failed stage to `RETRYING`.

- [ ] **Step 3: Preserve read-only operations.**

Do not add the policy to task detail, progress, artifact inspection, quality-report, artifact download, or result ZIP endpoints. Those endpoints must continue reading persisted snapshots and artifacts for archived tasks.

- [ ] **Step 4: Run focused Java tests and commit.**

```bash
mvn -pl htht-modules/htht-multisource -Dtest=DispatchNextStageUseCaseTest,RetryFailedStageServiceTest test
git add htht-modules/htht-multisource/src/main/java htht-modules/htht-multisource/src/test
git commit -m "feat: block dispatch and retry for archived legacy tasks"
```

### Task 5: Extract helpers currently reused by plan-v3

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy2d_altimeter.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/hy2d_pipeline.py`
- Test: `rs-algorithm-worker/tests/test_fy3h_mersi.py`
- Test: `rs-algorithm-worker/tests/test_hy2d_altimeter.py`
- Test: `rs-algorithm-worker/tests/test_hy2d_pipeline.py`
- Test: `rs-algorithm-worker/tests/test_gf6_wfv_l1a.py`

**Interfaces:**
- Consumes: product candidate and parameter snapshot.
- Produces: neutral validation/parameter helpers used directly by `build_staged_plan` and v3 execution.

- [ ] **Step 1: Add characterization tests before moving code.**

For each affected adapter, snapshot the current `plan-v3` stage keys, dependencies, output contracts, and normalized parameter snapshot. Add a test that the v3 builder does not require a legacy builder symbol.

- [ ] **Step 2: Extract FY-3H validation and DAG construction inputs.**

Move candidate/file validation and parameter normalization into `_validated_fy3h_inputs(candidate, parameter_snapshot)`. Make `build_staged_plan` construct its v3 DAG from the helper. Leave the old integrated builder temporarily calling the same helper so behavior remains stable during the transition.

- [ ] **Step 3: Extract HY-2D parameter normalization.**

Create `parse_hy2d_parameters(parameter_snapshot)` returning the canonical parameter dictionary. Make both `build_staged_plan` and `hy2d_pipeline._parameters` call it directly; remove the pipeline's dependency on `build_plan`.

- [ ] **Step 4: Extract GF-6 L1A candidate validation and parameter parsing.**

Create `_validated_gf6_l1a_inputs(product, parameter_snapshot)` returning `(inputs, parameters)`. Make `build_staged_plan` use it directly instead of calling `build_integrated_plan`. Keep output selection identical to the existing v3 contract.

- [ ] **Step 5: Run the full affected adapter suite.**

Run `pytest rs-algorithm-worker/tests/test_fy3h_mersi.py rs-algorithm-worker/tests/test_hy2d_altimeter.py rs-algorithm-worker/tests/test_hy2d_pipeline.py rs-algorithm-worker/tests/test_gf6_wfv_l1a.py -q`. The stage keys, dependencies, outputs, and normalized parameters must be unchanged for v3.

- [ ] **Step 6: Commit the extraction before deleting anything.**

```bash
git add rs-algorithm-worker/src/rs_algorithm_worker rs-algorithm-worker/tests
git commit -m "refactor: decouple plan-v3 builders from legacy planners"
```

### Task 6: Verify every supported product has a complete v3 path

**Files:**
- Modify: `rs-algorithm-worker/tests/test_registry.py`
- Create: `rs-algorithm-worker/tests/test_plan_v3_matrix.py`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

**Interfaces:**
- Consumes: all supported product candidates and parameter snapshots.
- Produces: a matrix proving that new planning and stage execution do not depend on old versions.

- [ ] **Step 1: Cover the product matrix.**

The matrix must include FY-3H MERSI, FY-4B AGRI, GF-6 WFV L1A, GF-6 WFV Browse, HY-1C CZI L2A, and HY-2D Altimeter. For each product assert `version == "plan-v3"`, non-empty stage contracts, and stage execution requests carrying `planVersion == "plan-v3"`.

- [ ] **Step 2: Cover unsupported/generic inputs explicitly.**

An unrecognized product must either produce the existing deliberately minimal v3 validation plan or be rejected with a clear unsupported-product error; it must never silently return `plan-v1` for a new request.

- [ ] **Step 3: Run Python and Java contract tests.**

Run the complete worker test suite and the multisource Java module test suite. Record the product/version matrix in the retirement runbook.

### Task 7: Perform the archive operation and enforce the release gate

**Files:**
- Modify: `docs/superpowers/plans/2026-09-16-plan-v3-retirement-runbook.md`
- Use: `script/sql/maintenance/20260916_plan_v3_inventory.sql`

**Interfaces:**
- Consumes: the running test database and the archive service from Task 3.
- Produces: zero dispatchable old tasks, preserved historical rows/artifacts, and an auditable archive record.

- [ ] **Step 1: Stop new task creation briefly and stop the scheduler/worker.**

This prevents a task from changing state while the inventory and archive operation run.

- [ ] **Step 2: Run the inventory in dry-run mode.**

Save the result, including task IDs, lifecycle states, stage states, lease expiry, and artifact counts. Do not proceed if any old lease is non-expired or if the database differs from the expected test environment.

- [ ] **Step 3: Archive old tasks through the service.**

Cancel stale/non-terminal old tasks through the domain service, insert archive markers, and preserve all task snapshots, events, input assets, artifacts, and output files.

- [ ] **Step 4: Re-run the inventory.**

The gate must show no old task in a dispatchable lifecycle and no old stage in an executable state. Terminal old tasks may remain as archived historical rows.

- [ ] **Step 5: Verify historical read-only behavior.**

Open one archived GF-6 Browse task, one archived `plan-v2` task, and one `plan-v3` task. Verify detail, progress, quality report, artifact inspection, artifact download, and result ZIP behavior before enabling the new code.

### Task 8: Remove only the legacy builder and execution branches

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy2d_altimeter.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_browse.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerResultTranslator.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageExecutionRequest.java`
- Test: all affected worker and multisource tests

**Interfaces:**
- Consumes: the v3-only product matrix and the archive gate.
- Produces: no new or archived task can invoke a legacy builder; v3 planning/execution remains the only worker path.

- [ ] **Step 1: Remove legacy `build-plan` selection.**

Delete registry calls to `build_integrated_plan`, `build_l2a_plan`, old GF-6 Browse `build_plan`, and the `plan-v1`/`plan-v2` fallback after Task 7 has passed. Keep the explicit v3 adapter map.

- [ ] **Step 2: Remove legacy stage execution routes.**

Remove `STANDARDIZE_*` integrated execution branches and `_execute_gf_browse_stage` only after confirming no unarchived old task remains. Keep v3 `execute_*_stage` routes.

- [ ] **Step 3: Delete only unreferenced legacy builders.**

Remove `build_integrated_plan`, `build_l2a_plan`, and old `base.build_plan` implementations only after `rg` confirms no production v3 or parameter-parsing call site references them. Do not delete neutral helpers extracted in Task 5.

- [ ] **Step 4: Make the Java translator v3-only for worker plan responses.**

Require `version == "plan-v3"` in new worker plan responses. Historical task snapshots are loaded from persistence and do not pass through this build-plan response decoder.

- [ ] **Step 5: Remove implicit `plan-v2` stage-request constructors.**

Delete the compatibility `StageExecutionRequest` constructor that defaults to `plan-v2`. Keep the full constructor used by `DispatchNextStageService`, which takes the persisted task plan version explicitly.

- [ ] **Step 6: Run full tests and commit the removal.**

```bash
pytest rs-algorithm-worker/tests -q
mvn -pl htht-modules/htht-multisource,htht-admin test
git add rs-algorithm-worker/src htht-modules/htht-multisource/src htht-admin/src rs-algorithm-worker/tests htht-modules/htht-multisource/src/test
git commit -m "refactor: retire legacy plan builders after v3 migration"
```

### Task 9: Production-like acceptance and rollback rehearsal

**Files:**
- Modify: `docs/superpowers/plans/2026-09-16-plan-v3-retirement-runbook.md`
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingTaskControllerTest.java`
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`

**Interfaces:**
- Consumes: the v3-only worker and archived database state.
- Produces: evidence that all supported new workflows work and historical data remains readable.

- [ ] **Step 1: Create one new task for every supported profile.**

Assert the task detail response reports `planVersion=plan-v3`, the expected v3 stage keys, and no legacy stage key.

- [ ] **Step 2: Exercise lifecycle paths.**

For at least one profile each, verify dispatch, stage completion, failure, retry, cancellation, progress refresh, quality report, artifact inspection/download, map preview, and final ZIP download.

- [ ] **Step 3: Verify rejection paths.**

Send direct build-plan requests with no version, `plan-v1`, and `plan-v2`; each must fail without creating a task. Attempt retry/dispatch on an archived old task; each must return the archive error without changing artifacts.

- [ ] **Step 4: Rehearse rollback.**

Restore the previous Java/worker binaries in a disposable test database snapshot. Confirm archived rows and artifacts remain readable and that the rollback does not require deleting the archive table.

- [ ] **Step 5: Close the retirement gate.**

Record the final inventory, test commands and outputs, application build identifiers, migration version, and the explicit statement that no legacy execution branch is reachable from a new task.

## Completion Criteria

- Every normal task creation request contains `requestedPlanVersion=plan-v3`.
- Missing/legacy build-plan requests fail before product adapter selection.
- All supported profiles have a complete v3 planning/execution matrix.
- No v3 builder calls a legacy `build_plan` or `build_integrated_plan` function.
- All old non-terminal tasks are canceled and archived before legacy execution removal.
- Historical task snapshots, stages, events, artifacts, previews, reports, and downloads remain readable.
- `plan-v1`/`plan-v2` builders and execution routes have no production references after removal.
- Full Python worker and Java multisource/admin test suites pass.
- A rollback to the previous build has been rehearsed without data deletion.

## Explicit Non-Goals

- Do not rewrite old task snapshots from `plan-v1`/`plan-v2` to `plan-v3`.
- Do not delete historical tasks, artifacts, result files, or quality reports.
- Do not redesign the plan-v3 stage DAGs or change product science behavior as part of this cleanup.
- Do not remove read-only historical task APIs unless a separate retention policy is approved.
