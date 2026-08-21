# Multisource Execution Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a created multisource task executable from its persisted plan through Worker dispatch, idempotent event handling, DAG progression, artifact registration, cancellation, retry, and progress queries.

**Architecture:** Keep orchestration use cases and state rules in `htht-multisource`; keep MyBatis and Spring HTTP composition in their existing infrastructure/admin boundaries. The first implementation uses the existing synchronous local Worker gateway, but all execution identity and event handling go through application ports so a queue can replace the gateway later. Existing `rs_task_stage`, `rs_task_stage_dependency`, `rs_task_event`, `rs_artifact`, and `rs_quality_result` tables are reused; no development database migration is executed.

**Tech Stack:** Java 17, Spring Boot 2.7, MyBatis-Plus, PostgreSQL/PostGIS, JUnit 5, MockMvc, existing local Python Worker contract.

---

## File Map

- Create domain execution value objects and transition methods under `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/`.
- Create execution event, grant, artifact, and quality application models under `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/`.
- Extend `TaskStageRepository`, `ArtifactRepository`, and add execution-event/recovery ports under `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/`.
- Create orchestration use cases under `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/`: dispatch, accept event, cancel, retry, recover timeout, and query progress.
- Extend entities, persistence mapping, MyBatis mappers, and repositories under `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/`.
- Add a migration only if the current schema verification proves the required execution idempotency constraint is missing: `script/sql/migrations/V20260821_01__add_execution_orchestration_constraints.sql`.
- Add authenticated execution controllers and DTOs under `htht-admin/src/main/java/com/htht/web/controller/multisource/`.
- Extend `MultiSourceAdapterConfiguration` only after application ports and repository adapters compile.
- Add focused tests beside each changed layer; do not add frontend tests or database writes against the development database.

## Corrected Current-Worker Contract

The existing `LocalProcessWorkerGateway` blocks until `execute-stage` returns and its response currently contains only `workerId`. This plan therefore records a Java-owned terminal event with `eventSequence=1` after each synchronous Worker invocation. Idempotency is the existing PostgreSQL key `(stage_id, attempt, event_sequence)`; no `workerEventId` column or migration is introduced. The asynchronous callback protocol remains deferred.

`TaskStage` keeps its existing five logical fields and its compatibility constructor. It gains a nullable `StageExecution` value for execution ID, lease expiry, Worker ID, error code/message, started time, and finished time. `attempt` remains an `int`; `ExecutionAttempt` is removed from this plan to avoid a needless breaking change across task creation and persistence.

## Task 1: Freeze Domain State Transitions And Execution Identity

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskStage.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/StageExecutionStatus.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/StageExecution.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/TaskStageTransitionTest.java`

- [x] **Step 1: Write failing transition tests.** Covered `PENDING -> RUNNING`, `RUNNING -> SUCCEEDED`, `RUNNING -> FAILED`, `RUNNING -> RECOVERY_PENDING`, `RECOVERY_PENDING -> RETRYING`, and `RETRYING -> RUNNING`; terminal, stale, and invalid-context transitions throw domain exceptions.
- [x] **Step 2: Run the focused test.** The new test failed at compilation because `TaskStage` did not yet provide the execution context or transition API.
- [x] **Step 3: Implement the smallest state API.** Kept `attempt` as an `int`, added nullable `StageExecution`, preserved the five-argument constructor, and added immutable guarded transitions.
- [x] **Step 4: Run tests and commit.** `TaskStageTransitionTest` passed with 8 tests. Compatibility regression (`ProcessingTaskTest`, `RepositoryMappingTest`, `RepositoryAdapterTest`, and `CreateProcessingTaskUseCaseTest`) passed with 23 tests.

## Task 2: Add Application Ports And In-Memory Orchestration Tests

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ExecutionEventRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ExecutionIdGenerator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ExecutionClock.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskStageRepository.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ArtifactRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/ExecutionEvent.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageClaim.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/ArtifactCandidate.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`

- [x] **Step 1: Define port contracts in tests.** Added an in-memory repository test proving `claimNextRunnable(taskId, execution, now)` selects only dependency-satisfied `PENDING` stages, transitions the selected stage to `RUNNING`, and prevents a second claim.
- [x] **Step 2: Run the focused test.** The new test failed at compilation because `StageClaim` and the atomic claim contract did not exist.
- [x] **Step 3: Add ports and models.** Added the execution ID/clock/event ports, `StageClaim`, `ExecutionEvent`, and `ArtifactCandidate`. `TaskStageRepository` now declares the atomic claim operation with a compatibility default until the Task 4 MyBatis implementation is added.
- [x] **Step 4: Run compilation and commit.** `DispatchNextStageUseCaseTest` and `TaskStageTransitionTest` passed with 10 tests. Compatibility regression remains the next verification before the Task 1/2 commit.

