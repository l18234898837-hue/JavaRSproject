# FY-4B AGRI 前端对接实施计划

> 交付对象：前端开发人员。执行仓库为 `D:\Lwy_project_space\RSImageProcess\rsimageprocess-view`；本计划文件保存在 Java 仓库的 `docs/superpowers/plans`，两仓库分别提交。

> **供执行人员使用：** 实施时按任务顺序逐项执行；每个任务完成后先检查测试结果，再提交该任务变更。

**Goal:** 在现有多源卫星处理面板中接入 FY-4B AGRI 五文件资产处理、参数提交、阶段进度和最终 ZIP 下载。

**Architecture:** 复用现有 Vue 页面、API 封装和 `useProcessingTask` composable。产品模板负责五角色资产和配对，参数模块负责白名单快照，状态模块负责预检、调度互斥、轮询、取消、重试和下载。

**Tech Stack:** Vue 3.4、TypeScript 5、Vite 5、Element Plus、Pinia、Axios、Pure Admin Thin、pnpm、Vitest。

**Spec:** `docs/superpowers/plans/2026-08-27-fy4b-agri-l1-full-disk-processing-implementation-plan.md` 以及 Java Worker 的 `fy4b_agri_profile.py`、`product_adapters/fy4b_agri.py`。

## Global Constraints

- 只提交 `SYS_FILE` 的系统文件 ID；禁止提交物理路径、Worker 命令、输出 URI、执行 ID、计划快照、阶段类型和原始本地路径。
- FY-4B 资产必须是 `L1_0500M`、`L1_1000M`、`L1_2000M`、`L1_4000M`、`GEO_4000M` 五个角色，各自 `ordinal:0`、`required:true`。
- `parameterSnapshot` 必须是 JSON 字符串，顶层只允许 `schemaVersion`、`targetCrs`、`roi`、`features`、`fusion`、`saliency`。
- 任务进入终态、组件卸载或模板切换后必须停止轮询；同一任务同时最多一个 dispatch 请求。
- 页面只显示后端公开的状态和最终产物信息，不显示 URI、存储键、物理路径、manifest 或中间文件。
- 本计划不包含海岸线控制点残差、云检测和分类功能。

## 目标详述

在现有“多源卫星数据处理”面板中接入 FY-4B AGRI 全圆盘五文件处理流程。用户能够从系统文件资产中选择一组同一观测时次的五个文件，填写可选处理参数，完成预检、创建任务、调度阶段、查看进度、取消或重试，并在成功后下载结果 ZIP。

本计划只负责前端接入和联调，不重新实现 Java、Worker 或 FY-4B 算法。后端已经提供五文件包校验、标定、名义定位、重投影、裁剪、标准 COG、特征、伪彩色融合和显著性检测阶段。海岸线控制点残差计算不属于本计划，也不在界面中出现。

## Architecture

复用现有 `LeftPanel -> MultiSourceSatelliteProcess.vue -> useProcessingTask -> multiSourceSatellite.ts -> http` 链路。产品模板负责声明文件槽位和候选匹配规则；参数构造器负责生成后端可接受的快照；composable 负责任务状态、轮询和调度互斥；组件只负责输入、展示和用户操作。

前端只提交系统文件 ID 和白名单参数。浏览器不读取本地绝对路径，也不向后端提交物理路径、Worker 命令、输出 URI、执行 ID、计划快照、阶段类型或原始本地路径。任务详情和进度响应中即使存在 `uri`/`storageUri`，也必须在映射层丢弃，页面不得显示或拼接这些值。当前后端只有任务 ZIP 下载端点，单个 Artifact 不直接下载。

## Tech Stack

- Vue 3.4、`<script setup>`、TypeScript 5、Vite 5
- Element Plus、Pinia、Axios、Pure Admin Thin
- 包管理器 `pnpm`
- 现有校验：`pnpm typecheck`、`pnpm build`、`pnpm lint`
- 逻辑单测：为本次功能增加 Vitest；不重复运行与本次改动无关的全量浏览器测试

## Spec

### 后端接口

```text
POST /api/multisource/processing-tasks/preflight
POST /api/multisource/processing-tasks
GET  /api/multisource/processing-tasks/{taskId}
GET  /api/multisource/processing-tasks/{taskId}/progress
POST /api/multisource/processing-tasks/{taskId}/dispatch
POST /api/multisource/processing-tasks/{taskId}/cancel
POST /api/multisource/processing-stages/{stageId}/retry
GET  /api/multisource/processing-tasks/{taskId}/result.zip
```

