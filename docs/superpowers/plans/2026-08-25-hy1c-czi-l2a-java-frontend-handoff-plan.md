# HY-1C CZI L2A Java 阶段产物与前端对接实施计划

> **供执行人员使用：** 按任务逐项实施并勾选。使用 Codex/Superpowers 时，必须使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans`，每个任务完成后先运行本任务测试再提交。

**目标：** 完成 Java 管理的 L2A 阶段产物生命周期和剩余 Python Worker 阶段，提供稳定 REST API，并使 Vue 前端能创建、观察、重试、取消和下载 HY-1C CZI L2A 任务。

**架构：** `plan-v2` 的每个阶段声明原始输入、上游输入、输出类型、相对路径与保留级别。Worker 仅写本次执行的临时输出目录；Java 校验回执后把文件原子提升到任务专属目录，持久化逻辑 URI 与溯源信息，再向下游授权其声明的文件。前端只提交公开参数、展示状态及下载最终 `output` 产物，不接触路径和 `work` 中间文件。

**技术栈：** Java 17、Spring Boot 2.7、MyBatis-Plus、PostgreSQL JSONB、Python 3.11+、h5py、NumPy、rasterio/GDAL、Vue 3、TypeScript、Element Plus、JUnit 5、pytest。

---

## 0. 基线、范围和约束

本计划已于 `2026-08-25` 合并到 `main`（合并提交 `fd3ed90`）；`hy1c-czi-l2a-v1` 分支和临时工作区已删除。后续开发直接在主仓库 `D:\Lwy_project_space\RSImageProcess\JavaRSproject` 的 `main` 分支进行。真实输入包为：

```text
D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L2A_20260715T024305_20260715T024401_41109_10
```

不得修改或提交其中的 HDF5、XML、JPG。

| 提交 | 已完成内容 |
| --- | --- |
| `9606656` | `ProcessingStage` 已有 `inputs`、`outputs`、`parameterSnapshot`；Java 可校验/解析 `plan-v2`。 |
| `ef34f7b` | 已审查 L2A fixture：CZI Rrc 路径、导航路径/缩放、质量标志名与位、默认掩膜位。 |
| `75eeabb` | 公开 L2A 参数解析、网格参数约束和 `AUTO_UTM` CRS 解析。 |
| `f03fb01` | 薄产品适配器、固定六阶段 `plan-v2` DAG 与可选算法分支。 |
| `0db1e7e` | L2A 波段缩放/无效值解码和 L2 标志掩膜，带合成 HDF5 测试。 |

当前未完成且不能视为安全生产实现的部分：

- `DispatchNextStageService` 仍把所有 `candidate.files()` 传给每一个 Worker 阶段，且使用任务级输出 URI。
- `ArtifactRegistrationService` 仍持久化 Worker URI，没有提升文件到任务专属目录。
- `Artifact` 没有 `relativePath` 与 `retention`，没有 `ArtifactStorage` 和 `LocalTaskArtifactStorage`。
- Worker 没有网格化、裁剪、COG、特征、融合、显著性阶段实现。
- Vue 仅支持 GF-6 WFV 的输入角色和阶段标签。

### 已同步的实际进度

截至合并时，以下基础能力已进入 `main`：

- Java 已能持久化 `plan-v2` 的阶段输入、输出和参数快照，并在建计划时校验依赖、重复输出和不安全相对路径。
- HY-1C CZI L2A 的 HDF5 路径、`Slope/Offset`、FillValue、有效范围和默认质量标志掩膜均已从真实样例和提供资料审查后写入 fixture/适配器。
- 已完成 L2A 参数校验、`AUTO_UTM` 解析、固定六阶段 DAG 以及可选特征/融合/显著性分支声明。
- Worker 已完成波段数值解码和 L2 质量标志掩膜；尚未完成网格化、裁剪、标准 COG 和可选算法的阶段执行。

因此，本计划中的“任务一”仍是后续开发的第一项，不应重复实现以上基础能力。

## 1. 必须保持的跨端契约

### 1.1 阶段输入/输出声明

`APPLY_L2_FLAGS` 的含义必须是：输入上游 `DECODED_CZI_RASTER`（角色 `decoded`）和原始 `MAIN`（角色 `main`）；输出中间 `QUALITY_MASKED_CZI_RASTER` 和 `VALID_MASK`。推荐计划 JSON：

```json
{
  "stageKey": "APPLY_L2_FLAGS",
  "dependsOnStageKeys": ["DECODE_SCALE_AND_NODATA"],
  "inputs": [
    {"key": "decoded", "source": "UPSTREAM_ARTIFACT", "artifactType": "DECODED_CZI_RASTER", "required": true},
    {"key": "main", "source": "RAW_INPUT", "artifactType": "MAIN", "required": true}
  ],
  "outputs": [
    {"artifactType": "QUALITY_MASKED_CZI_RASTER", "relativePath": "quality/masked-czi-raster.npz", "retention": "INTERMEDIATE"},
    {"artifactType": "VALID_MASK", "relativePath": "quality/valid-mask.npz", "retention": "INTERMEDIATE"}
  ]
}
```

`RAW_INPUT.artifactType` 表示上传候选文件角色；`UPSTREAM_ARTIFACT.artifactType` 表示已持久化 `Artifact.type`。上游产物必须由 `dependsOnStageKeys` 中阶段产生；`FINAL` 不能再被下游消费。输入顺序以 `ProcessingStage.inputs()` 为准，不能依赖扫描顺序。

### 1.2 Worker 回执和安全边界

Worker 只允许在 manifest 指定的 `<work-root>/<execution-id>/output` 下按声明相对路径写文件，回执不可包含绝对路径：

```json
{
  "workerId": "worker-local",
  "status": "SUCCEEDED",
  "artifacts": [{
    "uri": "rs://output/7/task-01/stages/APPLY_L2_FLAGS/quality/masked-czi-raster.npz",
    "type": "QUALITY_MASKED_CZI_RASTER",
    "sizeBytes": 123456,
    "checksum": "sha256:<64位小写十六进制>"
  }]
}
```

遗漏声明产物、额外产物、反斜杠、绝对路径、`..`、越界文件、类型/路径不匹配、大小或 SHA-256 不一致，都必须使阶段终态失败。

### 1.3 Java 持久目录

任务 `101`、阶段 `APPLY_L2_FLAGS`、第 `1` 次尝试的示例：

```text
<artifact-root>/101/work/APPLY_L2_FLAGS/1/quality/masked-czi-raster.npz
<artifact-root>/101/work/APPLY_L2_FLAGS/1/quality/valid-mask.npz
<artifact-root>/101/output/standard/czi-l2a.tif
<artifact-root>/101/output/standard/czi-l2a.json
```

`INTERMEDIATE` 写入 `work`，`FINAL` 写入 `output`。提升流程为复制到同目录 `.tmp`、关闭并校验大小和 SHA-256、再原子移动。重试只能写自身 `<stage-key>/<attempt>/<relative-path>`，绝不可覆盖其他任务或阶段。

### 1.4 前端公开参数

前端应提交以下结构，不能计算数据相关系数，也不能发送路径、质量位整数、阶段依赖或 Worker 配置：

```json
{
  "schemaVersion": "1",
  "operation": "STANDARDIZE_L2A",
  "bands": ["B460", "B560", "B650", "B825"],
  "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
  "roiWgs84": {"minLon": 120.0, "minLat": 30.0, "maxLon": 121.0, "maxLat": 31.0},
  "quality": {"policy": "default"},
  "algorithms": {
    "features": {"enabled": false, "metrics": ["statistics", "gradient", "local_variance"], "windowSize": 3},
    "fusion": {"enabled": false, "red": "B650", "green": "B560", "blue": "B460", "lowPercentile": 2.0, "highPercentile": 98.0},
    "saliency": {"enabled": false, "source": "B560", "threshold": 0.5, "windowSize": 5}
  }
}
```

## 2. 任务一：持久化并提升阶段产物

**文件：**

- 新建 `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ArtifactStorage.java`
- 修改 `.../domain/model/Artifact.java`、`.../application/usecase/service/ArtifactRegistrationService.java`
- 修改 `.../infrastructure/persistence/entity/ArtifactEntity.java`、`.../infrastructure/persistence/mapping/TaskPersistenceMapper.java`
- 新建 `htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactStorage.java`
- 新建 `script/sql/migrations/V20260825_01__add_multisource_stage_artifact_contract.sql`
- 新增 `ArtifactRegistrationServiceTest.java`、`LocalTaskArtifactStorageTest.java`

- [ ] **步骤 1：写红色测试。** 覆盖中间产物提升后返回 `rs://artifact/101/work/APPLY_L2_FLAGS/1/quality/masked.npz`，并有 `relativePath=quality/masked.npz`、`retention=INTERMEDIATE`。同时覆盖 `../escape`、其他任务/执行 URI、源文件哈希变化、未声明类型或路径、未验证源文件、同一重试但字节不同的拒绝路径。

