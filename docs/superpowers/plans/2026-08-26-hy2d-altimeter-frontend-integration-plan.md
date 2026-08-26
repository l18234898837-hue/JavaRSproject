# HY-2D 测高沿轨产品前后端对接实施计划

> **交接对象：** 后端和前端各一名开发人员，或一名全栈开发人员。  
> **执行方式：** 严格按任务 0 至任务 7 的顺序执行；每项先写或补测试，再写实现，再运行本项验证。不要与 HY-2D 基线算法实现混在同一个提交中。  
> **范围：** 本计划只完成 HY-2D IDR 2MC `.tar.gz` 产品的任务创建、参数提交、执行进度、最终产物下载和沿轨结果展示。不会改变已完成的公共算子、薄产品适配器和 DAG 算法逻辑。

## 1. 目标和现有基线

用户在页面选择一个 HY-2D IDR 2MC `.tar.gz` 包，填写沿轨处理参数后，系统完成“上传 -> 预检 -> 创建任务 -> `/dispatch` 逐阶段执行 -> 轮询进度 -> 下载最终结果 -> 在地图查看轨迹和异常点”。

算法侧的基线分支为 `hy2d-altimeter-baseline-v1`，已实现并通过真实样例验证的阶段为：

```text
VALIDATE_SOURCE
DECODE_ALTIMETER_TRACK
APPLY_TRACK_QUALITY
CALCULATE_BASELINE_SSH
EXTRACT_TRACK_FEATURES
DETECT_TRACK_ANOMALIES
WRITE_STANDARD_TRACK_OUTPUT
GRID_TRACK_OPTIONAL
```

前端仓库是 `D:\Lwy_project_space\RSImageProcess\rsimageprocess-view`，当前只实现了 GF-6 WFV 浏览产品的三个固定输入槽和 ZIP 下载。Java 当前接口为 `/dispatch`，不是 `/start`。`progress` 和 `detail` 目前会返回内部 `uri`/`storageUri`，这是本次对接必须先消除的边界问题。

### 1.1 不可突破的边界

- Worker 只读取本次 manifest 授权的输入，只在自己的临时输出目录写文件；前端不接触 manifest、Worker URI、物理路径或 `work` 中间产物。
- Java 校验 Worker 文件、提升到任务专属目录、持久化产物，并仅向任务所属用户暴露最终产物。
- HY-2D 是**沿轨观测产品**，不是影像产品。页面和文案必须使用“沿轨异常检测”“统计网格”，不得出现“多波段融合”“图像显著性”“影像投影”。
- 统计网格是 `bin-statistics-no-interpolation`，不得称为空间插值结果。
- `absoluteSshLimitScientificStatus=ENGINEERING_BASELINE_REQUIRES_OFFICIAL_REVIEW` 时，必须显示“工程基线，需按官方规范复核”；不得写成官方质量判据、官方海面高度产品或科学结论。
- 真数据目录 `D:\Lwy_project_space\RSImageProcess\otherfile\H2D_OPER_IDR_2MC_0188_0242_20260701` 仅作为只读验收输入，任何任务、测试或前端上传流程都不能写入该目录。

### 1.2 对外契约（先确认、后冻结）

保留现有接口：

```text
POST /system/file/uploadStatic
POST /api/multisource/processing-tasks/preflight
POST /api/multisource/processing-tasks
POST /api/multisource/processing-tasks/{taskId}/dispatch
GET  /api/multisource/processing-tasks/{taskId}/progress
POST /api/multisource/processing-tasks/{taskId}/cancel
POST /api/multisource/processing-stages/{stageId}/retry
GET  /api/multisource/processing-tasks/{taskId}/result.zip
```

新增最终产物下载接口：

```text
GET /api/multisource/processing-tasks/{taskId}/artifacts/{artifactType}/download
```

`progress` 的目标字段如下。字段可以在响应中增加，但不能再返回 `uri`、`storageUri`、本地绝对路径、`workRoot`、`artifactRoot` 或 manifest 内容。

