# FY-3H MERSI 前后端对接实施计划

> **供代理式执行者使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务顺序逐项执行。所有步骤均使用复选框跟踪；每个任务必须先让测试失败，再写最小实现，再验证通过。

**目标：** 在既有“多源卫星数据处理”页面中，支持 FY-3H MERSI-III `1000M + GEO1K` 成对 HDF 产品的上传、预检、参数提交、逐阶段进度、最终 Artifact 下载和失败重试，且不向浏览器泄露 Worker URI、存储 URI、物理路径或中间产物。

**架构：** 复用现有通用 REST 流程 `上传 -> preflight -> 创建任务 -> dispatch -> progress -> 下载`，不新建第二套任务系统。Java 先把公开 DTO 和下载端点收敛为安全的“任务归属 + 最终产物”契约；Vue 前端随后把目前写死的 GF-6 三文件模板泛化为产品模板，并为 FY-3H 增加双 HDF 配对校验和参数快照构造器。

**技术栈：** Java 17、Spring MVC、JUnit 5、AssertJ、Vue 3、TypeScript、Element Plus、Pinia、Axios、Vitest、pnpm、Vite。

---

## 0. 交接范围、职责和不可突破的边界

### 0.1 交接对象与仓库

本计划可交给一名全栈开发人员执行；若前后端由两人分别执行，按下列职责切分：

| 责任方        | 任务               | 交付物                                                         |
| ------------- | ------------------ | -------------------------------------------------------------- |
| Java 开发人员 | 任务 1             | 安全 REST DTO、单 Artifact 下载端点、控制器测试和 API 契约文档 |
| 前端开发人员  | 任务 0、2、3、4、5 | FY-3H 模板、参数表单、任务状态机、页面展示、前端测试和联调记录 |
| 联调负责人    | 任务 5             | 本地服务、真实 FY-3H 样例、浏览器验收记录                      |

涉及仓库：

```text
D:\Lwy_project_space\RSImageProcess\JavaRSproject
D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
```

后端起始基线必须包含：

```text
029f1a4 merge: integrate FY-3H MERSI backend
48a6dbc fix: align FY-3H standard COG input roles
```

前端现有入口和通用骨架：

```text
src/components/LeftPanel/MultiSourceSatelliteProcess.vue
src/features/multisource/productTemplates.ts
src/features/multisource/useProcessingTask.ts
src/api/multiSourceSatellite.ts
```

### 0.2 已完成的后端能力，不在本计划重复实现

FY-3H 后端已具备成对识别、25 通道解码与定标、质量掩膜、swath 网格化、标准 COG、特征提取、RGB 融合、显著性检测、Java 授权与真实 `2335/2350` 样例回归。其 DAG 键如下，前端只展示，不自行拼装或执行：

```text
VALIDATE_FY3H_PAIR
DECODE_MERSI_L1
CALIBRATE_MERSI_L1
APPLY_MERSI_QUALITY
GRID_MERSI_SWATH
WRITE_MERSI_STANDARD_COG
EXTRACT_MERSI_FEATURES              # 可选
FUSE_MERSI_BANDS                    # 可选
DETECT_MERSI_SALIENCY               # 可选
```

前端可下载的最终 Artifact 类型：

```text
STANDARD_MERSI_COG
STANDARD_MERSI_QUALITY_COG
STANDARD_MERSI_SUMMARY_JSON
MERSI_FEATURE_COG                   # 仅启用 gradient/local_variance 时存在
MERSI_FEATURE_SUMMARY_JSON          # 启用特征时存在
MERSI_FUSION_COG                    # 启用融合时存在
MERSI_SALIENCY_SCORE_COG            # 启用显著性时存在
MERSI_SALIENCY_MASK_COG             # 启用显著性时存在
```

### 0.3 强制边界

- 浏览器不得获得或拼接 `uri`、`storageUri`、`storageKey`、Worker 临时目录、物理磁盘路径、asset manifest、执行 ID 或中间 Artifact 路径。
- 前端只上传用户主动选择的两个文件，并以 `SYS_FILE` 的服务端文件 ID 创建 `OBSERVATION` 与 `GEO1K` 资产；不能读取本机绝对路径或试图直接访问 HDF 内容。
- `1000M` 和 `GEO1K` 文件名的配对只是浏览器中的即时提示；HDF 根属性、轨道、时间、版本和二维尺寸仍由 Worker 作权威校验。
- 前端只允许 Worker 已支持的 `continuousResampling="nearest"`。不得暴露双线性插值开关。
- UI 必须始终显示“FY-3H 定标公式仍待官方 PDF 人工复核”的静态说明，直到后端的 `FORMULA_REVIEW_STATUS` 被正式改为已复核。不得把处理成功文案写成“官方科学产品已认证”。
- 本计划只提供下载和元数据展示，不直接把 COG 作为 Cesium 图层加载，也不在浏览器解释质量位；这些需要后续独立的瓦片/预览服务和科学展示规范。

### 0.4 最终 HTTP 契约

保留既有端点：

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

新增安全的单 Artifact 下载端点：

```text
GET /api/multisource/processing-tasks/{taskId}/artifacts/{artifactType}/download
```

`preflight` 候选必须返回 profile 信息，供前端精确匹配而不是依赖 `packageId` 前缀：

