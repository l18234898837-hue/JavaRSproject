# HY-1C CZI L2A 后端真实阶段化执行实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 HY-1C CZI L2A 从单一 `STANDARDIZE_L2A` Worker 调用改为可独立执行、可重试、可追踪且通过真实产物传递的 `plan-v3` 阶段 DAG。

**Architecture:** Python Worker 按 `stageKey` 执行单一职责阶段，只读取 Java manifest 授权的原始文件或上游 artifact，并只写计划声明的输出。Java 负责阶段依赖调度、阶段级输入授权、产物校验与提升；现有进度 REST API 保持不变，前端直接显示后端阶段状态。旧 `plan-v2` 任务继续走兼容的单阶段流程。

**Tech Stack:** Java 17、Spring Boot 2.7、MyBatis-Plus、PostgreSQL JSONB、Python 3.11、pytest、NumPy、h5py、rasterio/GDAL、pyproj、Vue 3/TypeScript。

**Spec:** `docs/superpowers/plans/2026-08-25-hy1c-czi-l2a-java-frontend-handoff-plan.md`、`docs/superpowers/plans/2026-08-25-hy1c-czi-preprocessing-foundation-plan.md`。

## Global Constraints

- 本次只改 HY-1C CZI L2A；FY-3H、FY-4B、GF-6 和 HY-2D 行为保持不变。
- 新任务使用 `plan-v3`；历史 `plan-v2`/单阶段任务必须继续可查看、重试和下载。
- 不改变现有 REST 请求字段、轮询协议、取消/重试语义和最终 ZIP 接口。
- Worker 不接触数据库、HTTP 或未授权物理路径；Java 是任务状态唯一来源。
- 阶段输出只能使用声明的安全相对路径；禁止绝对路径、反斜杠、`..` 和越出阶段目录的 URI。
- `INTERMEDIATE` 产物进入任务 `work` 区域，`FINAL` 产物进入任务 `output` 区域；最终下载只包含 `FINAL`。
- 第一版按 DAG 依赖串行调度；后三个算法阶段保留无互相依赖的声明，后续可安全改为并行。
- 不根据文件名推断定标、HDF5 路径、质量位、坐标系或重采样规则；继续使用已审查的 HY-1C fixture。

---

### Task 1: 冻结 HY-1C `plan-v3` 阶段契约

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py:158-194`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/l2a_profile.py`
- Modify: `rs-algorithm-worker/tests/test_hy1c_czi_l2a.py`
- Modify: `rs-algorithm-worker/tests/test_registry.py`

**Interfaces:**
- `build_l2a_plan(candidate: dict, parameter_snapshot: dict) -> dict` 返回 `version == "plan-v3"`。
- 每个阶段包含 `stageKey`、`stageCode`、`dependsOnStageKeys`、`inputs`、`outputs`、`parameterSnapshot`。
- `UPSTREAM_ARTIFACT` 必须来自声明依赖阶段；`FINAL` 产物不得被下游消费。

- [ ] **Step 1: Write failing contract tests.** 开启全部算法时断言阶段顺序为：

```python
assert [s["stageKey"] for s in plan["stages"]] == [
    "VALIDATE_SOURCE", "DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS",
    "GEOLOCATE_AND_GRID", "CROP_CZI", "WRITE_STANDARD_COG",
    "EXTRACT_CZI_FEATURES", "FUSE_CZI_BANDS", "DETECT_CZI_SALIENCY",
]
assert plan["stages"][2]["inputs"][0] == {
    "key": "decoded", "source": "UPSTREAM_ARTIFACT",
    "artifactType": "DECODED_CZI_RASTER", "required": True,
}
```

同时覆盖可选阶段关闭、依赖错误、重复输出和非法相对路径。

- [ ] **Step 2: Run focused tests to confirm failure.**

```powershell
$env:PYTHONPATH=(Join-Path (Get-Location) 'src')
.\.venv\Scripts\python.exe -m pytest tests/test_hy1c_czi_l2a.py tests/test_registry.py -q
```

Expected: 当前实现只返回一个 `STANDARDIZE_L2A` 阶段。

- [ ] **Step 3: Implement the minimal builder.** 新增 `RAW_INPUT`、`UPSTREAM_ARTIFACT`、`INTERMEDIATE`、`FINAL` 契约辅助函数，按以下边界生成阶段：

```text
VALIDATE_SOURCE -> DECODE_SCALE_AND_NODATA -> APPLY_L2_FLAGS
-> GEOLOCATE_AND_GRID -> CROP_CZI -> WRITE_STANDARD_COG
-> EXTRACT_CZI_FEATURES / FUSE_CZI_BANDS / DETECT_CZI_SALIENCY
```

每个阶段保留 mapping version、公开参数和确定性的参数快照。

