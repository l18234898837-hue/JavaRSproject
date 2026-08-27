# GF6 WFV L1A 前端完全替代实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to complete this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **给执行开发人员：** 必须使用独立分支或 worktree 执行。每个任务按“先写失败测试 -> 运行确认失败 -> 最小实现 -> 运行通过 -> 提交”完成。前端不得自行补写或猜测 Java/Worker 处理逻辑；接口不满足本计划时，应记录为后端依赖并阻止联调阶段继续。

**目标：** 以前端单一的 `GF-6 WFV L1A` 工作流完全替代现有 `GF-6 WFV Browse` 三文件页面，使用户能够上传完整 L1A 产品、设置工程处理参数、创建和跟踪任务，并下载最终结果。

**架构：** `MultiSourceSatelliteProcess.vue` 不再提供产品选择器和 Browse 三文件槽位，只渲染 L1A 的十个固定槽位及参数表单。`gf6WfvL1aTemplate.ts` 负责纯数据模型、产品基名归一化、文件校验和参数快照；`useProcessingTask.ts` 负责上传、预检、任务创建、启动、轮询、取消、重试和 ZIP 下载；`multiSourceSatellite.ts` 只定义与 Java API 对齐的请求/响应类型。Java 后端能力作为联调依赖，Browse 后端 profile 可继续存在，但前端不再调用它。

**技术栈：** Vue 3、TypeScript、Element Plus、现有 `http` API 客户端、pnpm、Vite；新增 Vitest 和 `@vue/test-utils` 仅用于本流程的纯函数与 composable 测试。

**规格依据：** 当前会话中用户的明确决定：“GF6 WFV L1A 前端完全代替 Browse 三文件流程”；不新建并存产品入口。

**依据：** 用户在当前会话明确决定“完全代替”；当前前端事实是 Browse 产品三文件流程：`productTemplates.ts` 只定义 `BROWSE_IMAGE/GEOREFERENCE/METADATA`，`useProcessingTask.ts` 固定三槽位，`MultiSourceSatelliteProcess.vue` 固定渲染 JPG/JGW/JSON。

## 全局约束

1. 前端产品固定为 `GF-6-WFV-L1A/v1`，页面中不得保留 Browse 产品下拉项、Browse 文件槽位、Browse 参数快照或 Browse 阶段中文标签。
2. 只替换前端入口，不删除 Java/Worker 内的 Browse 代码、数据库 profile 或历史任务数据。
3. L1A 必需上传：3 个 TIFF、XML、RPB、NAV、TIL；SHP、DBF、SHX 可选，但三者必须“全不传”或“全部传”。
4. 三个 TIFF 必须按 tile 1/2/3 传给后端，资产 `ordinal` 固定为 0/1/2；其他角色 ordinal 为 0。
5. 参数快照固定为 `schemaVersion="gf6-wfv-l1a-v1"`；不得向用户暴露 Worker stage code、本地路径、manifest、RPC 系数、授权 token 或 Java 内部 URI。
6. 页面必须显示 `NOMINAL_RPC_NO_DEM`、`UNVALIDATED`；启用显著性时必须显示 `CANDIDATE_ONLY`。
7. 页面中的“下载结果”只下载 Java 提供的任务最终 ZIP，不生成客户端假 ZIP，也不逐个拼接内部 artifact。
8. 不做地图浏览、全景预览、DEM 正射校正、控制点配准、目标识别或变化检测。
9. 所有浏览器文本使用中文；代码标识、API 枚举和值保持英文。

## Java 接口前置条件

开始真实联调前，前端开发人员必须得到 Java 开发人员确认以下接口已经存在且通过后端测试。前端只可为此编写类型和 mock，不可绕过：

