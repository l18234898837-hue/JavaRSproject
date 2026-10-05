# Source and Artifact Recycle Bin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Extend the existing retention flows so source uploads and task artifacts have independent 14-day recycle-bin lifecycles, batch recycle actions, restore, expiry cleanup, and confirmation-gated permanent deletion.

**Architecture:** Reuse the existing source/artifact retention aggregates, repositories, history query, and floating history dialog. Add only the missing source recycle state and both independent recycle expiry timestamps/policies. One scheduled cleanup entry point will call two isolated source/artifact cleanup methods; no unified recycle-bin table, resource API, or new dialog is introduced.

**Tech Stack:** Java 17/Spring Boot/MyBatis/JUnit/AssertJ; Vue 3/TypeScript/Element Plus.

**Spec:** `docs/superpowers/specs/2026-10-04-source-artifact-recycle-bin-design.md`

## Global Constraints

- Source and artifact retention state, expiry timestamps, and cleanup branches remain independent.
- Both recycle bins default to 14 days and use separate workspace policy fields.
- Physical purge preserves task/resource metadata and remains retryable after failure.
- Every operation verifies current-user ownership and terminal-task constraints.
- Preserve unrelated working-tree changes.

---

### Task 1: Domain state, policy, and persistence

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskInputRetentionStatus.java`
- Modify: `TaskInputRetention.java`, `TaskArtifactRetention.java`, `WorkspaceStoragePolicyRepository.java`
- Modify: retention entities, MyBatis mappers/repositories, and the existing retention migration
- Test: existing domain, repository, policy, and migration tests

**Interfaces:** Source gains `RECYCLE_BIN`, `recycledAt`, and `recycleExpiresAt`; artifact gains `recycleExpiresAt`; policy gains independent `sourceRecycleBinRetentionDays()` and `artifactRecycleBinRetentionDays()` defaults of 14.

- [ ] Add failing tests for source/artifact recycle, restore, expiry-clock reset, independent policy bounds, and migration columns/indexes.
- [ ] Run focused module tests and observe the expected failures.
- [ ] Implement immutable transitions and persistence mappings with version-qualified compare-and-set.
- [ ] Rerun focused module tests.

### Task 2: Application services and scheduled cleanup

**Files:**
- Modify: `TaskInputRetentionUseCase.java`, `TaskInputRetentionService.java`
- Modify: `TaskArtifactRetentionUseCase.java`, `TaskArtifactRetentionService.java`
- Modify: existing input/artifact purge runner and local purge services under `htht-admin/src/main/java/com/hhtht/config/multisource/`
- Test: retention service, runner, and local purge service tests

**Interfaces:** Add source/artifact `recycle` and `restore` operations. Reuse one scheduler entry point with two private branches: `cleanupSourceRecycleBin()` and `cleanupArtifactRecycleBin()`. Each branch claims only its own status and expiry column.

- [ ] Add failing tests for independent policy selection, ownership, optimistic locking, expiry claim isolation, and retryable physical failure.
- [ ] Run focused tests and verify failure.
- [ ] Implement service transitions and isolated cleanup branches; route source cleanup only to upload/input storage and artifact cleanup only to output/cache storage.
- [ ] Run retention and purge tests.

### Task 3: Existing history controller and batch semantics

**Files:**
- Modify: `htht-admin/src/main/java/com/hhtht/web/controller/multisource/TaskHistoryController.java`
- Modify: existing retention DTOs and batch request handling
- Test: `TaskHistoryControllerTest` and related admin tests

**Interfaces:** Source batch `PURGE` becomes `RECYCLE`; add source single-task `RECYCLE/RESTORE`; retain artifact `RECYCLE/RESTORE`; keep permanent purge endpoints confirmation-gated. Recycle-bin listings reuse history filters `inputRetention=RECYCLE_BIN` and `retention=RECYCLE_BIN`.

- [ ] Add failing controller tests for source/artifact batch recycle, restore, ownership, terminal checks, and task-number confirmation.
- [ ] Run focused admin tests and verify failure.
- [ ] Implement operations and per-task `CHANGED/SKIPPED/FAILED` responses without adding a new controller family.
- [ ] Run all multisource admin controller tests.

### Task 4: Existing frontend history dialog

**Files:**
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Modify: `src/features/multisource/taskHistoryBatch.ts`
- Modify: `src/components/LeftPanel/TaskHistoryDialog.vue`
- Modify: `MultiSourceSatelliteProcess.vue` only if an existing entry/tab needs wiring
- Test: `taskHistoryBatch.test.ts`, `taskProgressDialog.test.ts`

**Interfaces:** Add source/artifact recycle operation types and use history filters for recycle-bin mode. The toolbar exposes `批量移入源文件回收站` and `批量移入产物回收站`; detail actions expose independent restore and permanent delete.

- [ ] Add failing frontend tests for independent eligibility, operation names, recycle-bin filtering, restore, and permanent-delete confirmation copy.
- [ ] Run focused frontend tests and verify failure.
- [ ] Implement the existing dialog tab/filter mode, independent batch preflight, restore, and refresh behavior.
- [ ] Run focused frontend tests and `pnpm typecheck`.

### Task 5: End-to-end verification

- [ ] Run module domain/migration/retention tests.
- [ ] Run admin multisource controller and cleanup tests.
- [ ] Run frontend focused tests and `pnpm typecheck`.
- [ ] Run `git diff --check` in both repositories.
- [ ] Review that source cleanup never receives artifact paths and artifact cleanup never receives source paths.