## Task 3: Implement Dispatch And Event State Transitions

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/DispatchNextStageUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/AcceptWorkerEventUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/AcceptWorkerEventService.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/AcceptWorkerEventUseCaseTest.java`

- [ ] **Step 1: Add failing dispatch tests.** Assert a claim generates server-owned `ExecutionId`, grant, and `StageExecutionRequest`; assert Worker dispatch is called after the stage is claimed; assert no dispatch occurs for missing dependencies, cancelled tasks, or no pending stages.
- [ ] **Step 2: Add failing event tests.** Assert one success event changes the stage to `SUCCEEDED`, registers artifacts, and makes the dependent stage runnable; duplicate `(stage, attempt, eventSequence)` is a no-op; stale execution IDs are recorded as ignored; failure changes the stage to `FAILED` with the Worker reason.
- [ ] **Step 3: Implement dispatch.** Load the task and stage snapshots, claim the stage through the repository, create an execution grant limited to the task inputs/output and expiry, build `StageExecutionRequest` from the persisted plan snapshot, and call `TaskDispatcher` outside the persistence transaction. Persist a structured dispatch failure event when gateway invocation fails.
- [ ] **Step 4: Implement event acceptance.** Insert the event idempotently first; only the newly inserted event may mutate stage/artifact/task state. Validate execution ID and attempt, translate Worker success/failure into domain transitions, and return the prior result for duplicates.
- [ ] **Step 5: Run focused tests and commit.** Run `mvn -pl htht-modules/htht-multisource -am -Dtest=DispatchNextStageUseCaseTest,AcceptWorkerEventUseCaseTest test`; commit `feat: dispatch stages and apply worker events`.

## Task 4: Align Persistence With The Existing `rs_` Schema

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/TaskStageEntity.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/ArtifactEntity.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/TaskEventEntity.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskStageMyBatisMapper.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskEventMyBatisMapper.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/ArtifactMyBatisMapper.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapping/TaskPersistenceMapper.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisTaskStageRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisExecutionEventRepository.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisArtifactRepository.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/RepositoryMappingTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/MigrationScriptTest.java`

- [ ] **Step 1: Extend mapping tests first.** Verify stage execution ID, lease/error/timestamps, event sequence and acceptance status, artifact size, and JSON payload map without dropping existing columns.
- [ ] **Step 2: Verify migration before writing one.** Run `mvn -pl htht-modules/htht-multisource -am -Dtest=MigrationScriptTest test` and inspect `V20260819_01__create_multisource_core_tables.sql`. If the existing unique `(stage_id, attempt, event_sequence)` constraint is sufficient for the Worker contract, add no migration. If a globally stable Worker event ID is required by the codec, add `V20260821_01__add_execution_orchestration_constraints.sql` with only the missing nullable column/index and a rollback comment.
- [ ] **Step 3: Implement entities, mapper, and repositories.** Add row-lock/compare-and-set SQL for claiming the next runnable stage using `rs_task_stage_dependency`; use `insertIfAbsent` and map duplicate-key conflicts to an idempotent duplicate result. Preserve PostgreSQL JSONB handlers and never alter `st_island_info` or `sd_observe_data`.
- [ ] **Step 4: Run persistence tests.** Run `mvn -pl htht-modules/htht-multisource -am -Dtest=RepositoryMappingTest,MigrationScriptTest test`; commit `feat: persist execution state and events`.