| 接口 | 前端需要的最小字段 |
|---|---|
| `POST /api/multisource/processing-tasks/preflight` | `preflightToken`、一个候选、`packageId`、`profileKey.productCode`、`profileKey.profileVersion`、`supportStatus` |
| `POST /api/multisource/processing-tasks` | `taskId` |
| `POST /api/multisource/processing-tasks/{taskId}/dispatch` | `dispatched`、`stageId` |
| `GET /api/multisource/processing-tasks/{taskId}/progress` | `lifecycleStatus`、阶段 `stageKey/status/attempt`、artifact `type/sizeBytes/checksum/downloadable/retention`、限制状态 |
| `POST .../cancel`、`POST .../retry` | 成功或稳定错误码 |
| `GET .../result.zip` | 已完成任务返回 ZIP；未完成、无权限、未找到返回可翻译的 HTTP 错误 |

候选必须明确返回：

```json
{
  "packageId": "GF6-WFV-L1A-1420724603",
  "profileKey": { "productCode": "GF-6-WFV-L1A", "profileVersion": "v1" },
  "supportStatus": "SUPPORTED"
}
```

不能再用 `packageId.startsWith(...)` 判断产品类型；前端必须使用 `profileKey` 的两个字段精确比较。

## 目标用户流程

```text
打开“多源卫星数据处理”
  -> 只显示 GF-6 WFV L1A
  -> 选择 7 个必需文件和可选足迹文件
  -> 前端本地校验文件
  -> 上传文件
  -> 后端预检并返回唯一 L1A candidate
  -> 设置处理参数
  -> 创建并调度任务
  -> 每 2 秒轮询阶段状态
  -> 失败时提示并重试，运行中可取消
  -> 成功后下载最终 ZIP
```

## 文件职责

| 文件 | 改动职责 |
|---|---|
| `rsimageprocess-view/package.json` | 添加可运行的 Vitest 测试脚本和最小依赖。 |
| `rsimageprocess-view/vitest.config.ts` | 配置 `@` 别名、jsdom、测试文件匹配。 |
| `src/features/multisource/gf6WfvL1aTemplate.ts` | L1A 唯一模板、文件校验、产品基名、参数快照、asset 生成。 |
| `src/features/multisource/productTemplates.ts` | 删除 Browse 专用公开类型和默认 Browse 模板；仅保留可复用的通用文件工具，或把工具迁入 L1A 模块。 |
| `src/api/multiSourceSatellite.ts` | L1A-only 请求/响应类型，不再导入 Browse `SlotId` 或 `ProductFileRole`。 |
| `src/features/multisource/useProcessingTask.ts` | 用 L1A 模板和 form 状态重写上传与任务状态机。 |
| `src/components/LeftPanel/MultiSourceSatelliteProcess.vue` | 移除产品下拉和 Browse 文案，渲染 L1A 文件、参数、状态、限制和下载。 |
| `src/features/multisource/*.test.ts` | 新增模板、状态机和展示层测试。 |

---

## Task 1：建立前端测试入口并锁定替换范围

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/package.json`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/vitest.config.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/test/setup.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aReplacementScope.test.ts`

**接口：**

```ts
export const GF6_WFV_L1A_PRODUCT = {
  productCode: "GF-6-WFV-L1A",
  profileVersion: "v1",
  parameterSnapshotSchema: "gf6-wfv-l1a-v1"
} as const;
```

**步骤：**

- [ ] 写失败测试，断言唯一公开产品常量是 L1A；不允许导入或渲染 `GF6_WFV_BROWSE_TEMPLATE`、`BROWSE_IMAGE`、`GEOREFERENCE`、`DEFAULT_PROCESS_SNAPSHOT`。
- [ ] 运行：`pnpm vitest run src/features/multisource/gf6WfvL1aReplacementScope.test.ts`；预期当前失败，因为项目没有 Vitest 脚本和替换范围尚未完成。
- [ ] 添加 `vitest`、`@vue/test-utils`、`jsdom`，在 `package.json` 增加：`"test:unit": "vitest run"`；配置 `@` 指向 `src`。
- [ ] 在测试 setup 中仅注册 Vue/Element Plus 测试必要内容，不引入真实网络或地图运行时。
- [ ] 运行同一测试至通过，再运行 `pnpm typecheck`。
- [ ] 提交：`test: add GF6 L1A frontend test harness`。