```json
{
  "taskId": 101,
  "lifecycleStatus": "RUNNING",
  "qualityStatus": "ENGINEERING_BASELINE_REQUIRES_OFFICIAL_REVIEW",
  "stages": [{
    "stageId": 801,
    "stageKey": "DETECT_TRACK_ANOMALIES",
    "status": "SUCCEEDED",
    "attempt": 1,
    "startedAt": "2026-08-26T09:00:00Z",
    "finishedAt": "2026-08-26T09:00:03Z",
    "errorCode": null,
    "errorMessage": null
  }],
  "artifacts": [{
    "stageId": 801,
    "type": "TRACK_ANOMALIES_GEOJSON",
    "checksum": "sha256:...",
    "sizeBytes": 4567,
    "retention": "FINAL",
    "downloadable": true,
    "fileName": "hy2d-track-anomalies.geojson",
    "contentType": "application/geo+json"
  }]
}
```

`GET /{taskId}` 同样必须隐藏原始输入的 `storageUri`；只返回供业务显示所需的角色、文件名、校验和、产品候选、阶段和最终产物摘要。

## 2. 执行前检查（任务 0）

**目的：** 确保 UI 开发接到的是完整 HY-2D 后端基线，而不是仓库 `main` 上的旧接口或旧 Worker。

**涉及仓库：**

- `D:\Lwy_project_space\RSImageProcess\JavaRSproject`
- `D:\Lwy_project_space\RSImageProcess\rsimageprocess-view`

- [ ] 确认 `hy2d-altimeter-baseline-v1` 已合并到本次开发分支，或以提交 `84ae339`、`da3b01b` 为基础创建对接分支。没有该基线时，先停止 UI 开发并完成合并，不要复制 Worker 代码到前端分支。
- [ ] 记录 Java、Worker、前端三个仓库的起始提交和当前 `git status --short`。工作区已有的其他文档修改不回退、不混入本功能提交。
- [ ] 运行现有基线验证；真实样例只读，未配置环境时真实数据 smoke test 可以跳过，但单元测试不能跳过。

```powershell
git -C D:\Lwy_project_space\RSImageProcess\JavaRSproject status --short
git -C D:\Lwy_project_space\RSImageProcess\rsimageprocess-view status --short
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
$env:PYTHONPATH=(Join-Path (Get-Location) 'src')
.\.venv\Scripts\python.exe -m pytest -q tests/test_hy2d_altimeter.py tests/test_hy2d_pipeline.py
```

**验收：** 开发人员能指出将要对接的 `HY-2D-ALTIMETER/v1` 适配器、八阶段 DAG、最终产物类型，以及当前分支确实包含它们。

## 3. Java：先冻结安全的 REST 契约（任务 1）

**文件：**

- 修改 `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingTaskController.java`
- 修改 `htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- 修改 `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskDetailResponse.java`
- 修改 `htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ExecutionProgressResponse.java`
- 视实际阶段失败信息所在领域模型，修改对应查询 DTO/mapper；不得在 Controller 拼接或猜测失败原因。
- 新增或修改 `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingTaskControllerTest.java`
- 新增或修改 `htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`

### 步骤

1. **先写控制器响应测试（红灯）。** 用任务 `101` 构造一项 `FINAL` 和一项 `INTERMEDIATE` 产物。断言 `progress` 中 FINAL 有 `downloadable=true`、文件名、大小、校验和和保留级别；中间产物有 `downloadable=false`。断言 JSON 不含 `uri`、`storageUri`、`D:\\`、`/worker-work/`、`artifactRoot`、`manifest`。断言失败阶段可返回稳定 `errorCode` 和适合展示的 `errorMessage`。
2. **只暴露展示 DTO。** `TaskDetailResponse.InputAssetResponse` 删除 `storageUri`；产物响应删除 `uri`。保留 `type`、`checksum`、`sizeBytes`、`retention`、`downloadable`、`fileName`、`contentType`。`downloadable` 的唯一判定规则是“任务所属用户有权限且 retention 为 FINAL”。
3. **补单产物下载。** 实现 `GET /artifacts/{artifactType}/download`：先用当前用户读取任务；只选择该任务唯一的 FINAL 产物；禁止 `..`、未知类型和中间类型；通过 Java 产物存储服务流式写响应，设置服务端安全文件名和正确 `Content-Type`。不接受文件路径、URI 或相对路径作为请求参数。
4. **限制 ZIP。** `result.zip` 的归档服务只收录 `FINAL` 产物；没有最终产物或任务未成功时返回明确业务错误。不得把解码、质量掩膜、临时 manifest 打进 ZIP。
5. **补 API 文档。** 在 `docs/deployment/` 或现有 API 文档中记录请求路径、成功/失败字段、最终产物下载、权限和不可返回的字段，供前端固定类型使用。

**验证命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-admin -am '-Dtest=ProcessingTaskControllerTest,ProcessingExecutionControllerTest' -DfailIfNoTests=false test
```

