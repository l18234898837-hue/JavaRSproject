# Task Storage Retention and Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans (recommended). Steps use checkbox syntax for tracking.

**Goal:** Add workspace-configurable source-file retention, reference-safe cleanup, independent Worker-directory TTL cleanup, and a front-end storage-management experience that keeps original inputs separate from task artifacts.

**Architecture:** Source-input retention and task-artifact retention remain separate aggregates. Every multisource-managed input is registered against its task, cleanup claims are protected by optimistic locking, and filesystem deletion is limited to verified managed roots. Existing artifact purge behavior remains available, while source cleanup and Worker cleanup use independent policies.

**Tech Stack:** Spring Boot, PostgreSQL/Flyway, MyBatis-Plus, Java NIO, Vue 3, TypeScript, Element Plus, Node test runner, Maven.

**Spec:** docs/superpowers/specs/2026-09-29-task-history-artifact-retention-design.md plus the approved decisions in this plan: input retention defaults to 14 days, orphan-upload TTL is 24 hours, Worker-directory TTL is 48 hours, and automatic cleanup is disabled during initial rollout.

## Global Constraints

- Original inputs and task artifacts use separate statuses, APIs, actions, confirmations, and audit records.
- Existing task rows and uploaded files are not automatically deleted during migration or initial rollout.
- Cleanup is allowed only for terminal tasks and must reject running, queued, recovery, canceling, or retry-active work.
- Every physical deletion is asynchronous, idempotent, retryable, and retains task/history/quality/metadata rows.
- A source file is deletable only when it is registered as multisource-managed and has no active task or external reference.
- rs_legacy_plan_archive remains unrelated to source or artifact retention.
- Workspace defaults are input_retention_days=14, orphan_upload_ttl_hours=24, worker_directory_ttl_hours=48, auto_cleanup_enabled=false.
- Keep tests focused; small UI changes need regression assertions, not a new end-to-end suite.

---

### Task 1: Add workspace policy, managed-file, and input-reference schema

**Files:**
- Create: script/sql/migrations/V20261002_01__create_storage_retention_policy.sql
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/WorkspaceStoragePolicyEntity.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/ManagedInputFileEntity.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/TaskInputReferenceEntity.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/WorkspaceStoragePolicyMyBatisMapper.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/ManagedInputFileMyBatisMapper.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskInputReferenceMyBatisMapper.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskInputAssetMyBatisMapper.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/StorageRetentionMigrationTest.java

**Interfaces:**

- Add workspace_key varchar(128) NOT NULL DEFAULT 'default' to rs_processing_task; backfill existing rows to default.
- Add rs_workspace_storage_policy(workspace_key PK, input_retention_days, orphan_upload_ttl_hours, worker_directory_ttl_hours, auto_cleanup_enabled, version, updated_at).
- Add rs_managed_input_file(managed_file_id PK, workspace_key, sys_file_id, file_path_snapshot, checksum, size_bytes, status, created_at, last_seen_at, version).
- Add rs_task_input_reference(reference_id PK, task_id, managed_file_id, source_type, source_id, checksum, reference_status, created_at, released_at, version) with unique (task_id, managed_file_id).
- Add indexes on workspace/task, managed-file/status, and task/reference-status.

- [ ] Write migration assertions for tables, defaults 14/24/48, version columns, unique constraints, and indexes.
- [ ] Run mvn -pl htht-modules/htht-multisource -Dtest=StorageRetentionMigrationTest test and verify it fails before implementation.
- [ ] Add the non-destructive Flyway migration and the default policy row.
- [ ] Add MyBatis entities and mappers using existing persistence conventions.
- [ ] Run mvn -pl htht-modules/htht-multisource -Dtest=StorageRetentionMigrationTest,PostgreSqlMigrationIntegrationTest test.
- [ ] Commit with feat: add source retention and workspace storage policy schema.

### Task 2: Register managed source files and task references

**Files:**
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ManagedInputFileRepository.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskInputReferenceRepository.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskInputReferenceService.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinator.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskInputReferenceServiceTest.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/SysFileAssetStorageAdapterTest.java

**Interfaces:**

