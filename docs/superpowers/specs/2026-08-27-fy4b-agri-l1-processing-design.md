# FY-4B AGRI 全圆盘 L1 处理设计

## 目标

在既有多源处理后端中，为一个完整的 FY-4B AGRI 全圆盘 L1 观测包提供受控的后端处理：输入核验、辐射定标、静止卫星名义定位验证、重投影、裁剪、标准 COG、特征提取、近红外伪彩融合和显著性候选检测。

本设计只覆盖 `FY-4B-AGRI/v1` 的全圆盘 `MULT/NOM` L1 HDF 数据。Java 保持任务、授权、阶段状态和产物登记的唯一所有者；Python Worker 只读取 Manifest 已授权的输入并返回逻辑产物信息。

## 范围

### 纳入本版

- 一个完整观测包必须包含同一卫星、仪器、星下点、观测起止时间和版本的五个文件：`L1_0500M`、`L1_1000M`、`L1_2000M`、`L1_4000M`、`GEO_4000M`。
- HDF 结构和元数据依据已下载的 FY-4B AGRI 全圆盘 L1 500M、1KM、2KM、4KM、GEO 数据格式说明 V1.0（2026-04 修订）验证。
- DN 到定标后反射率、表观反射率、辐亮度和亮温的转换使用 HDF 内的 `CALChannelXX`、`CALIBRATION_COEF(SCALE+OFFSET)`、`ESUN`、日地距离比及 GEO 太阳天顶角。
- 静止卫星名义投影、WGS84 或 CGCS2000 地理坐标重投影、WGS84 边界裁剪和 COG 输出。
- 固定的多分辨率产品、基础空间特征、C03/C02/C01 近红外伪彩融合，以及局部对比度显著性候选检测。
- 真实样例和经人工审核的海岸线控制点用于验证名义投影的定位残差。

### 明确不纳入本版

- 云、火点、降水、陆表温度、气溶胶等 L2 专题反演或分类；显著性结果只称为候选区域。
- 自动地形校正、云顶视差校正和未受控制点验证的高精度几何校正。
- 真彩色产品。AGRI 的本版可见光/近红外组合没有绿色通道，唯一公开的显示融合为 `C03/C02/C01` 近红外伪彩。
- 多时相变化检测、前端页面、下载 API 和数据库模式变更。

## 已审阅的数据契约

| 文件角色 | 分辨率 | 必需影像通道 | 处理用途 |
| --- | --- | --- | --- |
| `L1_0500M` | 500M | C02，0.65 um | 高分辨率红光产品 |
| `L1_1000M` | 1KM | C01-C03，0.47/0.65/0.825 um | 可见光/近红外特征及伪彩 |
| `L1_2000M` | 2KM | C01-C08，含 C07/C08 3.75 um 高低增益 | 中分辨率光谱产品 |
| `L1_4000M` | 4KM | C01-C15，至 13.3 um | 全谱特征和热红外产品 |
| `GEO_4000M` | 4KM | `LineNumber`、`ColumnNumber`、卫星/太阳角 | 名义几何、表观反射率和定位质量 |

所有影像 DN 的有效范围由数据集 `valid_range` 决定；说明书中的标准范围为 `0..4095`，填充值为 `65535`。实现必须读入真实属性，不得将该标准范围硬编码为唯一可接受来源。

`NavQualityFlag` 与 `CalQualityFlag` 均为按通道的 15 项状态；`Data Quality`、`QA_Scan_Flag` 和 `QA_Pixel_Flag` 是文件级摘要。它们不是像元级云掩膜。像元质量 COG 只表达 DN 无效、范围无效、通道导航失败、通道定标降级、全局文件质量差和圆盘外区域，不能伪装成云检测结果。

## 定标和几何规则

反射通道 C01-C06 以查找表为主、线性系数为交叉校验：

```text
rho = CALChannelXX[DN]
rho = SCALE * DN + OFFSET
L = rho * ESUN / pi
rho_TOA = rho * earth_sun_distance_ratio^2 / cos(NOMSunZenith)
```

热红外通道 C07-C15 使用 `CALChannelXX[DN]` 得到亮温，使用线性系数得到辐亮度。C07 和 C08 保持为不同的高/低增益输入；本版不执行未经审核的动态范围拼接。

原始 L1 说明书说明该数据已完成地理定位。Worker 将根属性中的 `NOMCenterLon`、`NOMCenterLat`、`NOMSatHeight`、`dEA`、`dObRecFlat`、`dSamplingAngle` 和 `dSteppingAngle` 转换为明确的静止卫星 CRS 和仿射网格。GEO 的 `LineNumber`、`ColumnNumber` 和角度层用于确认五种分辨率的 8/4/2/1 对齐关系及反射率几何量。

现有格式说明定义了字段，但没有给出可作为精度声明依据的完整反算公式和误差阈值。因此输出在真实控制点验收前必须标记为 `NOMINAL_UNVERIFIED`。验收使用独立的海岸线控制点清单，计算预测像素与人工识别像素的残差；通过工程阈值后标记为 `COASTLINE_VALIDATED`。该状态表示对本样例的工程验证，不表示官方定位认证。

## Worker 结构

