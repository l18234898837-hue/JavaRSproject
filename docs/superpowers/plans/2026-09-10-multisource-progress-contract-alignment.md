# 多源数据阶段进度契约对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 HY‑1C、FY‑3H、FY‑4B、GF‑6、HY‑2D 的前端进度与后端真实 plan-v3 阶段一致，正确处理一对多映射和内部阶段，避免出现“实际完成但界面等待”或虚假的阶段总数。

**Architecture:** 后端任务 `plan_snapshot` 和 `rs_task_stage` 是执行状态唯一事实来源；不改变 Worker 的科学阶段和现有 REST JSON 基本结构。前端维护一份显式、可测试的 product/stage 映射：一个远端 stage 可贡献到一个或多个 UI 步骤；内部阶段可合并到已有步骤，不再作为额外 remote-only 阶段计数。旧 plan-v1/plan-v2 只按其返回的真实阶段展示，不补造新阶段。

**Tech Stack:** Vue 3、TypeScript、Node test/tsx、Java 17、Spring Boot 2.7、Python 3.11、PostgreSQL JSONB、pytest、Maven。

**Spec:** 现有 `docs/superpowers/plans/2026-09-09-multisource-staged-backend.md` 的 Task 5 与本计划中的映射矩阵；本计划针对已发现的进度展示缺口细化执行步骤。

## Global Constraints

- 不改变现有 REST 请求字段、进度 JSON 顶层结构、取消/重试语义和 ZIP 下载接口。
- 后端真实 stage 状态、stageId、attempt 和 artifact 归属不得由前端伪造。
- 不把不同产品强行改成相同科学阶段；只统一展示映射、计数和兼容规则。
- `plan-v2`/`plan-v1` 历史任务继续按原阶段运行；本次改动不得迁移或重写数据库历史快照。
- 保留工作区中与本任务无关的前端未提交修改；只提交本计划涉及文件。

---

### Task 1: 建立全产品后端阶段—前端步骤映射矩阵

**Files:**
- Modify: `rsimageprocess-view/src/features/multisource/processingRunState.ts`
- Modify: `rsimageprocess-view/src/features/multisource/pipelineDefinitions.ts`
- Test: `rsimageprocess-view/src/features/multisource/processingRunState.test.ts`
- Test: `rsimageprocess-view/src/features/multisource/pipelineDefinitions.test.ts`

**Interfaces:**
- `uiStepKeysForRemoteStage(stageKey: string, productId?: string): readonly string[]` 返回远端阶段对应的所有 UI step key。
- `buildRuntimeStageViews(productId, steps, stages, artifacts, invalidatedSteps)` 保留现有调用兼容重载，输出每个 UI 步骤最多一个 view，并把已匹配远端阶段从 remote-only 集合排除。

- [ ] **Step 1: 写失败测试**

```ts
test("maps HY-1C decode stage to decode and scale-factor UI steps", () => {
  assert.deepEqual(
    uiStepKeysForRemoteStage("DECODE_SCALE_AND_NODATA", "hy1c-czi-l2a-v1"),
    ["band-metadata-decode", "scale-factor-processing"]
  );
});

test("maps every staged product key without leaving known backend stages remote-only", () => {
  const expected = {
    "fy3h-mersi-l1-1000m-geo1k-v1": ["VALIDATE_FY3H_PAIR", "DECODE_MERSI_L1", "CALIBRATE_MERSI_L1", "APPLY_MERSI_QUALITY", "GRID_MERSI_SWATH", "WRITE_MERSI_STANDARD_COG"],
    "fy4b-agri-v1": ["VALIDATE_FY4B_PACKAGE", "BUILD_FY4B_NOMINAL_GRID", "CALIBRATE_AND_GEOREFERENCE_FY4B", "REPROJECT_AND_CROP_FY4B", "WRITE_FY4B_STANDARD_COG"],
    "gf6-wfv-l1a-v1": ["VALIDATE_GF6_WFV_L1A", "WRITE_GF6_STANDARD_COG"],
    "hy2d-altimeter-v1": ["VALIDATE_SOURCE", "DECODE_ALTIMETER_TRACK", "APPLY_TRACK_QUALITY", "CALCULATE_BASELINE_SSH", "WRITE_STANDARD_TRACK_OUTPUT"]
  } as const;
  for (const [productId, stageKeys] of Object.entries(expected)) {
    for (const stageKey of stageKeys) assert.notEqual(uiStepKeysForRemoteStage(stageKey, productId), [stageKey]);
  }
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm test:multisource --test-name-pattern "HY-1C decode|every staged product"`