- TaskInputReferenceService.register(task, workspaceKey) registers every SYS_FILE input after task persistence and before dispatch.
- ManagedInputFileRepository.findOrRegister(workspaceKey, sysFileId, path, checksum, sizeBytes) is idempotent.
- TaskInputReferenceRepository.findActiveReferences(managedFileId) and releaseForTask(taskId, operatorId, now) expose reference safety checks.
- Normal source resolution remains read-only; only the cleanup adapter can delete managed files.

- [ ] Add failing tests for first registration, repeated registration, two tasks sharing one file, checksum/path mismatch, and path-outside-upload-root rejection.
- [ ] Implement repositories and idempotent registration.
- [ ] Invoke registration in task creation before dispatch; registration failure prevents dispatch.
- [ ] Preserve existing SysFileAssetStorageAdapter read and authorization behavior.
- [ ] Run mvn -pl htht-modules/htht-multisource,htht-admin -Dtest=TaskInputReferenceServiceTest,SysFileAssetStorageAdapterTest,ProcessingTaskControllerTest test.
- [ ] Commit with feat: register multisource input references safely.

### Task 3: Model source retention and optimistic-lock transitions

**Files:**
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskInputRetentionStatus.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskInputRetention.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/WorkspaceStoragePolicyRepository.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskInputRetentionUseCase.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskInputRetentionService.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/TaskInputRetentionTest.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskInputRetentionServiceTest.java

**Interfaces:**

- States: AVAILABLE, SCHEDULED, PURGING, PURGE_FAILED, PURGED, MISSING.
- schedule(taskId, workspaceKey, expiresAt, operator), changePolicy(taskId, workspaceKey, choice, operator), and claim(taskId, expectedVersion, now).
- Claims use a version-qualified update and return conflict on stale versions.
- Policy lookup uses default workspace until a multi-workspace context is available.

- [ ] Test default 14-day expiry, custom expiry, permanent pin, terminal-state validation, stale-version conflict, retry from PURGE_FAILED, and rejection of PURGED.
- [ ] Implement immutable transitions with version increments and audit timestamps.
- [ ] Validate workspace policy bounds: input days 0..3650, orphan TTL at least 24 hours, Worker TTL at least 1 hour.
- [ ] Run mvn -pl htht-modules/htht-multisource -Dtest=TaskInputRetentionTest,TaskInputRetentionServiceTest test.
- [ ] Commit with feat: model source retention with optimistic locking.

### Task 4: Implement source-file and orphan-upload cleanup

**Files:**
- Create: htht-admin/src/main/java/com/htht/config/multisource/LocalManagedInputFilePurgeService.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/OrphanUploadCleanupService.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskInputPurgeUseCase.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskInputPurgeService.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/StorageCleanupScheduler.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java
- Modify: htht-admin/src/main/resources/application.yml
- Test: htht-admin/src/test/java/com/htht/config/multisource/LocalManagedInputFilePurgeServiceTest.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/OrphanUploadCleanupServiceTest.java

**Interfaces:**

- TaskInputPurgeService.request(taskId, subject, confirmationTaskNo) claims source retention and submits one async purge.
- LocalManagedInputFilePurgeService.purge(managedFile) canonicalizes under file.upload-path, rechecks references, deletes the file, and marks the file row.
- OrphanUploadCleanupService.scanAndCleanup(now) only targets unregistered paths older than 24 hours with no active upload session.

- [ ] Write tests for traversal, symlink escape, shared-file protection, active-task protection, missing-file idempotency, failure-to-PURGE_FAILED, and the 24-hour cutoff.
- [ ] Implement claim-before-delete and a second reference check immediately before deletion.
- [ ] Implement orphan scanning for .multisource-archive and incomplete upload files without touching registered inputs.
- [ ] Add disabled-by-default scheduler flags; source and orphan scans are separately switchable.
- [ ] Run mvn -pl htht-modules/htht-multisource,htht-admin -Dtest=LocalManagedInputFilePurgeServiceTest,OrphanUploadCleanupServiceTest test.
- [ ] Commit with feat: clean expired and orphaned source files safely.

### Task 5: Add independent Worker-directory TTL cleanup

**Files:**
- Create: htht-admin/src/main/java/com/htht/config/multisource/WorkerDirectoryCleanupService.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/StorageCleanupScheduler.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/WorkerDirectoryCleanupServiceTest.java

