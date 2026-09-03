# GF-3 SAR SLC 处理与前后端闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在现有多源卫星任务框架中接入 GF3 单景、VV 单极化 SLC 产品，实现分块解码、幅度/强度/相位生成、相对辐射预处理、可选去斑、无 DEM 名义 RPC 定位、重采样、裁剪、特征提取、显著性候选检测及 Java/前端闭环。

**架构：** 新增独立的 `GF-3-SAR-SLC/v1` 产品适配器和一个 `STANDARDIZE_GF3_SAR_SLC` Worker 阶段。Worker 以窗口方式读取当前数据的双通道 I/Q TIFF，先生成派生 SAR 图层，再进行 RPC 名义定位和标准 COG 输出；可选算法复用现有通用算子，但不得复用 GF6 的八波段光学数据契约。Java 继续负责预检、任务编排、进度、取消、重试和结果归档，前端增加 GF3 产品模板和参数抽屉。

**技术栈：** Python 3.11、Rasterio/GDAL、NumPy、SciPy、pytest；Java 17、Spring Boot、Flyway、JUnit 5；Vue 3、TypeScript、Element Plus、pnpm。

**Spec：** 当前会话已确认的 GF3 数据检查结论、现有 `rs-algorithm-worker`、Java 多源任务模块和 `rsimageprocess-view` 前端实现。本计划不依赖尚未取得的 GF3 绝对定标说明书。

## 全局约束

- 产品标识固定为 `GF-3-SAR-SLC/v1`，数据形状沿用 Java 已支持的 `RASTER_GRID`，在 metadata 中标记 `complexSource=true`。
- 当前样例必需输入为 `.tiff`、`.meta.xml`、`.rpc`；`.jpg`、`intensity.tiff`、`intensity_rpc.txt` 只能作为可选辅助输入。
- 原始 SLC TIFF 必须窗口化读取，禁止一次性加载约 1.5 GB 文件；默认窗口为 256×256，必要时允许 512×512。
- 当前只做相对辐射预处理，不输出未经资料支持的 Sigma0、Beta0 或 Gamma0。
- RPC 处理固定为常量高程 0 米，状态为 `NOMINAL_RPC_NO_DEM`，精度状态为 `UNVALIDATED`。
- 当前 VV 单极化不提供真实波段配准、极化分解和多波段物理融合；任何幅度/强度/对数强度伪彩色必须标记为可视化结果。
- 显著性输出必须标记 `CANDIDATE_ONLY`，不得宣称目标检测精度。
- 不改变现有 GF6、FY3H、FY4B、HY1C、HY2D 的 profile key、stage code、输入角色、输出类型和接口行为。
- Worker 返回只允许 `rs://output/...` 逻辑 URI、artifact type、大小和 SHA-256，不得泄露本地路径、RPC 系数或栈信息。
- 实现前使用独立开发分支；不得覆盖当前工作区中的用户改动。

---

### 任务 1：建立 GF3 产品证据和参数契约

**文件：**

- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/models_gf3.py`
- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf3_sar_slc.py`
- 新建 `rs-algorithm-worker/tests/gf3_fixture.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_sar_slc.py`

**接口：**

```python
inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]
inspect_evidence(manifest: dict[str, Any]) -> Gf3ProductEvidence
parse_parameters(raw: dict[str, Any] | None) -> dict[str, Any]
build_integrated_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None) -> dict[str, Any]
```

- [ ] 编写失败测试：识别 GF3/SAR/SL/L1/SLC/VV 元数据，要求 TIFF、XML、RPC 三个必需角色。
- [ ] 编写失败测试：拒绝缺少 I/Q 通道、通道数错误、非有符号 16 位样本和尺寸异常的 TIFF。
- [ ] 编写失败测试：拒绝缺少 RPC 字段、非法数字、重复 RPC 或与产品基名不匹配的文件。
- [ ] 编写失败测试：验证参数默认值、ROI、CRS、分辨率、图层、去斑窗口、特征指标和显著性阈值。
- [ ] 实现 XML 解析、TIFF 头检查、RPC 文件读取和 metadata snapshot；保留原始尺寸、卫星、模式、极化、分辨率、入射角和地理范围。
- [ ] 规范化默认参数：`EPSG:32649`、1 米、0 米常量高程、强度图层、去斑关闭、特征和显著性关闭。
- [ ] 生成单阶段 plan，阶段 key/code 为 `STANDARDIZE_GF3_SAR_SLC`，列出 raw inputs 和按开关确定的 final outputs。
- [ ] 运行 `pytest -q tests/test_gf3_sar_slc.py`，确认产品识别和参数校验通过。

