# HY-2D 测高沿轨基线产品

## 适用范围

输入为 HY-2D IDR 2MC `.tar.gz` 包。包内必须有一个 `NETCDF3_CLASSIC` 的 `.nc` 文件和一个 `.meta.xml` 文件。本产品输出的是沿轨基线质控与统计结果，不是遥感影像产品。

## 已实现处理

- NetCDF 数值解码：应用 `scale_factor`、`add_offset` 与 `_FillValue`。
- 基线质量策略：`hy2d-idr-2mc-baseline-v1`。
- 基线 SSH 公式：`hy2d-idr-2mc-baseline-v1`。
- 沿轨窗口特征：均值、标准差、趋势、梯度和有效比例。
- 沿轨异常：滑动 MAD、相邻跳变与绝对 SSH 工程阈值。
- 标准输出：NetCDF、Parquet、CSV、轨迹 GeoJSON、异常 GeoJSON 和摘要 JSON。
- 可选网格：统计分箱 NetCDF、四波段统计 COG 与摘要 JSON。

## 科学边界

当前未取得完整官方 IDR/IGDR 数据格式和质量控制细则，因此所有质量规则、改正项符号和异常阈值均为可追踪的工程基线，不得表述为官方科学产品。

绝对 SSH 阈值的版本为 `hy2d-idr-2mc-engineering-baseline-v1`，默认 100 米。摘要会写入 `absoluteSshLimitScientificStatus=ENGINEERING_BASELINE_REQUIRES_OFFICIAL_REVIEW`，前端应提示该结果需要官方规范复核。

轨道状态根据样例内嵌属性处理：3 保留为正常，8 剔除；4 至 7 只记录为待定状态，不擅自剔除。未知及非有限质量编码均保留可审计原因码。

网格仅对有效沿轨观测进行分箱统计，元数据固定标记为 `bin-statistics-no-interpolation`，不做空间插值，也不称为卫星影像投影产品。

## 官方规范到位后的复核项

- 像元/观测质量位编码与轨道状态等级。
- SSH 改正项的定义、符号、单位和适用条件。
- 绝对 SSH、MAD 和相邻跳变阈值。
- 网格空间范围、分辨率和质量验收标准。
