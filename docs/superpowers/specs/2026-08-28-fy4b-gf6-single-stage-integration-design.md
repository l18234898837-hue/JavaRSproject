# FY-4B AGRI 与 GF-6 WFV L1A 单阶段接入设计

## 目标

在不改变 HY-1C CZI L2A、HY-2D 高度计和 FY-3H MERSI 现有执行契约的前提下，将源仓库中的 FY-4B AGRI 全圆盘 L1 与 GF-6 WFV L1A 工程处理能力接入当前 Java 后端和多源卫星前端。

两个新增产品都使用当前后端已经验证过的“一个 Java 任务阶段对应一次完整 Worker 调用”模式。Worker 内部仍按各自真实数据流程顺序执行，但只向 Java 返回最终产物。这样不要求升级共享的 Java 多阶段输入解析器，也不会改变三个既有产品的 profile、stage、artifact 或重试行为。

## 现状与约束

- 当前后端工作树包含尚未提交的 HY-1C、HY-2D、FY-3H 实现，这些文件属于既有在途成果，接入时必须做增量合并，不能以源仓库文件整份覆盖。
- 当前 `DispatchNextStageService` 为每个阶段授权并下发候选产品的原始文件，不会把上一阶段产物解析成下一阶段输入。HY-1C、HY-2D、FY-3H 因此都使用单阶段整合计划。
- 源仓库 FY-4B 使用完整多阶段 DAG；直接复制到当前后端会在第二阶段开始缺少上游产物。
- 源仓库 GF-6 L1A 的阶段计划缺少当前 Java 强制要求的 `inputs`、`outputs` 和 `parameterSnapshot`，并且多个阶段重复执行同一次完整 RPC 重采样，不能原样接入。
- 前端现有 GF-6 入口是 JPG/JGW/JSON 浏览产品，与真实 GF-6 WFV L1A 样本不匹配。历史 Browse 后端代码和历史任务保留，但前端入口改为 L1A 工程处理。
- 样本中的 GF-6 第二块 TIFF 大于 6 GB，当前 Spring 5,000 MB 和 Nginx 100 MB 限制不足以完成上传。
- 本次验证以代码逻辑为主，不运行 FY-4B 全圆盘或 GF-6 约 14 GB 数据的完整算法链。

## 总体架构

```text
Vue 产品模板与参数抽屉
  -> 逐文件上传，取得 SYS_FILE id
  -> preflight(assets: id + role + ordinal)
  -> Worker inspect，返回精确 profile candidate
  -> create(parameterSnapshot)
  -> dispatch 单一整合 stage
  -> Worker 内部执行产品专属流水线
  -> Java 校验最终 artifact type/URI/size/SHA-256
  -> progress / retry / cancel / result.zip
```

Java 的 REST API、预检 token、授权 manifest、任务状态、结果 ZIP 和权限校验继续复用现有实现。新增逻辑集中在 Python 产品适配器、整合管线、registry 精确路由、profile migration、前端产品模板和参数模块中。

## FY-4B AGRI 产品设计

### 身份与输入

- `productCode`: `FY-4B-AGRI`
- `profileVersion`: `v1`
- `stageKey` / `stageCode`: `STANDARDIZE_FY4B_AGRI`
- 固定五个必需输入，角色和 ordinal 如下：

| 前端槽位 | Worker role | ordinal | 文件 |
| --- | --- | ---: | --- |
| 500 米 FDI | `L1_0500M` | 0 | `...FDI...0500M...HDF` |
| 1000 米 FDI | `L1_1000M` | 0 | `...FDI...1000M...HDF` |
| 2000 米 FDI | `L1_2000M` | 0 | `...FDI...2000M...HDF` |
| 4000 米 FDI | `L1_4000M` | 0 | `...FDI...4000M...HDF` |
| 4000 米 GEO | `GEO_4000M` | 0 | `...GEO...4000M...HDF` |

