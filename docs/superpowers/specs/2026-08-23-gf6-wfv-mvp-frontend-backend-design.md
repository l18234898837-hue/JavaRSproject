# GF-6 WFV 浏览产品 MVP 前后端对接设计

## 目标

为 `GF-6 WFV Level 1A` 浏览产品建立首条可端到端运行的多源处理链路：用户登录后，从产品选择器选择 GF-6 WFV 浏览产品，按模板上传一个完整产品包，后端预检、创建单任务并在服务器端自动执行全部阶段。任务成功后，用户下载一个经过归属校验的 ZIP，其中至少包含 `browse-report.json`。

此功能验证多源处理平台的完整边界，不将 GF-6 浏览影像表述为可做辐射定标、反射率、光谱指数、RPC 正射校正、地形校正或科学质量掩膜分析的原始科学产品。

## 已确认范围

首期产品类型为 `GF-6 WFV 浏览产品`，后端产品标识保持为：

```text
productCode: GF-6-WFV-BROWSE
profileVersion: v1
dataShape: RASTER_GRID
```

用户一次只能创建一个任务。一个任务只上传一个同名产品包：

```text
GF6-WFV-LEVEL1A-<product-id>.jpg
GF6-WFV-LEVEL1A-<product-id>.jgw
GF6-WFV-LEVEL1A-<product-id>.json
```

对应后端资产角色固定为：

| 前端上传槽位 | 文件扩展名 | 后端资产角色 | ordinal | 必填 |
| --- | --- | --- | --- |
| 浏览影像 | `.jpg` 或 `.jpeg` | `BROWSE_IMAGE` | `0` | 是 |
| 地理参考文件 | `.jgw` | `GEOREFERENCE` | `0` | 是 |
| 产品元数据 | `.json` | `METADATA` | `0` | 是 |

前端不向用户展示 `role`、`ordinal`、`SYS_FILE` 或 Worker 参数。三个槽位必须各有一个文件，三者去除扩展名后的文件名必须完全相同；任意槽位缺失、扩展名不匹配或文件名前缀不一致时，不允许上传预检。

页面保留产品选择器，但 MVP 只有唯一可选产品 `GF-6 WFV 浏览产品`。其他产品在具备模板、Worker 适配器、执行计划和产物契约之前不显示为可选项。

## 样例依据

联调样例位于：

```text
D:\Lwy_project_space\RSImageProcess\otherfile\GF
```

目录包含两个独立产品包：

```text
GF6-WFV-LEVEL1A-1420733151
GF6-WFV-LEVEL1A-1420734648
```

每个包各有 JPEG、JGW 和 JSON。现有 Python GF-6 适配器已经能用这两个样例识别两个 `GF-6-WFV-BROWSE` 候选；在设置 `MULTISOURCE_GF_SAMPLE_ROOT` 后，`tests/test_gf6_wfv_browse.py` 通过 6 个测试。

样例 JSON 及现有适配器验证以下边界：`satelliteid=GF6`、`sensorid=WFV`、`sublevel=LEVEL1A`、元数据 ID 与文件名产品 ID 一致、影像为正尺寸 RGB JPEG、JGW 为六个有限值且仿射变换非退化、JGW 原点与 JSON 足迹一致、时间有序、云量在 `[0, 100]` 内。

## 用户流程

```text
选择 GF-6 WFV 浏览产品
  -> 按三个槽位选择同名的 JPG/JGW/JSON
  -> 前端本地校验模板完整性
  -> 上传三个文件至现有 sys_file 文件服务
  -> 以三个 sys_file ID 调用预检
  -> 显示唯一 SUPPORTED 候选并确认
  -> 创建单任务（DEFAULT_PROCESS）
  -> 启动服务器端任务运行器
  -> 前端轮询进度
  -> 成功后下载 result.zip
```

前端事实来源是后端 `taskId` 和任务状态。浏览器刷新、关闭、网络波动或重复点击启动按钮不得决定任务是否继续执行。

## 参数契约

前端创建任务时使用固定、版本化的参数快照：

```json
{
  "schemaVersion": "1",
  "operation": "DEFAULT_PROCESS"
}
```

`DEFAULT_PROCESS` 是公开的前后端操作名。仅在 GF-6 WFV 浏览 Worker 内部，它映射到已有的 `BROWSE_VISUAL_FEATURES` 能力。客户端不发送内部 Worker 操作名，也不能指定 Worker 命令、执行 ID、输出路径、阶段图或物理文件路径。

后端必须拒绝以下情形：无效 JSON、未知 `schemaVersion`、不是 `DEFAULT_PROCESS` 的操作、候选产品不是 `GF-6-WFV-BROWSE/v1`、未支持的浏览/科学处理请求。拒绝应以稳定业务 code 返回，GF 浏览能力越界统一使用不可重试的 `UNSUPPORTED_BROWSE_OPERATION`。