**提交：**

```powershell
git add htht-admin/src/main htht-admin/src/test docs
git commit -m "feat: expose safe multisource task artifacts"
```

**验收：** 浏览器抓包、日志和 API 返回均无法得到实际磁盘路径；用户可下载 FINAL 的 GeoJSON、CSV、NetCDF、Parquet、COG、JSON 及最终 ZIP，但不能下载 `work` 产物。

## 4. 前端：产品模板与参数契约（任务 2）

**文件：**

- 修改 `rsimageprocess-view/src/features/multisource/productTemplates.ts`
- 新建 `rsimageprocess-view/src/features/multisource/hy2dAltimeterParameters.ts`
- 新建 `rsimageprocess-view/src/features/multisource/__tests__/hy2dAltimeterParameters.spec.ts`
- 新建或修改 `rsimageprocess-view/vitest.config.ts`、`rsimageprocess-view/package.json`（仅在仓库没有既有单元测试运行器时）

### 步骤

1. **先写参数测试（红灯）。** 测试默认值可生成稳定 JSON；偶数或小于 3 的 `featureWindowSize` 被拒绝；`madThreshold`、`jumpThresholdMeters` 必须为正有限数；`grid.spacingDegrees` 在启用网格时必须位于 `(0, 5]`；禁用网格时不提交 `spacingDegrees`。测试只允许一个 `.tar.gz` 输入槽，拒绝 `.zip`、`.nc` 和多个原始文件拼装。
2. **扩展模板模型，不硬编码 GF-6。** 将 `ProductFileRole`、`SlotId`、`ProductFiles` 改为可扩展的字符串联合或泛型映射，保留 GF-6 兼容项。新增 `HY2D_ALTIMETER_TEMPLATE`：

```ts
{
  id: "hy2d-altimeter-v1",
  label: "HY-2D 雷达高度计 IDR 2MC",
  productCode: "HY-2D-ALTIMETER",
  profileVersion: "v1",
  slots: [{ id: "package", role: "MAIN", extensions: [".tar.gz"], required: true }]
}
```

3. **实现参数模型和构造函数。** 只让前端提交用户可选择的参数：

```json
{
  "schemaVersion": "1",
  "operation": "STANDARDIZE_ALONG_TRACK",
  "qualityPolicyVersion": "hy2d-idr-2mc-baseline-v1",
  "featureWindowSize": 11,
  "anomaly": {
    "madThreshold": 3.5,
    "jumpThresholdMeters": 0.5
  },
  "grid": { "enabled": false }
}
```

启用网格时再加入 `grid.spacingDegrees`。数值输入统一转换为 `number` 并用 `Number.isFinite` 校验；不发送空字符串、`NaN`、`Infinity`、文件系统路径、质量位、SSH 改正项或 Worker 配置。
4. **显示固定科学说明。** 在 HY-2D 参数区使用简短静态说明：当前质量规则和绝对 SSH 阈值是工程基线，结果待官方 IDR/IGDR 规范复核。该提示不是可关闭的“成功”提示，也不能放在下载按钮之后。