文件名必须属于同一星下点、观测起止时间和版本。Worker 继续执行源实现中的 HDF 根属性、通道、标定表、导航数组和全圆盘 shape 检查，前端文件名校验只用于提前给出提示，后端 inspect 是权威结论。

### 参数

参数快照沿用源仓库 `schemaVersion=1` 契约：

- `targetCrs`: 仅 `EPSG:4326` 或 `EPSG:4490`；
- `roi`: 可空，或 WGS84 `[minLon,minLat,maxLon,maxLat]`；
- `features`: `statistics`、`gradient`、`local_variance`、`toa_nir_red_nd`、`btd_c13_c14`；
- `fusion`: 固定 `NIR_RED_BLUE`，百分位范围合法；
- `saliency`: 固定白名单 source、`0..1` 阈值、大于等于 3 的奇数窗口；
- 显著性使用派生特征时，参数构建器自动启用并补齐相应 feature。

前后端分别校验同一契约。任务创建后使用不可变 JSON 快照，不允许调用方覆盖标定表、质量位、导航常量或物理波段定义。

### Worker 内部顺序

```text
五文件证据检查
  -> 构建名义静止卫星网格
  -> 四分辨率 DN 定标、质量掩膜和名义地理参考
  -> 按目标 CRS 重投影并按可选 ROI 裁剪
  -> 写四组 science/quality COG 和摘要
  -> 可选特征
  -> 可选 C03/C02/C01 近红外伪彩
  -> 可选显著性候选
```

整合执行器在一次受授权的 Worker invocation 中调用源仓库的 FY-4B 算法函数。阶段间文件只存在于本次 invocation 的 output 临时目录，作为内部输入使用，不返回给 Java；完成后删除 native/grid 等中间目录。响应中只能出现 `rs://output/...` URI、文件大小和 SHA-256。

### 最终产物

基础产物全部声明为 `FINAL`：

- `FY4B_RED_500M_COG`
- `FY4B_RED_500M_QUALITY_COG`
- `FY4B_VIS_NIR_1000M_COG`
- `FY4B_VIS_NIR_1000M_QUALITY_COG`
- `FY4B_SPECTRAL_2000M_COG`
- `FY4B_SPECTRAL_2000M_QUALITY_COG`
- `FY4B_MULTISPECTRAL_4000M_COG`
- `FY4B_MULTISPECTRAL_4000M_QUALITY_COG`
- `FY4B_STANDARD_SUMMARY_JSON`

按参数追加：

- `FY4B_FEATURE_COG`、`FY4B_FEATURE_SUMMARY_JSON`
- `FY4B_FALSE_COLOR_COG`
- `FY4B_SALIENCY_SCORE_COG`、`FY4B_SALIENCY_MASK_COG`、`FY4B_SALIENCY_SUMMARY_JSON`

输出只能表述为名义导航和候选显著性，不声明官方高精度定位、云检测或地物识别。

## GF-6 WFV L1A 产品设计

### 身份与输入

- `productCode`: `GF-6-WFV-L1A`
- `profileVersion`: `v1`
- `stageKey` / `stageCode`: `STANDARDIZE_GF6_WFV_L1A`
- 七个必需输入：

| 前端槽位 | Worker role | ordinal | 文件 |
| --- | --- | ---: | --- |
| TIFF 分块 1 | `MAIN_TIFF_TILE` | 0 | `产品名-1.tiff` |
| TIFF 分块 2 | `MAIN_TIFF_TILE` | 1 | `产品名-2.tiff` |
| TIFF 分块 3 | `MAIN_TIFF_TILE` | 2 | `产品名-3.tiff` |
| 产品 XML | `METADATA_XML` | 0 | `产品名.xml` |
| 主 RPC | `RPC_RPB` | 0 | `产品名.rpb` |
| 导航文件 | `NAV` | 0 | `产品名.nav` |
| 分块索引 | `TILE_INDEX_TIL` | 0 | `产品名.til` |

当前科学流水线不消费 SHP/DBF/SHX，因此本次不把它们列为任务输入。样本目录中的浏览 JPG、缩略图、每块 TIFF 的独立 RPB 和 `.aux.xml` 也不进入任务。前端说明必须帮助用户选择不带 `-1/-2/-3` 的主 RPB。