```json
{
  "id": "candidate-opaque-id",
  "packageId": "FY3H_MERSI_GRAN_L1_20260701_2335_V0",
  "productCode": "FY-3H-MERSI",
  "profileVersion": "L1-1000M-GEO1K-v1",
  "supportStatus": "SUPPORTED",
  "confidence": 0.95
}
```

`progress` 和 `GET /{taskId}` 的 Artifact 项只能公开下列字段：

```json
{
  "stageId": 106,
  "type": "STANDARD_MERSI_COG",
  "checksum": "sha256:...",
  "sizeBytes": 123456,
  "retention": "FINAL",
  "downloadable": true,
  "fileName": "mersi.tif",
  "contentType": "image/tiff"
}
```

阶段项补充展示所需字段：

```json
{
  "stageId": 106,
  "stageKey": "WRITE_MERSI_STANDARD_COG",
  "status": "FAILED",
  "attempt": 1,
  "startedAt": "2026-08-27T09:00:00Z",
  "finishedAt": "2026-08-27T09:01:00Z",
  "errorCode": "WORKER_DISPATCH_FAILED",
  "errorMessage": "处理服务暂时不可用，请稍后重试"
}
```

禁止向前端返回 `uri`、`storageUri`、`storageKey`、绝对路径、`workRoot`、`artifactRoot`、manifest 内容或 Python 异常栈。

---

## 1. 执行前检查（任务 0，前端开发人员）

**文件：** 此任务不改代码，只记录基线、接口和验证结果。

- [ ] **步骤 1：记录两仓库的起始状态**

```powershell
git -C D:\Lwy_project_space\RSImageProcess\JavaRSproject status --short
git -C D:\Lwy_project_space\RSImageProcess\JavaRSproject log --oneline -3
git -C D:\Lwy_project_space\RSImageProcess\rsimageprocess-view status --short
git -C D:\Lwy_project_space\RSImageProcess\rsimageprocess-view log --oneline -3
```

预期：后端 `main` 含 `029f1a4`；前端若有其他人的未提交修改，只记录并避开，不回退、不混入本功能提交。

- [ ] **步骤 2：确认服务端 FY-3H 计划键和参数解析器**

阅读：

```text
JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py
JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_profile.py
JavaRSproject/rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py
```

确认标准 COG 输入键为 `grid` 和 `qualityGrid`；确认参数对象只包含 `targetCrs`、分辨率、`roi`、`continuousResampling`、`bands`、`features`、`fusion`、`saliency`，不能附带 GF-6 的 `schemaVersion` 或 `operation`。

- [ ] **步骤 3：跑后端基线回归**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
$env:PYTHONPATH=(Resolve-Path src).Path
.\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi.py tests\test_fy3h_mersi_pipeline.py tests\test_registry.py tests\test_cli.py -q

cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-modules/htht-multisource test
```

预期：Python FY-3H 相关测试通过；Java 模块测试通过。若失败，停止前端开发，先定位基线问题。

- [ ] **步骤 4：运行现有前端类型检查**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm typecheck
```

预期：命令退出码为 `0`。若已有类型错误，记录其文件和行号；只在错误与本计划直接相关时修复。

---

## 2. Java：冻结安全的任务与 Artifact 对外契约（任务 1，Java 开发人员）

**文件：**

- 修改：`htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingTaskController.java`
- 修改：`htht-admin/src/main/java/com/htht/web/controller/multisource/ProcessingExecutionController.java`
- 修改：`htht-admin/src/main/java/com/htht/web/controller/multisource/dto/PreflightResponse.java`
- 修改：`htht-admin/src/main/java/com/htht/web/controller/multisource/dto/TaskDetailResponse.java`
- 修改：`htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ExecutionProgressResponse.java`
- 修改：`htht-admin/src/main/java/com/htht/config/multisource/LocalWorkerResultArchiveService.java`
- 修改：`htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskArtifactView.java`
- 修改：`htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/GetTaskDetailService.java`
- 修改：`htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingTaskControllerTest.java`
- 修改：`htht-admin/src/test/java/com/htht/web/controller/multisource/ProcessingExecutionControllerTest.java`
- 修改：`htht-admin/src/test/java/com/htht/config/multisource/LocalWorkerResultArchiveServiceTest.java`

- [ ] **步骤 1：先为预检候选补红灯测试**

在 `ProcessingTaskControllerTest` 构造一项 `FY-3H-MERSI/L1-1000M-GEO1K-v1` 候选，断言返回体同时有 `productCode` 和 `profileVersion`，而不是在前端猜测 `packageId`：

```java
assertThat(response.getBody().getData().candidates()).singleElement().satisfies(candidate -> {
    assertThat(candidate.productCode()).isEqualTo("FY-3H-MERSI");
    assertThat(candidate.profileVersion()).isEqualTo("L1-1000M-GEO1K-v1");
});
```

- [ ] **步骤 2：先为公开进度 DTO 写红灯测试**

构造一项 `FINAL` 的 `STANDARD_MERSI_COG` 和一项 `INTERMEDIATE` 的 `GRIDDED_MERSI_RASTER`，断言：