- [ ] **步骤 2：确认测试失败。**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am '-Dtest=LocalTaskArtifactStorageTest,ArtifactRegistrationServiceTest' -DfailIfNoTests=false test
```

- [ ] **步骤 3：建立领域端口和模型。** `ArtifactStorage.promote(...)` 必须接收任务/阶段/执行 ID、阶段键、尝试次数、阶段输出 URI、Worker 候选项和 `StageOutput`。`Artifact` 新增 `relativePath`、`StageOutput.Retention retention`，构造时校验正大小、非空 type/checksum/path、安全斜杠相对路径、非空 retention。只在旧 GF-6 测试确有需要时保留兼容构造器。

- [ ] **步骤 4：实现 `LocalTaskArtifactStorage`。** 只能在 `<work-root>/<execution-id>/output/<候选相对路径>` 找源文件；流式计算哈希，对比 `ArtifactCandidate.checksum`；路径规范化后确保位于 `artifactRoot`；写 `<target>.tmp`，复核大小/哈希，移动到目标。仅返回 `rs://artifact/{task-id}/work/...` 或 `rs://artifact/{task-id}/output/...`。实现 `toAuthorizedInputs`，为提升产物生成 `sourceType=TASK_ARTIFACT` 的 `InputAsset`。

- [ ] **步骤 5：加数据库迁移和往返测试。**

