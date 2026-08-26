# FY-3H MERSI L1 1000M GEO1K 后端处理设计

## 目标

为 FY-3H MERSI-III L1 的一对同轨同版本 `1000M` 与 `GEO1K` HDF 文件提供可调度、可审计的后端预处理链路。处理结果包括已定标、质量控制并地理化的标准 COG，以及可选的特征、显示融合和显著性结果。前端实现不属于本设计范围。

## 范围与边界

本版本只支持 MERSI-III `1000M + GEO1K` 成对输入。一个产品候选由一份观测文件和一份地理定位文件构成；两者必须具有相同的观测日期、开始时间、结束时间、轨道号和产品版本，且栅格尺寸相同。

不支持原生 250m 处理、LSR 大气校正、官方云掩膜、NDVI/EVI 官方产品复现，或未有官方规则支持的导航插值。原生 250m 处理需要在后续版本以同轨 `250M + GEOQK` 文件另行适配。

产品档案键为 `FY-3H-MERSI / L1-1000M-GEO1K-v1`。Worker 内的 FY-3H 适配器只描述输入配对、HDF 数据集路径、波段映射、质量规则和定标元数据；公共算子继续负责数组读取、质量掩膜、网格化、COG 写出和通用算法。Java 继续使用现有 `plan-v2`、阶段输入输出声明、资产授权与 Artifact 登记机制，不新增旁路调度。

## 真实输入结构

观测 HDF 使用以下数据集：

- `Data/EV_250_Aggr.1KM_RefSB`，通道 1-4。
- `Data/EV_1KM_RefSB`，通道 5-19。
- `Data/EV_1KM_Emissive`，通道 20-23。
- `Data/EV_250_Aggr.1KM_Emissive`，通道 24-25。
- `Calibration/VIS_Cal_Coeff`，形状 `(19, 3)`。
- `Calibration/IR_Cal_Coeff`，形状 `(6, 4, 200)`。
- `QA/QA_Frame_Flag`，形状 `(200,)`。

GEO1K HDF 的逐像元字段位于 `Geolocation/`：`Latitude`、`Longitude`、`SolarZenith`、`SolarAzimuth`、`SensorZenith`、`SensorAzimuth`、`Altitude`、`LandSeaMask`、`LandCover` 及 `QA_GEO`。时间和昼夜信息位于 `Timedata/`。真实样例中观测和导航数组均为 `(2000, 2048)`，其中 200 个扫描帧各占 10 行。

## 科学处理规则

### 太阳反射通道

通道 1-19 的原始观测先按各数据集属性执行 `dn = DN * Slope + Intercept`。适配器从 `Calibration/VIS_Cal_Coeff` 读取每通道多项式系数，并从根属性读取 `EarthSun Distance Ratio` 与 `Solar_Irradiance`。在有有效 `Geolocation/SolarZenith` 的白天像元上，按 FY-3H L1 说明计算表观反射率、TOA 反射率和 TOA 辐亮度。

实现前将 L1 手册中 TOA 辐亮度公式的排版按原 PDF 页面复核一次，并在代码和摘要中固定公式版本。无效太阳天顶角、非白天像元或不能计算余弦项的像元，不产生太阳反射变量，而是以质量原因掩膜。

### 热红外通道

通道 20-25 使用各观测数据集的 `Slope` 和 `Intercept` 还原辐亮度。红外定标参数按扫描帧和探元由 `Calibration/IR_Cal_Coeff` 映射；等效中心波数来自产品元数据或经核对的通道表。Planck 反演得到等效黑体温度，再按根属性 `TBB_Trans_Coefficient` 中的每通道 A、B 系数得到亮温。辐亮度与亮温为不同变量，必须保留各自单位。

### 质量规则

适配器将观测中的 `65535`、`65534`、`65533` 分别标记为缺失、饱和和坏探元；数据集 `valid_range` 外的数据标记为范围无效。`QA/QA_Frame_Flag` 的通道失败、整体预处理失败、反射或热红外定标失败、定位失败等帧级规则扩展到对应扫描行。`Geolocation/QA_GEO`、无效经纬度、无效太阳/传感器角度共同组成导航质量规则。