```java
String body = objectMapper.writeValueAsString(controller.progress(101L).getBody().getData());
assertThat(body).doesNotContain("rs://", "storageUri", "storageKey", "D:\\", "workRoot", "artifactRoot");
assertThat(body).contains("STANDARD_MERSI_COG", "downloadable", "fileName", "sizeBytes", "retention");
assertThat(body).contains("GRIDDED_MERSI_RASTER");
```

并对对象字段断言：最终产物 `downloadable=true`、中间产物 `downloadable=false`；失败阶段返回稳定的 `errorCode`、`errorMessage`，未执行阶段对应字段为 `null`。

- [ ] **步骤 3：运行红灯测试**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-admin -am '-Dtest=ProcessingTaskControllerTest,ProcessingExecutionControllerTest' -DfailIfNoTests=false test
```

预期：新增断言因 DTO 仍包含 URI 或缺少公开字段而失败；若测试因装配问题失败，先修正 fixture，直到失败原因只剩契约未实现。

- [ ] **步骤 4：在应用层保留完成映射所需的受控元数据**

把 `TaskArtifactView` 扩展为包含 `sizeBytes`，并在 `GetTaskDetailService` 从已注册的 `Artifact` 填充该值。不要把 `storageKey` 添加到公开 DTO。

控制器根据 `detail.stages()`、`detail.plan().stages()` 和 Artifact 的 `type` 确定对应声明输出的 `retention` 与 `relativePath`；仅由该声明输出计算 `fileName` 和 `downloadable`。计算规则必须是：

```java
boolean downloadable = output.retention() == StageOutput.Retention.FINAL;
String fileName = Path.of(output.relativePath()).getFileName().toString();
String contentType = MediaTypeFactory.getMediaType(fileName)
    .map(MediaType::toString)
    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
```

不存在声明输出或一个 Artifact 对应多个声明输出时抛出服务端错误，不能猜测保留级别。

- [ ] **步骤 5：替换所有会泄露内部定位信息的 DTO 字段**

将候选、任务详情和进度的记录类型收敛为下列形状：

```java
public record ArtifactProgressResponse(
    long stageId,
    String type,
    String checksum,
    long sizeBytes,
    String retention,
    boolean downloadable,
    String fileName,
    String contentType
) {}

public record StageProgressResponse(
    long stageId,
    String stageKey,
    String status,
    int attempt,
    Instant startedAt,
    Instant finishedAt,
    String errorCode,
    String errorMessage
) {}
```

从 `TaskDetailResponse.InputAssetResponse` 删除 `storageUri`，从 `TaskDetailResponse.ArtifactResponse` 和 `ExecutionProgressResponse.ArtifactProgressResponse` 删除 `uri`。前端不需要且不得依赖这些字段。

- [ ] **步骤 6：实现按 Artifact 类型下载**

在 `ProcessingExecutionController` 添加端点：

```java
@GetMapping("/processing-tasks/{taskId}/artifacts/{artifactType}/download")
public void downloadArtifact(@PathVariable long taskId, @PathVariable String artifactType,
                             HttpServletResponse response) throws IOException {
    AccessSubject subject = currentSubject.get();
    TaskDetailView detail = detailUseCase.get(TaskId.of(taskId), subject);
    resultArchiveService.writeArtifact(detail, subject, artifactType, response);
}
```

`LocalWorkerResultArchiveService.writeArtifact` 必须：只从该用户拥有的任务中选取类型完全一致且声明为 `FINAL` 的唯一 Artifact；通过已注册的 `TaskArtifactStore` 打开文件；设置由受控 `fileName/contentType` 派生的响应头；拒绝未知类型、中间产物、重复类型、非终态任务和不属于当前用户的任务。路径、URI、相对路径都不能成为请求参数。

同时将 `write(...)` 的 ZIP 收集逻辑限制为 `FINAL` Artifact，禁止把任何 `work` 中间产物打入 ZIP。

- [ ] **步骤 7：为单文件下载和 ZIP 白名单写测试**

在 `ProcessingExecutionControllerTest` 和 `LocalWorkerResultArchiveServiceTest` 覆盖：

```java
assertThatThrownBy(() -> service.writeArtifact(detail, owner, "GRIDDED_MERSI_RASTER", response))
    .isInstanceOf(IllegalArgumentException.class);
assertThat(response.getHeader("Content-Disposition")).contains("mersi.tif");
assertThat(zipEntryNames).contains("mersi.tif", "quality.tif", "summary.json")
    .doesNotContain("mersi.npz", "quality-mask.npz");
```

另加越权任务、未知 `artifactType` 和 `../mersi.tif` 形式请求的拒绝测试。

- [ ] **步骤 8：运行绿灯测试与模块回归**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-admin -am '-Dtest=ProcessingTaskControllerTest,ProcessingExecutionControllerTest,LocalWorkerResultArchiveServiceTest' -DfailIfNoTests=false test
mvn -pl htht-modules/htht-multisource test
```

预期：所有新增测试通过，且返回 JSON 中不含路径和 URI。

- [ ] **步骤 9：提交 Java 契约变更**

```powershell
git add htht-admin/src/main htht-admin/src/test htht-modules/htht-multisource/src/main docs
git commit -m "feat: expose safe multisource artifact downloads"
```

---

