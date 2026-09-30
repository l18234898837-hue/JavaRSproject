# 历史任务与产物保留 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户在独立历史任务弹窗中安全地查询、下载、回收、恢复和永久清理自己已完成任务的全部产物。

**Architecture:** 后端在 `htht-multisource` 定义任务级保留状态、查询和清理用例，在 `htht-admin` 通过 MyBatis、控制器和本地文件适配器落地。所有产物读取入口在调用文件/worker 前经过同一个访问守卫。前端以独立 `TaskHistoryDialog` 承载筛选、列表和详情，通过已有产物动作接口复用预览、下载、地图和质量报告能力。

**Tech Stack:** Java 17、Spring MVC、MyBatis-Plus、PostgreSQL/Flyway、自定义 JUnit 5 测试；Vue 3、TypeScript、Element Plus、tsx node:test。

**Spec:** `docs/superpowers/specs/2026-09-29-task-history-artifact-retention-design.md`

## Global Constraints

- 只按任务整体治理产物；不得删除单个产物、任务、阶段、事件、质量结果或原始上传文件。
- 只能查询和操作 `creator_id` 等于当前用户的任务；他人或不存在任务都不得泄露可枚举信息。
- `AVAILABLE` 和 `PINNED` 才可访问产物；`RECYCLE_BIN`、`PURGING`、`PURGE_FAILED`、`PURGED`、`MISSING` 必须拒绝所有新下载、预览、检查和瓦片请求。
- 永久清理只接受 `SUCCEEDED`、`FAILED`、`CANCELED` 任务；先持久化为 `PURGING`，再异步删除；失败保留为 `PURGE_FAILED` 并可显式重试。
- 所有文件路径必须由受信任元数据解析并限制在配置根目录；不允许由 HTTP 参数构建文件系统路径。
- 首期不注册自动过期清理、用户配额或管理员跨用户能力。

---

### Task 1: 数据模型、迁移与持久化适配器