Expected: FAIL because the current alias API returns only one step and does not distinguish product-specific mappings.

- [ ] **Step 3: 实现显式映射表**

在 `processingRunState.ts` 中把当前 `Record<string, string>` 改为 `Record<string, readonly string[]>`，增加产品覆盖表：

```ts
const REMOTE_STAGE_TO_UI_STEPS: Record<string, readonly string[]> = {
  VALIDATE_SOURCE: ["upload-validation"],
  DECODE_SCALE_AND_NODATA: ["band-metadata-decode", "scale-factor-processing"],
  APPLY_L2_FLAGS: ["l2-quality-control"],
  GEOLOCATE_AND_GRID: ["projection-standardization"],
  CROP_CZI: ["projection-standardization"],
  WRITE_STANDARD_COG: ["standard-product"],
  VALIDATE_FY3H_PAIR: ["upload-validation"],
  DECODE_MERSI_L1: ["data-decode"],
  CALIBRATE_MERSI_L1: ["radiometric-calibration"],
  APPLY_MERSI_QUALITY: ["quality-control"],
  GRID_MERSI_SWATH: ["swath-standardization"],
  WRITE_MERSI_STANDARD_COG: ["standard-product"],
  VALIDATE_FY4B_PACKAGE: ["upload-validation"],
  BUILD_FY4B_NOMINAL_GRID: ["geo-grid-construction"],
  CALIBRATE_AND_GEOREFERENCE_FY4B: ["radiometric-calibration", "projection-standardization"],
  REPROJECT_AND_CROP_FY4B: ["projection-standardization"],
  WRITE_FY4B_STANDARD_COG: ["standard-product"],
  VALIDATE_GF6_WFV_L1A: ["upload-validation"],
  WRITE_GF6_STANDARD_COG: ["standard-product"],
  VALIDATE_BROWSE: ["upload-validation"],
  GEOREFERENCE_BROWSE: ["rpc-geolocation"],
  VALIDATE_SOURCE: ["upload-validation"],
  DECODE_ALTIMETER_TRACK: ["along-track-decode"],
  APPLY_TRACK_QUALITY: ["quality-control"],
  CALCULATE_BASELINE_SSH: ["ssh-baseline"],
  WRITE_STANDARD_TRACK_OUTPUT: ["map-preview"]
};
```

保留单字符串别名函数作为兼容包装，返回数组第一项；新增数组函数供渲染使用。

- [ ] **Step 4: 运行映射测试**

Run: `pnpm test:multisource --test-name-pattern "HY-1C decode|every staged product"`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/features/multisource/processingRunState.ts src/features/multisource/pipelineDefinitions.ts src/features/multisource/processingRunState.test.ts src/features/multisource/pipelineDefinitions.test.ts
git commit -m "fix: define multisource stage display mappings"
```

### Task 2: 支持一对多阶段匹配、内部阶段合并与准确计数

**Files:**
- Modify: `rsimageprocess-view/src/features/multisource/processingRunState.ts`
- Test: `rsimageprocess-view/src/features/multisource/processingRunState.test.ts`

**Interfaces:**
- `buildRuntimeStageViews` 必须将同一远端 stage 绑定到多个 UI views；每个绑定 view 共享同一 stage 状态和 artifact 集合。
- `runtimeProgressPercent` 和完成数量必须基于 UI steps 去重后的状态；同一个远端 stage 映射两个 UI 步骤时，按两个用户可见步骤计数。
- 未映射且确属内部阶段的 stage 不创建 `remoteOnly` view；未知外部 stage 仍创建 `remoteOnly` 并保留真实状态。

- [ ] **Step 1: 写失败测试**

```ts
test("counts HY-1C decode and scale-factor as two completed UI steps", () => {
  const views = buildRuntimeStageViews(
    getPipelineDefinition("hy1c-czi-l2a-v1").steps,
    [stage({ stageId: 10, stageKey: "DECODE_SCALE_AND_NODATA", status: "SUCCEEDED" })]
  );
  assert.equal(views.find(v => v.step.key === "band-metadata-decode")?.status, "SUCCEEDED");
  assert.equal(views.find(v => v.step.key === "scale-factor-processing")?.status, "SUCCEEDED");
});