## 3. 前端：建立 FY-3H 模板、双文件校验与参数快照（任务 2，前端开发人员）

**文件：**

- 修改：`rsimageprocess-view/package.json`
- 修改：`rsimageprocess-view/pnpm-lock.yaml`
- 新建：`rsimageprocess-view/vitest.config.ts`
- 修改：`rsimageprocess-view/src/features/multisource/productTemplates.ts`
- 新建：`rsimageprocess-view/src/features/multisource/fy3hMersiFiles.ts`
- 新建：`rsimageprocess-view/src/features/multisource/fy3hMersiParameters.ts`
- 新建：`rsimageprocess-view/src/features/multisource/__tests__/fy3hMersiFiles.spec.ts`
- 新建：`rsimageprocess-view/src/features/multisource/__tests__/fy3hMersiParameters.spec.ts`

- [ ] **步骤 1：引入只用于逻辑层的 Vitest 测试入口**

安装测试工具并添加脚本：

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm add -D vitest@^2.1.8
```

`package.json` 增加：

```json
{
  "scripts": {
    "test:unit": "vitest run",
    "test:unit:watch": "vitest"
  }
}
```

`vitest.config.ts` 使用 `node` 环境，并复用 `@` 到 `src` 的别名：

```ts
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: { environment: "node", include: ["src/**/*.spec.ts"] }
});
```

- [ ] **步骤 2：先写 FY-3H 文件名配对的红灯测试**

`fy3hMersiFiles.spec.ts` 至少覆盖：

```ts
import { describe, expect, it } from "vitest";
import { validateFy3hMersiPair } from "../fy3hMersiFiles";

describe("validateFy3hMersiPair", () => {
  it("accepts a matching 1000M and GEO1K filename pair", () => {
    expect(validateFy3hMersiPair({
      observation: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2335_1000M_V0.HDF"),
      geo1k: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2335_GEO1K_V0.HDF")
    })).toMatchObject({ valid: true });
  });

  it("rejects a GEO file placed in the observation slot", () => {
    expect(validateFy3hMersiPair({
      observation: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2335_GEO1K_V0.HDF"),
      geo1k: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2335_GEO1K_V0.HDF")
    }).valid).toBe(false);
  });

  it("rejects different identity or version values", () => {
    expect(validateFy3hMersiPair({
      observation: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2335_1000M_V0.HDF"),
      geo1k: new File([""], "FY3H_MERSI_GRAN_L1_20260701_2350_GEO1K_V1.HDF")
    }).valid).toBe(false);
  });
});
```

如果 Node 测试环境没有全局 `File`，在该测试文件顶部添加最小 File polyfill；不要把 polyfill 放入业务代码。

- [ ] **步骤 3：运行文件校验红灯**

```powershell
pnpm test:unit -- fy3hMersiFiles
```

预期：测试因模块不存在而失败。

- [ ] **步骤 4：实现 FY-3H 文件名校验器**

在 `fy3hMersiFiles.ts` 声明与 Worker 相同的文件名模式：

```ts
const FY3H_MERSI_FILENAME = /^(?<identity>FY3H_MERSI_.+)_(?<kind>1000M|GEO1K)_(?<version>[^_]+)\.HDF$/i;

export interface Fy3hMersiPairFiles {
  observation?: File;
  geo1k?: File;
}

export function validateFy3hMersiPair(files: Fy3hMersiPairFiles) {
  const observation = files.observation && FY3H_MERSI_FILENAME.exec(files.observation.name);
  const geo = files.geo1k && FY3H_MERSI_FILENAME.exec(files.geo1k.name);
  const valid = observation?.groups?.kind === "1000M" && geo?.groups?.kind === "GEO1K"
    && observation.groups.identity === geo.groups.identity
    && observation.groups.version === geo.groups.version;
  return { valid: Boolean(valid) };
}
```

校验结果还应返回可展示的中文错误码，例如 `MISSING_FILE`、`INVALID_FILENAME`、`WRONG_SLOT`、`UNMATCHED_IDENTITY`、`UNMATCHED_VERSION`。只用于提示；预检失败时以服务器信息为准。

- [ ] **步骤 5：先写 FY-3H 参数快照红灯测试**

`fy3hMersiParameters.spec.ts` 覆盖以下最小行为：

```ts
it("builds the exact default Worker parameter object", () => {
  expect(buildFy3hMersiSnapshot(defaultFy3hMersiForm())).toEqual({
    targetCrs: "EPSG:4326",
    resolutionDegrees: 0.01,
    continuousResampling: "nearest",
    bands: Array.from({ length: 25 }, (_, index) => index + 1),
    features: { enabled: false },
    fusion: { enabled: false },
    saliency: { enabled: false }
  });
});

it("rejects an even local variance window", () => {
  expect(() => buildFy3hMersiSnapshot({ ...defaultFy3hMersiForm(), features: {
    enabled: true, metrics: ["local_variance"], windowSize: 4
  }})).toThrow(/奇数/);
});