**Interfaces:**

- scan(workRoot, now) classifies directories as ACTIVE, EXPIRED, ORPHANED, or PROTECTED.
- Terminal task execution directories use workspace worker_directory_ttl_hours (default 48).
- Unmatched directories require at least 24 hours since last modification and no process lock.

- [ ] Test running, retrying, terminal-inside-TTL, terminal-outside-TTL, unmatched-old, unmatched-recent, and failed-deletion cases.
- [ ] Resolve ownership through stage execution_id and task lifecycle; never infer ownership solely from a directory name.
- [ ] Delete only under configured work-root; preserve task metadata and record failures for retry.
- [ ] Run mvn -pl htht-admin -Dtest=WorkerDirectoryCleanupServiceTest test.
- [ ] Commit with feat: add independent Worker directory cleanup.

### Task 6: Expose source retention, batch actions, and storage statistics APIs

**Files:**
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingStorageController.java
- Modify: htht-admin/src/main/java/com/htht/web/controller/multisource/TaskHistoryController.java
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskInputRetentionRequest.java
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/dto/BatchTaskCleanupRequest.java
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/dto/WorkspaceStoragePolicyRequest.java
- Modify: rsimageprocess-view/src/api/multiSourceSatellite.ts
- Test: htht-admin/src/test/java/com/htht/web/controller/multisource/TaskHistoryControllerTest.java
- Test: htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingStorageControllerTest.java

**Interfaces:**

- Extend history rows/detail with independent source-retention and artifact-retention summaries.
- Add POST /processing-tasks/{taskId}/input-retention for PIN, SET_EXPIRY, PURGE.
- Add POST /processing-tasks/batch-input-retention for source-only batch operations.
- Add POST /processing-tasks/batch-artifact-retention for pin/recycle/restore; keep permanent purge individually confirmed.
- Add GET /processing-storage/summary, GET /processing-storage/policy, and versioned PATCH /processing-storage/policy.
- Summary returns source bytes, artifact bytes, Worker bytes, ZIP bytes, reclaimable bytes, and cleanup failures.

- [ ] Add DTO tests proving source/artifact fields and copy remain separate.
- [ ] Implement ownership/workspace filtering and per-task changed/skipped/failed batch results.
- [ ] Implement filesystem-backed summary without counting metadata as reclaimable bytes.
- [ ] Update TypeScript types for source statuses, batch results, summary, and workspace policy.
- [ ] Run mvn -pl htht-admin -Dtest=TaskHistoryControllerTest,ProcessingStorageControllerTest test.
- [ ] Commit with feat: expose source retention and storage policy APIs.

### Task 7: Add history-list selection and separated batch actions

**Files:**
- Modify: rsimageprocess-view/src/components/LeftPanel/TaskHistoryDialog.vue
- Modify: rsimageprocess-view/src/features/multisource/taskHistory.ts
- Create: rsimageprocess-view/src/features/multisource/taskHistorySelection.ts
- Test: rsimageprocess-view/src/features/multisource/taskHistorySelection.test.ts
- Modify: rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts

**Interfaces:**

- Selection exposes selectedCount, toggle, togglePage, clear, and isSelected.
- IDs are normalized to strings to avoid JavaScript precision loss.
- Table status cell shows labeled 源文件 and 任务产物 rows.
- Batch toolbar reports changed/skipped/failed tasks and persists selection across pagination.

- [ ] Test page selection, cross-page selection, clear-after-success, and ineligible rows.
- [ ] Add checkbox column, sticky batch toolbar, source-status filter, and dual-status rendering.
- [ ] Add separate dialogs: 批量设置原始输入保留期, 批量清理原始输入, 批量移入任务产物回收站.
- [ ] Ensure no generic 批量清理 destructive action exists.
- [ ] Run pnpm test:multisource -- --test-name-pattern="history selection|source retention|dual status|batch".
- [ ] Commit with feat: add history selection and separated batch cleanup actions.

### Task 8: Add isolated source/artifact detail cards and confirmations