输出统一的位编码质量掩膜和布尔有效掩膜。科学变量被掩膜为 NoData，但质量原因保留，摘要按通道统计每类原因和有效比例。

## 处理 DAG 与产物

主链路固定为下列六个阶段：

1. `VALIDATE_FY3H_PAIR`：验证配对、根属性、数据集存在性、维度与 GEO 完整性，输出 `FY3H_INPUT_VALIDATION_JSON`。
2. `DECODE_MERSI_L1`：读取分块 DN、定标参数、逐像元导航和必要元数据，输出 `DECODED_MERSI_L1`。
3. `CALIBRATE_MERSI_L1`：生成反射率、TOA 辐亮度、热红外辐亮度和亮温中间结果，输出 `CALIBRATED_MERSI_RASTER`。
4. `APPLY_MERSI_QUALITY`：生成有效掩膜、位编码质量掩膜和统计，输出 `QUALITY_MERSI_RASTER`、`MERSI_QUALITY_MASK`、`MERSI_QUALITY_SUMMARY_JSON`。
5. `GRID_MERSI_SWATH`：只使用 GEO1K 的逐像元经纬度进行目标网格化，输出 `GRIDDED_MERSI_RASTER`。
6. `WRITE_MERSI_STANDARD_COG`：输出 `STANDARD_MERSI_COG`、`STANDARD_MERSI_QUALITY_COG` 与 `STANDARD_MERSI_SUMMARY_JSON`。

可选阶段仅消费主链路标准化产物，且不得修改标准 COG：

- `EXTRACT_MERSI_FEATURES`：输出特征 COG 和统计 JSON。
- `FUSE_MERSI_BANDS`：输出显示用途的 RGB 融合 COG。
- `DETECT_MERSI_SALIENCY`：输出显著性评分 COG 与二值掩膜 COG。

每个阶段在计划中声明原始输入或上游 Artifact 类型、相对输出路径及 `INTERMEDIATE` 或 `FINAL` 保留级别。Java 根据该声明构建 Worker manifest，只授权本阶段所需文件；Worker 只能在本次临时输出目录写入相对产物路径。Java 验证路径、大小和 SHA-256 后提升文件并登记 Artifact。

## 参数与网格规则

参数快照只允许以下内容：目标 CRS、米级分辨率、可选 ROI、连续科学变量重采样方式、选择通道、特征指标与窗口、RGB 通道和拉伸百分位、显著性来源、阈值与窗口大小。官方定标公式、质量位和无效值规则不允许被任务参数覆盖。

连续变量使用明确选择的连续重采样方法；质量掩膜和分类变量只能使用最近邻。NoData 和质量掩膜必须参与网格化，不能作为有效邻域样本污染连续变量。所有输出 COG 写入波段名称、单位、中心波长、目标 CRS、网格分辨率、NoData、规则版本和参数摘要。摘要 JSON 记录输入文件、处理时间、参数、质量统计和产物校验和。

## 验证策略

单元测试使用小型成对 HDF 夹具验证配对失败、数据集缺失、维度不一致、定标公式、无效值、帧级质量位和 GEO 质量位。栅格测试验证 NoData 不泄漏、连续变量和质量层采用不同的重采样规则、COG 元数据完整。可选算法测试验证只能使用选择的波段、窗口必须为奇数、失败不会影响主链路产物。

集成测试验证 Java 只向每个阶段授权声明的原始文件和上游产物，拒绝越界输出、大小或 SHA-256 不匹配的结果，并将合格结果提升和登记。真实场景验收使用 `2335`、`2340`、`2345` 或 `2355` 中的一对正常文件；`2350` 的 71 个定位异常扫描用于质量掩膜回归测试。

## 完成标准

- 可识别且只接受有效的 `1000M + GEO1K` 成对产品。
- 主链路生成有追溯元数据的标准科学 COG、质量 COG 和摘要。
- 每项科学变量、质量规则和输出单位有明确来源和版本。
- 真实正常样例完成端到端执行，异常 GEO 样例产生可解释的质量统计。
- 每阶段的输入授权、输出校验、Artifact 提升与登记均通过 Java 集成测试。
- 前端可在后续工作中只依赖任务、阶段状态和 Artifact 逻辑 URI，不接触 Worker 物理路径。