```sql
ALTER TABLE rs_artifact ADD COLUMN relative_path varchar(2048), ADD COLUMN retention varchar(32);
UPDATE rs_artifact SET relative_path = '', retention = 'FINAL'
WHERE relative_path IS NULL OR retention IS NULL;
ALTER TABLE rs_artifact ALTER COLUMN relative_path SET NOT NULL,
  ALTER COLUMN retention SET NOT NULL,
  ADD CONSTRAINT ck_rs_artifact_retention CHECK (retention IN ('INTERMEDIATE', 'FINAL'));
```

不要将提升产物写入 `rs_task_input_asset`，它们不是原始候选文件。

- [ ] **步骤 6：接入注册服务。** `ArtifactRegistrationService` 先确保回执与 `StageOutput` 精确一一匹配，再调用 `ArtifactStorage.promote`，只持久化提升后的产物。提升失败时不保存任何产物，执行应失败。

- [ ] **步骤 7：测试并提交。**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am '-Dtest=LocalTaskArtifactStorageTest,ArtifactRegistrationServiceTest,RepositoryMappingTest,MigrationScriptTest' -DfailIfNoTests=false test
git add htht-admin htht-modules/htht-multisource script/sql/migrations
git commit -m "feat: promote and persist multisource stage artifacts"
```

## 3. 任务二：按声明派发输入和最小授权

**文件：** 修改 `DispatchNextStageService.java`、`StageExecutionRequest.java`、`WorkerContractCodec.java`、`LocalWorkerAssetManifestWriter.java`、`SysFileAssetStorageAdapter.java`、`MultiSourceAdapterConfiguration.java`；补充调度、编码和 manifest 测试。

- [ ] **步骤 1：写红色测试。** `APPLY_L2_FLAGS` 的派发请求仅含 `decoded` 和 `main`，来源类型分别是 `TASK_ARTIFACT` 和 `SYS_FILE`。缺失 `DECODED_CZI_RASTER` 时 Worker 调用次数为零，阶段失败码是 `MISSING_UPSTREAM_ARTIFACT`。

- [ ] **步骤 2：实现 `resolveInputs`。** `RAW_INPUT` 精确选择角色匹配、ordinal 为 `0` 的文件，缺失报 `MISSING_RAW_INPUT`；`UPSTREAM_ARTIFACT` 精确选择被声明依赖阶段生产的同类型产物，缺失报 `MISSING_UPSTREAM_ARTIFACT`。提升产物经 `ArtifactStorage.toAuthorizedInputs` 转换，输入顺序保持声明顺序。使用 `new StorageUri(task.outputUri().value() + "/stages/" + planStage.stageKey())` 作为阶段 URI；重试由 `ExecutionId` 获得独立临时目录。

- [ ] **步骤 3：传递阶段契约。** `StageExecutionRequest` 新增 `List<StageOutput> outputs`。`WorkerContractCodec` 编码 outputs 与本阶段参数快照；manifest writer 只能解析 request 输入，不能从输出 URI 推导额外可读路径。

- [ ] **步骤 4：限制资产解析。** 同时支持 `rs://sys-file/{id}` 和 `rs://artifact/{task-id}/...`；后者必须任务 ID 相同并出现在当前 execution 的 grant scope，任何未授权 URI 必须拒绝。