## 前端设计

### 产品模板

新增独立的多源产品模板模块，而不是继续在 Vue 组件中维护自由文本卫星类型和操作列表。模板定义产品选择项、显示名、三个文件槽位、允许扩展名、后端角色、缺失提示、同名约束和固定参数快照。

`MultiSourceSatelliteProcess.vue` 负责渲染产品选择器、槽位上传控件、文件状态、预检候选、任务状态和结果下载，不负责拼装角色规则、Axios 请求细节或轮询状态机。

每个槽位的本地状态为：`EMPTY`、`SELECTED`、`UPLOADING`、`UPLOADED`、`FAILED`。只有所有槽位均为 `UPLOADED`、存在 `sysFileId` 且同名规则通过时，才能触发预检。删除或替换预检前文件会清除已有预检结果和任务创建上下文。

### API 客户端与状态机

前端 API 模块取代现有模拟的 `/multi-source-satellite/*` 请求，并封装：文件上传、预检、创建单任务、启动任务、查询详情/进度、取消、重试和 ZIP 下载。现有全局 Axios 拦截器继续发送 `Authorization: Bearer <token>`。

前端任务状态机至少区分：

| 后端任务状态 | 页面状态 | 操作 |
| --- | --- | --- |
| `CREATED` | 已创建，等待启动 | 启动、取消 |
| `RUNNING` | 正在处理 | 查询进度、取消 |
| `RECOVERY_PENDING` | 等待恢复 | 显示失败阶段、允许合法重试 |
| `SUCCEEDED` | 处理完成 | 下载 ZIP、重新创建任务 |
| `FAILED` | 处理失败 | 显示失败阶段、允许合法重试 |
| `CANCELED` | 已取消 | 重新创建任务 |

启动后每 2 秒查询一次进度；连续 10 次无状态变化后改为每 5 秒。终态、页面卸载、切换任务或取消任务时必须清除轮询计时器。前端不会反复调用单阶段 `dispatch` 来推动任务。

## 后端设计

### 资产、预检与创建

继续复用：

```text
POST /system/file/uploadStatic
POST /api/multisource/processing-tasks/preflight
POST /api/multisource/processing-tasks
```

预检请求把前端模板映射为三个 `SYS_FILE` 资产。`SysFileAssetStorageAdapter` 继续负责验证文件记录、受管上传根目录、可读性和校验和。Worker 的现有 GF-6 适配器是产品真实性的最终裁判，前端本地校验仅用于减少无效请求。

由于 MVP 的三个上传槽位只能组成一个同名产品包，预检必须恰好返回一个 `SUPPORTED` 的 `GF-6-WFV-BROWSE/v1` 候选。返回零个候选、多个候选或任何其他产品配置都视为预检失败，页面不创建任务。样例目录中的两个产品必须拆为两个独立任务，不以一个批次页面处理。

### 服务器端自动执行

现有 `POST /api/multisource/processing-tasks/{taskId}/dispatch` 的语义是“调度一个下一可运行阶段”。为满足自动完成全部阶段的 MVP，新增：

```text
POST /api/multisource/processing-tasks/{taskId}/start
```

该接口无请求体，先执行任务归属校验，再把任务提交给服务器端单任务运行器，返回 `202 Accepted`。运行器在后端进程中连续调用既有单阶段调度用例，直到任务进入 `SUCCEEDED`、`FAILED` 或 `CANCELED`，或当前没有可运行阶段。它为同一 `taskId` 保持互斥，重复启动返回“已启动”而不创建并发执行。

取消接口保留现有语义。运行器每次调度前、每次阶段返回后重新读取任务状态；发现取消或终态时停止继续调度。重试接口只重置合法失败阶段，用户随后可再次调用 `start` 恢复后续阶段。

运行器使用后端受控执行器，不引用浏览器会话、请求线程、物理输入路径或客户端轮询状态。进程重启恢复、分布式调度和队列不属于 MVP；重启前未完成任务会保持既有持久化状态并由用户重新启动或重试。

### GF-6 Worker 执行计划

Worker 的 `build-plan` 不能继续无条件返回单个 `VALIDATE_SOURCE` 阶段。对于 `GF-6-WFV-BROWSE/v1` 和公开的 `DEFAULT_PROCESS`，它生成固定 DAG：

```text
VALIDATE_BROWSE
  -> GEOREFERENCE_BROWSE
  -> EXTRACT_VISUAL_FEATURES
  -> WRITE_BROWSE_REPORT
```

每个阶段仅访问 manifest 中授权的三份输入。由于每个 Worker 调用有自己的执行目录，阶段不得依赖上一个阶段的未持久化临时文件；需要的信息从三个原始输入重新读取并验证。

阶段职责固定为：