**验证命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- hy2dAltimeterParameters
pnpm typecheck
```

若新增 Vitest，先将 `test:unit` 固定为 `vitest run`，然后运行上述命令；不得以 `any`、关闭 `vue-tsc` 或 `skipLibCheck` 绕过类型问题。

**提交：**

```powershell
git add package.json pnpm-lock.yaml src/features/multisource
git commit -m "feat: add HY-2D task template and parameters"
```

**验收：** 选择 HY-2D 后页面只允许 `.tar.gz`；非法窗口和阈值不能启动；生成的 `parameterSnapshot` 与 Worker 的 `HY-2D-ALTIMETER/v1` 校验结构一致。

## 5. 前端：API 类型与通用任务状态机（任务 3）

**文件：**

- 修改 `rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 重构 `rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 新建 `rsimageprocess-view/src/features/multisource/__tests__/useProcessingTask.spec.ts`

### 步骤

1. **先写状态机测试（红灯）。** 覆盖 GF-6 三文件和 HY-2D 单文件均能生成正确 `assets`；HY-2D 预检只接受唯一的 `HY-2D-ALTIMETER` 且 `profileVersion=v1` 候选；`dispatch` 后轮询；终态停止轮询；失败阶段可重试；成功且存在 FINAL 产物时可下载。测试 `progress` 响应没有 `uri`，避免前端重新依赖被移除字段。
2. **扩展 API 类型。** 根据任务 1 的实际 DTO 增加阶段时间、错误码/消息、产物大小、保留级别、可下载标识、文件名、内容类型。新增 `downloadProcessingArtifact(taskId, artifactType)`，它只按 `taskId` 和枚举产物类型请求下载接口。
3. **让 composable 接受模板和参数构造函数。** `useProcessingTask(template, buildSnapshot)` 或等价的显式上下文传入方式均可。禁止内部固定 `GF6_WFV_BROWSE_TEMPLATE`、`DEFAULT_PROCESS_SNAPSHOT`、固定三个槽位和 `gf6-wfv-result.zip` 文件名。
4. **保留生命周期语义。** `CREATED`、`RUNNING`、`RECOVERY_PENDING` 可取消；`FAILED` 和 `RECOVERY_PENDING` 的失败阶段可重试；`SUCCEEDED`、`FAILED`、`CANCELED` 停止轮询。网络轮询失败时指数或固定退避后继续，页面销毁时一定清理定时器。
5. **错误展示只用公开字段。** 优先显示服务端 `errorCode` 和 `errorMessage`，保留当前中文兜底翻译；不从路径、Python 栈追踪或 Worker 标识推断错误。

**验证命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- useProcessingTask
pnpm typecheck
```

**提交：**

```powershell
git add src/api/multiSourceSatellite.ts src/features/multisource
git commit -m "refactor: support product-specific processing tasks"
```

**验收：** 现有 GF-6 页面流程仍可用；切换 HY-2D 不会遗留 GF-6 文件、候选或参数；所有下载请求都只有任务 ID 和产物类型。

## 6. 前端：HY-2D 任务页面、进度和下载（任务 4）

**文件：**

- 修改 `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 新建 `rsimageprocess-view/src/features/multisource/hy2dPresentation.ts`
- 新建 `rsimageprocess-view/src/features/multisource/__tests__/hy2dPresentation.spec.ts`

### 步骤

1. **先写展示映射测试（红灯）。** 每个 HY-2D 阶段键都有中文名；未知阶段显示服务端原值而不崩溃；产物 `INTERMEDIATE` 永不生成下载项；工程基线状态生成复核提示；`TRACK_GRID_COG` 文案为“统计网格 COG”。
2. **切换产品时切换表单。** 下拉列表保留 GF-6 和 HY-2D。HY-2D 上传区显示一个“IDR 2MC 产品包（.tar.gz）”文件槽，参数区显示窗口、MAD 阈值、跳变阈值及“生成统计网格”开关。GF-6 原有三个槽位和页面文案不能被 HY-2D 逻辑污染。
3. **使用下列阶段名称。**

```ts
const HY2D_STAGE_LABELS = {
  VALIDATE_SOURCE: "校验 IDR 2MC 产品包",
  DECODE_ALTIMETER_TRACK: "解析沿轨观测",
  APPLY_TRACK_QUALITY: "执行沿轨质量过滤",
  CALCULATE_BASELINE_SSH: "计算工程基线 SSH",
  EXTRACT_TRACK_FEATURES: "提取沿轨窗口特征",
  DETECT_TRACK_ANOMALIES: "检测沿轨异常",
  WRITE_STANDARD_TRACK_OUTPUT: "生成标准沿轨结果",
  GRID_TRACK_OPTIONAL: "生成统计网格（可选）"
} as const;
```

4. **展示最终产物而非内部文件。** 成功后按 `downloadable===true` 显示最终产物列表和单个下载图标，并保留“下载全部结果 ZIP”。按类型显示中文名：标准沿轨 NetCDF、Parquet、CSV、轨迹 GeoJSON、异常 GeoJSON、结果摘要、统计网格 NetCDF/COG/摘要。列表显示文件名、大小、校验和短码；不显示 URI。
5. **处理异常状态。** `FAILED` 显示失败阶段、错误码和错误消息；`RECOVERY_PENDING` 显示等待恢复并允许该阶段重试；取消操作只发送请求，随后依靠轮询更新为 `CANCELED`。下载失败应保留当前任务上下文，不能清空结果。

**验证命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- hy2dPresentation
pnpm typecheck
pnpm build
```

