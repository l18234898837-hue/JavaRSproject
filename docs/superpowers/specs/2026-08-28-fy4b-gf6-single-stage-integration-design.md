# FY4B AGRI 与 GF6 WFV L1A 单阶段接入设计

## 目标

在不改变现有 HY-1C CZI L2A、HY-2D 高度计和 FY-3H MERSI 行为契约的前提下，将源仓库中的 FY-4B AGRI 全圆盘 L1 与 GF-6 WFV L1A 工程处理能力接入当前后端，并在现有“多源卫星数据处理”前端中提供对应产品入口。

## 约束与现状

- 当前后端对 HY-1C、HY-2D、FY-3H 实际采用单阶段整合执行；`DispatchNextStageService` 会把候选原始文件整体下发，并未按计划中的上游产物解析阶段输入。
- 源 FY-4B 实现是多阶段 DAG，不能原样交给当前 Java 调度器。
- 源 GF-6 L1A 计划缺少 Java `plan-v2` 所需的输入、输出和阶段参数声明，且多个名义阶段重复执行完整 RPC 重采样。
- 当前前端的 GF-6 Browse（JPG/JGW/JSON）与真实 GF-6 WFV L1A 样本不匹配；历史后端 Browse 兼容代码和数据仍需保留。
- 两个工作树已有大量用户未提交修改，所有接入必须增量合并，不得覆盖或回退既有改动。

## 后端架构

### FY-4B AGRI

新增独立产品 `FY-4B-AGRI/v1`，预检要求同一星下点、观测起止时间和版本的五个 HDF：

1. `L1_0500M`
2. `L1_1000M`
3. `L1_2000M`
4. `L1_4000M`
5. `GEO_4000M`

对源仓库的文件识别、HDF 结构验证、参数白名单、定标、名义静止卫星网格、重投影、COG、特征、伪彩和显著性内核做兼容性迁移。对 Java 只暴露一个 `STANDARDIZE_FY4B_AGRI` 阶段；Worker 在同一受控 invocation 中按 tile/window 流式执行并只返回声明的最终产物，禁止把 500m 全圆盘波段一次性拼入内存：

```text
验证五文件包
  -> 构建名义网格
  -> 定标与地理参考
  -> 重投影和可选 ROI
  -> 四分辨率 science/quality COG 与摘要
  -> 可选特征、NIR_RED_BLUE 伪彩、显著性候选
```

导航状态必须继续表述为 `NOMINAL_UNVERIFIED`，不得宣称测绘级几何校正。反射通道从 GEO 文件的 `NOMSunZenith` 读取太阳天顶角并应用 Earth/Sun 距离修正；合法夜间像元、导航无效和圆盘外像元使用不同质量位，夜间与盘外 science 值均保持 NoData，GEO 的填充值/有效范围优先于仅依靠行列号的判断。

### GF-6 WFV L1A

新增独立产品 `GF-6-WFV-L1A/v1`。必需输入为三块按 `-1/-2/-3` 排序的 8 波段 UInt16 TIFF、产品 XML、主 RPB、NAV 和 TIL；SHP/DBF/SHX 可选，但只能整组提供。浏览 JPG、缩略图、tile 级 RPB 和 `.aux.xml` 不属于任务输入。

对 Java 只暴露一个 `STANDARDIZE_GF6_WFV_L1A` 阶段，内部执行：

```text
验证产品身份、TIL 排列、RPC 与定标元数据
  -> 构建轻量 VRT/TIL 镶嵌
  -> 固定高程 0m 的名义 RPC 网格
  -> 窗口化 RPC 重采样、辐射定标和可选 ROI
  -> 8 波段标准 COG
  -> 可选特征、RGB 融合和显著性候选
```

标准 COG 只生成一次，可选算法复用该产物，不重复对原始 14GB 影像执行完整 RPC 重采样。派生特征和显著性按带 halo 的窗口计算并裁回中心块，避免 256 像元块边界接缝，同时严格保留原始 NoData；统计摘要使用流式合并，不积累整幅像元。RGB 融合先对整幅进行确定性稀疏采样并计算一组全局百分位，所有输出块复用同一拉伸参数；RGB 的有效 0 值通过内嵌 dataset mask 与无效像元区分，不产生旁车文件。显著性掩膜约定 `0=非显著、1=显著、255=NoData`。标准与派生 COG 均允许按需写 BigTIFF。输出继续标记 `NOMINAL_RPC_NO_DEM`、`UNVALIDATED`，显著性标记为候选结果。