## Task 5: Add Artifact Validation And DAG Task Summary

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ArtifactValidator.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/AcceptWorkerEventService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingTask.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ArtifactRegistrationServiceTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/TaskLifecycleSummaryTest.java`

- [ ] **Step 1: Write failing artifact tests.** Accept only regular Worker outputs under the controlled work root with declared checksum/type; reject missing files, checksum mismatch, client paths, and duplicate artifact registration.
- [ ] **Step 2: Implement registration.** Validate the Worker result through an application port, create `Artifact`, save it in the same state transaction as stage success, and prevent a duplicate event from creating another artifact.
- [ ] **Step 3: Write and implement summary tests.** Assert all required stages succeeded -> task `SUCCEEDED`; a non-retryable failure -> `FAILED`; cancellation with no runnable work -> `CANCELLED`; any active stage -> `RUNNING`; quality remains pending until quality result exists.
- [ ] **Step 4: Run focused tests and commit.** Run `mvn -pl htht-modules/htht-multisource -am -Dtest=ArtifactRegistrationServiceTest,TaskLifecycleSummaryTest test`; commit `feat: register artifacts and summarize task lifecycle`.

## Task 6: Implement Cancel, Retry, And Timeout Recovery

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/CancelProcessingTaskService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/RetryFailedStageUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/RecoverTimedOutStageUseCase.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskDispatcher.java` only if a status-aware cancellation result is required
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/CancelProcessingTaskUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/RetryFailedStageUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/RecoverTimedOutStageUseCaseTest.java`

- [ ] **Step 1: Write failure-path tests.** Verify cancellation blocks new claims and calls `TaskDispatcher.cancel` for the active execution; verify only `FAILED` or `RECOVERY_PENDING` stages can retry; verify maximum attempts reject further retries; verify late events cannot revive cancelled or superseded attempts.
- [ ] **Step 2: Implement cancellation and retry.** Enforce task ownership through `AccessSubject`, persist cancellation before stopping the Worker, mark pending stages skipped, and increment attempt only during a valid retry transition.
- [ ] **Step 3: Implement timeout recovery.** Find expired `RUNNING` stages, transition to `RECOVERY_PENDING`, record a timeout event, and allow the retry use case to move it to `RETRYING` only within the configured attempt limit.
- [ ] **Step 4: Run focused tests and commit.** Run `mvn -pl htht-modules/htht-multisource -am -Dtest=CancelProcessingTaskUseCaseTest,RetryFailedStageUseCaseTest,RecoverTimedOutStageUseCaseTest test`; commit `feat: support multisource cancellation retry and recovery`.

## Task 7: Expose Authenticated Execution APIs

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/DispatchStageRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/RetryStageRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ExecutionProgressResponse.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`
- Test: `htht-admin/src/test/java/com/htht/config/multisource/MultiSourceAdapterConfigurationTest.java`

- [ ] **Step 1: Write controller tests.** Verify authenticated owner can dispatch, cancel, retry, and query progress; non-owner receives the existing not-found response; requests cannot supply execution IDs, physical paths, Worker commands, output URIs, or plan snapshots.
- [ ] **Step 2: Implement controller mapping.** Add `POST /api/multisource/processing-tasks/{taskId}/dispatch`, `POST /api/multisource/processing-tasks/{taskId}/cancel`, `POST /api/multisource/processing-stages/{stageId}/retry`, and `GET /api/multisource/processing-tasks/{taskId}/progress`. Delegate all behavior to use cases.
- [ ] **Step 3: Assemble Spring beans.** Wire the new use cases to existing repositories, `TaskDispatcher`, asset validator, ID generator, clock, and authenticated-subject adapter. Keep `htht-multisource` free of Admin/System imports.
- [ ] **Step 4: Run controller tests and commit.** Run `mvn -pl htht-admin -am -Dtest=ProcessingExecutionControllerTest,MultiSourceAdapterConfigurationTest test`; commit `feat: expose multisource execution endpoints`.

## Task 8: Verification, Documentation, And Clean-Database Check

**Files:**
- Modify: `docs/superpowers/specs/2026-08-21-multisource-execution-orchestration-design.md` to record implemented behavior and verification evidence.
- Modify: `docs/superpowers/plans/2026-08-21-multisource-execution-orchestration-plan.md` by checking completed steps and recording any intentional deviations.
- Verify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/PostgreSqlMigrationIntegrationTest.java`
- Verify: all focused Java and Worker tests.

- [ ] **Step 1: Run Java regression.** Run `mvn -pl htht-modules/htht-multisource,htht-admin -am test`; expect all existing and new tests to pass.
- [ ] **Step 2: Run Worker regression.** Run `pytest -q --basetemp .pytest-tmp` from `rs-algorithm-worker`; record real-sample skips separately from failures.
- [ ] **Step 3: Run isolated migration verification only.** Use the migration integration test's isolated PostgreSQL/test schema if configured; do not point it at the development database and do not execute application startup migrations against business data.
- [ ] **Step 4: Perform boundary review.** Confirm no controller accepts physical paths or execution details, no source table is modified, no `htht-multisource` import crosses into Admin/System, and no environment-generated files are staged.
- [ ] **Step 5: Commit documentation and report.** Run `git diff --check`, inspect `git status --short`, commit only implementation and documentation files with `feat: complete multisource execution orchestration`, and report exact test output plus any unavailable isolated database check.

## Execution Order And Checkpoints

Tasks 1-3 are the first implementation checkpoint and must pass before persistence changes. Task 4 then aligns the database adapter with the already-created schema. Tasks 5-6 complete reliability behavior. Task 7 is the HTTP adapter. Task 8 is the final verification gate. No task authorizes a development-database write; that requires a separate explicit user confirmation after the isolated migration checks pass.