**提交：**

```powershell
git add src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/features/multisource
git commit -m "feat: present HY-2D processing progress and outputs"
```

**验收：** 用户能看懂八阶段中当前执行到哪一步、失败在哪一步、哪些结果可以下载；页面无影像融合/显著性术语，且工程基线复核提示始终可见。

## 7. 前端：地图预览的最小可用实现（任务 5）

**文件：**

- 新建 `rsimageprocess-view/src/features/multisource/hy2dTrackLayer.ts`
- 新建 `rsimageprocess-view/src/features/multisource/__tests__/hy2dTrackLayer.spec.ts`
- 修改实际承载 Cesium `Viewer` 的现有地图组件或其状态/事件服务；先在 `src/components/Map/` 中确认项目当前 Viewer 获取方式，复用它，不创建第二个 Viewer。
- 修改 `MultiSourceSatelliteProcess.vue`，仅增加“在地图查看沿轨结果”命令。

### 步骤

1. **先写 GeoJSON 解析测试（红灯）。** 接受轨迹 `LineString` 和异常 `Point` 的 `FeatureCollection`；缺失经纬度或不是预期几何类型时给可读错误；清除当前 HY-2D 图层只清自己的 datasource，不调用 `viewer.entities.removeAll()`。
2. **下载并解析最终 GeoJSON。** 通过任务 1 的授权下载接口，以 Blob/文本形式获取 `STANDARD_TRACK_GEOJSON` 和 `TRACK_ANOMALIES_GEOJSON`。仅在两项均为 `downloadable=true` 时启用预览。不得将其 URL 写入持久化状态或让 Cesium 直接访问未经鉴权的磁盘地址。
3. **添加单独 datasource。** 轨迹画为蓝色或青色折线，异常点画为红色点；点击异常点弹窗只显示已公开的时间、经纬度、异常类型、值和阈值。加载后飞到轨迹范围，重新加载时替换旧的 HY-2D datasource。
4. **明确网格展示策略。** 本期不把 `TRACK_GRID_COG` 直接贴到 Cesium 影像层。COG 需要受控切片/渲染服务，浏览器不能安全、可靠地直接读取任意 GeoTIFF。本期显示网格摘要并允许下载；后续若引入受鉴权 COG tile 服务，再单独做图层接口和可视化计划。