前端先去掉 TIFF 的 `-1/-2/-3` 后比较产品基名，并验证三个 tile 编号齐全且唯一。Worker 再权威校验 XML 中 `GF6/WFV/LEVEL1A`、ProductID、8 波段增益/偏移、三个 8-band UInt16 TIFF、TIL 文件名与偏移以及主 RPB 系数。

样本 XML 声明尺寸与 TIL 覆盖相差少量边界像元。接入时以 XML 声明画布为准，未被 TIL 覆盖的边缘保持 NoData，并在结果元数据中记录 `tileCoverageDimensions` 与 `declaredDimensions`，不把补零区域当作有效 DN。

### 参数

- `schemaVersion`: 固定 `gf6-wfv-l1a-v1`；
- `targetCrs`: 本版仅 `EPSG:4326` 或 `EPSG:4490`；
- `resolution`: 正有限数；
- `rpcHeightMode`: 固定 `CONSTANT`；
- `rpcHeight`: 默认 `0.0` 米；
- `roi`: 可空，或合法 WGS84 bbox；
- `bands`: 1–8 的非空、无重复子集；
- `features`: 可选 `statistics`、`gradient`、`local_variance`；
- `fusion`: RGB 波段必须已选，百分位合法；
- `saliency`: source 必须是已选波段，阈值合法，窗口为大于等于 3 的奇数。

页面固定提示“无 DEM 的名义 RPC”“定位精度未验证”“显著性仅为候选区域”。不提供 DEM、正射精度、任意算法或大气校正选项。

### Worker 内部顺序

```text
产品证据、XML/RPB/TIL/TIFF 检查
  -> TIL 驱动的三块 TIFF 虚拟拼接
  -> 构建 constant-height 名义 RPC 目标网格
  -> 一次窗口化 RPC 重采样、AGain/AOffset 定标和 ROI 裁剪
  -> 写 8 波段标准 COG 与名义 RPC 网格摘要
  -> 从同一个标准 COG 生成可选特征、RGB 融合和显著性
```

与源仓库不同，定标、配准、裁剪和标准 COG 不拆成四次重复的全量重采样。临时 VRT 在 finally 中删除，可选算法直接消费本次已经生成的标准 COG，不重新处理约 14 GB 原始 TIFF。

### 最终产物

基础产物：

- `GF6_STANDARD_COG`
- `GF6_NOMINAL_RPC_GRID_JSON`

按参数追加：

- `GF6_FEATURES_COG`、`GF6_FEATURES_SUMMARY_JSON`
- `GF6_RGB_FUSION_COG`、`GF6_FUSION_SUMMARY_JSON`
- `GF6_SALIENCY_SCORE_COG`、`GF6_SALIENCY_MASK_COG`、`GF6_SALIENCY_SUMMARY_JSON`

历史 `GF-6-WFV-BROWSE/v1` adapter、migration 和任务记录不删除；registry 先识别新的 L1A 七文件证据，再识别旧 Browse 三文件证据。前端只展示新的 L1A 模板。

## Java 与配置改动边界

- 不改 REST endpoint、DTO、数据库核心表、预检 token、任务生命周期或下载 ticket 协议。
- 不改 HY-1C、HY-2D、FY-3H 的 profile key、registry 分支、stage、参数或 artifact。
- 只新增 GF-6 WFV L1A profile migration；FY-4B AGRI `v1` 已由现有 profile migration 发布。
- registry 以精确输入证据路由，新增 L1A 与 FY-4B 分支必须位于默认 FY-3H fallback 之前。
- Spring `max-file-size` 和 `max-request-size` 提高到至少 10 GB；Nginx `client_max_body_size` 同步为 10 GB，并关闭大文件代理请求缓冲。该改动只放宽上限，不改变既有上传 API。
- Worker 超时继续由 `MULTISOURCE_WORKER_TIMEOUT` 外部配置，不在代码中写死新的超时。