- [ ] **步骤 5：测试并提交。**

```powershell
mvn -pl htht-admin,htht-modules/htht-multisource -am '-Dtest=DispatchNextStageUseCaseTest,WorkerContractCodecTest,LocalWorkerAssetManifestWriterTest' -DfailIfNoTests=false test
git add htht-admin htht-modules/htht-multisource
git commit -m "feat: dispatch multisource stages with declared artifacts"
```

## 4. 任务三：补齐 HY-1C CZI L2A Worker

**文件：** 修改 `rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py`、`registry.py`、`product_adapters/hy1c_czi_l2a.py`；修改 `tests/test_hy1c_czi_l2a.py`、`tests/test_registry.py`。

- [ ] **步骤 1：写红色测试。** `WRITE_STANDARD_COG` 结果为 `EPSG:32651`、4 波段、描述为 `B460/B560/B650/B825`、有 NoData。覆盖不相交 ROI、缺失声明产物、未知阶段码、额外回执产物、逻辑 URI 越出阶段输出 URI。

- [ ] **步骤 2：实现固定阶段。**

  - `GEOLOCATE_AND_GRID`：按已审查的 `Slope/Offset` 解码经纬度，按配置行窗口读取 HDF5，调用公共最近邻条带网格化算子，一次解析 `AUTO_UTM`，保留波段和有效掩膜。
  - `CROP_OPTIONAL`：无 ROI 时透传；有 ROI 时 WGS84 转目标 CRS 后调用公共裁剪，不相交即拒绝。
  - `WRITE_STANDARD_COG`：调用 `write_cog`，写 `STANDARD_METADATA_JSON`，包含映射版本、请求/解析 CRS、分辨率、重采样、波段、单位、NoData、有效像元数、源哈希、质量策略、参数快照。

不得同时读取真实场景的全部原始波段、flags、导航数组；必须记录 rows-per-window。L2A `Rrc` 为输入产品变量，不得引入没有权威依据的 L1B 定标。

- [ ] **步骤 3：实现可选分支。** `EXTRACT_CZI_FEATURES` 调用 `extract_features`，仅输出启用指标和 `statistics-v1` 元数据；`FUSE_CZI_BANDS` 只使用选中 RGB 调用 `fuse_bands`，标记 `displayProduct=true`、`method=percentile-rgb-v1`；`DETECT_CZI_SALIENCY` 使用一条标准波段或显式声明特征输入调用 `detect_saliency`，在原标准网格输出分数/掩膜，标记 `method=contrast-v1`。三类算法均不能修改 `STANDARD_COG`，不能添加未经审查的光谱指数、增强或分类。

- [ ] **步骤 4：接入 registry。** `execute-stage` 按 `profileCode`、`profileVersion`、`packageId` 路由，不按扩展名猜产品；只能读取 manifest 输入；只能向 manifest 输出目录写声明产物；回执返回 type、逻辑 URI、sizeBytes、sha256。

- [ ] **步骤 5：回归和提交。**