**验收：** 开发人员可用一条命令执行本功能测试；没有因为测试引入浏览器真实请求或全局副作用。

## Task 2：重写 L1A 文件模板、产品基名和本地校验

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/productTemplates.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.test.ts`

**固定类型：**

```ts
export type Gf6L1aSlotId =
  | "tile1" | "tile2" | "tile3" | "metadata" | "rpc" | "navigation" | "tileIndex"
  | "shp" | "dbf" | "shx";

export type Gf6L1aFiles = Readonly<Partial<Record<Gf6L1aSlotId, File>>>;

export interface Gf6L1aUploadedFile {
  readonly sysFileId: string | number;
  readonly file: File;
}

export type Gf6L1aUploadedFiles = Readonly<
  Partial<Record<Gf6L1aSlotId, Gf6L1aUploadedFile>>
>;

export interface Gf6L1aAssetRequest {
  sourceType: "SYS_FILE";
  sourceId: string | number;
  role: Gf6L1aRole;
  ordinal: 0 | 1 | 2;
  required: boolean;
}

export interface Gf6L1aFileValidationResult {
  valid: boolean;
  missingSlotIds: readonly Gf6L1aSlotId[];
  invalidSlotIds: readonly Gf6L1aSlotId[];
  incompleteFootprintSidecars: boolean;
  productStem: string | null;
  diagnostics: readonly string[];
}

export function normalizeGf6L1aProductStem(name: string): string;
export function validateGf6WfvL1aFiles(files: Gf6L1aFiles): Gf6L1aFileValidationResult;
export function buildGf6WfvL1aAssets(uploaded: Gf6L1aUploadedFiles): Gf6L1aAssetRequest[];
```

**归一化规则：**

- `GF6_..._L1A1420724603-1.tiff`、`-2.tiff`、`-3.tiff` 去除扩展名和末尾 `-1/-2/-3` 后，必须得到同一个产品基名。
- XML、RPB、NAV、TIL、SHP、DBF、SHX 只去除扩展名后，必须得到同一个产品基名。
- 名称含 `_thumb`、扩展名为 `.jpg/.jpeg/.jgw/.json` 的文件不属于 L1A 槽位，必须拒绝。

**步骤：**

- [ ] 写失败测试：真实样例文件名归一化为同一产品基名；tile 序号错位、缺少任一必需文件、错误扩展名、重复同槽位、仅上传 SHP/DBF/SHX 中一部分、元数据基名不一致均失败。
- [ ] 写失败测试：`buildGf6WfvL1aAssets` 的三块 TIFF 角色均为 `MAIN_TIFF_TILE`，ordinal 依次为 `0/1/2`；必需文件的 `required=true`，三类足迹文件为 `required=false`。
- [ ] 运行：`pnpm vitest run src/features/multisource/gf6WfvL1aTemplate.test.ts`，确认失败。
- [ ] 实现模板、归一化、校验和 asset 生成；不复用 Browse 的三文件 `SlotId` 联合类型。
- [ ] 从 `productTemplates.ts` 删除 Browse 专用模板、默认快照和 Browse 角色。若有其他页面调用其中通用的 `basenameWithoutExtension`，保留该纯函数但不保留 Browse 产品定义。
- [ ] 运行模板测试和 `pnpm typecheck`，提交：`feat: replace Browse file template with GF6 L1A`。

**验收：** 真正的 L1A 文件名可以通过；Browse 的 JPG/JGW/JSON 不能通过或出现在任何前端槽位中。

## Task 3：严格实现 L1A 参数表单模型和参数快照

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aParameters.test.ts`

**固定参数接口：**

```ts
export interface Gf6L1aForm {
  targetCrs: "EPSG:4326";
  resolution: number;
  rpcHeight: number;
  roi: [number, number, number, number] | null;
  bands: number[];
  features: { enabled: boolean; metrics: Array<"statistics" | "gradient" | "local_variance"> };
  fusion: { enabled: boolean; red: number; green: number; blue: number };
  saliency: { enabled: boolean; source: `B${number}`; windowSize: number; threshold: number };
}

export function buildGf6WfvL1aSnapshot(form: Gf6L1aForm): string;
```