## 前端设计

- 保留现有产品下拉框和 H1C、H2D、FY3H 模块。
- 将旧 `GF6_WFV_BROWSE_TEMPLATE` 前端入口替换成 `GF6_WFV_L1A_TEMPLATE`，并新增 `FY4B_AGRI_TEMPLATE`。
- `ProductFileSlot` 增加可选 `ordinal`；构建预检 assets 时使用 `slot.ordinal ?? 0`，从而支持三个相同 role 的 GF-6 TIFF。
- FY-4B 与 GF-6 各有独立的文件配对校验、参数模型、参数抽屉和单元测试文件，继续复用现有抽屉样式和任务 composable。
- 主组件只增加产品条件分支、摘要和中文 stage label，不把新产品逻辑堆入现有 H1C/H2D/FY3H 参数文件。
- 预检候选必须精确匹配 productCode/profileVersion；GF-6 L1A 不使用旧 Browse packageId 前缀兼容匹配。
- 任务切换、轮询、取消、重试和 ZIP 下载继续复用现有行为；终态后停止轮询。

## 错误处理

- 文件扩展名、产品基名、角色缺失和参数问题在前端立即显示中文提示并禁用开始按钮。
- Worker inspect/build/execute 的 `ValueError` 保留可操作的错误文本，经 Java `WorkerProtocolException` 和 progress DTO 传给前端；不返回 Python traceback、服务器路径或 manifest 内容。
- ROI 不相交、输入包不完整、HDF/TIFF 元数据不一致、RPC/TIL 无效分别使用稳定错误前缀，便于前端翻译。
- Worker 失败只使当前新产品任务失败，不改变其他产品 registry 路由或已有任务状态。
- 中间文件不登记为 FINAL，也不进入结果 ZIP。

## 测试与验收

所有行为改动采用测试先行。

### Worker

- FY-4B：五文件角色/时间/版本配对、参数、单阶段 plan、registry 路由、整合执行器最终产物集合。
- GF-6：七文件识别、三个 TIFF ordinal、主 RPB 选择、TIL/XML 边界差异的 NoData 策略、参数、单阶段 plan、只执行一次标准化、可选产物集合。
- 使用合成小 HDF/TIFF 夹具验证算法和 artifact contract；真实样本只执行只读 inspect/metadata smoke，不生成完整输出。
- 运行新增测试和现有 H1C/H2D/FY3H 回归测试。

### Java

- migration 内容与幂等测试；
- plan/worker artifact exact contract 的现有测试继续通过；
- 不新增共享调度语义，不要求数据库迁移核心表。
- 使用指定 Maven 3.8.8、用户 settings 和本地仓库执行聚焦模块测试。

### 前端

- 两个模板的 profile、角色、ordinal、扩展名与结果文件名；
- FY-4B 五文件同组校验；
- GF-6 `-1/-2/-3` 归一化与七文件同产品校验；
- 两套参数默认值、依赖补齐、边界错误与快照序列化；
- 既有 H1C/H2D/FY3H 模板和任务规则回归；
- `pnpm test:multisource` 和 `pnpm typecheck`。若完整 Vite build 受现有项目问题影响，报告实际错误而不修改无关代码。

## 完成标准

1. 前端同时显示并可选择 H1C、H2D、FY3H、FY4B AGRI 和 GF-6 WFV L1A。
2. 提供的 FY-4B 五文件和 GF-6 七个核心文件能够通过前端文件校验及 Worker 真实只读预检。
3. 两个新产品创建的计划都只有一个整合 stage，声明的最终产物与 Worker 响应严格一致。
4. 新增聚焦测试及既有三个产品回归测试通过；Maven 与前端类型检查有新鲜验证记录。
5. 未覆盖或删除用户现有未提交改动，未改变 H1C、H2D、FY3H 的可观察行为。
6. 未声称完整 FY-4B 全圆盘或 GF-6 14 GB 算法任务已在本机完成；真实重数据验收由用户重启后端后执行。
