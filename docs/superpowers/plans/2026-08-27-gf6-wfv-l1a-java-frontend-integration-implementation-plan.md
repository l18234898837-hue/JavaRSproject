# 已废弃：GF6 WFV L1A Java 与前端闭环对接实施计划

> **状态：已废弃（2026-08-27）。不得继续执行。**
>
> **替代计划：** `docs/superpowers/plans/2026-08-27-gf6-wfv-l1a-frontend-replacement-implementation-plan.md`
>
> **废弃原因：** 本计划采用“保留 Browse 三文件入口，同时增加 L1A 入口”的前提；用户已明确决定前端以 GF6 WFV L1A 完全替代 Browse 三文件流程。新计划将前端任务限定为单一 L1A 工作流，并把 Java 作为已定义的接口依赖和联调门槛，不再让前端开发人员同时承担后端重构。

---

# 历史内容（仅供追溯）

> **给执行开发人员：** 本计划用于在独立分支中逐项执行。每个步骤都必须先写失败测试，再实现最小改动，最后运行本步骤的验证命令并提交。除非本计划明确要求，不得修改已经通过专项测试的 GF6 Worker 算法实现。

**目标：** 将已完成的 GF6 WFV L1A Worker 处理链接入 Java 多源任务系统和 Vue 前端，完成上传、预检、创建任务、启动、阶段轮询、失败重试、取消、最终产物下载的完整闭环。

**架构：** Java 多源模块负责产品 profile、任务参数快照、阶段 DAG、原始文件授权、上游产物绑定、产物登记和下载权限；Worker 继续负责拼接、RPC 名义重采样、定标、裁剪、特征、融合和显著性。Vue 前端通过现有多源 API 调用 Java，不直接暴露本地路径、Worker manifest、RPC 系数或内部文件 URI。

**技术栈：** Java 17、Spring Boot、MyBatis、Flyway、JUnit 5、AssertJ、Vue 3、TypeScript、Element Plus、Vitest（若项目已配置）、pnpm、现有 `multiSourceSatellite.ts` API 封装。

**依据：** 已完成的 Worker 计划 `docs/superpowers/plans/2026-08-27-gf6-wfv-l1a-engineering-processing-implementation-plan.md`、当前 Worker 提交 `37961cc`、Java 多源任务生命周期实现、前端 `src/features/multisource` 现有 Browse 流程。

## 一、执行边界与固定约束

1. Java 产品标识固定为 `GF-6-WFV-L1A/v1`，不得复用 `GF-6-WFV-BROWSE/v1`。
2. GF6 L1A 必须上传三块 TIFF、XML、RPB、NAV、TIL；SHP/DBF/SHX 为可选足迹文件，三者若上传必须成套校验。
3. 前端参数快照固定使用 `schemaVersion="gf6-wfv-l1a-v1"`，并传递 `targetCrs`、`resolution`、`rpcHeightMode`、`rpcHeight`、`roi`、`bands`、`features`、`fusion`、`saliency`。
4. `rpcHeightMode` 只允许 `CONSTANT`；默认高度为 `0` 米。页面必须显示“无 DEM 名义 RPC，定位精度未验证”的限制。
5. 显著性结果必须显示 `CANDIDATE_ONLY`，不得显示为目标识别、变化检测或官方质量评价。
6. Java 返回给前端的 artifact 只能包含逻辑 URI、类型、大小、SHA-256、是否可下载和保留级别；不得返回物理本地路径。
7. 只有 `FINAL` 产物可以下载；中间拼接 TIFF、临时 VRT、Worker manifest、RPC 系数不进入下载包。
8. 现有 GF6 Browse、FY-3H、FY-4B、HY-1C、HY-2D 的行为必须保持不变。
9. 本计划不包含 DEM 正射校正、控制点配准、绝对定位精度认证、Java 算法重写或前端地图专题展示。

## 二、目标闭环