预检请求的 `assets` 必须包含五条记录，每条结构为：

```ts
{
  sourceType: "SYS_FILE";
  sourceId: number | string;
  role: "L1_0500M" | "L1_1000M" | "L1_2000M" | "L1_4000M" | "GEO_4000M";
  ordinal: 0;
  required: true;
}
```

创建任务的 `parameterSnapshot` 是 JSON 字符串，不是嵌套对象；前端不传 `outputUri`。`preflightToken` 和 `candidateId` 必须来自同一次预检，token 过期或输入改变后必须重新预检。

FY-4B 参数顶层只能有 `schemaVersion`、`targetCrs`、`roi`、`features`、`fusion`、`saliency`。默认快照为 `EPSG:4326`、无 ROI、关闭可选阶段、`features.metrics=["statistics"]`、融合百分位 `[2,98]`、显著性源 `C02_REFLECTANCE_500M`、阈值 `0.5`、窗口 `3`。

### 五文件角色和候选规则

| 界面槽位 | 后端 role | 文件约束 | 配对依据 |
| --- | --- | --- | --- |
| 500 米 L1 | `L1_0500M` | FY-4B AGRI FDI，500M/0500M | 卫星、起止时间、版本一致 |
| 1000 米 L1 | `L1_1000M` | FY-4B AGRI FDI，1000M | 同上 |
| 2000 米 L1 | `L1_2000M` | FY-4B AGRI FDI，2000M | 同上 |
| 4000 米 L1 | `L1_4000M` | FY-4B AGRI FDI，4000M | 同上 |
| 4000 米 GEO | `GEO_4000M` | FY-4B AGRI GEO，4000M | 与四个 L1 文件同一观测包 |

系统文件列表中的 `id` 是唯一允许提交的 `sourceId`。前端可以复用现有数据源查询接口和列表组件来选择资产；若列表接口返回名称、时间和缩略图，可用于显示，但不能把返回的物理路径当作请求字段。

### 阶段和 Artifact 展示

阶段标签至少覆盖：

```text
VALIDATE_FY4B_PACKAGE              验证 FY-4B 五文件包
BUILD_FY4B_NOMINAL_GRID            构建名义静止卫星网格
CALIBRATE_AND_GEOREFERENCE_FY4B    辐射定标与名义定位
REPROJECT_AND_CROP_FY4B            重投影与区域裁剪
WRITE_FY4B_STANDARD_COG             生成标准 COG
EXTRACT_FY4B_FEATURES               提取特征
FUSE_FY4B_BANDS                     生成近红外伪彩色
DETECT_FY4B_SALIENCY                显著性候选检测
```

任务状态覆盖 `CREATED`、`QUEUED`、`RUNNING`、`RECOVERY_PENDING`、`SUCCEEDED`、`FAILED`、`CANCELING`、`CANCELED`；阶段状态覆盖 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`SKIPPED`、`RETRYING`、`RECOVERY_PENDING`。未知状态显示“未知状态”，不得使组件崩溃。

结果列表按 Artifact 类型显示中文名称和后端允许公开的文件名；最稳定的用户操作是“下载全部最终结果 ZIP”。不显示 `uri`、`storageUri`、Worker 临时目录、中间 NPZ 或 manifest 内容。

## 任务 0：执行前基线和接口核对

**文件：** 不修改文件；记录两个仓库状态。

**步骤：**

1. 在前端仓库运行 `git status --short`、`git log --oneline -3`，确认不覆盖其他开发者改动。
2. 阅读并以当前代码为准核对 `src/api/multiSourceSatellite.ts`、`src/features/multisource/useProcessingTask.ts`、`src/features/multisource/productTemplates.ts`、`src/components/LeftPanel/MultiSourceSatelliteProcess.vue`、`src/components/LeftPanel/LeftPanel.vue`。
3. 确认 `src/api/dataSource/panel/loal.ts` 的系统数据查询返回记录含 `id`，确认 `src/utils/http/index.ts` 的统一响应解包和 Blob 行为。
4. 核对后端 FY-4B `ROLE_ORDER`、参数解析器和阶段键，不以旧 GF-6 文本推断字段。

