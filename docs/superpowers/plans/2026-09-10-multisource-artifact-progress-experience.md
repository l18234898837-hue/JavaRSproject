# 多源处理产物与进度联动体验实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or **superpowers:executing-plans** to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在每个处理阶段完成时立即展示可理解的阶段结果和产物，并支持安全预览、下载、耗时和诊断信息。

**Architecture:** 保留现有 `/progress` 接口、阶段状态和产物授权链路；后端只扩展可选元数据，前端以阶段完成边沿驱动自动展开和“刚生成”提示。产物按 `stageId` 归属，图片/JSON 在线预览，大型科学文件显示元数据并下载。

**Tech Stack:** Java 17、Spring Boot、MyBatis、Vue 3、TypeScript、Element Plus、Node test/tsx、Maven。

**Spec:** `docs/superpowers/specs/2026-09-10-multisource-artifact-progress-experience-design.md`

## Global Constraints

- 不改变任务状态、阶段状态、取消、重试、下载授权和最终 ZIP 接口语义。
- 新增进度字段全部可选，旧 plan-v1/plan-v2 任务和旧客户端继续可用。
- 前端不推测或伪造阶段完成；阶段和产物以服务端返回为准。
- 未授权的存储路径、Worker 原始日志和内部文件路径不得直接暴露。
- 保留工作区中与本计划无关的用户未提交修改。

---

### Task 1: 扩展后端进度 DTO 的阶段与产物元数据

**Files:**
- Modify: `JavaRSproject/htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ExecutionProgressResponse.java`
- Modify: `JavaRSproject/htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- Modify: `JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskStageView.java`
- Modify: `JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskArtifactView.java`
- Test: `JavaRSproject/htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`

**Interfaces:**
- 阶段响应增加可选 `executionId`、`startedAt`、`finishedAt`、`durationMs`、`description`、`declaredOutputs`。
- 产物响应增加可选 `sizeBytes`、`retention`、`downloadable`、`mediaType`、`previewable`、`displayName`。
- 领域 view 保持旧构造器兼容；旧数据库记录缺字段时返回 null/0/默认值。

- [ ] **Step 1: 写失败测试** — 构造包含执行时间和 Artifact 大小的 detail，断言 controller 透传字段；构造旧式 detail，断言新增字段为空或默认值。
- [ ] **Step 2: 运行测试确认失败**

`mvn -pl htht-admin -am -Dtest=ProcessingExecutionControllerTest test`

预期：因 DTO/view 缺少新增字段而失败。

- [ ] **Step 3: 实现最小字段扩展** — 在 `TaskStageView` 从 `StageExecution` 传出执行时间；在 `TaskArtifactView` 传出 `sizeBytes`，由 controller 根据 URI/类型补全安全展示字段。
- [ ] **Step 4: 运行后端测试**

`mvn -pl htht-admin -am -Dtest=ProcessingExecutionControllerTest test`

预期：通过；旧构造器测试继续通过。

- [ ] **Step 5: 提交**

`git add htht-admin/src/main/java htht-modules/htht-multisource/src/main/java htht-admin/src/test && git commit -m "feat: expose artifact progress metadata"`

### Task 2: 增加阶段职责、耗时和产物分类函数

**Files:**
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Modify: `rsimageprocess-view/src/features/multisource/processingRunState.ts`
- Test: `rsimageprocess-view/src/features/multisource/processingRunState.test.ts`

**Interfaces:**
- `formatStageDuration(stage, now?): string`
- `artifactPresentation(artifact): { displayName: string; kind: "image"|"json"|"scientific"|"file"; canPreview: boolean; sizeLabel: string; retentionLabel: string }`
- `isStageCompletionEdge(previous, current): boolean`

- [ ] **Step 1: 写失败测试** — 覆盖完成阶段耗时、运行中耗时、图片/JSON/COG 分类、后端 previewable 缺失时的扩展名回退、保留级别文案。
- [ ] **Step 2: 运行定向测试确认失败**

`pnpm test:multisource --test-name-pattern "artifact presentation|stage duration|completion edge"`

- [ ] **Step 3: 实现纯函数** — 所有函数对缺失可选字段提供稳定回退，不读取 DOM 或网络。
- [ ] **Step 4: 运行完整前端测试**

`pnpm test:multisource`

- [ ] **Step 5: 提交**

`git add src/api/multiSourceSatellite.ts src/features/multisource/processingRunState.ts src/features/multisource/processingRunState.test.ts && git commit -m "feat: classify artifacts and stage timing"`

### Task 3: 实现轮询完成边沿与自动展开

**Files:**
- Modify: `rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts`
- Test: `rsimageprocess-view/src/features/multisource/processingTaskRules.test.ts`

**Interfaces:**
- 父组件维护 `previousStageStatuses: Map<stageId, status>`、`newArtifactKeys: Set<string>` 和用户展开集合。
- 新增 `autoExpandStage(stageId)` 逻辑：阶段首次进入 SUCCEEDED 且产物数量增加时加入展开集合；不重复触发。
- 轮询刷新不得关闭预览、不得清除用户手动展开状态。

- [ ] **Step 1: 写失败测试** — 文本断言自动展开、刚生成标记、完成边沿去重和预览状态保留。
- [ ] **Step 2: 运行测试确认失败**

`pnpm test:multisource --test-name-pattern "auto-expand|刚生成|completion edge"`

- [ ] **Step 3: 实现轮询快照比较** — 在进度更新处比较旧/新阶段和 artifact key；只对首次完成阶段设置自动展开目标；阶段再次轮询不重复。
- [ ] **Step 4: 实现最小 UI** — 阶段行增加耗时、输入/产物统计、职责说明和“刚生成”标签；产物卡片增加类型说明、大小、保留级别、校验和摘要。
- [ ] **Step 5: 运行完整前端测试**

`pnpm test:multisource`

- [ ] **Step 6: 提交**

`git add src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/features/multisource/useProcessingTask.ts src/features/multisource/taskProgressDialog.test.ts src/features/multisource/processingTaskRules.test.ts && git commit -m "feat: link completed stages to new artifacts"`

### Task 4: 增强预览与诊断信息

**Files:**
- Modify: `rsimageprocess-view/src/components/LeftPanel/TaskProgressDialog.vue`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Modify: `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- Test: `rsimageprocess-view/src/features/multisource/taskProgressDialog.test.ts`