**步骤：**

- [ ] 写失败测试：默认参数固定为 EPSG:4326、0.00015、0 米、八波段全选、全部可选功能关闭。
- [ ] 写失败测试：分辨率不是有限正数、ROI 不是 `minLon/minLat/maxLon/maxLat`、波段重复/越界、融合波段没有被选中、显著性 source 未选中、窗口不是大于等于 3 的奇数、阈值不在 0 至 1 均失败。
- [ ] 写失败测试：快照 JSON 只含 `schemaVersion/targetCrs/resolution/rpcHeightMode/rpcHeight/roi/bands/features/fusion/saliency`，其中 `rpcHeightMode` 固定为 `CONSTANT`，不得含 File、sysFileId 或路径。
- [ ] 运行：`pnpm vitest run src/features/multisource/gf6WfvL1aParameters.test.ts`，确认失败。
- [ ] 实现校验和快照；禁止页面提供 DEM、高程模式选择、大气校正或任意算法操作。
- [ ] 运行测试和 `pnpm typecheck`，提交：`feat: define GF6 L1A processing snapshot`。

**验收：** 所有参数能用一个稳定 JSON 传给 Java；非法参数在创建任务前被页面阻止。

## Task 4：将 API 类型从 Browse 改为 L1A 任务契约

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/api/multiSourceSatellite.test.ts`

**固定类型：**

```ts
export interface PreflightCandidate {
  id: string;
  packageId: string;
  profileKey: { productCode: string; profileVersion: string };
  supportStatus: "SUPPORTED" | "UNSUPPORTED";
  confidence: number;
}

export interface ExecutionProgressArtifact {
  stageId: number;
  type: string;
  sizeBytes: number;
  checksum: string;
  retention: "INTERMEDIATE" | "FINAL";
  downloadable: boolean;
}
```

**步骤：**

- [ ] 写失败测试或 adapter mock 断言：预检资产使用 `Gf6L1aAssetRequest[]`；候选匹配只接受 `GF-6-WFV-L1A/v1`；进度 artifact 不依赖或显示 `uri`。
- [ ] 运行定向测试，确认当前 API 类型仍从 Browse `ProductFileRole/SlotId` 导入而失败。
- [ ] 删除 API 模块中 Browse 类型导入、`ProcessingAssetRequest` 和 `ProcessingSlotFiles`；统一为 L1A 资产类型。
- [ ] 补充前置条件所需的 profileKey 和 artifact 安全字段；若 Java 尚未返回这些字段，先保留 TypeScript 类型并让 mock 覆盖，实际联调列为阻塞，不得退回 packageId 前缀判断。
- [ ] 保留既有 upload、preflight、create、dispatch、progress、cancel、retry、ZIP 下载 URL，不新增 `/start` 这类 Java 未提供的接口。
- [ ] 运行测试和 `pnpm typecheck`，提交：`feat: align frontend API with GF6 L1A contract`。

**验收：** 前端请求和响应类型没有 Browse 文件角色；候选和结果判断有可验证的 L1A 契约。

## Task 5：重写 L1A 任务状态机

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.test.ts`

**状态机要求：**

```text
EMPTY/SELECTED -> UPLOADING -> UPLOADED
全部上传且本地合法 -> PREFLIGHT_READY
预检成功且唯一 L1A candidate -> CANDIDATE_READY
创建并 dispatch -> CREATED/RUNNING
轮询 -> RUNNING | RECOVERY_PENDING | SUCCEEDED | FAILED | CANCELED
FAILED/RECOVERY_PENDING -> retry
RUNNING/CREATED -> cancel
SUCCEEDED -> download ZIP
替换任一文件/重置/卸载 -> 停止轮询并清空任务上下文
```

**步骤：**