it("rejects thermal bands as RGB inputs", () => {
  expect(() => buildFy3hMersiSnapshot({ ...defaultFy3hMersiForm(), fusion: {
    enabled: true, red: 20, green: 2, blue: 1, percentiles: [2, 98]
  }})).toThrow(/1 至 19/);
});
```

再补：空或重复波段、非有限分辨率、非法 ROI、`low >= high`、显著性窗口为偶数、启用显著性但来源未出现在选中波段或可用特征中的失败断言。

- [ ] **步骤 6：运行参数红灯**

```powershell
pnpm test:unit -- fy3hMersiParameters
```

预期：测试因模块不存在而失败。

- [ ] **步骤 7：实现只生成 Worker 接受字段的参数构造器**

在 `fy3hMersiParameters.ts` 创建表单类型、默认值和 `buildFy3hMersiSnapshot(form)`。输出必须与 `parse_fy3h_parameters` 一致：

```ts
export interface Fy3hMersiSnapshot {
  targetCrs: "EPSG:4326";
  resolutionDegrees: number;
  continuousResampling: "nearest";
  bands: number[];
  roi?: [number, number, number, number];
  features: { enabled: boolean; metrics?: ("statistics" | "gradient" | "local_variance")[]; windowSize?: number };
  fusion: { enabled: false } | {
    enabled: true; red: number; green: number; blue: number; percentiles: [number, number];
  };
  saliency: { enabled: false } | {
    enabled: true; source: string; windowSize: number; threshold: number;
  };
}
```

首版 UI 固定 `targetCrs="EPSG:4326"`，仅提交 `resolutionDegrees`。ROI 打开时才发送 `[minLon, minLat, maxLon, maxLat]`；关闭时完全省略 `roi`。特征关闭时必须输出 `{ enabled: false }`，不能留下 `metrics/windowSize`。融合关闭和显著性关闭也遵守同一规则。

显著性来源只从下列集合生成：所选波段的 `CH01_REFLECTANCE` 至 `CH19_REFLECTANCE`、`CH20_BRIGHTNESS_TEMPERATURE` 至 `CH25_BRIGHTNESS_TEMPERATURE`，以及已启用 `gradient/local_variance` 对应的 `gradient:<band>` 和 `local_variance:<band>`。不得允许手工自由输入来源字符串。

- [ ] **步骤 8：泛化产品模板并添加 FY-3H 模板**

将 `ProductFileRole`、`SlotId` 和 `ProductFiles` 从 GF-6 固定联合类型改为可扩展的字符串映射，同时保持 GF-6 行为不变。添加：

```ts
export const FY3H_MERSI_TEMPLATE: ProductTemplate = Object.freeze({
  id: "fy3h-mersi-l1-1000m-geo1k-v1",
  label: "FY-3H MERSI-III L1（1000M + GEO1K）",
  productCode: "FY-3H-MERSI",
  profileVersion: "L1-1000M-GEO1K-v1",
  slots: Object.freeze([
    Object.freeze({ id: "observation", label: "1000M 观测文件", role: "OBSERVATION", extensions: [".hdf"], required: true }),
    Object.freeze({ id: "geo1k", label: "GEO1K 定位文件", role: "GEO1K", extensions: [".hdf"], required: true })
  ])
});

export const PROCESSING_PRODUCT_TEMPLATES = Object.freeze([
  GF6_WFV_BROWSE_TEMPLATE,
  FY3H_MERSI_TEMPLATE
]);
```

模板需要一个可选的 `validateFiles(files)` 钩子。GF-6 继续调用其现有同名文件校验；FY-3H 调用 `validateFy3hMersiPair`。

- [ ] **步骤 9：运行逻辑绿灯和类型检查**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- fy3hMersiFiles fy3hMersiParameters
pnpm typecheck
```

预期：测试和类型检查通过；GF-6 的模板、角色、扩展名仍保持原样。

- [ ] **步骤 10：提交模板与参数变更**

```powershell
git add package.json pnpm-lock.yaml vitest.config.ts src/features/multisource
git commit -m "feat: add FY-3H MERSI task template and parameters"
```

---

## 4. 前端：收敛 API 类型和通用任务状态机（任务 3，前端开发人员）

**文件：**

- 修改：`rsimageprocess-view/src/api/multiSourceSatellite.ts`
- 重构：`rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- 新建：`rsimageprocess-view/src/features/multisource/processingPresentation.ts`
- 新建：`rsimageprocess-view/src/features/multisource/__tests__/useProcessingTask.spec.ts`
- 新建：`rsimageprocess-view/src/features/multisource/__tests__/processingPresentation.spec.ts`

- [ ] **步骤 1：先写 API 与状态机红灯测试**

使用依赖注入而非模块级 mock。让 `useProcessingTask` 接收 `template`、`buildSnapshot` 和最小 `ProcessingTaskApi`，测试以下行为：

```ts
it("creates FY-3H assets with OBSERVATION and GEO1K roles", async () => {
  const task = createTaskHarness(FY3H_MERSI_TEMPLATE, buildFy3hMersiSnapshot);
  await task.uploaded("observation", 11);
  await task.uploaded("geo1k", 12);
  await task.runPreflight();
  expect(api.preflight).toHaveBeenCalledWith({ assets: [
    { sourceType: "SYS_FILE", sourceId: 11, role: "OBSERVATION", ordinal: 0, required: true },
    { sourceType: "SYS_FILE", sourceId: 12, role: "GEO1K", ordinal: 0, required: true }
  ] });
});