**通过标准：** 前端开发记录当前基线、API 前缀和真实资产列表来源；若基线已有未提交改动，只避开相关文件，不回退、不清理。

**交付接口：** 输出系统资产记录的 `id`、后端状态枚举和 API 前缀核对记录，供后续模板和 API 任务使用。

## 任务 1：建立 FY-4B 模板、文件选择和参数类型

**文件：**

- 修改 `src/features/multisource/productTemplates.ts`
- 新建 `src/features/multisource/fy4bAgriFiles.ts`
- 新建 `src/features/multisource/fy4bAgriParameters.ts`
- 可选新建 `src/features/multisource/types.ts`，集中放置产品无关 DTO 类型
- 新建 `src/features/multisource/__tests__/fy4bAgriFiles.spec.ts`
- 新建 `src/features/multisource/__tests__/fy4bAgriParameters.spec.ts`

**接口：** `FY4B_AGRI_TEMPLATE.slots` 产生五个带 `role` 的槽位；`buildFy4bParameterSnapshot(input)` 返回后端参数对象；`validateFy4bAssets(assets)` 返回 `{ valid, reason }`。

**实现：**

1. 保留 GF-6 模板行为，新增 `FY4B_AGRI_TEMPLATE`，模板槽位严格按上述五个 role 排列。
2. 文件选择使用系统资产记录（`sourceId=id`），界面显示文件名、分辨率角色、观测时间和选择状态；不要求用户在浏览器中提供物理路径。
3. 用纯函数解析 FY-4B 文件名中的卫星、时间、种类、分辨率和版本，拒绝重复 role、缺少 role、FDI/GEO 类型错误、时间不一致、版本不一致。
4. 参数类型使用联合类型限制 CRS、特征指标、显著性来源和奇数窗口；提供 `DEFAULT_FY4B_PARAMETERS` 与 `buildFy4bParameterSnapshot`。
5. 参数构造器在提交前完成依赖闭包：显著性来源是 `toa_nir_red_nd`、`btd_c13_c14`、`gradient:C02_REFLECTANCE_500M` 或 `local_variance:C02_REFLECTANCE_500M` 时自动开启特征并补入对应 metric。
6. 特征关闭时仍发送合法的 `metrics:["statistics"]`；融合关闭时仍发送合法 `mode` 和百分位；ROI 必须满足经纬度范围、有限数值和最小值小于最大值。

**先写失败测试：** 覆盖五角色完整包通过、缺文件/错分辨率/错时间失败、无路径字段、默认快照、ROI 边界、窗口奇偶、融合百分位和显著性依赖自动补全。

**通过测试：** `pnpm test:unit -- fy4bAgriFiles fy4bAgriParameters`；再运行 `pnpm typecheck`。

**提交：** `feat: add FY-4B AGRI frontend template and parameters`。

**执行步骤：**

- [ ] 编写并运行文件配对和参数失败测试，确认新模板和构造器尚未满足约束。
- [ ] 编写最小模板、纯函数校验和参数归一化实现。
- [ ] 重跑定向 Vitest 与 `pnpm typecheck`，通过后提交。

## 任务 2：收敛 API DTO 和下载封装

**文件：** 修改 `src/api/multiSourceSatellite.ts`；必要时修改 `src/utils/downloadFile.ts`，不改统一 HTTP 拦截器的全局行为。

**接口：** `preflightProcessingTask`、`createProcessingTask`、`getProcessingProgress`、`dispatchProcessingTask`、`cancelProcessingTask`、`retryProcessingStage` 和 `downloadProcessingResult` 均返回现有 `http.request` Promise；下载结果为 Blob 响应。

**实现：**

1. 将 `PreflightCandidate` 补齐 `productCode`、`profileVersion`，前端只接受 `productCode === "FY-4B-AGRI"` 且 `profileVersion === "v1"` 的候选。
2. 为任务详情、进度、阶段、Artifact 建立准确的 TypeScript 类型，兼容后端新增字段；在 `toPublicProgress` 映射中丢弃 URI、存储键和物理路径。
3. 保留并核对预检、创建、进度、调度、取消、重试和 ZIP 下载函数的路径、HTTP 方法和 `Result<T>` 解包方式。
4. ZIP 下载使用 Blob，优先从 `Content-Disposition` 获取文件名，缺失时使用 `fy4b-agri-processing-result.zip`。不新增按 URI 下载函数。
5. 所有动态下载路径参数使用 `encodeURIComponent`；artifact 类型不直接进入 DOM，也不接受用户输入作为路径。