**Interfaces:**
- JSON 产物支持文本预览；图片预览优先使用后端 `previewable`，扩展名作为回退。
- 大文件仍使用下载接口；下载失败保留非阻塞错误提示。
- 诊断区显示执行 ID、attempt、错误码和输入/输出摘要，不展示原始物理路径。

- [ ] **Step 1: 写失败测试** — 断言 JSON 预览入口、元数据显示、诊断折叠区和大文件下载回退。
- [ ] **Step 2: 运行定向测试确认失败**

`pnpm test:multisource --test-name-pattern "JSON|诊断|artifact metadata"`

- [ ] **Step 3: 实现弹窗展示和事件** — 增加 `preview-json-artifact` 或统一预览事件，沿用现有 Blob 授权下载；增加诊断折叠。
- [ ] **Step 4: 运行完整前端测试和类型检查**

`pnpm test:multisource && pnpm typecheck`

预期：多源测试通过；类型检查只允许报告已知仓库错误。

- [ ] **Step 5: 提交**

`git add src/components/LeftPanel/TaskProgressDialog.vue src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/api/multiSourceSatellite.ts src/features/multisource/taskProgressDialog.test.ts && git commit -m "feat: add artifact preview and diagnostics"`

### Task 5: 真实数据验收与交付记录

**Files:**
- Create: `JavaRSproject/docs/superpowers/verification/2026-09-10-multisource-artifact-progress-verification.md`

**Interfaces:**
- 记录五类产品的阶段、耗时、产物数量、预览/下载结果和兼容性结果。

- [ ] **Step 1: 运行前端测试**

`pnpm test:multisource`

- [ ] **Step 2: 运行后端定向测试**

`mvn -pl htht-modules/htht-multisource -am "-Dtest=DispatchNextStageUseCaseTest,AcceptWorkerEventUseCaseTest,WorkerContractCodecTest" "-DfailIfNoTests=false" test`

- [ ] **Step 3: 启动后端并分别创建 HY‑1C、FY‑3H、FY‑4B、GF‑6、HY‑2D 新任务，记录每个 taskId 和 progress 响应。**
- [ ] **Step 4: 验收每阶段自动展开、产物归属、预览/下载、失败诊断和旧 plan-v2 任务兼容。**
- [ ] **Step 5: 将命令输出、任务 ID、已知限制写入验证文档并提交。**

## 验收标准

- 阶段完成后立即显示对应产物，当前完成阶段自动展开且只提示一次“刚生成”。
- 阶段行展示职责、耗时、输入/产物数量。
- 图片/JSON 可预览，科学大文件可下载并显示大小、保留级别和校验和。
- 失败阶段提供可读错误和诊断摘要，下游阶段不会被误标成功。
- 旧任务、旧计划和现有下载/重试行为保持兼容。
- 前端多源测试和后端定向测试通过；类型检查中的既有问题如实记录。