```text
用户选择 10 个文件槽位
  -> 前端扩展名、同产品基名、必选/可选文件校验
  -> 上传 SYS_FILE
  -> Java 预检
  -> Worker INSPECT
  -> 返回唯一 GF-6-WFV-L1A/v1 candidate
  -> 前端提交参数快照
  -> Java 创建任务并冻结快照
  -> Worker BUILD_PLAN
  -> Java 按阶段声明解析输入和授权
  -> 依次执行 Worker 阶段并登记 artifact
  -> 前端轮询任务状态
  -> 显示阶段、限制、失败原因和重试入口
  -> 仅任务所有者下载最终 ZIP
```

固定阶段及依赖：

```text
INSPECT_GF6_WFV_L1A
  -> MOSAIC_GF6_TILES
  -> CALIBRATE_GF6_WFV
  -> BUILD_GF6_RPC_GRID
  -> ALIGN_GF6_BANDS
  -> CROP_GF6_RASTER
  -> WRITE_GF6_STANDARD_COG
       -> EXTRACT_GF6_FEATURES（可选）
       -> FUSE_GF6_BANDS（可选）
       -> DETECT_GF6_SALIENCY（可选）
```

最终 artifact 类型：

- 固定：`GF6_STANDARD_COG`、`GF6_CALIBRATION_SUMMARY_JSON`、`GF6_NOMINAL_RPC_GRID_JSON`
- 可选：`GF6_FEATURES_COG`、`GF6_FEATURES_SUMMARY_JSON`
- 可选：`GF6_RGB_FUSION_COG`、`GF6_RGB_FUSION_JPEG`、`GF6_FUSION_SUMMARY_JSON`
- 可选：`GF6_SALIENCY_SCORE_COG`、`GF6_SALIENCY_MASK_COG`、`GF6_SALIENCY_SUMMARY_JSON`

## 三、分支、目录和交付约定

- Java/Worker 仓库：`D:/Lwy_project_space/RSImageProcess/JavaRSproject`
- 前端仓库：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view`
- 执行分支：两个仓库都使用 `feature/gf6-wfv-l1a-engineering`；若另一位开发人员必须再隔离，使用该分支创建自己的 worktree。
- Java 数据库迁移：`JavaRSproject/script/sql/migrations/`
- Java 模块：`JavaRSproject/htht-modules/htht-multisource/`
- Java 管理端：`JavaRSproject/htht-admin/`
- Worker 代码只读依赖：`JavaRSproject/rs-algorithm-worker/`
- 前端功能目录：`rsimageprocess-view/src/features/multisource/`
- 前端页面：`rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`

每个任务完成后提交一次，提交信息使用 `feat: integrate GF6 ...` 或 `test: cover GF6 ...`，不得提交临时数据、整景 TIFF、VRT、ZIP、日志和本地绝对路径。

---

## Task 1：冻结前后端公共产品与参数契约

**目的：** 让 Java、Worker、前端对 profile、角色、参数和 artifact 类型使用完全相同的字符串。

**文件：**

- 修改：`JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`（只核对并冻结公开字段，不改算法）
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProductProfile.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingStage.java`
- 新建：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/Gf6WfvL1aContract.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java`
- 新建：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/Gf6WfvL1aContractTest.java`

**固定接口：**

```java
public final class Gf6WfvL1aContract {
    public static final ProductProfileKey PROFILE = new ProductProfileKey("GF-6-WFV-L1A", "v1");
    public static final String PARAMETER_SCHEMA = "gf6-wfv-l1a-v1";
    public static List<String> requiredRawRoles();
    public static List<String> optionalRawRoles();
    public static List<String> finalArtifactTypes();
    public static void validateParameterSnapshot(String snapshot);
}
```

**步骤：**

- [ ] 先写测试，断言 profile key、7 个必需原始角色、3 个可选 SHP 角色、固定阶段键和最终 artifact 类型完全匹配。
- [ ] 写参数快照测试：缺字段、未知字段、非正分辨率、非 `CONSTANT` 高程、ROI 顺序错误、融合波段未选中、显著性 source 未选中必须稳定失败。
- [ ] 运行：`mvn -pl htht-modules/htht-multisource -Dtest=Gf6WfvL1aContractTest,ProcessingPlanValidatorTest test`，先确认测试失败。
- [ ] 实现契约常量和严格 JSON 校验；不得用字符串包含判断代替字段级解析。
- [ ] 再运行同一命令，确认通过。
- [ ] 提交：`test: freeze GF6 L1A public contract` 和 `feat: add GF6 L1A contract validator`。