**先写失败测试：** mock HTTP 请求，断言五文件预检请求字段完整、创建请求的 `parameterSnapshot` 为字符串、不会出现 `outputUri`/`physicalPath`，并断言 ZIP 使用 Blob 配置。

**通过测试：** `pnpm test:unit -- multiSourceSatellite`（若 API 纯类型无法单测，则由 composable mock 契约测试覆盖）；`pnpm typecheck`。

**提交：** `refactor: align multisource API types with FY-4B contract`。

**执行步骤：**

- [ ] 先 mock HTTP 并运行请求字段失败测试，确认当前 DTO 无法完整表达 FY-4B 字段或安全约束。
- [ ] 添加 DTO、端点参数编码和 Blob 文件名处理的最小实现。
- [ ] 重跑 API 定向测试和类型检查，通过后提交。

## 任务 3：改造通用任务 composable 和调度状态机

**文件：**

- 重构 `src/features/multisource/useProcessingTask.ts`
- 新建 `src/features/multisource/processingPresentation.ts`
- 新建 `src/features/multisource/__tests__/useProcessingTask.spec.ts`
- 新建 `src/features/multisource/__tests__/processingPresentation.spec.ts`

**接口：** `useProcessingTask({ template, buildSnapshot, api })` 暴露 `runPreflight`、`createAndStart`、`startProcessing`、`cancel`、`retry`、`downloadResult`、`stopPolling`、`reset` 及响应式 `progress/taskStatus`；展示模块提供 `taskStatusLabel`、`stageLabel`、`artifactLabel`。

**实现：**

1. 把当前写死的 `GF6_WFV_BROWSE_TEMPLATE`、三槽位和 `DEFAULT_PROCESS_SNAPSHOT` 改为模板和快照构造器参数；切换产品时清空文件、候选、token、任务和定时器。
2. `buildAssets` 按模板槽位生成 `SYS_FILE` 资产，FY-4B 必须生成五条、`ordinal` 从 0 开始且每条 `required:true`。
3. `runPreflight` 校验完整包和参数后才发请求，精确匹配 FY-4B 候选；预检成功后保存 token、过期时间和候选，输入变化立即使其失效。
4. `createAndStart` 发送 `JSON.stringify(buildSnapshot())`，创建后先获取进度，再按“最多一个 dispatch 请求”的互斥规则调度。
5. 只有任务非终态、无 `RUNNING` 阶段且存在 `PENDING` 阶段时才 dispatch；`dispatched:false` 停止自动 dispatch，但继续按退避间隔轮询。网络重试不得重复 dispatch。
6. 轮询首次立即执行，正常间隔 2 秒；连续十次内容不变后使用 5 秒；网络错误 5 秒后重试。任务进入 `SUCCEEDED`、`FAILED`、`CANCELED` 后停止定时器；组件卸载时清理定时器。
7. `cancel` 只发送一次取消请求并等待进度确认；`retry` 只针对当前失败或恢复中的阶段，刷新进度后再由状态机决定下一次调度，不在前端硬编码三次重试规则。
8. 下载函数仅在 `SUCCEEDED` 且有最终结果时可用；创建 object URL、触发下载并在点击处理结束后释放。
9. 展示映射函数集中提供任务状态、阶段标签、Artifact 类型标签和未知值回退，不把后端键直接当作用户文案。

**先写失败测试：** 覆盖五资产请求、候选精确匹配、切换模板清理上下文、并发 dispatch 互斥、`dispatched:false`、终态停止轮询、取消/重试边界、ZIP 未完成禁用和 URI 不进入公开模型。

**通过测试：** `pnpm test:unit -- useProcessingTask processingPresentation`；本任务完成后只运行一次 `pnpm typecheck`。

**提交：** `refactor: make processing task state product aware`。

**执行步骤：**

- [ ] 先运行 composable 状态机失败测试，确认并发调度、终态停止和模板切换清理约束尚未满足。
- [ ] 实现模板参数化、dispatch 互斥和退避轮询的最小改造。
- [ ] 重跑定向测试并确认通过后提交。

## 任务 4：实现 FY-4B 参数面板

**文件：**