- [ ] **Step 4: Run tests and commit.**

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_hy1c_czi_l2a.py tests/test_registry.py -q
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py rs-algorithm-worker/src/rs_algorithm_worker/l2a_profile.py rs-algorithm-worker/tests
git commit -m "feat: add HY-1C CZI L2A plan-v3 stages"
```

### Task 2: 建立跨阶段中间 artifact 并拆分 Python Worker

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/contracts.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/l2a_intermediate.py`
- Create: `rs-algorithm-worker/tests/test_hy1c_czi_l2a_stages.py`

**Interfaces:**
- `execute_l2a_stage(stage_key: str, manifest: dict, parameter_snapshot: dict, output_dir: Path, output_uri: str) -> list[dict]`。
- `write_intermediate(path: Path, raster: RasterData, valid_mask: np.ndarray | None, metadata: dict) -> dict`。
- `read_intermediate(path: Path) -> tuple[RasterData, np.ndarray | None, dict]`。
- 旧 `execute_l2a_product(...)` 继续服务 `plan-v2` 的 `STANDARDIZE_L2A`。

- [ ] **Step 1: Write failing stage tests.** 用合成 HDF5 覆盖解码、质量标志、网格化、ROI 裁剪、标准 COG 和三个可选算法阶段；断言每阶段只返回其声明产物。
- [ ] **Step 2: Run tests to confirm failure.**

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_hy1c_czi_l2a_stages.py -q
```

- [ ] **Step 3: Implement self-describing intermediate files.** 保存 values、mask、CRS、transform、nodata、波段元数据、mapping version、参数哈希和 producer stage；读取时校验形状、checksum 和生产阶段。
- [ ] **Step 4: Split execution by `stageKey`.** 复用现有 `_decode`、`_apply_flags`、`_grid_l2a_source`、`_feature_artifacts`、`_fusion_artifacts`、`_saliency_artifacts`，但禁止阶段间传递内存对象或重复计算上游阶段。
- [ ] **Step 5: Update registry routing.** `plan-v3` 按阶段调用 `execute_l2a_stage`；`plan-v2` 仍调用旧完整流程；未知阶段、未声明输入/输出和非法 URI 必须拒绝。
- [ ] **Step 6: Run full Worker tests and commit.**

```powershell
.\.venv\Scripts\python.exe -m pytest -q
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: execute HY-1C CZI L2A stages independently"
```

### Task 3: 持久化阶段产物的 retention、relative path 和原子提升

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ArtifactStorage.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/Artifact.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/ArtifactEntity.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapping/TaskPersistenceMapper.java`
- Create: `htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactStorage.java`
- Create: `script/sql/migrations/V20260909_01__add_multisource_stage_artifact_contract.sql`
- Create: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationServiceTest.java`
- Create: `htht-admin/src/test/java/com/htht/config/multisource/LocalTaskArtifactStorageTest.java`

**Interfaces:**
- `ArtifactStorage.promote(TaskId, StageId, ExecutionId, String stageKey, int attempt, StorageUri stageOutputUri, ArtifactCandidate, StageOutput) -> Artifact`。
- `Artifact` 增加 `relativePath` 和 `StageOutput.Retention retention`。
- `ArtifactStorage.toAuthorizedInputs(TaskId, ExecutionId, List<StageInput>) -> List<InputAsset>`。

- [ ] **Step 1: Write failing promotion tests.** 覆盖中间/最终产物、checksum/size 不一致、未声明类型、`../escape`、跨任务 URI 和重试隔离。
- [ ] **Step 2: Run focused Maven tests to confirm failure。**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am "-Dtest=LocalTaskArtifactStorageTest,ArtifactRegistrationServiceTest" -DfailIfNoTests=false test
```

- [ ] **Step 3: Add domain fields and migration.** 新增非空 `relative_path`、`retention`，已有记录回填 `FINAL`，并限制值为 `INTERMEDIATE` 或 `FINAL`；不写入原始输入表。
- [ ] **Step 4: Implement controlled local storage.** 仅从 execution output 目录读取，流式校验 SHA-256，写 sibling `.tmp` 后原子移动到 `<task>/work/<stage>/<attempt>/...` 或 `<task>/output/...`，只返回 `rs://artifact/{task-id}/...`。
- [ ] **Step 5: Integrate registration service and commit.**

```powershell
git add htht-admin htht-modules/htht-multisource script/sql/migrations
git commit -m "feat: promote multisource stage artifacts safely"
```

### Task 4: 按阶段声明派发输入并限制授权范围

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageExecutionRequest.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/LocalWorkerAssetManifestWriter.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- Create/modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

**Interfaces:**
- `StageExecutionRequest` 携带已解析的 `inputs`、本阶段 `outputs` 和 execution grant，禁止隐式携带全部候选文件。
- `DispatchNextStageService.resolveInputs(task, planStage, execution) -> List<InputAsset>` 按声明顺序返回。