test("does not add CROP_CZI as an extra remote-only progress step", () => {
  const views = buildRuntimeStageViews(
    getPipelineDefinition("hy1c-czi-l2a-v1").steps,
    [stage({ stageId: 11, stageKey: "CROP_CZI", status: "SUCCEEDED" })]
  );
  assert.equal(views.some(v => v.remoteOnly && v.stage?.stageKey === "CROP_CZI"), false);
});

test("keeps truly unknown stage visible as remote-only", () => {
  const views = buildRuntimeStageViews([], [stage({ stageId: 12, stageKey: "FUTURE_STAGE" })]);
  assert.equal(views.some(v => v.remoteOnly && v.stage?.stageKey === "FUTURE_STAGE"), true);
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm test:multisource --test-name-pattern "counts HY-1C|CROP_CZI|truly unknown"`

Expected: FAIL because the current implementation maps a stage to one view and treats unmatched internal stages as remote-only.

- [ ] **Step 3: 实现匹配和计数**

修改 `stageMatchesStep` 为数组匹配；构造 views 时先取得每个 stage 的 UI keys，再把同一个 `stage` 绑定到所有对应 step。增加 `INTERNAL_STAGE_KEYS`（`CROP_CZI`、FY‑4B 的中间网格/裁剪阶段等）并在 remote-only 过滤前排除。保留 stageId 排序，不按数组位置猜测阶段。

```ts
const INTERNAL_STAGE_KEYS = new Set([
  "CROP_CZI", "BUILD_FY4B_NOMINAL_GRID", "REPROJECT_AND_CROP_FY4B"
]);
```

`runtimeProgressPercent` 继续按 UI views 的 `SUCCEEDED` 数量计算；一对多映射自然贡献两个已完成用户步骤，但不生成额外 remote-only view。

- [ ] **Step 4: 运行前端回归**

Run: `pnpm test:multisource`

Expected: PASS with existing unknown-stage, artifact grouping, and legacy plan tests unchanged。

- [ ] **Step 5: 提交**

```bash
git add src/features/multisource/processingRunState.ts src/features/multisource/processingRunState.test.ts
git commit -m "fix: count mapped multisource stages accurately"
```

### Task 3: 校准五类产品 UI 步骤名称和阶段总数

**Files:**
- Modify: `rsimageprocess-view/src/features/multisource/pipelineDefinitions.ts`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Test: `rsimageprocess-view/src/features/multisource/pipelineDefinitions.test.ts`
- Test: `rsimageprocess-view/src/features/multisource/processingRunState.test.ts`

**Interfaces:**
- 每个 product template 的用户可见 steps 只包含用户可理解的处理职责；内部 artifact 交接阶段不得单独增加总数。
- 阶段标题来自统一 `STAGE_LABELS` 或 StepDefinition，不得在组件内为同一 stageKey 定义冲突文案。

- [ ] **Step 1: 写失败测试**

```ts
test("exposes stable visible step counts for all products", () => {
  assert.equal(getPipelineDefinition("hy1c-czi-l2a-v1").steps.length, 10);
  assert.equal(getPipelineDefinition("fy3h-mersi-l1-1000m-geo1k-v1").steps.length, 11);
  assert.equal(getPipelineDefinition("fy4b-agri-v1").steps.length, 10);
  assert.equal(getPipelineDefinition("gf6-wfv-l1a-v1").steps.length, 7);
  assert.equal(getPipelineDefinition("hy2d-altimeter-v1").steps.length, 8);
});
```

若现有产品定义与这些用户可见职责不一致，先让断言按实际产品需求明确化；测试必须同时检查标题和 key，防止只改数字。

- [ ] **Step 2: 运行测试确认失败并记录差异**

Run: `pnpm test:multisource --test-name-pattern "stable visible step counts"`

Expected: FAIL only for product definitions whose visible steps currently缺少或重复职责；记录每项差异后再改对应定义。

- [ ] **Step 3: 调整定义和组件文案**

在 `pipelineDefinitions.ts` 中补齐 FY‑3H 的定标步骤、FY‑4B 的网格/定标/质量/投影职责、GF‑6 的实际阶段职责、HY‑2D 的 SSH/轨迹分析职责；不把后端内部 artifact 阶段直接复制进 UI。组件只调用 `buildRuntimeStageViews` 的结果渲染，不自行按后端数组长度计算总数。

- [ ] **Step 4: 运行全量前端测试和类型检查**

Run: `pnpm test:multisource && pnpm typecheck`

Expected: 两条命令均退出码 0。

- [ ] **Step 5: 提交**

```bash
git add src/features/multisource/pipelineDefinitions.ts src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/features/multisource/pipelineDefinitions.test.ts src/features/multisource/processingRunState.test.ts
git commit -m "fix: align visible steps across multisource products"
```

### Task 4: 增加后端进度契约回归与旧任务兼容验证

**Files:**
- Test: `JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- Test: `JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/AcceptWorkerEventUseCaseTest.java`
- Test: `JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

**Interfaces:**
- 后端不改变阶段 key；测试证明 plan-v3 DAG 依赖、阶段成功后的自动推进、失败下游不被标为成功均保持。
- 旧 plan-v2 的 `STANDARDIZE_L2A`、`STANDARDIZE_MERSI_L1` 等任务仍可按原协议执行和返回。

- [ ] **Step 1: 增加后端兼容测试**

断言 plan-v3 每个产品 stage key 唯一且依赖可达；断言旧 plan-v2 单阶段请求仍走 legacy executor；断言成功事件后只派发依赖满足的下一个阶段，失败事件不会派发下游。

- [ ] **Step 2: 运行 Maven 测试**

Run: `mvn -pl htht-modules/htht-multisource -am -Dtest=DispatchNextStageUseCaseTest,AcceptWorkerEventUseCaseTest,WorkerContractCodecTest -DfailIfNoTests=false test`

Expected: PASS with zero failures。

- [ ] **Step 3: 提交**

```bash
git add htht-modules/htht-multisource/src/test/java
git commit -m "test: preserve staged backend progress compatibility"
```

### Task 5: 真实样例验收和交付检查

**Files:**
- Create: `JavaRSproject/docs/superpowers/verification/2026-09-10-multisource-progress-matrix.md`
- No production code changes.

- [ ] **Step 1: 运行 Python 计划契约测试**

Run: `JavaRSproject/rs-algorithm-worker/.venv/Scripts/python.exe -m pytest JavaRSproject/rs-algorithm-worker/tests/test_registry.py JavaRSproject/rs-algorithm-worker/tests/test_hy1c_czi_l2a.py -q`

Expected: PASS；记录每个 profile 的 plan version 和 stage keys。

- [ ] **Step 2: 启动最新后端并创建五类新任务**

使用 `start-multisource-backend-local.cmd` 启动，确保 `PYTHONPATH` 指向当前 worker 源码；每类产品创建新任务，不复用历史 taskId。

- [ ] **Step 3: 对照数据库和进度接口**

对每个 task 执行：

```sql
select task_id, lifecycle_status, plan_snapshot->>'version' from rs_processing_task where task_id = :task_id;
select stage_key, execution_status, attempt from rs_task_stage where task_id = :task_id order by stage_id;
```

记录 UI 可见步骤、后端真实 stage、最终 artifact 数量；确认同一远端阶段映射多个 UI 步骤时计数符合预期，内部阶段不额外增加总数。

- [ ] **Step 4: 验证旧任务**

打开一个历史 `plan-v2` 任务，确认仍显示 `STANDARDIZE_*` 真实阶段，不被新 plan-v3 映射强行拆分。

- [ ] **Step 5: 运行最终回归并提交验证记录**

Run: `pnpm test:multisource && pnpm typecheck && mvn -pl htht-modules/htht-multisource -am -Dtest=DispatchNextStageUseCaseTest,AcceptWorkerEventUseCaseTest,WorkerContractCodecTest -DfailIfNoTests=false test`

将命令结果、任务 ID、阶段矩阵和已知限制写入验证记录；提交：

```bash
git add docs/superpowers/verification/2026-09-10-multisource-progress-matrix.md
git commit -m "test: record multisource progress alignment verification"
```

## 验收标准

- HY‑1C 的 `DECODE_SCALE_AND_NODATA` 完成时，“波段与元数据解码”和“比例因子处理”同时完成；`CROP_CZI` 不再产生额外 remote-only 计数。
- FY‑3H、FY‑4B、GF‑6、HY‑2D 的所有 plan-v3 阶段都有明确 UI 映射或明确内部阶段标记；未知阶段仍安全显示。
- 进度百分比和“已完成/总阶段”只基于去重后的用户可见步骤，不因远端 stage 数量变化而失真。
- 新旧计划兼容：新任务使用 plan-v3，历史 plan-v1/plan-v2 不迁移且可继续查看/重试/下载。
- Python、Maven、前端测试和类型检查全部通过，真实样例数据库阶段与 UI 结果可逐项对照。