- 新建 `src/features/multisource/Fy4bAgriParameterForm.vue`
- 新建 `src/features/multisource/__tests__/fy4bAgriParameterForm.spec.ts`（若工程没有 Vue 组件测试环境，则将纯校验提取到参数模块并只测试纯函数）

**接口：** `Fy4bAgriParameterForm` 接收 `modelValue: Fy4bAgriParameters`，发出 `update:modelValue` 和 `validity-change`；提交前调用 `buildFy4bParameterSnapshot` 得到规范化快照。

**界面：**

1. 目标坐标系只提供 `EPSG:4326`、`EPSG:4490`。
2. ROI 提供开关和四个数字输入，按 WGS84 经度/纬度范围校验；关闭时发送 `null`。
3. 特征提供开关和 `statistics`、`gradient`、`local_variance`、`toa_nir_red_nd`、`btd_c13_c14` 多选；特征开启但无指标时阻止提交。
4. 仅选择 `local_variance` 时显示奇数窗口输入，最小为 3。
5. 融合提供开关、固定模式 `NIR_RED_BLUE` 和两个百分位输入；关闭时仍保留合法默认值。
6. 显著性提供开关、来源下拉、0 到 1 阈值和奇数窗口；选择特征来源时自动联动特征和指标，不能提交未满足依赖的组合。
7. 页面固定提示“结果为工程处理产物；FY-4B 定标和定位结果仍需按官方资料完成人工复核”，不添加云检测、分类或海岸线控制点选项。

**先写失败测试：** 测试非法 ROI、偶数窗口、空指标、百分位反向、显著性依赖和切换关闭状态时快照归一化。

**通过测试：** `pnpm test:unit -- fy4bAgriParameterForm fy4bAgriParameters`。

**提交：** `feat: add FY-4B AGRI parameter form`。

**执行步骤：**

- [ ] 先运行参数面板失败测试，确认非法 ROI、窗口、百分位和显著性依赖会被识别。
- [ ] 使用 Element Plus 控件实现最小表单和联动校验。
- [ ] 重跑定向测试，通过后提交。

## 任务 5：改造多源处理页面

**文件：** 修改 `src/components/LeftPanel/MultiSourceSatelliteProcess.vue`；仅在确有必要时修改 `src/components/LeftPanel/LeftPanel.vue` 的挂载条件。

**接口：** 页面向 composable 传入当前产品模板和 `buildFy4bParameterSnapshot`；页面消费 `slots`、`candidate`、`progress`、`taskStatus` 及任务操作函数，不直接发 Axios 请求。

**实现流程：**

1. 产品下拉同时显示现有 GF-6 和 FY-4B AGRI；模板切换调用 composable reset，不能保留另一产品的文件和任务。
2. FY-4B 页面显示五个“系统文件资产”选择槽位。选择器复用数据源列表返回的记录 ID；不得把 `<input type=file>` 得到的本地路径传给后端。若保留上传入口，只能先完成系统文件登记并使用登记返回的 ID，不能绕过 `SYS_FILE`。
3. 每个槽位显示角色、文件名、观测时间、选择状态和校验错误；显示缺失、重复、分辨率、时间、版本和 FDI/GEO 类型的明确原因。
4. 参数面板通过 `v-model` 绑定 FY-4B 快照；只有五个槽位已选、客户端配对通过、参数合法且不在请求中时启用“预检并开始”。
5. 预检区显示候选产品代码、版本、支持状态和置信度；候选不匹配时不允许创建任务，并提示重新选择文件。
6. 进度区显示任务状态、质量状态、每个阶段中文标签、阶段状态、尝试次数、开始/结束时间和后端公开的错误信息。未知阶段仍显示原键并保持页面可用。
7. `CREATED`、`QUEUED`、`RUNNING`、`RECOVERY_PENDING`、`CANCELING` 提供取消操作；取消前弹出确认。`FAILED` 或 `RECOVERY_PENDING` 阶段显示重试，成功或失败后都可以重新开始一组新任务。
8. 仅在任务成功时显示“下载全部最终结果 ZIP”；下载中显示忙碌状态，失败保留当前进度和错误提示。
9. 不显示 `uri`、`storageUri`、checksum 之外的内部执行细节、物理路径、manifest、Worker 命令或中间文件。

**先写失败测试：** 测试模板切换清理、按钮启用条件、五槽位角色渲染、状态/阶段标签、取消确认、失败重试和成功下载按钮条件。组件测试基础设施不足时，抽取 `canStart`、`canCancel`、`canDownload` 等纯函数测试，浏览器联调验证 DOM。