- [ ] **Step 1: Write failing dispatch tests.** `APPLY_L2_FLAGS` 只能收到 `decoded` 和 `main`；缺少 `DECODED_CZI_RASTER` 时 Worker 调用次数为零且错误码为 `MISSING_UPSTREAM_ARTIFACT`。
- [ ] **Step 2: Implement exact input resolution.** `RAW_INPUT` 按 role/ordinal 匹配；`UPSTREAM_ARTIFACT` 仅匹配依赖阶段产生的同类型、同任务产物；阶段 URI 使用 `task.outputUri()/stages/{stageKey}`。
- [ ] **Step 3: Encode output contract and manifest.** 序列化 `StageOutput` 和参数快照；manifest writer 只能根据 resolved inputs 生成可读文件，不能扫描 output URI 推导路径。
- [ ] **Step 4: Enforce URI authorization and run tests.** 接受 `rs://sys-file/{id}` 及同任务 `rs://artifact/{task-id}/...`；拒绝跨任务、未授权 execution 和路径穿越。
- [ ] **Step 5: Commit.**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am "-Dtest=DispatchNextStageUseCaseTest,WorkerContractCodecTest,LocalWorkerAssetManifestWriterTest" -DfailIfNoTests=false test
git add htht-admin htht-modules/htht-multisource
git commit -m "feat: dispatch stages with declared authorized inputs"
```

### Task 5: 阶段状态传播、重试、取消和旧计划兼容

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/TaskCreationCoordinator.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/MyBatisTaskStageRepository.java`
- Modify: `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- Modify: `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ExecutionProgressResponse.java`
- Modify: frontend stage label mapping only when a new label is missing

- [ ] **Step 1: Add lifecycle tests.** 覆盖仅 claim ready 阶段、失败后下游保持等待/跳过、重试使用新 attempt 目录、取消停止新派发、旧 `plan-v2` 仍执行 `STANDARDIZE_L2A`。
- [ ] **Step 2: Implement plan-version routing.** 新任务保留 `plan-v3` 快照；旧任务不原地迁移，继续使用旧执行器。
- [ ] **Step 3: Implement failure propagation.** 只记录当前阶段错误；不把 descendants 标记为成功；重试成功后依据持久化依赖和 artifact 重新计算 ready 状态。
- [ ] **Step 4: Verify progress compatibility.** 保持进度接口 JSON 结构不变，返回所有阶段、状态、时间戳、错误和 artifact stage 归属；补齐新阶段标签。
- [ ] **Step 5: Run Java/frontend regression and commit.**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am test
cd ..\rsimageprocess-view
npm test -- --runInBand
npm run build
git add htht-admin htht-modules/htht-multisource
git commit -m "feat: preserve staged lifecycle and legacy plan compatibility"
```

### Task 6: 端到端验收、灰度和文档收尾

**Files:**
- Create: `rs-algorithm-worker/tests/test_hy1c_czi_l2a_e2e.py`
- Create/modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/StageExecutionEndToEndTest.java`
- Modify: `docs/superpowers/plans/2026-09-09-hy1c-czi-l2a-staged-backend.md`

- [ ] **Step 1: Run real-sample Worker E2E.** 使用 `MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT` 指向供应样例，开启全部算法，校验每阶段 checksum、metadata 及输出目录边界。
- [ ] **Step 2: Run Java-to-Worker E2E.** 创建新任务并轮询 `/processing-tasks/{taskId}/progress`，验证九阶段顺序、独立时间戳、artifact 所属、重试、取消和只含 `FINAL` 的 ZIP。
- [ ] **Step 3: Run negative-path tests.** 篡改中间文件、删除上游产物、增加未声明输出、使用跨任务 URI、不相交 ROI 均必须只失败当前阶段。
- [ ] **Step 4: Run full regression.**

```powershell
cd JavaRSproject\rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest -q
cd ..
mvn -pl htht-admin,htht-modules/htht-multisource -am test
cd ..\rsimageprocess-view
npm test -- --runInBand
npm run build
```

- [ ] **Step 5: Commit the verification record.**

```powershell
cd ..\JavaRSproject
git add docs/superpowers/plans/2026-09-09-hy1c-czi-l2a-staged-backend.md
git commit -m "test: verify HY-1C CZI L2A staged backend execution"
```

## 验收标准

开启全部算法的新 HY-1C 任务必须在进度接口出现九个真实阶段；每阶段独立进入运行/成功/失败状态，产物按授权传递，失败不会伪造下游成功，重试不会覆盖其他阶段或任务文件，旧单阶段任务仍可正常操作，最终下载只包含 `FINAL` 产物。