### Java 与 Worker 集成边界

- 保持现有 REST、预检 token、任务创建、单阶段 dispatch、取消、重试、进度和 ZIP 下载接口不变。
- 在 Python registry 中先识别 GF-6 L1A 和 FY-4B，再进入现有 HY-2D/HY-1C/FY-3H 分支；现有三产品 profile key、stage code、artifact type 和路径不得改变。
- 每个新阶段声明完整 raw inputs、final outputs 与规范化 parameter snapshot，使现有 `StageOutputContractValidator` 可做精确校验。
- 系统文件物理存储名可能是 UUID；Java 授权 manifest 必须同时携带受校验的 `originalFileName` 与 `required`，Worker 以逻辑原名识别包、以授权绝对路径读取数据。旧 manifest 缺少逻辑原名时仍回退到物理 basename。
- Worker 响应只返回 `rs://output/...` URI、artifact type、size 和 SHA-256；不得暴露本地路径。
- 新增 GF-6 L1A 数据库 profile seed；保留 GF-6 Browse seed。
- 将 Spring 单文件和请求上传限制设为 `10000MB`，部署 nginx 上限设为 `10g`，以容纳样本中约 6.8GB 的 tile。

## 前端设计

沿用现有深蓝任务控制台的布局、字体、色彩、文件槽位和参数抽屉，不引入新的视觉体系。产品选择器继续保留 HY-1C、HY-2D、FY-3H，并新增 FY-4B；旧 GF-6 Browse 入口替换为 GF-6 WFV L1A。

- FY-4B：五个固定 HDF 槽位，文件名解析必须验证 FDI/GEO、分辨率、时次、星下点和版本一致。
- GF-6：七个必需槽位与三个可选足迹槽位；三 TIFF 去除 `-1/-2/-3` 后与 XML/RPB/NAV/TIL 基名一致，足迹三件套必须全有或全无。
- 两个产品分别使用独立参数模型、校验器和参数抽屉；参数快照在任务创建后锁定。
- 进度区域增加两个单阶段的中文名称，同时保留对未知 stage code 的安全回退。
- GF-6 页面明确显示“无 DEM 名义 RPC、定位精度未验证、显著性仅为候选区域”；FY-4B 显示“名义定位未验证”。

现有视觉语言已经针对卫星数据操作设计：深海蓝、青色信号线、等宽技术标签和紧凑任务阶段结构。新增模块只强化产品专属的输入说明和工程限制，不增加模板化卡片或装饰性动效。

## 错误处理

- 文件缺失、角色错误、产品标识不一致和参数错误在前端先提示，后端预检仍为权威判断。
- Python 普通输入错误保留可行动的原始消息，由现有 Java/前端错误转换边界展示；协议错误仍使用稳定错误码。
- GF-6 预计像元数和标准/特征/融合/显著性总输出在任何 VRT/COG 创建前统一预算，预算包含 overview 与 RGB 内嵌 1-bit 有效掩膜；超出限制时使用稳定的 `GF6_OUTPUT_LIMIT_EXCEEDED`，Java 只对该白名单错误返回安全文案，不透传任意 Worker 文本或物理路径。
- GF-6 特征与显著性窗口必须为 3–255 的奇数；前后端采用同一硬上限，防止参数触发不可控内存分配。
- GF-6 TIL 覆盖尺寸与 XML 声明存在少量差异时，以 TIL 实际覆盖范围为数据范围，未覆盖区视为 NoData，并在 metadata summary 中记录差异，避免对真实样本做无依据拒绝。
- ROI 不相交、输出过大或依赖缺失应产生明确错误，不得返回空成功产物。

## 验证策略

- 新增合成小 HDF/TIFF/XML/RPB/TIL fixtures，覆盖文件识别、参数规范化、单阶段计划、registry 路由和输出契约的 RED/GREEN 测试。
- 使用真实目录只做只读预检/metadata smoke；不运行完整 FY-4B 全圆盘或 GF-6 14GB 处理。
- 运行 Worker 针对性测试以及既有 HY-1C/HY-2D/FY-3H 回归。
- 使用指定 Maven 安装、settings 和本地仓库执行相关 Java 模块测试/编译。
- 运行前端 multisource 单元测试、TypeScript/Vue 类型检查和构建。