- [ ] 写失败测试：slots 初始化为全部十个 L1A 槽位；上传 tile2 产生 ordinal 1；必须全部必需槽位上传后才允许预检。
- [ ] 写失败测试：预检只接受一个 `profileKey=GF-6-WFV-L1A/v1` 的 supported candidate；零个、多个、错误 profile 均给出中文错误。
- [ ] 写失败测试：创建任务参数必须来自 `buildGf6WfvL1aSnapshot(form)`，不允许发送 `DEFAULT_PROCESS_SNAPSHOT`。
- [ ] 写失败测试：终态、取消、重置、替换文件、组件卸载均停止轮询；错误轮询以 5 秒重试但不覆盖已有成功状态。
- [ ] 运行：`pnpm vitest run src/features/multisource/useProcessingTask.test.ts`，确认失败。
- [ ] 重写 composable 的 slots、files、validation、buildAssets、productName、runPreflight、createAndStart；删除 Browse 前缀、三个文件数量和 Browse 默认模板的硬编码。
- [ ] 暴露 `form`、`artifacts`、L1A validation、限制状态和已有任务控制方法给 Vue 页面。
- [ ] 运行测试和 `pnpm typecheck`，提交：`feat: run GF6 L1A processing tasks from frontend`。

**验收：** 同一个用户动作不会创建两次任务；任务状态的变化不会因为文件替换或组件卸载继续污染页面。

## Task 6：完全替换页面为 L1A 文件与参数工作区

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.test.ts`

**页面内容和顺序：**

1. 标题固定为“GF-6 WFV L1A 工程处理”，不显示产品下拉。
2. 文件区：七个必需槽位与三个可选足迹槽位；显示扩展名、上传状态、文件名、删除图标和具体校验提示。
3. 参数区：分辨率、RPC 高程、ROI、波段多选、特征开关/复选项、融合 RGB 下拉、显著性 source/window/threshold。
4. 提示区：`无 DEM 名义 RPC` 和 `定位精度未验证` 始终可见；显著性开启后显示 `仅候选显著性结果`。
5. 任务区：预检候选、阶段状态、失败重试、取消、最终结果下载和重新处理。

**步骤：**

- [ ] 写失败组件测试：没有 `select#product-template`，没有 JPG/JGW/JSON 文案，页面渲染十个 L1A 槽位。
- [ ] 写失败组件测试：融合开关关闭时 RGB 控件不可编辑；显著性关闭时相关控件不可编辑；开始处理按钮只有本地文件合法、上传完成且不忙时可点击。
- [ ] 写失败组件测试：阶段键映射为中文 L1A 阶段名；下载按钮只在 `SUCCEEDED` 出现。
- [ ] 运行：`pnpm vitest run src/components/LeftPanel/MultiSourceSatelliteProcess.test.ts`，确认失败。
- [ ] 重写模板和 script setup，使用 Element Plus 的 `el-switch`、`el-checkbox-group`、`el-select`、`el-input-number`、`el-button`；删除旧原生产品 selector。
- [ ] 保持面板紧凑、文本不溢出、操作区不嵌套卡片；删除操作使用图标按钮和 `title`。
- [ ] 完成窄侧栏宽度和桌面宽度的浏览器检查，修正长文件名、错误提示和按钮的换行或截断。
- [ ] 运行组件测试、`pnpm typecheck`，提交：`feat: replace Browse page with GF6 L1A workspace`。

**验收：** 用户打开页面时只会看到 L1A；不会误把 Browse 文件放进任务；所有参数控件与任务状态可用且可理解。

## Task 7：实现错误提示、结果清单和安全下载体验

**文件：**

- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/src/features/multisource/gf6WfvL1aPresentation.test.ts`

**错误码映射：**

| 错误码/状态 | 页面文案 |
|---|---|
| `GF6_EVIDENCE_INVALID` | 产品文件不完整或彼此不匹配，请检查三块影像和元数据。 |
| `GF6_PARAMETER_INVALID` | 处理参数无效，请检查分辨率、ROI 和波段设置。 |
| `ROI_DOES_NOT_INTERSECT` | ROI 与影像名义覆盖范围不相交，请调整范围。 |
| `NOMINAL_RPC_NO_DEM` | 当前使用无 DEM 名义 RPC 校正。 |
| `UNVALIDATED` | 定位精度尚未通过独立验证。 |
| `CANDIDATE_ONLY` | 显著性结果仅用于候选区域查看。 |

**步骤：**

- [ ] 写失败测试：每个错误码映射到中文提示；未识别错误只显示通用错误，不展示异常堆栈。
- [ ] 写失败测试：artifact 仅显示 `FINAL` 且 `downloadable=true` 的项目；页面不得渲染 URI、存储键或本地路径。
- [ ] 实现阶段中文名称：拼接、辐射定标、RPC 名义网格、波段同网格检查、ROI 裁剪、标准 COG、特征提取、RGB 融合、显著性检查。
- [ ] 处理 ZIP 下载异常：未完成提示“任务尚未完成”，403 提示“无权下载该任务结果”，404 提示“结果不存在或已清理”，其他错误提示“结果下载失败”。
- [ ] 运行定向测试和 `pnpm typecheck`，提交：`feat: present GF6 L1A task status safely`。

**验收：** 用户能理解状态、限制和失败原因；浏览器开发者网络响应与页面均没有暴露内部路径或 Worker 协议内容。

## Task 8：前端回归、Java 联调和真实数据验收

**文件：**

- 新建：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/docs/validation/gf6-wfv-l1a-frontend-acceptance.md`
- 修改：`D:/Lwy_project_space/RSImageProcess/rsimageprocess-view/README.md` 或已有多源处理说明。

**真实验收数据：**

`D:/Lwy_project_space/RSImageProcess/otherfile/GF6_WFV_E107.6_N9.0_20260716_L1A1420724603`

**步骤：**

- [ ] 运行前端单元测试：`pnpm test:unit`。
- [ ] 运行前端门禁：`pnpm typecheck`，`pnpm build`。
- [ ] 和 Java 开发人员核对接口前置条件表；若 `profileKey`、artifact 权限字段或 ZIP 接口行为尚未到位，在验收报告写明 `BLOCKED`，不得伪造通过。
- [ ] 真实浏览器验收：上传三块 TIFF、XML、RPB、NAV、TIL；确认预检返回 `GF-6-WFV-L1A/v1`、参数快照版本正确；用小 ROI 创建任务并轮询到完成。
- [ ] 分别开启特征、RGB 融合、显著性，确认阶段名称、限制提示和最终下载按钮正确。
- [ ] 负向验收：上传 JPG/JGW/JSON、缺 tile、基名不一致、只上传 SHP、ROI 不相交、取消任务、失败重试、未完成下载和无权限下载。
- [ ] 打开下载 ZIP，确认没有原始 TIFF、VRT、manifest、本地路径、RPC 系数、token、栈信息；仅包含 Java 标记为 FINAL 的结果。
- [ ] 在验收文档写明命令结果、浏览器版本、接口版本、验证结果和已知限制。不得写真实绝对输入路径。
- [ ] 提交：`docs: record GF6 L1A frontend replacement acceptance`。

**验收：** 前端 Browse 三文件流程完全消失，L1A 单一流程完成“上传 -> 预检 -> 参数 -> 创建/启动 -> 轮询 -> 取消或重试 -> 最终 ZIP 下载”。

## 最终交付检查清单

- [ ] 页面无 Browse selector、JPG/JGW/JSON 文案、Browse role、Browse snapshot 和 Browse stage 标签。
- [ ] L1A 十个槽位、基名规则、tile ordinal、可选足迹文件规则都有自动化测试。
- [ ] L1A 参数 snapshot 严格校验并有自动化测试。
- [ ] 前端只通过 `profileKey` 确认候选产品，不依赖 packageId 前缀。
- [ ] 页面始终显示无 DEM/未验证限制；显著性开启时显示候选结果限制。
- [ ] 轮询在终态、取消、重置、文件替换和卸载时停止。
- [ ] 只显示可下载 FINAL artifact；ZIP 安全检查已完成。
- [ ] `pnpm test:unit`、`pnpm typecheck`、`pnpm build` 均有新鲜通过输出。
- [ ] 真实小 ROI 联调通过，或有明确的 Java 接口阻塞记录。