**Files:**
- Create: `script/sql/migrations/V20260930_01__create_task_artifact_retention.sql`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskArtifactRetentionStatus.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/TaskArtifactRetention.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskArtifactRetentionRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/TaskArtifactRetentionEntity.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/TaskArtifactRetentionMyBatisMapper.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisTaskArtifactRetentionRepository.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/TaskArtifactRetentionTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/persistence/MigrationScriptTest.java`

**Interfaces:**
- Produces `TaskArtifactRetention(taskId, status, pinned, recycledAt, purgeStartedAt, purgedAt, operatorId, failureReason, version)`.
- Produces `TaskArtifactRetentionRepository.findOrCreate(taskId)`, `find(taskId)`, and `compareAndSet(expectedVersion, next)`.

- [ ] **Step 1: Write failing domain tests for legal transitions and pinned semantics**

```java
@Test void recyclingPinnedRetentionClearsPinAndCanRestoreAvailable() {
    var pinned = TaskArtifactRetention.available(TaskId.of(12L)).pin(new AccessSubject(7L), NOW);
    assertEquals(TaskArtifactRetentionStatus.RECYCLE_BIN, pinned.recycle(new AccessSubject(7L), NOW).status());
    assertFalse(pinned.recycle(new AccessSubject(7L), NOW).pinned());
    assertEquals(TaskArtifactRetentionStatus.AVAILABLE,
        pinned.recycle(new AccessSubject(7L), NOW).restore(new AccessSubject(7L), NOW).status());
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the model is absent**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionTest test`  
Expected: compilation failure for missing retention classes.

- [ ] **Step 3: Implement the immutable retention aggregate and transition guards**

```java
public TaskArtifactRetention beginPurge(AccessSubject operator, Instant now) {
    if (status != RECYCLE_BIN && status != PURGE_FAILED) throw new IllegalStateException("...");
    return copy(PURGING, false, recycledAt, now, null, operator.userId(), null, version + 1);
}
```

- [ ] **Step 4: Run the domain test and verify it passes**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionTest test`  
Expected: PASS.

- [ ] **Step 5: Write failing migration assertions**

```java
assertThat(script).contains("CREATE TABLE rs_task_artifact_retention");
assertThat(script).contains("PURGE_FAILED");
assertThat(script).contains("idx_rs_processing_task_creator_updated");
```

- [ ] **Step 6: Add Flyway SQL, entity/mapper/repository, and Spring bean wiring**

The SQL uses `task_id` PK/FK, enum check, optimistic `version`, audit timestamps, `purge_started_at`, and exactly the three indexes specified in the design. The repository uses one version-qualified `UPDATE`; zero updated rows raises an optimistic conflict.

- [ ] **Step 7: Run persistence tests and commit**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionTest,MigrationScriptTest test`  
Expected: PASS.  
Commit: `git add script/sql/migrations htht-modules/htht-multisource/src htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java && git commit -m "feat: persist task artifact retention"`

### Task 2: 历史摘要、存储汇总与任务归属查询

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskHistoryQueryUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskHistoryQueryService.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskHistoryQuery.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskHistoryPage.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskHistoryRepository.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisTaskHistoryRepository.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/ProcessingTaskMyBatisMapper.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskHistoryQueryServiceTest.java`

**Interfaces:**
- Consumes `TaskHistoryQuery(AccessSubject subject, String productCode, TaskLifecycleStatus lifecycle, Instant completedFrom, Instant completedTo, TaskArtifactRetentionStatus retention, int page, int pageSize)`.
- Produces task rows containing task id/no/name, product codes, updated time, lifecycle, artifact count, `coalesce(sum(size_bytes), 0)`, and effective retention.

- [ ] **Step 1: Write a failing query-service test proving owner filtering is mandatory**

```java
assertThat(service.query(new TaskHistoryQuery(new AccessSubject(7L), null, null, null, null, null, 1, 20)).items())
    .extracting(TaskHistoryItem::taskId).containsExactly(TaskId.of(101L));
verify(repository).findPage(argThat(query -> query.subject().userId() == 7L));
```

- [ ] **Step 2: Run the test and verify it fails for missing query service**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskHistoryQueryServiceTest test`  
Expected: compilation failure for missing history types.

- [ ] **Step 3: Implement request validation and repository SQL projection**

Use a single grouped query rooted at `rs_processing_task t`, inner-join `rs_task_product p` only when product is filtered, left-join artifacts and retention, filter `t.creator_id = #{subject.userId}`, order `t.updated_at DESC, t.task_id DESC`, and use a separate matching count query. Treat no retention record as `AVAILABLE` until the scanner writes a row.

- [ ] **Step 4: Run query service tests and commit**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskHistoryQueryServiceTest test`  
Expected: PASS.  
Commit: `git add htht-modules/htht-multisource/src htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java && git commit -m "feat: query task history and storage usage"`

### Task 3: 保留操作、访问守卫与历史详情

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskArtifactRetentionUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskArtifactAccessGuard.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskArtifactRetentionService.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DefaultTaskArtifactAccessGuard.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/ArtifactRetentionOperation.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskArtifactRetentionServiceTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/DefaultTaskArtifactAccessGuardTest.java`

**Interfaces:**
- `TaskArtifactRetentionUseCase.change(TaskId, AccessSubject, ArtifactRetentionOperation)` returns current retention.
- `TaskArtifactAccessGuard.requireAccessible(TaskId, AccessSubject)` throws a typed exception with one of `ARTIFACT_RECYCLED`, `ARTIFACT_PURGING`, `ARTIFACT_PURGE_FAILED`, `ARTIFACT_PURGED`, `ARTIFACT_MISSING`.

- [ ] **Step 1: Write a failing test for recycle denial and restore**

```java
assertThatThrownBy(() -> guard.requireAccessible(TaskId.of(8L), OWNER))
    .isInstanceOf(ArtifactAccessDeniedException.class)
    .extracting("code").isEqualTo("ARTIFACT_RECYCLED");
assertEquals(AVAILABLE, service.change(TaskId.of(8L), OWNER, RESTORE).status());
```

- [ ] **Step 2: Run the focused tests and verify expected failure**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionServiceTest,DefaultTaskArtifactAccessGuardTest test`  
Expected: compilation failure for missing use cases.

- [ ] **Step 3: Implement owner-before-state checks and idempotent operations**

Load the task through `ProcessingTaskRepository`; compare `task.creator()` with subject before reading/writing retention; make repeated `PIN`, `RECYCLE`, and `RESTORE` return the existing legal target state. Reject `PURGED`, `PURGING`, `PURGE_FAILED`, and `MISSING` transitions with conflict errors.

- [ ] **Step 4: Run tests and commit**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionServiceTest,DefaultTaskArtifactAccessGuardTest test`  
Expected: PASS.  
Commit: `git add htht-modules/htht-multisource/src htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java && git commit -m "feat: guard task artifact access"`

### Task 4: 任务级异步物理清理与只读存在性扫描

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskArtifactPurgePort.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskArtifactPurgeUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskArtifactPurgeService.java`
- Create: `htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactPurgeService.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/WorkerArtifactRasterPreviewService.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-admin/src/test/java/com/htht/config/multisource/LocalTaskArtifactPurgeServiceTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskArtifactPurgeServiceTest.java`

**Interfaces:**
- `TaskArtifactPurgeUseCase.request(TaskId, AccessSubject, String confirmationTaskNo)` returns `PURGING` after checking terminal lifecycle and exact task number.
- `TaskArtifactPurgePort.purge(TaskDetailView)` deletes only task-owned output, work, ZIP and cache resources.
- `WorkerArtifactRasterPreviewService.invalidateTask(long taskId)` removes metadata and tile cache entries for that task.

- [ ] **Step 1: Write a failing filesystem test proving purge cannot escape roots**

```java
assertThatThrownBy(() -> service.purge(detailWithStorageKey("../../upload/original.tif")))
    .isInstanceOf(IllegalArgumentException.class);
assertThat(uploadRoot.resolve("original.tif")).exists();
```

- [ ] **Step 2: Run the test and verify it fails because the purge adapter does not exist**

Run: `mvn -pl htht-admin -Dtest=LocalTaskArtifactPurgeServiceTest test`  
Expected: compilation failure for missing purge adapter.

- [ ] **Step 3: Implement idempotent local deletion and cache invalidation**

Resolve only `artifactRoot/{taskId}`, `workRoot/{executionId}`, and `workRoot/archives/{taskId}` after normalizing and checking `startsWith(root)`. Walk directories deepest-first, treat missing paths as success, call `invalidateTask(taskId)`, and never reference `file.upload-path` in purge code.

- [ ] **Step 4: Write a failing service test for terminal-state confirmation and retry**

```java
assertThatThrownBy(() -> service.request(TaskId.of(9L), OWNER, "other-task"))
    .isInstanceOf(IllegalArgumentException.class);
service.request(TaskId.of(9L), OWNER, "TASK-9");
assertEquals(PURGING, retentionRepository.find(TaskId.of(9L)).orElseThrow().status());
```

- [ ] **Step 5: Implement async handoff and failure persistence**

The service verifies ownership and terminal lifecycle before `beginPurge`; after successful compare-and-set it submits a task to a configured `Executor`. The worker changes status to `PURGED` only after port success; catches all deletion failures and records sanitized failure text as `PURGE_FAILED`. A startup recovery component marks stale `PURGING` records `PURGE_FAILED`; it never deletes automatically.

- [ ] **Step 6: Run focused tests and commit**

Run: `mvn -pl htht-admin -Dtest=LocalTaskArtifactPurgeServiceTest test && mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactPurgeServiceTest test`  
Expected: PASS.  
Commit: `git add htht-admin/src htht-modules/htht-multisource/src && git commit -m "feat: asynchronously purge task artifacts"`

### Task 5: 历史、状态操作和存储汇总 HTTP 契约

**Files:**
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/TaskHistoryController.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskHistoryResponse.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskHistoryDetailResponse.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ArtifactRetentionRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ArtifactPurgeRequest.java`
- Create: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ProcessingStorageSummaryResponse.java`
- Modify: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/TaskHistoryControllerTest.java`
- Test: `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`

**Interfaces:**
- Adds the five endpoints specified in the design and returns `202 Accepted` for valid purge requests.
- `ExecutionProgressResponse.ArtifactProgressResponse.downloadable` and `.view.enabled` are false with reason for inaccessible status.

- [ ] **Step 1: Write a failing controller test for `POST artifact-retention`**

```java
var response = controller.changeRetention(12L, new ArtifactRetentionRequest("RECYCLE"));
assertEquals("RECYCLE_BIN", response.getBody().getData().status());
verify(useCase).change(TaskId.of(12L), OWNER, ArtifactRetentionOperation.RECYCLE);
```

- [ ] **Step 2: Run it and verify it fails for missing controller/DTO**

Run: `mvn -pl htht-admin -Dtest=TaskHistoryControllerTest test`  
Expected: compilation failure.

- [ ] **Step 3: Implement controller validation and progress/detail composition**

Validate page bounds and ISO timestamps, accept only `PIN`/`RECYCLE`/`RESTORE`, require exact `taskNo` confirmation, and map typed guard errors through the existing global API error response. Build `history-detail` by combining `GetTaskDetailUseCase` output with retention metadata and summed artifact sizes.

- [ ] **Step 4: Write failing regression tests for stale ZIP and preview tickets**

```java
assertThatThrownBy(() -> controller.download(12L, validTicket, response))
    .hasMessageContaining("ARTIFACT_RECYCLED");
assertThatThrownBy(() -> controller.tile(12L, "COG", 0, 0, 0, validPreviewTicket))
    .hasMessageContaining("ARTIFACT_RECYCLED");
```

- [ ] **Step 5: Insert access-guard calls into every existing artifact route**

Call the guard in `progress`, `downloadArtifact`, ZIP ticket issue, ZIP consume, inspection, `tilePreview`, anonymous `image`, and anonymous `tile`; for anonymous routes resolve ticket first then use its stored subject. Never just disable the UI or rely on ticket expiry.

- [ ] **Step 6: Run controller tests and commit**

Run: `mvn -pl htht-admin -Dtest=TaskHistoryControllerTest,ProcessingExecutionControllerTest test`  
Expected: PASS.  
Commit: `git add htht-admin/src && git commit -m "feat: expose task artifact history APIs"`

### Task 6: 历史数据只读初始化扫描

**Files:**
- Create: `htht-admin/src/main/java/com/htht/config/multisource/TaskArtifactRetentionScanRunner.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/TaskArtifactRetentionScanUseCase.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskArtifactRetentionScanService.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/TaskArtifactRetentionScanServiceTest.java`

**Interfaces:**
- Scanner returns counts for created `AVAILABLE`, created `MISSING`, skipped existing records, and unreadable metadata.

- [ ] **Step 1: Write a failing scanner test for non-overwrite behavior**

```java
repository.save(TaskArtifactRetention.available(TaskId.of(2L)).pin(OWNER, NOW));
service.scan();
assertEquals(PINNED, repository.find(TaskId.of(2L)).orElseThrow().status());
```

- [ ] **Step 2: Run it, implement only read-only discovery, then rerun**

Run: `mvn -pl htht-modules/htht-multisource -Dtest=TaskArtifactRetentionScanServiceTest test`  
Expected before implementation: compilation failure; expected after: PASS. The runner is disabled by default and requires an explicit startup property, scanning only absent retention rows and never deleting/recovering files.

- [ ] **Step 3: Commit**

`git add htht-admin/src htht-modules/htht-multisource/src && git commit -m "feat: scan historical task artifact availability"`

### Task 7: 前端 API 类型、状态解释和纯函数测试

**Files:**
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Create: `rsimageprocess-view/src/features/multisource/taskHistory.ts`
- Create: `rsimageprocess-view/src/features/multisource/taskHistory.test.ts`

**Interfaces:**
- Adds `TaskArtifactRetentionStatus`, paged history, detail, summary and operation request types plus five API clients.
- Produces `artifactAccessReason(status)` and `artifactActionsEnabled(status)` for UI decisions.

- [ ] **Step 1: Write a failing pure-function test for all inaccessible states**

```ts
test("recycle-bin artifacts are disabled with a recoverable reason", () => {
  assert.equal(artifactActionsEnabled("RECYCLE_BIN"), false);
  assert.match(artifactAccessReason("RECYCLE_BIN"), /回收站/);
});
```

- [ ] **Step 2: Run it and verify it fails for missing module**

Run: `pnpm test:multisource -- src/features/multisource/taskHistory.test.ts`  
Expected: module-not-found failure.

- [ ] **Step 3: Add exact API contracts and status helpers**

Use `http.request<Result<...>>` with the specified five routes. Keep `ProcessingIdentifier` for all task IDs; pass purge confirmation as `{ taskNo }`; return status-specific Chinese messages for `RECYCLE_BIN`, `PURGING`, `PURGE_FAILED`, `PURGED`, and `MISSING`.

- [ ] **Step 4: Run test and typecheck, then commit**

Run: `pnpm test:multisource -- src/features/multisource/taskHistory.test.ts && pnpm typecheck`  
Expected: PASS.  
Commit: `git add src/api/multiSourceSatellite.ts src/features/multisource/taskHistory* && git commit -m "feat: add task history API client"`

### Task 8: 历史任务弹窗与现有产物动作复用

**Files:**
- Create: `rsimageprocess-view/src/components/LeftPanel/TaskHistoryDialog.vue`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/taskHistory.test.ts`
- Test: `rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts`

**Interfaces:**
- `TaskHistoryDialog` accepts `modelValue`, emits `update:modelValue`, `preview-artifact`, `download-artifact`, `download-result`, and `view-quality-report` with a task id/artifact payload.
- It owns filters, pagination, history API refresh, retention buttons and typed task-number purge confirmation.

- [ ] **Step 1: Write a failing test for disabled action semantics in dialog view-model helpers**

```ts
test("purged task history item does not expose download actions", () => {
  assert.equal(historyActions({ retentionStatus: "PURGED" }).canDownload, false);
  assert.match(historyActions({ retentionStatus: "PURGED" }).reason, /已永久删除/);
});
```

- [ ] **Step 2: Run it and verify it fails for missing helper/export**

Run: `pnpm test:multisource -- src/features/multisource/taskHistory.test.ts`  
Expected: assertion or export failure.

- [ ] **Step 3: Implement dialog list/detail and the independent launch card**

Place the card immediately after `.mission-launch`, with title “历史任务与产物”, explanatory copy, and arrow affordance. The dialog uses Element Plus filters/table/pagination, fetches only when opened or filters change, expands a selected row into the existing progress representation, and emits to the parent’s existing preview/download/map/quality functions instead of duplicating formatting or URL logic. Disable actions solely from server retention status and show the helper’s reason in tooltips.

- [ ] **Step 4: Implement retention confirmation flows**

Use `ElMessageBox.confirm` for recycle and `ElMessageBox.prompt` for purge. The purge validator requires exact displayed `taskNo`; after every mutation refresh detail, list and storage summary. `PURGE_FAILED` exposes “重试永久删除”; `RECYCLE_BIN` exposes “恢复产物”; `PINNED` exposes the current long-retention label.

- [ ] **Step 5: Run frontend tests, typecheck and build; commit**

Run: `pnpm test:multisource && pnpm typecheck && pnpm build`  
Expected: PASS.  
Commit: `git add src/components/LeftPanel src/features/multisource && git commit -m "feat: add task history dialog"`

### Task 9: 全量回归、迁移验证和交付检查

**Files:**
- Modify: only files corrected by failing verification; do not refactor unrelated code.

- [ ] **Step 1: Run backend module and admin test suites**

Run: `mvn -pl htht-modules/htht-multisource,htht-admin test`  
Expected: PASS with no skipped new retention tests.

- [ ] **Step 2: Run frontend regression suite and production build**

Run: `pnpm test:multisource && pnpm typecheck && pnpm build`  
Expected: PASS.

- [ ] **Step 3: Inspect migrations and working trees**

Run: `git diff --check && git status --short` in both repositories. Confirm the migration only creates the retention table/indexes and does not include `DELETE`, `DROP`, or `rs_legacy_plan_archive` updates.

- [ ] **Step 4: Commit any validation-only fixes and report evidence**

Use focused commits for corrections. Report branch names, commits, exact test commands/results, and any environmental limitations.