参数快照必须使用以下字段，不包含 UI 文案：

```json
{
  "schemaVersion": "gf3-sar-slc-v1",
  "targetCrs": "EPSG:32649",
  "resolution": 1.0,
  "rpcHeightMode": "CONSTANT",
  "rpcHeight": 0,
  "roi": null,
  "sourceLayer": "INTENSITY",
  "speckle": {"enabled": false, "method": "LEE", "windowSize": 5},
  "features": {"enabled": false, "metrics": ["statistics"], "windowSize": 3},
  "saliency": {"enabled": false, "source": "INTENSITY", "windowSize": 9, "threshold": 0.95}
}
```

### 任务 2：实现窗口化 SLC I/Q 解码和派生图层

**文件：**

- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf3_slc.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_slc.py`

**接口：**

```python
read_slc_window(path: Path, window: Window) -> np.ndarray
derive_slc_layers(iq: np.ndarray, epsilon: float = 1e-6) -> dict[str, np.ndarray]
```

- [ ] 使用 4×4 合成 TIFF 写失败测试，验证通道 1 为 I、通道 2 为 Q，并得到 `complex64`。
- [ ] 写数值测试，验证幅度 `sqrt(I**2 + Q**2)`、强度 `I**2 + Q**2`、相位 `atan2(Q, I)` 和对数强度。
- [ ] 写窗口边界测试，验证左上、中心、右下窗口不会越界，空窗口会抛出稳定错误。
- [ ] 实现 Rasterio window 读取、样本类型检查、有限值检查和派生图层计算。
- [ ] 所有整景写出逻辑使用 256×256 窗口，不在内存中保留完整复数影像。
- [ ] 运行 `pytest -q tests/test_gf3_slc.py`，确认解码值和窗口边界通过。

### 任务 3：实现 GF3 RPC 名义定位、投影、重采样和裁剪

**文件：**

- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf3_rpc.py`
- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf3_spatial.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_rpc.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_spatial.py`

- [ ] 用合成 RPC 写失败测试，验证 RPC 字段转换为 Rasterio 可接受的模型。
- [ ] 测试四角和中心点均返回有限经纬度，并拒绝无效或超出范围的目标 CRS。
- [ ] 测试固定高程只能为 `0`，非零高程和非 `CONSTANT` 模式返回参数错误。
- [ ] 实现目标网格估计、RPC 重采样和窗口化写出，复用现有 `reproject.py` 的 COG 约定。
- [ ] 实现经纬度 ROI 与像素 ROI 的交集检查；ROI 不相交时返回 `ROI_DOES_NOT_INTERSECT`。
- [ ] 保留 `navigationStatus=NOMINAL_RPC_NO_DEM`、`accuracyStatus=UNVALIDATED` 和 `heightMeters=0` 标签。
- [ ] 运行两个空间测试文件，确认输出 transform、CRS、宽高和 NoData 正确。

### 任务 4：接入相对预处理、去斑、特征和显著性

**文件：**

- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/gf3_processing.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_processing.py`
- 复用 `algorithms/features.py`、`algorithms/saliency.py` 和 `operators/cog.py`；只有当窗口化调用暴露明确兼容性问题时，才在本任务范围内修改对应函数并补充回归测试

- [ ] 写相对预处理测试：浮点转换、无效值、对数变换、常量影像和全无效窗口。
- [ ] 实现可选 Lee/Frost/Gamma-MAP 去斑，验证窗口必须是 3–255 的奇数，并保留原始强度。
- [ ] 调用现有统计、梯度和局部方差算法，改为窗口化计算并保留 NoData。
- [ ] 调用现有局部对比度显著性算法，输出分数栅格和候选掩膜；CFAR 不纳入本次首版范围。
- [ ] 禁止调用通用 `fusion.py` 生成真实 RGB；如提供伪彩色，明确使用幅度、强度、对数强度并标记 `PSEUDO_COLOR`。
- [ ] 运行 `pytest -q tests/test_gf3_processing.py tests/test_operators_core.py tests/test_operators_cog.py`。