```powershell
cd rs-algorithm-worker
$env:PYTHONPATH=(Join-Path (Get-Location) 'src')
$env:MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L2A_20260715T024305_20260715T024401_41109_10'
.\.venv\Scripts\python.exe -m pytest -q
git add src tests
git commit -m "feat: execute HY-1C CZI L2A processing stages"
```

## 5. 任务四：冻结 REST API 再开始前端

**文件：** 修改 `ProcessingTaskController.java`、`ProcessingExecutionController.java`、`TaskDetailResponse.java`、`ExecutionProgressResponse.java`、`DispatchStageResponse.java`，在 `htht-admin/src/test/java/com/htht/web/controller/multisource/` 补控制器测试。

- [ ] **步骤 1：写响应契约测试。** JSON 必须包含 `stageCode=APPLY_L2_FLAGS`、`errorCode=MISSING_UPSTREAM_ARTIFACT`、`retention=FINAL`、`downloadable=true`；不得包含 `D:\\`、`workRoot`、`artifactRoot`、`asset-manifest`。

- [ ] **步骤 2：扩展响应 DTO。** 阶段状态包含 key/code/status/attempt/executionId/开始结束时间/errorCode/errorMessage；产物包含 stageId/type/逻辑 URI/checksum/sizeBytes/retention/downloadable。`downloadable` 仅对经任务授权的 FINAL 为 true。

- [ ] **步骤 3：增加最终产物下载。** 添加 `GET /api/multisource/processing-tasks/{taskId}/artifacts/{type}/download`，校验任务归属，只允许最终产物，流式读取已提升的任务文件，使用安全文件名；`result.zip` 只能打包最终产物。

- [ ] **步骤 4：测试并提交。**

```powershell
mvn -pl htht-admin -am '-Dtest=ProcessingTaskControllerTest,ProcessingExecutionControllerTest' -DfailIfNoTests=false test
git add htht-admin/src/main htht-admin/src/test
git commit -m "feat: expose multisource task progress and final artifacts"
```

## 6. 任务五：Vue 改为多产品模板

**仓库：** `D:\Lwy_project_space\RSImageProcess\rsimageprocess-view`

**文件：** 修改 `src/features/multisource/productTemplates.ts`、`useProcessingTask.ts`、`src/api/multiSourceSatellite.ts`、`src/components/LeftPanel/MultiSourceSatelliteProcess.vue`；新建 `src/features/multisource/hy1cCziL2aParameters.ts` 和既有测试目录中的测试。

- [ ] **步骤 1：模板及参数测试。** `buildHy1cCziL2aSnapshot(defaultL2aForm())` 必须输出第 1.4 节 JSON。模板必须要求 `MAIN:.h5`、`METADATA:.xml`、`BROWSE:.jpg/.jpeg`，不得复用 GF-6 的 `BROWSE_IMAGE/GEOREFERENCE`。

- [ ] **步骤 2：实现模板。** 增加 `HY1C_CZI_L2A_TEMPLATE`：`id=hy1c-czi-l2a-v1`、`label=HY-1C CZI L2A`、`productCode=HY-1C-CZI`、`profileVersion=L2A-v1`，三个上传槽为 L2A HDF5、产品 XML、浏览 JPG。选择器必须支持 GF-6 和 HY-1C；文件校验按当前模板，不能强制跨产品同基名。

- [ ] **步骤 3：实现参数页。** 提供选择波段、目标 CRS（默认 `AUTO_UTM` 或显式投影坐标系）、正数米级分辨率、可选 WGS84 ROI、可选特征/融合/显著性。科学重采样固定显示 `nearest`；融合 RGB 必须均已选中；显著性源必须已选中；算法默认关闭。

- [ ] **步骤 4：更新进度和下载 UI。** 更新 TypeScript 类型以含 stageCode、时间、错误、sizeBytes、retention、downloadable。使用如下标签映射：

```ts
const L2A_STAGE_LABELS: Record<string, string> = {
  VALIDATE_SOURCE: "校验 L2A 产品", DECODE_SCALE_AND_NODATA: "解码波段与无效值",
  APPLY_L2_FLAGS: "应用 L2 质量标志", GEOLOCATE_AND_GRID: "地理定位与网格化",
  CROP_OPTIONAL: "按范围裁剪", WRITE_STANDARD_COG: "生成标准 COG",
  EXTRACT_CZI_FEATURES: "提取特征", FUSE_CZI_BANDS: "生成显示融合",
  DETECT_CZI_SALIENCY: "生成显著性结果"
};
```