it("accepts only the exact FY-3H profile candidate", async () => {
  api.preflight.mockResolvedValue(candidate("FY-3H-MERSI", "L1-1000M-GEO1K-v1"));
  await expect(task.runPreflight()).resolves.toMatchObject({ productCode: "FY-3H-MERSI" });
});

it("does not expose or use artifact uri values", () => {
  expect(publicArtifact).not.toHaveProperty("uri");
  expect(downloadProcessingArtifact).toHaveBeenCalledWith(101, "STANDARD_MERSI_COG");
});
```

另外覆盖：切换产品会清空已上传文件、预检 token、候选和轮询；终态停止轮询；网络轮询失败 5 秒后继续；`FAILED/RECOVERY_PENDING` 仅允许失败阶段重试；未成功任务不能下载；单 Artifact 只能在 `downloadable=true` 时下载。

- [ ] **步骤 2：运行状态机红灯**

```powershell
pnpm test:unit -- useProcessingTask processingPresentation
```

预期：测试因 composable 尚绑定 GF-6 固定模板、DTO 仍有 `uri`、无单 Artifact 下载函数而失败。

- [ ] **步骤 3：更新前端 API 类型和请求函数**

把 `PreflightCandidate` 增加 `productCode`、`profileVersion`。把 `ExecutionProgressStage` 扩展为 `startedAt`、`finishedAt`、`errorCode`、`errorMessage`；把 `ExecutionProgressArtifact` 更换为任务 1 的安全字段，删除 `uri`。

添加：

```ts
export const downloadProcessingArtifact = (taskId: number, artifactType: string) =>
  http.request<Blob>(
    "get",
    `/api/multisource/processing-tasks/${taskId}/artifacts/${encodeURIComponent(artifactType)}/download`,
    { responseType: "blob" }
  );
```

不接受路径、URI 或文件名作为下载函数参数。

- [ ] **步骤 4：将 composable 改成模板驱动**

使用下列边界替换所有 `GF6_WFV_BROWSE_TEMPLATE`、三固定槽位和 `DEFAULT_PROCESS_SNAPSHOT`：

```ts
export interface ProcessingTaskOptions {
  template: ReadonlyRef<ProductTemplate>;
  buildSnapshot: () => unknown;
  api?: ProcessingTaskApi;
}

export function useProcessingTask(options: ProcessingTaskOptions) { /* ... */ }
```

槽位状态按 `options.template.value.slots` 动态初始化。`buildAssets()` 按当前模板的 `role` 生成资产；`runPreflight()` 必须匹配候选的 `productCode/profileVersion`；`createAndStart()` 调用 `JSON.stringify(options.buildSnapshot())`。

保留现有轮询语义：首轮立刻请求，之后 2 秒；连续 10 次内容不变时改为 5 秒；网络错误 5 秒后重试；组件卸载或模板切换时清除 timer。`cancel()` 只发取消请求，状态只能由下一次 `progress` 更新为 `CANCELED`。

下载 ZIP 的默认文件名改为 `processing-result.zip`；下载单 Artifact 时使用后端公开的 `fileName`，并在 `URL.revokeObjectURL` 前等待触发点击。

- [ ] **步骤 5：建立纯展示映射**

在 `processingPresentation.ts` 提供纯函数，输入模板和安全 DTO，输出槽位、阶段、Artifact 的中文展示文本。FY-3H 阶段映射固定为：

```ts
export const FY3H_STAGE_LABELS = {
  VALIDATE_FY3H_PAIR: "校验 1000M 与 GEO1K 配对",
  DECODE_MERSI_L1: "解码 MERSI L1 数据",
  CALIBRATE_MERSI_L1: "执行 25 通道物理定标",
  APPLY_MERSI_QUALITY: "生成质量掩膜与统计",
  GRID_MERSI_SWATH: "按逐像元定位网格化",
  WRITE_MERSI_STANDARD_COG: "生成标准 MERSI COG",
  EXTRACT_MERSI_FEATURES: "提取可选特征",
  FUSE_MERSI_BANDS: "生成 RGB 融合产品",
  DETECT_MERSI_SALIENCY: "生成显著性产品"
} as const;
```

Artifact 映射必须显示“标准科学 COG”“质量掩膜 COG”“处理摘要”“特征 COG/摘要”“RGB 融合 COG”“显著性评分/掩膜”。未知键直接显示服务端键，不抛异常。测试断言任何 UI 文案中都不出现 `rs://`。

- [ ] **步骤 6：运行绿灯、类型检查和构建**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- useProcessingTask processingPresentation
pnpm typecheck
pnpm build
```

预期：GF-6 仍可生成原三项资产；FY-3H 只生成两个角色正确的资产；构建通过。

- [ ] **步骤 7：提交 API 和状态机变更**

```powershell
git add src/api/multiSourceSatellite.ts src/features/multisource
git commit -m "refactor: support product-specific processing tasks"
```

---

## 5. 前端：FY-3H 表单、进度和最终产品页面（任务 4，前端开发人员）

**文件：**

- 修改：`rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- 新建：`rsimageprocess-view/src/features/multisource/Fy3hMersiParameterForm.vue`
- 新建：`rsimageprocess-view/src/features/multisource/__tests__/fy3hMersiPresentation.spec.ts`