| 阶段 | 行为 | 产物 |
| --- | --- | --- |
| `VALIDATE_BROWSE` | 重新执行 GF-6 产品包完整性、JPEG、JGW 和 JSON 证据校验。 | 无 |
| `GEOREFERENCE_BROWSE` | 解析仿射 world file，验证非退化变换与足迹一致性，准备近似地理参考信息。 | 无 |
| `EXTRACT_VISUAL_FEATURES` | 从 RGB 浏览影像提取最小、可重复的视觉特征：宽度、高度与基础 RGB 统计。 | 无 |
| `WRITE_BROWSE_REPORT` | 汇总经验证的产品元数据、近似地理参考方法和视觉特征，写出确定性的 JSON 报告。 | `browse-report.json` |

执行请求需要补充候选 `packageId`，使 Worker 在阶段执行时绑定创建任务时的同一产品候选，而不根据目录中的其他文件猜测产品。Worker 对 `parameterSnapshot` 解析并验证后，将 `DEFAULT_PROCESS` 映射到内部 `BROWSE_VISUAL_FEATURES`。它返回最终报告的逻辑 URI、SHA-256、类型和字节数；Java 端继续使用现有 artifact validator 验证文件位于当前执行目录的受控 `output` 下。

### 结果归档与下载

Worker 的最终报告先由现有产物校验器验证，并登记为任务产物。任务转为 `SUCCEEDED` 后，后端结果归档服务从已登记产物创建：

```text
<multisource.result-root>/<task-id>/result.zip
```

归档服务使用临时文件后原子替换，ZIP 内仅放已验证且属于该任务的文件。首期 ZIP 至少包含 `browse-report.json`；后续可以新增预览图、GeoJSON 或其他浏览产物，但不能把原始上传文件、Worker 清单、物理路径或授权信息打包进去。

下载接口固定为：

```text
GET /api/multisource/processing-tasks/{taskId}/result.zip
```

接口先验证登录用户拥有任务，再确认任务状态为 `SUCCEEDED` 并且 ZIP 已存在，最后以 `application/zip` 和安全的 `Content-Disposition` 文件名流式返回。任何未授权、未完成、无产物或归档丢失的请求都不暴露物理路径。

## 错误与权限边界

前端应提供明确的用户提示，但只依据稳定 code、任务状态和阶段状态做程序分支，不解析 message 文本。最低限度的错误分类如下：

| 情况 | 处理 |
| --- | --- |
| 槽位缺失、扩展名不符、文件名前缀不一致 | 前端阻止预检并指出具体槽位。 |
| 上传失败 | 槽位保留失败状态，用户可只重试该文件。 |
| 预检无支持候选 | 显示后端返回的识别失败信息，不创建任务。 |
| 预检令牌失效 | 清除候选与令牌，要求重新预检。 |
| 任务重复启动 | 显示已在运行，不创建第二个服务器运行器。 |
| Worker 或阶段失败 | 显示失败阶段和尝试次数；仅在后端允许时显示重试。 |
| 取消 | 立即停止轮询以外的启动行为，最终以轮询得到的 `CANCELED` 为准。 |
| 未登录或越权 | 按现有 Axios 401 流程登录；不得显示其他用户任务或产物信息。 |
| ZIP 不存在 | 显示“结果归档不可用”，不暴露存储实现。 |

## 测试与验收

Python 测试继续使用环境变量：

```text
MULTISOURCE_GF_SAMPLE_ROOT=D:\Lwy_project_space\RSImageProcess\otherfile\GF
```

测试覆盖必须包括：

1. 两个真实样例分别通过 GF 产品识别，缺失 JGW、无效 JSON、无效 JGW、元数据身份不一致均被拒绝。
2. `DEFAULT_PROCESS` 只为 GF-6 WFV 浏览候选生成四阶段 DAG；任何科学处理操作被拒绝。
3. 四个阶段均可由 Worker 执行，最终报告是确定性 JSON，并返回可通过 Java artifact validator 的 URI、大小和 SHA-256。
4. 后端运行器一次启动执行四阶段，重复启动不并发，取消会停止后续阶段，失败/重试遵守最大尝试次数。
5. 结果归档只包含已登记产物，任务归属校验后才能下载 ZIP。
6. 前端模板阻止不完整、扩展名错误和不同前缀文件；上传、预检、创建、启动、轮询、取消、重试和 ZIP 下载使用真实 API 类型。
7. 端到端验收从登录到下载 `result.zip` 成功，并确认 ZIP 内含 `browse-report.json` 且不含原始输入或物理路径。

## 非目标

- 批次创建、同时执行多个 GF 产品包、任务历史列表和分布式任务恢复。
- 非 GF-6 WFV 浏览产品的前端模板或执行逻辑。
- 辐射定标、反射率、光谱指数、RPC/正射校正、地形校正和科学质量掩膜。
- 客户端指定物理路径、Worker 命令、输出 URI、执行 ID、阶段图或授权信息。
