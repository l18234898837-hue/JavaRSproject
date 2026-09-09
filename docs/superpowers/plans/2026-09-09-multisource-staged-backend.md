# 多源数据后端真实阶段化执行实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有当前支持的多源产品从单次 integrated Worker 调用统一升级为可独立执行、可重试、可追踪、按真实 artifact 传递的阶段 DAG。

**Architecture:** Python 适配器负责产品证据、参数和阶段契约；Worker 阶段只读取 Java manifest 授权的输入并写声明输出。Java 统一负责 DAG 调度、输入授权、artifact 校验/提升和状态传播。新任务使用 `plan-v3`，旧 `plan-v1`/`plan-v2` 继续走兼容执行器，现有 REST 进度、取消、重试和下载协议保持不变。

**Tech Stack:** Java 17、Spring Boot 2.7、MyBatis-Plus、PostgreSQL JSONB、Python 3.11、pytest、NumPy、h5py、netCDF4/xarray、rasterio/GDAL、pyproj、Vue 3/TypeScript。

**Spec:** `docs/superpowers/plans/2026-08-25-hy1c-czi-l2a-java-frontend-handoff-plan.md`、`docs/superpowers/plans/2026-08-25-hy1c-czi-preprocessing-foundation-plan.md`。

## Global Constraints

- 覆盖 HY‑1C CZI L2A、FY‑3H MERSI、FY‑4B AGRI、GF‑6 WFV L1A、GF‑6 浏览、HY‑2D 测高轨迹和 generic base adapter。
- 不改变现有 REST 请求字段、轮询 JSON 结构、取消/重试语义和最终下载接口。
- 不根据文件名猜测科学参数、数据路径、定标、质量位、坐标系或重采样规则。
- 阶段输入只能是声明的 `RAW_INPUT` 或 `UPSTREAM_ARTIFACT`；输出只能是安全相对路径。
- `INTERMEDIATE` 进入任务 `work`，`FINAL` 进入任务 `output`；最终 ZIP 只包含 `FINAL`。
- 第一版按依赖就绪串行调度；DAG 必须保留真实并行分支的依赖关系。
- 不强行统一不同产品的科学阶段名称；统一的是协议、授权、存储和状态模型。

---

### Task 1: 统一 plan-v3 协议与产品能力矩阵

**Files:** `ProcessingStage.java`、`ProcessingPlan.java`、`TaskCreationCoordinator.java`、新增 `PlanVersion.java`、新增 Python `stage_contracts.py` 及其测试。

- [ ] 写跨产品契约测试：每个 adapter 返回非空阶段列表、唯一 stageKey、无环依赖、确定性参数快照、合法 retention；标记 integrated-only 适配器。
- [ ] 运行 Python/Java 重点测试，确认当前单阶段计划导致失败。
- [ ] 实现 `validate_plan(plan)`：拒绝重复 key、循环依赖、重复输出、不安全路径、无生产者的 upstream 输入和下游消费的 FINAL 输出。
- [ ] 新任务写入 `plan-v3`，旧版本原样保留，不原地迁移历史任务。
- [ ] 运行测试并提交：`feat: define multisource plan-v3 stage contract`。

### Task 2: 将所有产品适配器改为真实 DAG

**Files:** `product_adapters/hy1c_czi_l2a.py`、`fy3h_mersi.py`、`fy4b_agri.py`、`gf6_wfv_l1a.py`、`gf6_wfv_browse.py`、`hy2d_altimeter.py`、`base.py` 及对应测试。

**阶段边界：**

- HY‑1C：校验 → 解码/比例因子 → L2 质量 → 定位网格 → ROI 裁剪 → 标准 COG → 特征/融合/显著性。
- FY‑3H：复用已有校验、解码、定标、质量、网格、标准 COG 和可选算法阶段。
- FY‑4B：校验五文件 → 分辨率解码/定标 → 质量控制 → 网格 → 标准输出 → 特征/融合/显著性。
- GF‑6 L1A：校验瓦片 → 解码 → 定标 → RPC 地理定位 → 镶嵌 → 标准输出 → 特征/融合/显著性。
- GF‑6 浏览：校验 → 地理参考 → 视觉特征 → 报告写出；前三阶段必须产生真实中间 artifact。
- HY‑2D：复用已有轨迹校验、解码、质量、SSH 基线、特征/异常、标准输出和可选网格阶段。
- Generic：仅保留通用校验和声明的特征阶段，不臆造产品科学处理。

- [ ] 为每个 adapter 写阶段数量、依赖、输入角色、可选分支和 artifact 类型测试。
- [ ] FY‑3H/HY‑2D 直接接入已有 DAG；FY‑4B/GF‑6/HY‑1C 拆除 integrated plan；GF‑6 浏览补齐中间输出。
- [ ] 运行全部 adapter 测试并提交：`feat: declare plan-v3 DAGs for all multisource products`。

### Task 3: 统一 Worker 阶段执行与中间 artifact

**Files:** `registry.py`、各产品 `*_pipeline.py`、新增 `stage_executor.py`、新增 `intermediate_artifacts.py` 及全产品执行测试。