- [ ] **步骤 1：先写 FY-3H 展示红灯测试**

测试 `processingPresentation.ts` 或从组件提取的纯函数，覆盖：

```ts
it("labels all FY-3H stages in Chinese and preserves unknown stage keys", () => {
  expect(stageLabel("FY-3H-MERSI", "WRITE_MERSI_STANDARD_COG")).toBe("生成标准 MERSI COG");
  expect(stageLabel("FY-3H-MERSI", "FUTURE_STAGE")).toBe("FUTURE_STAGE");
});

it("shows individual download controls only for final downloadable artifacts", () => {
  expect(toArtifactItem({ type: "STANDARD_MERSI_COG", retention: "FINAL", downloadable: true }).canDownload).toBe(true);
  expect(toArtifactItem({ type: "GRIDDED_MERSI_RASTER", retention: "INTERMEDIATE", downloadable: false }).canDownload).toBe(false);
});
```

- [ ] **步骤 2：运行展示红灯**

```powershell
pnpm test:unit -- fy3hMersiPresentation
```

预期：新增映射或可下载判定尚不存在而失败。

- [ ] **步骤 3：将选择器和上传区改为当前模板驱动**

`MultiSourceSatelliteProcess.vue` 的下拉框遍历 `PROCESSING_PRODUCT_TEMPLATES`，而不是只渲染 GF-6。当前模板变化时：调用 composable 的 reset、重置 FY-3H 表单值、停止轮询、隐藏旧候选和旧 Artifact。

上传区按当前模板遍历槽位：

```vue
<article v-for="slot in selectedTemplate.slots" :key="slot.id">
  <strong>{{ slot.label }}</strong>
  <small>{{ slot.extensions.join("、").toUpperCase() }}</small>
  <input :accept="slot.extensions.join(',')" type="file" @change="handleFileChange(slot.id, $event)" />
</article>
```

FY-3H 选择时显示两个槽位：“1000M 观测文件（.HDF）”和“GEO1K 定位文件（.HDF）”。在开始按钮上方显示配对校验的中文失败原因；不得只提示“文件名不同”。开始按钮仅在两个文件上传成功、客户端配对通过、参数表单通过时可用。

- [ ] **步骤 4：实现 FY-3H 参数组件**

`Fy3hMersiParameterForm.vue` 使用 Element Plus 的 `el-input-number`、`el-checkbox-group`、`el-switch`、`el-select`，并通过 `v-model` 向父组件提供表单对象。必须具备：

```text
目标网格：固定 EPSG:4326；分辨率（度），默认 0.01
ROI：开关；开启后 minLon/minLat/maxLon/maxLat 四个数字输入
波段：1 至 25 的多选，默认全选；不可为空
特征：开关；statistics/gradient/local_variance 多选；只有 local_variance 时显示奇数窗口
RGB 融合：开关；R/G/B 只显示当前选中的 1 至 19 波段；百分位低值和高值
显著性：开关；来源下拉；奇数窗口和 0 至 1 阈值
```

参数组件不显示原始定标系数、质量位定义、Worker 路径、CRS 自由文本、双线性重采样或任何绕过后端校验的高级设置。若融合启用而当前选择没有至少三个反射波段，显示错误并禁用开始按钮。

参数区固定显示以下非关闭提示：

```text
处理结果为工程处理产物。FY-3H MERSI 定标公式仍待按官方 L1 PDF 完成人工复核；处理完成不代表该复核已完成。
```

- [ ] **步骤 5：展示阶段、错误和最终 Artifact**

阶段列表使用 `stageLabel`，显示状态、尝试次数、开始/结束时间和服务器公开的错误信息。`FAILED` 与 `RECOVERY_PENDING` 且存在失败阶段时显示重试按钮；`CREATED/RUNNING/RECOVERY_PENDING` 显示取消按钮；最终状态停止动画和轮询。

仅遍历 `artifact.downloadable === true` 的 Artifact 生成单文件下载按钮；显示中文类型、`fileName`、格式化 `sizeBytes` 和校验和前 12 位。显示“下载全部最终结果 ZIP”按钮，但不显示中间 NPZ、质量工作文件或 URI。下载失败时保留当前任务和列表，显示错误提示。