**验证命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- hy2dTrackLayer
pnpm typecheck
pnpm build
```

**提交：**

```powershell
git add src/features/multisource src/components/LeftPanel src/components/Map
git commit -m "feat: preview HY-2D track and anomalies on map"
```

**验收：** 地图只增加本任务的轨迹和异常点；关闭/切换任务后不会删除其他地图业务图层；统计网格不会被冒充为插值影像。

## 8. 联调、权限和部署验收（任务 6）

**涉及文件：**

- 修改或新增 Java 集成测试、前端端到端测试说明。
- 修改 `JavaRSproject/docs/deployment/multisource-backend-deployment.md`（若不存在则新建）。
- 按实际环境修改部署 YAML，不提交真实绝对路径、口令或测试数据。

### 步骤

1. **环境前置检查。** Worker Python 环境必须包含 `numpy`、`netCDF4`、`pyarrow`、`rasterio` 等 HY-2D 已用依赖；缺失 `pyarrow` 时，在任务启动前或 worker 健康检查中报明确部署错误。配置独立 `work-root` 和 `artifact-root`，且二者不得与上传根或 `otherfile` 样例目录重叠。
2. **后端真实样例验收。** 以只读方式上传一个真实 `.tar.gz`，使用默认参数和一次启用统计网格参数各运行一次。检查任务最终为 `SUCCEEDED`，所有八阶段按 DAG 出现，最终产物包括标准沿轨结果、轨迹 GeoJSON、异常 GeoJSON、摘要；启用网格时还包括三类网格最终产物。
3. **浏览器联调。** 验证上传扩展名限制、预检候选、开始/取消/重试、刷新页面后重新查询任务、进度和错误展示、单产物下载、ZIP 下载、轨迹和异常点地图预览。用浏览器开发者工具检查响应和请求中没有本地路径或 Worker 目录。
4. **权限回归。** 换无权账户或无权任务 ID，确认详情、进度、ZIP、单产物下载和地图 GeoJSON 下载全被拒绝；不能通过修改 `artifactType` 下载其它任务或中间产物。
5. **全量回归。** Java、Worker 和前端都必须通过；真实样例集成测试只允许在环境变量未设置时跳过，变量已设置而结果错误必须失败。

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-modules/htht-multisource,htht-admin -am test

cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
$env:PYTHONPATH=(Join-Path (Get-Location) 'src')
$env:MULTISOURCE_HY2D_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H2D_OPER_IDR_2MC_0188_0242_20260701'
.\.venv\Scripts\python.exe -m pytest -q

cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit
pnpm typecheck
pnpm build
```

**提交：**

```powershell
git add docs htht-admin htht-modules/htht-multisource
git commit -m "test: verify HY-2D processing frontend integration"
git -C D:\Lwy_project_space\RSImageProcess\rsimageprocess-view add src package.json pnpm-lock.yaml
git -C D:\Lwy_project_space\RSImageProcess\rsimageprocess-view commit -m "test: verify HY-2D processing UI integration"
```

## 9. 最终交接清单（任务 7）

- [ ] 基线算法分支已合入本次对接分支，且没有复制实现或分叉参数格式。
- [ ] Java API 不输出物理路径、内部 URI、manifest 或中间产物。
- [ ] Java 只允许当前用户下载本任务的 FINAL 产物；ZIP 同样只含 FINAL。
- [ ] 前端可在 GF-6 与 HY-2D 之间切换，彼此不会复用错误的槽位或参数。
- [ ] HY-2D 仅上传一个 `.tar.gz`，并提交经过严格校验的参数快照。
- [ ] 八个阶段名称、失败原因、取消和重试均可理解且可操作。
- [ ] 最终产物可逐个下载，也可下载 ZIP；中间产物没有入口。
- [ ] 地图只预览轨迹和异常点；统计网格仅摘要/下载，不做虚假的 COG 直接渲染。
- [ ] 工程基线待官方规范复核的提示在参数区和结果区均可见。
- [ ] Java、Python、Vue 测试与构建命令均有成功输出，并记录在交接说明中。

## 10. 推荐提交顺序

1. `feat: expose safe multisource task artifacts`
2. `feat: add HY-2D task template and parameters`
3. `refactor: support product-specific processing tasks`
4. `feat: present HY-2D processing progress and outputs`
5. `feat: preview HY-2D track and anomalies on map`
6. `test: verify HY-2D processing frontend integration`

每个提交必须可独立构建。若任务 1 的 API 契约尚未通过控制器测试，任务 2 至任务 5 不得以模拟路径或临时 URI 绕过它。若官方 IDR/IGDR 说明后续到位，应新开“科学规则复核”任务，更新质量位、SSH 改正、阈值和网格规则，并重新验收；不要在前端悄悄改文案或阈值。