### 任务 5：实现 GF3 单阶段 Worker 流水线和输出契约

**文件：**

- 新建 `rs-algorithm-worker/src/rs_algorithm_worker/gf3_sar_pipeline.py`
- 新建 `rs-algorithm-worker/tests/test_gf3_sar_pipeline.py`

**阶段顺序：**

```text
VALIDATE_GF3_SLC
  -> DECODE_SLC_AND_DERIVE_LAYERS
  -> RELATIVE_RADIOMETRIC_PREPROCESS
  -> APPLY_SPECKLE_OPTIONAL
  -> RPC_NOMINAL_GEOLOCATE
  -> REPROJECT_AND_CROP
  -> WRITE_GF3_STANDARD_COG
  -> EXTRACT_GF3_FEATURES_OPTIONAL
  -> DETECT_GF3_SALIENCY_OPTIONAL
```

- [ ] 写编排测试，打桩每个内部阶段，断言顺序、禁用选项不执行、标准 COG 只生成一次。
- [ ] 实现 `execute_gf3_product(payload, manifest) -> dict[str, Any]`，从授权 manifest 读取输入，从 output 目录写结果。
- [ ] 基础输出固定为 `GF3_SLC_STANDARD_COG`、`GF3_SLC_AMPLITUDE_COG` 和 `GF3_SLC_PROCESSING_SUMMARY_JSON`。
- [ ] 可选输出为 `GF3_SLC_PHASE_COG`、`GF3_SLC_FILTERED_COG`、`GF3_SLC_FEATURES_COG`、`GF3_SLC_FEATURES_SUMMARY_JSON`、`GF3_SLC_SALIENCY_SCORE_COG`、`GF3_SLC_SALIENCY_MASK_COG`、`GF3_SLC_SALIENCY_GEOJSON`、`GF3_SLC_SALIENCY_SUMMARY_JSON`。
- [ ] 使用现有窗口化 COG 写出和流式 SHA-256；临时 VRT、validation、native、grid 目录不作为最终 artifact。
- [ ] 在 summary JSON 中记录输入摘要、处理参数、相对辐射限制、RPC 无 DEM 限制、输出尺寸和有效像元比例。
- [ ] 运行 `pytest -q tests/test_gf3_sar_pipeline.py`，确认 artifact 类型、逻辑 URI、大小和校验值正确。

### 任务 6：接入 Worker registry、Java profile 和数据库迁移

**文件：**