**验收：** Java、前端和 Worker 使用同一 profile、角色、参数 schema、阶段键和 artifact 类型；Browse profile 的字符串没有被替换。

## Task 2：接入 Java 产品 profile、能力和数据库种子

**目的：** 让预检能识别 GF6 L1A，并在数据库中发布独立 profile。

**文件：**

- 修改：`JavaRSproject/script/sql/migrations/V20260828_01__seed_gf6_wfv_l1a_profile.sql`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProductProfile.java`
- 修改：`JavaRSproject/htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- 参考并按实际 mapper 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapper/*Profile*`
- 新建：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/Gf6WfvL1aProfileTest.java`

**步骤：**

- [ ] 先写 profile 注册测试，断言 `GF-6-WFV-L1A/v1` 存在、数据形状为 `RASTER_GRID`、状态为 `SUPPORTED`，且 Browse profile 仍存在。
- [ ] 先运行：`mvn -pl htht-modules/htht-multisource,htht-admin -am -Dtest=Gf6WfvL1aProfileTest test`，确认失败。
- [ ] 完善 SQL：固定能力声明包括拼接、定标、RPC、配准、裁剪和标准 COG；可选能力只包含特征、融合、显著性；声明 `NOMINAL_RPC_NO_DEM`。
- [ ] 将 profile/capability 查询绑定到既有配置，不复制一套新的任务服务。
- [ ] 运行 Flyway/模块测试；若本地没有数据库，至少运行 SQL 语法检查和 mapper 单测，并记录外部数据库验收命令。
- [ ] 提交：`feat: register GF6 WFV L1A profile`。

**验收：** 通过 Java 预检请求可以拿到唯一 L1A candidate；Browse 输入不会误路由到 L1A。

## Task 3：实现 Java 阶段 DAG、输入解析和 Worker 请求编码

**目的：** 将 Worker 返回的 `plan-v2` 阶段转换为 Java 可授权、可调度的阶段，并确保每个阶段只能读取声明的输入。

**文件：**

- 修改：`JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`（让 `build_plan` 输出完整 `plan-v2` 阶段输入、输出和参数快照）
- 修改：`JavaRSproject/rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py`（增加 `plan-v2` 输入输出契约测试；若测试文件尚未存在则新建）
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/StageInputResolver.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingPlan.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingStage.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerResultTranslator.java`
- 测试：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java`
- 测试：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`
- 新建：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/Gf6WfvL1aWorkerContractTest.java`

**阶段输入输出表：**

| 阶段 | 输入 | 输出 | 保留 |
|---|---|---|---|
| `INSPECT_GF6_WFV_L1A` | 7 个必需原始角色，SHP 可选 | candidate metadata | INTERMEDIATE |
| `MOSAIC_GF6_TILES` | 三个 TIFF、TIL、XML/RPB/NAV | `GF6_DN_MOSAIC_TIFF` | INTERMEDIATE |
| `CALIBRATE_GF6_WFV` | `GF6_DN_MOSAIC_TIFF`、XML | `GF6_CALIBRATED_RASTER`、`GF6_CALIBRATION_SUMMARY_JSON` | 前者 INTERMEDIATE，后者 FINAL |
| `BUILD_GF6_RPC_GRID` | XML、RPB、NAV | `GF6_NOMINAL_RPC_GRID_JSON` | FINAL |
| `ALIGN_GF6_BANDS` | calibrated raster、RPC grid | `GF6_ALIGNED_RASTER` | INTERMEDIATE |
| `CROP_GF6_RASTER` | aligned raster、RPC grid、参数 ROI | `GF6_CROPPED_RASTER` | INTERMEDIATE |
| `WRITE_GF6_STANDARD_COG` | cropped raster、calibration summary | `GF6_STANDARD_COG` | FINAL |
| `EXTRACT_GF6_FEATURES` | standard COG | feature COG/summary | FINAL |
| `FUSE_GF6_BANDS` | standard COG | RGB COG/JPEG/summary | FINAL |
| `DETECT_GF6_SALIENCY` | standard COG 或声明的 feature COG | score/mask/summary | FINAL |

**步骤：**

- [ ] 先运行现有 Worker 计划检查，确认当前 GF6 `build_plan` 缺少 `inputs`、`outputs`、`parameterSnapshot`；将该失败作为本任务的 RED 证据保存到任务记录，不得绕过它让 Java 猜测阶段输入。
- [ ] 为 Worker `plan-v2` 增加每个阶段的 `inputs`、`outputs`、`parameterSnapshot`；原始输入使用 `RAW_INPUT`，上游结果使用 `UPSTREAM_ARTIFACT`，输出路径只能是相对路径且保留级别明确。
- [ ] 先写输入解析测试：原始角色缺失、上游 artifact 不属于声明依赖、同一类型出现两个 artifact、跨任务 artifact 均必须失败。
- [ ] 先写编码测试：GF6 stage request 必须包含 profile、stageKey、parameterSnapshot、授权 grant、逻辑 output URI；响应只能接受 artifact type、size、checksum、uri。
- [ ] 运行：`mvn -pl htht-modules/htht-multisource -Dtest=StageInputResolverTest,WorkerContractCodecTest,Gf6WfvL1aWorkerContractTest test`，确认失败。
- [ ] 实现阶段输入绑定和 `plan-v2` 翻译；阶段参数使用创建任务时冻结的同一快照，不允许前端在阶段级覆盖。
- [ ] 拒绝 `RADIOMETRIC_CALIBRATION`、大气校正、地形校正、任意光谱指数和模型分类等未声明操作。
- [ ] 对 Worker 当前实际输出做一次逐项对照：标准 COG、标定摘要、RPC 网格、特征、融合、显著性类型必须与 Java `StageOutput` 完全一致；若某个可选输出尚未由 Worker 生成，先在计划中将该能力标为不可选，不得在前端显示一个实际不会返回的下载项。
- [ ] 运行测试并提交：`feat: bind GF6 L1A stage contracts`。

**验收：** 任一阶段拿不到声明的文件或 artifact 时，Java 在调用 Worker 前失败；任何路径穿越或未授权物理路径不会进入请求。

## Task 4：完善 Java 产物登记、任务详情和下载权限

**目的：** 让前端能显示阶段和最终结果，同时阻止下载中间文件或他人任务结果。

**文件：**

- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskDetailView.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskStageView.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskArtifactView.java`
- 修改：`JavaRSproject/htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java`
- 修改：`JavaRSproject/htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- 修改：`JavaRSproject/htht-admin/src/main/java/com/htht/config/multisource/LocalWorkerResultArchiveService.java`
- 测试：现有 `GetTaskDetailUseCaseTest`、`DispatchNextStageUseCaseTest`
- 新建：`Gf6WfvL1aArtifactAccessTest.java`、`Gf6WfvL1aResultArchiveTest.java`

**固定返回字段：**

```json
{
  "stageId": 201,
  "stageKey": "WRITE_GF6_STANDARD_COG",
  "status": "SUCCEEDED",
  "attempt": 1,
  "artifacts": [
    {
      "type": "GF6_STANDARD_COG",
      "uri": "rs://output/task/101/gf6-standard-cog.tif",
      "sizeBytes": 6454,
      "checksum": "sha256:...",
      "retention": "FINAL",
      "downloadable": true
    }
  ]
}
```

**步骤：**

- [ ] 先写测试：任务详情不包含本地路径；中间 artifact `downloadable=false`；不同用户、不同 task、错误 taskId 均拒绝下载。
- [ ] 运行 focused Maven 测试，确认失败。
- [ ] 扩展 DTO 和 archive service，只将 `FINAL` artifact 加入 ZIP；ZIP 内使用稳定文件名和 `manifest.json`，manifest 不包含物理路径、RPC 系数和授权 token。
- [ ] 将下载接口返回的 HTTP 状态固定为：未完成 `409`，无权限 `403`，不存在 `404`，已完成且有最终结果 `200 application/zip`。
- [ ] 运行测试并提交：`feat: secure GF6 final artifact downloads`。

**验收：** 前端只凭任务详情中的逻辑 artifact 信息即可显示和下载最终结果；中间产物不会泄露。

## Task 5：扩展前端文件模板和纯函数校验

**目的：** 把当前 Browse 三文件选择器改为 GF6 L1A 多文件槽位，并保证校验逻辑可独立测试。

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/productTemplates.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.test.ts`

**固定槽位：**

| 槽位 ID | 角色 | 扩展名 | 必选 |
|---|---|---|---|
| `tile1`、`tile2`、`tile3` | `MAIN_TIFF_TILE` | `.tif`、`.tiff` | 是 |
| `metadata` | `METADATA_XML` | `.xml` | 是 |
| `rpc` | `RPC_RPB` | `.rpb` | 是 |
| `navigation` | `NAV` | `.nav` | 是 |
| `tileIndex` | `TILE_INDEX_TIL` | `.til` | 是 |
| `shp`、`dbf`、`shx` | `FOOTPRINT_*` | 对应扩展名 | 否 |

**步骤：**

- [ ] 先写纯函数测试：缺任一必需槽位、扩展名错误、三块 TIFF 产品基名不一致、只上传部分 SHP sidecar、重复 band、ROI 越界、偶数显著性窗口、融合波段未选中均失败。
- [ ] 运行：`pnpm vitest run src/features/multisource/gf6WfvL1aTemplate.test.ts`；确认失败或记录项目未配置 Vitest 的替代命令。
- [ ] 将 `ProductFileRole`、`SlotId`、`ProductFiles` 改为可同时表达 Browse 和 L1A；Browse 默认行为保持不变。
- [ ] 实现 `validateGf6WfvL1aFiles`、`buildGf6WfvL1aSnapshot` 和 `buildGf6WfvL1aAssets`，按 tile ordinal 传递 0/1/2，不把 File 对象或物理路径写入快照。
- [ ] 更新 API 类型：L1A 预检资产允许 `Gf6L1aRole`，但 Browse 仍使用原类型；创建任务接口继续只接收 JSON 字符串快照。
- [ ] 运行：`pnpm typecheck`，提交：`feat: add GF6 L1A upload template`。

**验收：** 页面可以准确提示缺少哪一个文件；合格文件生成的资产顺序与 TIL tile 1/2/3 一致。

## Task 6：接入前端任务状态机和 GF6 参数表单

**目的：** 让前端真正调用 L1A 的预检、创建、启动、进度、取消、重试和下载接口。

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 参考：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useGf6WfvL1aTask.test.ts`

**表单字段：**

- 目标 CRS：默认 `EPSG:4326`，允许项目后端白名单中的 CRS。
- 分辨率：默认 `0.00015`，必须为有限正数。
- RPC 高程：模式固定 `CONSTANT`，默认 `0` 米。
- ROI：可空；填写时为 `[minLon, minLat, maxLon, maxLat]`，经纬度顺序和范围必须校验。
- 波段：B1 至 B8 多选，默认全选。
- 特征：统计、梯度、局部方差；默认关闭。
- 融合：默认关闭；启用时选择红/绿/蓝三个已选波段。
- 显著性：默认关闭；启用时选择已选波段、奇数窗口和 0 至 1 阈值。

**步骤：**

- [ ] 先写 composable 测试：上传全部槽位后预检只出现一次；创建任务时发送 L1A snapshot；状态到 `SUCCEEDED/FAILED/CANCELED` 后停止轮询；组件卸载、重置、替换文件时停止轮询；重试只提交失败阶段。
- [ ] 运行前端定向测试或最小 TypeScript 测试，确认失败。
- [ ] 将 composable 的槽位类型、模板引用、资产构造和预检候选判断改为按当前模板工作，不再硬编码 Browse 三槽位和 `GF6-WFV-LEVEL1A-` 旧 package 前缀。
- [ ] `createAndStart` 使用 `buildGf6WfvL1aSnapshot(form)`；不能继续发送 `DEFAULT_PROCESS_SNAPSHOT`。
- [ ] 页面增加参数区域和“检查文件”“开始处理”“取消”“重试”“下载 ZIP”“重新处理”按钮；按钮状态与任务状态绑定。
- [ ] 阶段标签映射为 GF6 中文名称：拼接、辐射定标、RPC 名义网格、波段配准、裁剪、标准 COG、特征、融合、显著性。
- [ ] 页面固定显示限制文字：`无 DEM 名义 RPC`、`定位精度未验证`；启用显著性时显示 `仅候选显著性结果`。
- [ ] 运行：`pnpm typecheck`，提交：`feat: connect GF6 L1A task state machine`。

**验收：** 用户只在页面操作，不需要知道 Worker stage code 或本地文件路径；刷新页面不会取消任务，终态后不会继续轮询。

## Task 7：前端错误、进度和 artifact 展示

**目的：** 将 Java 的阶段状态、稳定错误码和最终 artifact 安全地呈现给用户。

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aPresentation.test.ts`

**步骤：**

- [ ] 先写展示测试：阶段状态顺序与后端一致；`ROI_DOES_NOT_INTERSECT`、`GF6_EVIDENCE_INVALID`、`GF6_PARAMETER_INVALID`、授权过期和 Worker 失败有稳定中文提示；只有 `downloadable=true` 的 artifact 显示下载。
- [ ] 实现 API 类型的 `retention`、`downloadable`、`sizeBytes`、`checksum`、`navigationStatus`、`accuracyStatus` 和显著性 interpretation 字段。
- [ ] 处理轮询失败：短暂网络错误重试，连续终态或文件替换后停止；不显示内部异常堆栈。
- [ ] 对 artifact 列表按固定顺序展示标准 COG、标定摘要、RPC 网格、特征、融合、显著性；摘要 JSON 可下载但中间文件不可下载。
- [ ] 运行 `pnpm typecheck` 和前端定向测试，提交：`feat: present GF6 task results safely`。

**验收：** 用户能看懂当前进行到哪一步、失败原因和哪些结果可下载；页面不泄露物理路径、manifest、RPC 系数或栈信息。

## Task 8：后端与前端集成测试

**目的：** 证明 Java 和 Vue 对同一组 GF6 L1A 数据使用同一契约。

**文件：**

- Java 新建：`JavaRSproject/htht-admin/src/test/java/com/htht/web/controller/multisource/Gf6WfvL1aProcessingControllerTest.java`
- Java 新建：`JavaRSproject/htht-modules/htht-multisource/src/test/java/com/htht/multisource/integration/Gf6WfvL1aTaskIntegrationTest.java`
- 前端新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aIntegration.test.ts`
- 测试数据只使用临时目录中的小型合成文件；真实数据只在验收任务使用，不提交仓库。

**步骤：**

- [ ] Java Mock Worker 返回唯一 candidate、plan-v2、各阶段 artifact 和最终状态，断言预检至下载的请求/响应字段。
- [ ] 测试 Java 拒绝错误 profile、缺少 tile、重复 tile、未声明 artifact、错误 checksum、跨任务下载。
- [ ] 前端使用真实 composable 和 API mock，模拟上传 10 个槽位、预检、创建、阶段进度、失败重试、成功下载和取消。
- [ ] 运行：`mvn -pl htht-modules/htht-multisource,htht-admin -am -Dtest=*Gf6WfvL1a* test`。
- [ ] 运行：`pnpm vitest run src/features/multisource/*Gf6*test.ts` 和 `pnpm typecheck`。
- [ ] 提交：`test: cover GF6 Java frontend task flow`。

**验收：** 集成测试中的 profile、role、snapshot、stage、artifact 字符串与 Worker 真实返回格式逐字一致。

## Task 9：真实 GF6 数据端到端验收和发布检查

**目的：** 用当前真实产品验证上传到最终下载的闭环，并形成可复核的验收记录。

**真实数据：**

`D:/Lwy_project_space/RSImageProcess/otherfile/GF6_WFV_E107.6_N9.0_20260716_L1A1420724603`

**文件：**

- 新建：`JavaRSproject/docs/validation/gf6-wfv-l1a-java-frontend-acceptance.md`
- 修改：`JavaRSproject/htht-modules/htht-multisource/README.md`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/README.md`（若不存在则放在现有多源说明文件）

**步骤：**

- [ ] 运行 Worker 有界真实验收：产品 ID `1420724603`，尺寸 `50721×17801`，8 个 `UInt16` 波段，RPC 网格状态 `NOMINAL_RPC_NO_DEM`。
- [ ] 通过前端上传三块 TIFF、XML、RPB、NAV、TIL；SHP sidecar 作为可选文件测试一次。
- [ ] 预检必须返回一个 `GF-6-WFV-L1A/v1` candidate；创建任务时记录参数快照哈希。
- [ ] 使用小 ROI 先验收标准 COG，再分别开启特征、RGB 融合和显著性；检查任务阶段、artifact 类型、大小和 SHA-256。
- [ ] 轮询至 `SUCCEEDED`，下载 ZIP，检查只包含 FINAL artifact，不包含 raw TIFF、VRT、manifest、绝对路径、RPC 系数、授权 token 或栈信息。
- [ ] 另测：ROI 不相交、删除一个 tile、错误扩展名、取消、失败重试、过期预检 token、非任务所有者下载。
- [ ] 运行最终门禁：

```powershell
$env:PROJ_LIB='C:\Users\dell\AppData\Roaming\Python\Python313\site-packages\rasterio\proj_data'
$env:PYTHONPATH='D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker\src'
& 'D:\Anaconda3\python.exe' -m pytest tests/test_gf6_wfv_l1a_adapter.py tests/test_gf6_wfv_mosaic.py tests/test_gf6_wfv_calibration.py tests/test_gf6_wfv_rpc.py tests/test_gf6_wfv_pipeline.py -q
mvn -pl htht-modules/htht-multisource,htht-admin -am test
pnpm --dir D:\Lwy_project_space\RSImageProcess\rsimageprocess-view typecheck
pnpm --dir D:\Lwy_project_space\RSImageProcess\rsimageprocess-view build
```

- [ ] 在验收报告中记录测试命令、通过数量、失败原因、限制和 artifact 摘要；不得记录本地绝对路径。
- [ ] 提交：`docs: record GF6 Java frontend acceptance`。

**验收：** 完成一次可复核的“上传 -> 预检 -> 创建 -> 启动 -> 阶段执行 -> 轮询 -> 最终下载”闭环；所有限制如实显示，且既有产品回归通过。

## 四、开发人员交接检查清单

交接前必须逐项回答“通过/不通过/阻塞原因”：

- [ ] Java profile 与 Browse profile 分离。
- [ ] 参数快照严格校验并在任务创建后冻结。
- [ ] 七个必需角色和三个可选 sidecar 角色映射正确。
- [ ] 每个阶段的输入只来自原始授权文件或声明依赖的 FINAL/INTERMEDIATE artifact。
- [ ] artifact 有大小、SHA-256、保留级别和下载权限。
- [ ] 前端不发送本地路径，不显示 Worker 内部协议。
- [ ] 终态停止轮询，取消/失败/重试状态正确。
- [ ] `NOMINAL_RPC_NO_DEM`、`UNVALIDATED`、`CANDIDATE_ONLY` 均在页面和摘要中出现。
- [ ] ZIP 不包含原始输入和内部中间文件。
- [ ] Java、前端、Worker 定向测试和真实小 ROI 验收均有新鲜命令输出。

## 五、明确不应宣称的内容

本计划完成后只能宣称“GF6 WFV L1A 工程处理闭环已接入并通过有界专项验收”。不能宣称：官方正射产品、DEM 正射校正、绝对定位精度认证、控制点亚像素配准、地物目标识别、变化检测、官方质量评价或科学产品认证。