**Files:**
- Modify: rsimageprocess-view/src/components/LeftPanel/TaskHistoryDialog.vue
- Modify: rsimageprocess-view/src/api/multiSourceSatellite.ts
- Modify: rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts

- [ ] Add static assertions for both card headings, separate API handlers, and source/artifact-specific copy.
- [ ] Implement the 原始输入 card with file count, bytes, status, expiry, policy, 修改保留期, and 立即清理源文件.
- [ ] Keep progress, artifacts, quality report, preview, and download controls inside the 任务产物 card.
- [ ] Source confirmation must say that task artifacts remain and rerun becomes unavailable.
- [ ] Artifact confirmation must say that source files remain and task records/quality reports remain.
- [ ] Run pnpm test:multisource -- --test-name-pattern="history details|retention" and pnpm typecheck.

### Task 9: Add the global storage page and workspace policy editor

**Files:**
- Create: rsimageprocess-view/src/views/processingStorage/index.vue
- Create: rsimageprocess-view/src/components/ProcessingStorage/StorageSummaryCards.vue
- Create: rsimageprocess-view/src/components/ProcessingStorage/StoragePolicyForm.vue
- Modify: rsimageprocess-view/src/api/multiSourceSatellite.ts
- Modify: rsimageprocess-view/src/router/modules/home.ts
- Test: rsimageprocess-view/src/features/multisource/processingStorage.test.ts

- [ ] Test route registration, summary categories, defaults 14/48/24, and source/artifact vocabulary.
- [ ] Implement cards for original inputs, artifacts, Worker directories, ZIP/orphan uploads, reclaimable bytes, and failed cleanups.
- [ ] Implement policy fields for input days, Worker TTL, orphan TTL with 24-hour minimum, and auto-cleanup switch.
- [ ] Require confirmation before enabling automatic cleanup and explain that existing history is not enrolled automatically.
- [ ] Add a permission-controlled /processing-storage route.
- [ ] Run pnpm test:multisource -- --test-name-pattern="processing storage" and pnpm typecheck.
- [ ] Commit with feat: add workspace storage statistics and policy page.

### Task 10: Dry-run rollout, observability, and final verification

**Files:**
- Modify: htht-admin/src/main/java/com/htht/config/multisource/StorageCleanupScheduler.java
- Modify: htht-admin/src/main/resources/application.yml
- Create: htht-admin/src/test/java/com/htht/config/multisource/StorageCleanupSchedulerTest.java
- Modify: JavaRSproject/start-multisource-backend-local.cmd
- Modify: docs/superpowers/specs/2026-09-29-task-history-artifact-retention-design.md

- [ ] Add dry-run logs for workspace, candidate count, candidate bytes, protected count, skip reasons, and failures; dry-run must not mutate files or states.
- [ ] Add multisource.storage-cleanup.enabled=false, multisource.storage-cleanup.dry-run=true, and multisource.storage-cleanup.interval=1h.
- [ ] Keep local cleanup disabled until a read-only scan is reviewed.
- [ ] Run from rsimageprocess-view: pnpm test:multisource, pnpm typecheck, git diff --check.
- [ ] Run from JavaRSproject: mvn -pl htht-modules/htht-multisource,htht-admin test.
- [ ] Perform a filesystem dry run and verify candidates are restricted to managed inputs, expired orphans, task-owned artifact/archive directories, and independently eligible Worker directories.
- [ ] Commit with chore: prepare safe storage cleanup rollout.

## Acceptance Checklist

- [ ] A task supports 14 days, 30 days, custom expiry, or permanent source retention.
- [ ] Workspace defaults are configurable and versioned.
- [ ] Source and artifact status are independently filterable and visible.
- [ ] Shared source files are never deleted while an active task references them.
- [ ] Two cleaners cannot claim the same task because of optimistic locking.
- [ ] Orphan uploads older than 24 hours are reported and safely removable.
- [ ] Worker directories use their own TTL and do not inherit source/artifact retention.
- [ ] Batch selection survives pagination and reports skipped/ineligible tasks.
- [ ] Source dialogs never say artifact deletion; artifact dialogs never say source cleanup.
- [ ] PURGED retains task, stage, event, quality, and artifact metadata.
- [ ] Existing historical files remain untouched until an explicit rollout decision.