- 修改 `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- 修改 `rs-algorithm-worker/tests/test_registry.py`
- 新建 `script/sql/migrations/V20260903_01__seed_gf3_sar_slc_profile.sql`
- 修改相关迁移脚本测试（若测试自动扫描迁移，无需增加 Java 业务代码）

- [ ] 写 registry RED 测试：GF3 三文件 manifest 必须返回 `GF-3-SAR-SLC/v1` 候选。
- [ ] 将 GF3 识别放在 FY3H 通用 fallback 之前，避免 `.tiff + .xml + .rpc` 被误判为其他栅格产品。
- [ ] 将 `GF-3-SAR-SLC` 和 `STANDARDIZE_GF3_SAR_SLC` 精确路由到 `execute_gf3_product`。
- [ ] 保留 GF6 Browse 的后端兼容分支，但不让其识别 GF3。
- [ ] 新增 profile seed：`GF-3-SAR-SLC`、`v1`、`RASTER_GRID`、`SUPPORTED`。
- [ ] 验证 Java `StageOutputContractValidator` 能接受 GF3 的 final artifact 列表；没有必要新增 GF3 专用 Controller。
- [ ] 运行 `pytest -q tests/test_registry.py tests/test_cli.py` 和多源 Java 模块的迁移/契约测试。

### 任务 7：增加前端 GF3 产品模板和参数闭环

**文件：**

- 修改 `rsimageprocess-view/src/features/multisource/productTemplates.ts`
- 修改 `rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 修改 `rsimageprocess-view/src/features/multisource/pipelineDefinitions.ts`
- 修改 `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 新建 `rsimageprocess-view/src/features/multisource/gf3SarFiles.ts`
- 新建 `rsimageprocess-view/src/features/multisource/gf3SarParameters.ts`
- 新建 `rsimageprocess-view/src/features/multisource/Gf3SarParameterDrawer.vue`
- 新建对应的 `*.test.ts` 测试文件

- [ ] 增加角色和槽位：`SLC_TIFF`、`METADATA_XML`、`RPC`，以及可选 `BROWSE_IMAGE`、`INTENSITY_TIFF`、`INTENSITY_RPC`。
- [ ] 前端文件校验必须识别 `.meta.xml` 和 `.rpc`，检查基名、重复角色和可选文件组合。
- [ ] 增加 `GF3_SAR_SLC_TEMPLATE`，产品代码和版本必须与 Worker 完全一致。
- [ ] 增加 GF3 参数表单和快照构造，前端校验规则与 Worker 参数白名单一致。
- [ ] GF3 流程只暴露预处理、RPC 定位/投影、裁剪、特征和显著性；不显示光学波段选择和真实 RGB 融合。
- [ ] 增加阶段中文标签：`STANDARDIZE_GF3_SAR_SLC -> 处理 GF-3 SAR SLC 产品`。
- [ ] 显示三项限制：相对辐射、无 DEM 名义 RPC、显著性仅为候选结果。
- [ ] 确认现有上传、预检、创建任务、轮询、取消、重试、ZIP 下载和 artifact 下载无需新增接口。
- [ ] 运行 `pnpm exec vitest run src/features/multisource/gf3SarFiles.test.ts src/features/multisource/gf3SarParameters.test.ts`，再运行 `pnpm typecheck`。

### 任务 8：使用当前真实数据做小范围联调

**输入目录：**

`D:\Lwy_project_space\RSImageProcess\otherfile\GF3_MDJ_SL_002104_E114.4_N30.5_20170102_L1A_VV_L10002088121`

- [ ] 只读检查真实 XML、TIFF 标签和 RPC，不改动原始目录。
- [ ] 生成授权 manifest，使用三件必需文件进行 Worker inspect 和 build-plan。
- [ ] 选择武汉附近的小 ROI，建议先用 256×256 或更小窗口，验证解码、RPC 输出范围和 COG 可读性。
- [ ] 检查 summary 中包含 `complexSource=true`、`VV`、`RELATIVE_ONLY`、`NOMINAL_RPC_NO_DEM` 和 `UNVALIDATED`。
- [ ] 检查输出 COG 的 CRS、transform、尺寸、NoData、压缩、内部瓦片和 overview。
- [ ] 检查显著性 GeoJSON 不包含本地路径，结果属性包含分数和 `CANDIDATE_ONLY`。
- [ ] 不执行整景 1.5 GB 生产处理，不把生成结果写入代码仓库。

### 任务 9：最终回归和交付检查

- [ ] 运行 GF3 Worker 专项测试和现有 Worker 回归测试，分别记录通过、跳过和失败数量。
- [ ] 使用 Maven 执行 `htht-modules/htht-multisource`、`htht-admin` 相关测试，确认 profile、stage、artifact 契约兼容。
- [ ] 运行前端 multisource 测试、`pnpm typecheck` 和 `pnpm build`。
- [ ] 在 JavaRSproject 和 rsimageprocess-view 中分别执行 `git diff --check`，确认无路径泄露、无临时文件和无无关改动。
- [ ] 核对前端从上传到 ZIP 下载的完整闭环，并核对失败提示、取消、重试和 ROI 错误提示。
- [ ] 只有专项测试、真实小 ROI 联调和前后端闭环均通过后，才将 GF3 标记为“可用”；绝对定标和 DEM 正射仍标记为未支持。

## 预期交付结果

完成后，用户可以在前端选择 GF3 SAR SLC，上传当前数据的 TIFF/XML/RPC 文件，配置目标投影、ROI、去斑、特征和显著性参数，提交任务并获得标准强度/幅度产品、可选相位/特征/显著性 COG、GeoJSON 和 JSON 摘要。

该版本的定位是“单景 VV SLC 工程处理版本”：可以完成数据解码、相对预处理、名义地理定位、裁剪、特征和候选目标分析，但不会把当前数据包装成经过绝对定标、DEM 正射、多极化融合或测绘精度验证的产品。