只为失败/可恢复阶段显示重试；原样显示服务端错误码和消息；只在 `downloadable===true` 显示单产物下载，同时保留最终 ZIP。

- [ ] **步骤 5：验证并提交。**

```powershell
pnpm typecheck
pnpm build
git add src/api src/features/multisource src/components/LeftPanel/MultiSourceSatelliteProcess.vue
git commit -m "feat: add HY-1C CZI L2A processing task UI"
```

与本功能无关的地图/鉴权错误单独记录，不得用 `any`、`skipLibCheck` 或关闭 typecheck 掩盖。

## 7. 任务六：端到端验收和部署

- [ ] **步骤 1：增加受环境控制的 Java 集成测试。** 设置 `MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT` 和明确 Worker/Python 配置时，使用真实样例处理到 `SUCCEEDED`。断言每个 manifest 仅有声明输入、最终包含 `STANDARD_COG` 和 `STANDARD_METADATA_JSON`。变量已设置但数据错误时必须失败，不能静默跳过。

- [ ] **步骤 2：配置隔离运行目录。** 修改 `MultiSourceWorkerProperties.java`、`MultiSourceAdapterConfiguration.java` 和 `docs/deployment/multisource-backend-deployment.md`，要求：

```yaml
multisource:
  worker:
    python-executable: D:/.../python.exe
    command-module: rs_algorithm_worker.cli
    work-root: D:/rs-runtime/worker-work
    artifact-root: D:/rs-runtime/task-artifacts
    l2a-window-rows: 128
```

启动时创建/规范化目录；若 `artifact-root` 与上传根相同、在其下、或包含上传根，则拒绝启动。记录 Python 依赖锁定和已审查 L2A 格式资料。

- [ ] **步骤 3：真实数据验收。** 核验：每个 manifest 只有声明资产；Worker/API 无本地真实路径；中间产物位于 `work`；最终 COG/JSON 位于 `output`；`gdalinfo -json` 显示正确 CRS、正尺寸、四个波段名和 NoData；前端可显示进度并下载 COG/ZIP；开启融合/显著性不会改写 `STANDARD_COG`。

- [ ] **步骤 4：全量回归并提交。**

```powershell
cd D:\Lwy_project_space\RSImageProcess\.worktrees\hy1c-czi-l2a-v1
mvn -pl htht-modules/htht-multisource,htht-admin -am test
cd rs-algorithm-worker
$env:PYTHONPATH=(Join-Path (Get-Location) 'src')
$env:MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L2A_20260715T024305_20260715T024401_41109_10'
.\.venv\Scripts\python.exe -m pytest -q
git add htht-admin htht-modules/htht-multisource rs-algorithm-worker docs/deployment script/sql/migrations
git commit -m "test: verify HY-1C CZI L2A Java and frontend handoff"
```

## 8. 完成定义与执行顺序

- [ ] 每个 L2A 阶段只获得声明的 `RAW_INPUT`、`UPSTREAM_ARTIFACT`。
- [ ] Worker 既不能读任意路径，也不能决定最终存储路径。
- [ ] 产物提升有哈希验证、任务隔离、重试隔离和 retention/path 溯源。
- [ ] 中间产物不提供下载；最终产物可授权列出、单独下载、写入 `result.zip`。
- [ ] 前端支持 HY-1C CZI L2A 三种必需文件角色，提交准确参数，展示阶段和下载最终产物。
- [ ] Java、Worker、前端测试/构建通过；仅未配置环境变量时的真实数据集成测试可跳过。

执行顺序固定为任务一到任务六：任务二依赖任务一的持久化产物；任务三依赖任务二将上游产物授权给 Worker；任务四冻结 API 后才实施任务五；任务六只在前面层次的单测通过后执行。每个任务独立提交。任何没有已审查 fixture 或 HY-1CD 数据格式说明支撑的科学参数，都不得猜测，必须记录为待确认项。