- [ ] 定义 `execute_stage(request, manifest) -> StageResponse`，旧 `execute_legacy_stage` 仅服务旧 integrated stage。
- [ ] 定义产品无关的中间格式，保存 payload、质量/掩膜、CRS/轨迹参考、变换、波段/变量元数据、profile、参数哈希、producer stage 和 schema version。
- [ ] 每个现有 pipeline 拆成按 stageKey 的 handler；handler 只读取声明输入、只写声明输出，并返回逻辑 URI、大小、SHA-256。
- [ ] registry 按 profileCode、profileVersion、planVersion、stageKey 路由；未知组合、未声明输入/输出、路径越界必须拒绝。
- [ ] 运行 Python 全量测试并提交：`feat: execute plan-v3 stages for all products`。

### Task 4: Java artifact 提升、输入授权和 Worker 协议

**Files:** `ArtifactStorage.java`（新增）、`Artifact.java`、`ArtifactRegistrationService.java`、`DispatchNextStageService.java`、`StageExecutionRequest.java`、`WorkerContractCodec.java`、持久化 entity/mapper、`LocalTaskArtifactStorage.java`（新增）、manifest writer、迁移 SQL 及 Java 测试。

- [ ] 先写失败测试：仅传递声明 raw/upstream 输入；缺失 upstream 时不调用 Worker；校验 checksum、大小、类型、相对路径、跨任务 URI 和重试隔离。
- [ ] 增加 `relative_path`、`retention` 字段，旧记录回填 FINAL；阶段 artifact 不写入原始输入表。
- [ ] 实现原子提升：中间产物到 `<task>/work/<stage>/<attempt>`，最终产物到 `<task>/output`；`.tmp` 校验后移动。
- [ ] `resolveInputs` 按 role/ordinal 和依赖阶段 artifact 精确匹配；request 携带本阶段 outputs 和 execution grant。
- [ ] 运行 Maven 重点测试并提交：`feat: authorize and promote artifacts for staged execution`。

### Task 5: 状态传播、前端阶段映射、重试取消和 API 兼容

**Files:** `DispatchNextStageService.java`、`MyBatisTaskStageRepository.java`、`TaskCreationCoordinator.java`、`ProcessingExecutionController.java`、`ExecutionProgressResponse.java`、`rsimageprocess-view/src/features/multisource/pipelineDefinitions.ts`、`processingRunState.ts`、`MultiSourceSatelliteProcess.vue` 及对应测试。

- [ ] 测试仅 claim 依赖已成功且 artifact 齐全的 ready 阶段；失败时下游保持等待/跳过。
- [ ] 重试递增 attempt 并使用独立目录；取消停止新派发；旧 plan-v1/v2 仍走旧 executor。
- [ ] 保持进度 JSON 结构不变，返回所有真实阶段、时间戳、错误和 artifact stage 归属；禁止前端伪造阶段。
- [ ] 在 `pipelineDefinitions.ts` 增加后端 stageKey 到 UI `StepDefinition` 的显式映射，覆盖所有产品的校验、解码、定标、质量、定位/网格、标准化、特征、融合、显著性和轨迹分析阶段。
- [ ] 在 `processingRunState.ts` 按远端 `stageId` 去重并稳定排序：已知阶段显示在对应 UI 步骤，未知阶段显示为 `remoteOnly`；旧单阶段任务仍只显示一个远端阶段，不补出虚假阶段。
- [ ] 在 `MultiSourceSatelliteProcess.vue` 统一阶段标题、描述、失败/重试入口和 artifact 展示；标签映射只改变文案，不改变后端状态和进度百分比来源。
- [ ] 前端测试覆盖全产品 stageKey 映射、远端排序、未知阶段、旧 plan-v2 单阶段、失败下游状态和 artifact 按 stageId 归属。
- [ ] 运行 Java 和前端回归并提交：`feat: preserve multisource staged lifecycle compatibility`。

### Task 6: 全产品端到端验收与灰度

**Files:** 新增 `test_multisource_staged_e2e.py`、Java `StageExecutionEndToEndTest.java`、灰度记录文档。

- [ ] 为每个 profile 准备有效/无效样例和可选算法配置。
- [ ] Worker E2E 校验每阶段只运行一次、只产生声明 artifact、元数据/CRS/质量正确且无越界写入。
- [ ] Java-to-Worker E2E 校验进度阶段顺序、状态、时间戳、重试、取消、artifact 所属和最终 ZIP。
- [ ] 负向测试覆盖篡改中间文件、缺失 upstream、未声明输出、跨任务 URI、路径穿越、未知 stage、超时和不相交 ROI。
- [ ] 通过 profile feature flag 灰度启用；以一条样例任务对比旧流程产物数量、checksum、耗时和资源，再逐步放量；关闭 flag 即可回滚新任务。
- [ ] 运行 Python、Maven、前端全量测试并提交：`test: verify all multisource staged backend profiles`。

## 验收标准

所有当前支持的 profile 新建任务都使用真实 `plan-v3` 阶段；每阶段职责、依赖、输入、输出和失败状态可在进度接口观察；artifact 按授权传递；失败不伪造下游成功；重试不覆盖其他阶段/任务；旧 integrated 任务仍可操作；最终 ZIP 只含 FINAL；前端不再补造进度阶段。