**通过测试：** `pnpm test:unit -- processingPresentation useProcessingTask`；随后运行 `pnpm typecheck` 和 `pnpm build` 各一次。

**提交：** `feat: integrate FY-4B AGRI processing panel`。

**执行步骤：**

- [ ] 先运行页面展示和按钮条件失败测试，确认 GF-6 固定三槽位逻辑无法满足 FY-4B 五槽位。
- [ ] 改为模板驱动渲染，接入资产选择器、参数表单、阶段列表和任务操作。
- [ ] 在浏览器手工检查错误文案和内部字段隐藏，再提交页面变更。

## 任务 6：前端回归和安全边界检查

**文件：** 不新增业务功能；必要时修正任务 1 至任务 5 的测试和类型。

**接口：** 本任务只消费前述公开的模板、参数、API 和展示函数，不改变后端契约。

**检查：**

1. 运行 FY-4B 逻辑单测、`pnpm typecheck`、`pnpm build`；只在失败时定位相关文件，不重复运行无关的后端全量回归。
2. 对请求 mock 做快照断言：五条资产、合法 `parameterSnapshot`、无 `physicalPath`、无 `workerCommand`、无 `outputUri`、无 `executionId`、无 `planSnapshot`、无 `stageType`。
3. 对进度和详情响应做 DOM/序列化检查：页面文本、按钮属性和可见列表不含 `uri`、`storageUri`、`storageKey`、盘符路径、Worker 临时目录或 manifest。
4. 检查 `pnpm lint:eslint` 和 `pnpm lint:stylelint`；格式化只触及本次修改文件，避免全仓库元数据变更。

**通过标准：** FY-4B 新增测试全部通过，类型检查和构建成功，现有 GF-6 模板仍可编译并保留原有三槽位行为。

**提交：** `test: cover FY-4B frontend contract and privacy boundaries`。

**执行步骤：**

- [ ] 先运行 FY-4B 定向单测，记录失败用例并只修复本计划涉及的文件。
- [ ] 运行一次 `pnpm typecheck`、`pnpm build` 和相关 lint，检查请求与 DOM 安全断言。
- [ ] 所有命令通过后提交回归测试变更。

## 任务 7：真实样例浏览器联调和交付记录

**前置条件：** Java 后端、Worker、系统文件服务和前端 API 代理已启动；真实 FY-4B 五文件已经登记为系统文件资产。后端真实 500 米全圆盘完整产物生成尚未在本计划前置验收中完成，因此联调时如资源不足必须记录为环境限制，不得把“请求已发出”写成“产物已正确生成”。

**接口：** 联调输入是五个系统资产 `id`；输出是任务 ID、阶段进度和 `result.zip` 下载结果，不保存 token、URI 或物理路径。

**执行顺序：**

1. 在数据源页面找到同一时次的 `L1_0500M`、`L1_1000M`、`L1_2000M`、`L1_4000M` 和 `GEO_4000M` 五个系统资产，确认页面提交的是五个 ID。
2. 选择 FY-4B AGRI，保留默认参数，确认预检返回 `FY-4B-AGRI/v1` 支持候选。
3. 创建任务并观察首个阶段 dispatch、后续阶段连续调度和 progress 轮询；确认浏览器没有重复 dispatch 请求。
4. 在任务运行中验证取消确认、取消后的终态和定时器停止；用后端允许重试的失败阶段验证重试后状态刷新。
5. 在资源允许时启用 `gradient`、近红外伪彩色和显著性，确认对应可选阶段只在开关启用时出现；参数依赖由前端自动补全。
6. 成功任务只通过 `result.zip` 下载，解压后检查标准 COG、质量 COG、摘要以及按开关生成的特征/融合/显著性结果；浏览器 Network 和页面 DOM 均不出现内部 URI 或物理路径。
7. 记录 API 响应摘要、任务 ID、参数快照摘要、阶段结果、下载结果、取消/重试结果和环境资源；不记录令牌、绝对路径或内部存储 URI。