```text
rs-algorithm-worker/src/rs_algorithm_worker/
  product_adapters/fy4b_agri.py       # 五文件证据、候选产品和阶段 DAG
  fy4b_agri_profile.py                # 严格参数白名单和能力前提
  fy4b_agri_models.py                 # 可序列化的包、标定和导航值对象
  fy4b_agri_navigation.py             # 静止卫星网格、GEO 对齐及控制点残差
  fy4b_agri_pipeline.py               # 阶段执行和逻辑产物返回
  registry.py                          # FY-4B 计划与执行路由
  algorithms/features.py               # 复用统计、梯度、局部方差
  algorithms/fusion.py                 # 复用显式 RGB 拉伸
  algorithms/saliency.py               # 复用局部对比度基线
```

产品适配器只验证产品和声明 DAG；它不读取未授权路径，也不实现科学计算。管线只接受阶段 Manifest 中的角色。HDF 读取按窗口进行，特别是 500M 全圆盘不得整幅加载到内存。阶段间科学栅格使用包含元数据的自描述、无旁路元数据文件的产物；每个 Worker 响应仅返回 `rs://output/...` 逻辑 URI、长度和 SHA-256。

## 参数和能力

公共参数是严格 JSON 对象，顶层仅允许：

```json
{
  "schemaVersion": "1",
  "targetCrs": "EPSG:4326",
  "roi": [100.0, 15.0, 125.0, 40.0],
  "features": {"enabled": true, "metrics": ["statistics", "gradient", "local_variance", "toa_nir_red_nd", "btd_c13_c14"], "windowSize": 5},
  "fusion": {"enabled": true, "mode": "NIR_RED_BLUE", "percentiles": [2, 98]},
  "saliency": {"enabled": true, "source": "btd_c13_c14", "threshold": 0.55, "windowSize": 5}
}
```

`targetCrs` 仅允许 `EPSG:4326` 或 `EPSG:4490`；`roi` 可省略，提供时始终为 WGS84 `[minLon, minLat, maxLon, maxLat]`。不允许调用方覆盖定标表、质量位规则、波段波长或导航常量。

特征名称必须反映物理量：`toa_nir_red_nd` 是 C03/C02 的表观反射率归一化差异，不命名为已大气校正的 NDVI；`btd_c13_c14` 是 C13 与 C14 的亮温差。融合只能输出标记为 `NIR_RED_BLUE_FALSE_COLOR` 的 1KM C03/C02/C01 图像。显著性基线只能接收一个已声明的标准波段或特征层，输出 `contrast-v1` 候选分数和掩膜，不输出地物类别。

## 阶段 DAG 与产物

```text
VALIDATE_FY4B_PACKAGE
  -> BUILD_FY4B_NOMINAL_GRID
  -> CALIBRATE_AND_GEOREFERENCE_FY4B
  -> REPROJECT_AND_CROP_FY4B
  -> WRITE_FY4B_STANDARD_COG
     -> EXTRACT_FY4B_FEATURES          # 参数启用时
     -> FUSE_FY4B_BANDS                # 参数启用时
     -> DETECT_FY4B_SALIENCY           # 参数启用时；可选 feature 输入
```

`VALIDATE_FY4B_PACKAGE` 使用全部五个原始角色，产生 `FY4B_INPUT_VALIDATION_JSON`。`BUILD_FY4B_NOMINAL_GRID` 使用 `GEO_4000M`，产生 `FY4B_NOMINAL_GRID_JSON`。`CALIBRATE_AND_GEOREFERENCE_FY4B` 产生四个分辨率的自描述中间栅格和质量栅格。`REPROJECT_AND_CROP_FY4B` 只使用上游中间栅格，产生重投影后的中间栅格。`WRITE_FY4B_STANDARD_COG` 输出以下最终产品：

- `FY4B_RED_500M_COG` 与 `FY4B_RED_500M_QUALITY_COG`
- `FY4B_VIS_NIR_1000M_COG` 与 `FY4B_VIS_NIR_1000M_QUALITY_COG`
- `FY4B_SPECTRAL_2000M_COG` 与 `FY4B_SPECTRAL_2000M_QUALITY_COG`
- `FY4B_MULTISPECTRAL_4000M_COG` 与 `FY4B_MULTISPECTRAL_4000M_QUALITY_COG`
- `FY4B_STANDARD_SUMMARY_JSON`

可选阶段消费最终 COG，避免复制大体积栅格。为支持这一点，Java `ProcessingPlanValidator` 必须允许来自声明依赖的 `FINAL` 产物作为下游输入；`StageInputResolver` 继续强制每个输入角色或产物类型恰好解析为一个已授权文件。该改动也修复 FY-3H 已声明但当前验证器拒绝的“标准 COG 后执行可选阶段”契约。

## 验收

单元测试以最小 HDF 夹具验证每个文件角色、元数据不匹配、缺失通道、DN/填充值、查表和线性定标、投影网格、ROI、质量状态、特征和显著性参数。真实样例测试由 `MULTISOURCE_FY4B_SAMPLE_ROOT` 指向包含五个 HDF 的目录，并且只读。

海岸线验收使用版本化的控制点 JSON，字段为 `id`、`longitude`、`latitude`、`observedLine500m`、`observedColumn500m`。每一控制点必须由人工在实际 C02 图像和独立海岸线数据中确认。测试报告最大值、均方根值和逐点残差；控制点文件缺失、少于八个点或任一点无效时，验收命令失败，不能以跳过测试代替验证。

最终完成条件为：Worker 单元和契约测试通过；Java 计划验证和阶段输入解析测试通过；真实五文件样例生成全部声明的 COG 和 JSON；COG 可被 Rasterio 重开且具有 CRS、变换、波段描述、NoData、概览和正确校验和；海岸线控制点报告通过预先写入测试的工程阈值；所有输出均可由 Java 产物登记而不暴露本地文件路径。