- [ ] **步骤 6：运行展示绿灯、类型检查和构建**

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit -- fy3hMersiPresentation processingPresentation useProcessingTask
pnpm typecheck
pnpm build
```

预期：所有测试通过；构建产物包含 FY-3H 页面逻辑；GF-6 选择后仍显示其原有三槽位。

- [ ] **步骤 7：提交 FY-3H 页面变更**

```powershell
git add src/components/LeftPanel/MultiSourceSatelliteProcess.vue src/features/multisource
git commit -m "feat: add FY-3H MERSI processing interface"
```

---

## 6. 联调、真实样例验收和交接（任务 5，联调负责人）

**文件：**

- 修改：`JavaRSproject/docs/deployment/multisource-backend-deployment.md`
- 新建：`JavaRSproject/docs/deployment/fy3h-mersi-frontend-acceptance.md`
- 可选修改：`rsimageprocess-view/.env.development`，只允许把 `VITE_API_PATH` 指向本地联调后端；禁止提交真实服务器凭据。

- [ ] **步骤 1：补齐部署与接口文档**

在部署手册写明：前端使用的 API 基址、前端静态资源部署边界、单 Artifact 下载端点、禁止公开 URI/路径、ZIP 仅含 `FINAL` Artifact、需要的跨域认证方式，以及前端开发服务器命令：

```powershell
cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm dev
```

- [ ] **步骤 2：在浏览器执行 FY-3H 正常路径**

使用只读真实样例目录中的 `2335` 成对文件：

```text
D:\Lwy_project_space\RSImageProcess\otherfile\FY3H_MERSI_20260701\FY3H_MERSI_20260701
FY3H_MERSI_GRAN_L1_20260701_2335_1000M_V0.HDF
FY3H_MERSI_GRAN_L1_20260701_2335_GEO1K_V0.HDF
```

验收顺序：选择 FY-3H 模板；上传两个文件；保留默认参数；预检得到唯一的 `FY-3H-MERSI/L1-1000M-GEO1K-v1` 候选；创建并 dispatch；轮询直至成功；确认可见标准科学 COG、质量 COG 和摘要；分别下载一个 COG、摘要和 ZIP。

- [ ] **步骤 3：在浏览器执行可选算法路径**

使用相同 `2335` 文件，启用 `gradient` 特征、选择 RGB `3/2/1`、启用显著性且来源选 `gradient:CH01_REFLECTANCE`。验收最终列表还出现特征 COG/摘要、融合 COG、显著性评分 COG、显著性掩膜 COG；禁用任一选项后，对应 Artifact 不应出现在下一次任务结果中。

- [ ] **步骤 4：执行关键失败路径**

逐项验证并记录浏览器截图或网络响应摘要：

```text
把 GEO1K 文件放到 1000M 槽位：开始按钮不可用，显示槽位错误。
选择 2335 的 1000M 加 2350 的 GEO1K：开始按钮不可用，显示配对不一致。
RGB 选择通道 20：表单拒绝并说明 RGB 仅限 1 至 19。
特征窗口填 4：表单拒绝并说明窗口必须为不小于 3 的奇数。
直接请求中间 Artifact 下载：服务器拒绝。
查看 progress/detail 网络响应：响应文本中没有 rs://、storageUri、D:\\、workRoot 或 artifactRoot。
```

- [ ] **步骤 5：验证异常场景保留有效结果**

使用 `2350` 成对文件完成至少到质量阶段的任务。确认服务端报告定位质量异常时，UI 不把任务描述成“数据完全正常”；若最终任务成功，仍只下载 `FINAL` Artifact。不要让前端把质量异常转成科学结论。

- [ ] **步骤 6：运行最终自动化回归**

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
$env:PYTHONPATH=(Resolve-Path src).Path
.\.venv\Scripts\python.exe -m pytest -q

cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-modules/htht-multisource test
mvn -pl htht-admin -am test

cd D:\Lwy_project_space\RSImageProcess\rsimageprocess-view
pnpm test:unit
pnpm typecheck
pnpm build
```

预期：三侧命令均退出 `0`。真实样例测试如因环境变量未配置而跳过，应在验收记录中写明原因，不能删除或弱化这些测试。

- [ ] **步骤 7：完成交接文档并提交**

`fy3h-mersi-frontend-acceptance.md` 必须列出：后端提交、前端提交、服务地址（不含密钥）、执行日期、`2335/2350` 结果、下载的 Artifact 类型、失败路径结果、已知限制和测试输出摘要。

```powershell
git -C D:\Lwy_project_space\RSImageProcess\JavaRSproject add docs/deployment
git -C D:\Lwy_project_space\RSImageProcess\JavaRSproject commit -m "docs: record FY-3H frontend acceptance"
```

前端仓库单独提交其文档或配置变更，不要跨仓库提交。

---

## 7. 最终验收清单

- [ ] 下拉选择中同时存在 GF-6 和 FY-3H；切换产品不会保留对方文件、候选、参数、任务或轮询。
- [ ] FY-3H 只接受两份 `.HDF`，且分别是 `1000M` 与 `GEO1K`，文件名身份和版本一致才可开始。
- [ ] 预检候选精确匹配 `FY-3H-MERSI/L1-1000M-GEO1K-v1`，前端不依赖包名字符串前缀。
- [ ] 参数快照能被 `parse_fy3h_parameters()` 接受；没有 GF-6 私有字段、非法重采样或未经支持的设置。
- [ ] RGB 只能使用 1 至 19 的选中反射波段；特征与显著性依赖关系能在提交前阻止非法组合。
- [ ] 前端显示全部九个可能阶段，并能处理未知未来阶段键而不崩溃。
- [ ] 仅 `FINAL + downloadable=true` Artifact 有单文件下载；ZIP 仅包含最终产物。
- [ ] 浏览器响应和 DOM 中没有内部 URI、物理路径、存储键、manifest 或 Worker 异常栈。
- [ ] `2335` 正常链路产出标准 COG、质量 COG、摘要；`2350` 定位质量异常不会导致 UI 伪称数据完全正常。
- [ ] 页面固定提示 FY-3H 公式官方 PDF 人工复核尚未完成；该项不被“任务成功”状态覆盖。