**验收命令：**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit
pnpm typecheck
pnpm build
pnpm dev
```

**提交：** `docs: record FY-4B frontend integration acceptance`，记录应放在 Java 仓库 `docs/deployment/fy4b-agri-frontend-acceptance.md`；前端仓库只提交前端代码和测试，不跨仓库提交后端文件。

## 最终交付检查清单

- [ ] 下拉框可选 GF-6 和 FY-4B AGRI，切换不会串用另一产品状态。
- [ ] FY-4B 严格选择五个系统文件资产，role、ordinal、required 完全正确。
- [ ] 文件配对在客户端给出清晰错误，服务端预检仍是最终权威。
- [ ] 参数快照只含后端白名单字段，ROI、窗口、百分位和显著性依赖均在提交前校验。
- [ ] 预检 token 和 candidate 不跨输入复用，过期后要求重新预检。
- [ ] dispatch 有互斥保护，轮询和组件卸载能停止，终态不再产生请求。
- [ ] 阶段映射覆盖主链路和可选链路，未知值不会使页面崩溃。
- [ ] 取消、重试和 ZIP 下载按钮的可用条件与后端状态一致。
- [ ] 浏览器请求和页面 DOM 不包含物理路径、Worker 命令、内部 URI、manifest 或执行 ID。
- [ ] FY-4B 真实样例完成浏览器联调；若未完成 500 米全圆盘产物生成，交付记录明确写出资源限制和未验收项。
- [ ] `pnpm test:unit`、`pnpm typecheck`、`pnpm build` 和相关 lint 命令有可复核输出。

---

# 中文大白话核对版

完整独立版本见：`docs/superpowers/plans/2026-08-27-fy4b-agri-frontend-integration-plain-language.md`。

这次前端要做的事情，就是把现在只会处理 GF-6 的那块“多源卫星处理”面板，增加一个 FY-4B AGRI 入口。

用户首先要在系统已经登记的文件列表里选五个文件：500 米、1000 米、2000 米、4000 米四个 FDI 文件，再加一个 4000 米 GEO 文件。浏览器只把这些文件在系统里的数字 ID 交给后端，不能把 `D:\...` 这样的电脑路径传过去。五个文件必须属于同一次观测，时间、版本和文件种类对不上时，按钮要直接告诉用户哪里不对。

文件选好后，用户可以决定是否裁剪区域、是否提取特征、是否做近红外伪彩色、是否做显著性检测。前端把这些选择整理成后端规定的 JSON 字符串。比如用户选择“用梯度做显著性”，页面要自动把“特征提取”和“梯度”也打开，用户不用自己记这个依赖。输入不合法时，前端先拦住，不让错误请求跑到后端。

点击开始后，页面按固定顺序做四件事：先问后端这五个文件是不是一个受支持的 FY-4B 包，再用后端返回的临时凭证创建任务，然后一次只调度一个可以执行的阶段，最后不断查询进度。页面不能因为轮询或网络重试同时发出两个调度请求，否则同一个阶段可能被重复启动。

进度区域要让人看懂：显示“验证五文件包”“构建名义网格”“辐射定标与名义定位”“生成标准 COG”等中文名称，同时显示等待、执行中、已完成、失败、恢复中等状态。后端以后增加了新状态，页面显示“未知状态”也要保持正常，不能白屏。

任务运行时可以取消；失败阶段如果后端允许重试，页面显示重试按钮。任务成功后只提供一个“下载全部最终结果 ZIP”按钮。页面不展示 Worker 路径、服务器目录、内部 URI、中间 NPZ 或 manifest，这些是后端内部实现，不是用户要看的内容。

开发人员完成后，最少要让下面几件事能被核对：

1. 五个正确系统文件能通过预检，错一个能给出具体提示。
2. 默认参数能创建任务，ROI、窗口、百分位和显著性依赖的非法组合会被拦住。
3. 页面不会重复调度，任务结束或组件关闭后不会继续轮询。
4. 取消、重试、成功下载和失败提示都跟后端实际状态一致。
5. 请求内容没有电脑路径和内部执行字段，页面文字里也找不到这些信息。
6. 用真实 FY-4B 五文件走一遍预检到 ZIP 下载；如果电脑资源不足以完成真实 500 米全圆盘，就如实记录“联调请求完成、完整产物未验收”，不能把它说成算法已经验收。

这个计划没有要求前端做海岸线控制点残差，也没有要求前端自己计算导航、重投影或 COG。那些工作由后端 Worker 完成，前端只负责把合法选择交给后端，并把后端公开的任务结果清楚、安全地呈现出来。
