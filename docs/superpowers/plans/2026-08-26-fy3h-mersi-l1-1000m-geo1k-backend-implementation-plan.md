# FY-3H MERSI L1 1000M GEO1K 后端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 支持一对同轨同版本的 FY-3H MERSI-III `1000M + GEO1K` L1 HDF 文件，从识别、定标、质量控制、地理化到标准 COG 与可选公共算法产物的完整后端处理闭环。

**架构：** FY-3H 适配器只识别、配对并声明 HDF 路径、通道和质量规则；`fy3h_mersi_pipeline.py` 编排 FY-3H 特有的物理定标，公共算子负责质量掩膜、网格、COG 和算法。所有阶段使用现有 `plan-v2` 输入输出契约，Java 仅授权已声明的原始文件或上游 Artifact，并管理产物提升与登记。标准 COG 默认保存 19 个 TOA 反射率和 6 个亮温；每通道辐亮度保留在定标中间产物中，供后续按需导出或算法使用。

**技术栈：** Python 3.11、h5py、numpy、scipy、pyproj、rasterio、pytest；Java 17、Spring、JUnit 5、AssertJ、Maven。

**权威依据：** `otherfile/FY3H_MERSI_20260701/FY-3H_L1_Data_Instruction_MERSI-III.pdf`；真实配对样例目录 `otherfile/FY3H_MERSI_20260701/FY3H_MERSI_20260701`；SRF 位于 `otherfile/FY3H_MERSI_20260701/FY3H-SRF-MERSI/MERSI`。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py` | 识别并配对 `1000M/GEO1K`，声明波段、数据集路径、官方质量位和 `plan-v2` DAG。 |
| `rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py` | 单阶段执行入口，负责 FY-3H 解码、物理定标、质量合成、地理化与产物响应。 |
| `rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_profile.py` | 严格解析任务参数、CRS、ROI、通道和可选算法参数。 |
| `rs-algorithm-worker/src/rs_algorithm_worker/models_fy3h.py` | 经验证的 FY-3H 中间数据模型和质量原因位定义。 |
| `rs-algorithm-worker/src/rs_algorithm_worker/operators/swath_grid.py` | 从逐像元经纬度将连续栅格和质量层写入目标规则网格。 |
| `rs-algorithm-worker/src/rs_algorithm_worker/registry.py` | 将已识别 FY-3H 候选路由到计划构建和阶段执行器。 |
| `rs-algorithm-worker/tests/test_fy3h_mersi.py` | 适配器、参数、计划、合成 HDF 与真实样例的 FY-3H 测试。 |
| `rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py` | 分阶段、网格、COG、可选算法和 Worker 响应测试。 |
| `rs-algorithm-worker/tests/conftest.py` | FY-3H 成对真实样例 fixture，环境变量为 `MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT`。 |
| `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java` | 验证 FY-3H 阶段仅解析声明的 `OBSERVATION`、`GEO1K` 和上游 Artifact。 |
| `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java` | 验证 Java 对 FY-3H `plan-v2` 的 manifest 授权、输出与调度。 |
| `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ArtifactRegistrationServiceTest.java` | 验证 FY-3H 声明产物的路径、类型、哈希和提升约束。 |

## 固定处理契约

原始输入角色固定为：`OBSERVATION`（`*_1000M_*.HDF`）和 `GEO1K`（`*_GEO1K_*.HDF`）。一对文件必须在根属性中具有一致的卫星、载荷、开始/结束时间、轨道号、版本和二维栅格尺寸。

主链路阶段与产物如下：

| 阶段 | 必需输入 | 输出 |
| --- | --- | --- |
| `VALIDATE_FY3H_PAIR` | `OBSERVATION`、`GEO1K` | `FY3H_INPUT_VALIDATION_JSON` |
| `DECODE_MERSI_L1` | `OBSERVATION`、`GEO1K` | `DECODED_MERSI_L1` |
| `CALIBRATE_MERSI_L1` | `DECODED_MERSI_L1` | `CALIBRATED_MERSI_RASTER` |
| `APPLY_MERSI_QUALITY` | `DECODED_MERSI_L1`、`CALIBRATED_MERSI_RASTER` | `QUALITY_MERSI_RASTER`、`MERSI_QUALITY_SUMMARY_JSON` |
| `GRID_MERSI_SWATH` | `QUALITY_MERSI_RASTER` | `GRIDDED_MERSI_RASTER`、`GRIDDED_MERSI_QUALITY_MASK` |
| `WRITE_MERSI_STANDARD_COG` | `GRIDDED_MERSI_RASTER`、`GRIDDED_MERSI_QUALITY_MASK` | `STANDARD_MERSI_COG`、`STANDARD_MERSI_QUALITY_COG`、`STANDARD_MERSI_SUMMARY_JSON` |

启用时，`EXTRACT_MERSI_FEATURES`、`FUSE_MERSI_BANDS`、`DETECT_MERSI_SALIENCY` 都依赖 `WRITE_MERSI_STANDARD_COG`，只读取标准栅格，不修改主产物。

### Task 1: 建立成对输入、官方映射与真实样例夹具

**文件：**
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py`
- 修改：`rs-algorithm-worker/tests/conftest.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi.py`
- 新增：`rs-algorithm-worker/tests/fy3h_fixture.py`

- [ ] **Step 1: 写出配对识别失败测试。**

```python
def test_fy3h_requires_exactly_one_matching_1000m_and_geo1k_pair(synthetic_fy3h_pair):
    observation, geo = synthetic_fy3h_pair
    candidate = inspect_manifest(manifest(observation, geo))[0]
    assert candidate["profileKey"] == {
        "productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"
    }
    assert [(item["role"], Path(item["path"]).name) for item in candidate["files"]] == [
        ("OBSERVATION", observation.name), ("GEO1K", geo.name),
    ]

def test_fy3h_rejects_cross_time_orbit_or_dimension_pair(synthetic_fy3h_pair):
    observation, geo = synthetic_fy3h_pair
    with h5py.File(geo, "r+") as handle:
        handle.attrs["Orbit Number"] = np.array([9999], dtype=np.uint32)
    with pytest.raises(ValueError, match="matching 1000M and GEO1K"):
        inspect_manifest(manifest(observation, geo))
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi.py -q`

预期：失败，因为当前适配器把每个 HDF 当成单独的 `MAIN` 候选，既不配对也没有 `L1-1000M-GEO1K-v1` 档案。

- [ ] **Step 3: 在 `tests/fy3h_fixture.py` 实现最小可控 HDF 对。**

夹具必须创建以下真实路径和属性，而不是使用与生产不一致的别名：

```python
def write_fy3h_pair(root: Path, *, time="2335", orbit=3948) -> tuple[Path, Path]:
    observation = root / f"FY3H_MERSI_GRAN_L1_20260701_{time}_1000M_V0.HDF"
    geo = root / f"FY3H_MERSI_GRAN_L1_20260701_{time}_GEO1K_V0.HDF"
    # 观测：Data、Calibration、GEO、QA；GEO：Geolocation、Timedata。
    # 所有二维观测/导航数组使用相同的小尺寸，例如 (20, 16)，并保持 2 扫描帧 x 10 行的关系。
    return observation, geo
```

观测文件必须写入真实根属性 `Satellite Name=FY-3H`、`Sensor Identification Code=MERSI`、观测开始/结束日期时间、`Orbit Number`、`EarthSun Distance Ratio`、`Solar_Irradiance`、`Effect_Center_WaveLength`、`TBB_Trans_Coefficient`；写入四个真实观测数据集、它们的 `Slope`、`Intercept`、`FillValue`、`valid_range`，以及 `VIS_Cal_Coeff`、`IR_Cal_Coeff`、`QA_Frame_Flag`。GEO 文件必须写入同一套识别属性、`Geolocation/Latitude`、`Longitude`、四个角度、`QA_GEO` 和 `Timedata/DayNightFlag`。

- [ ] **Step 4: 实现严格的配对和映射。**

在 `fy3h_mersi.py` 中定义不可变映射，而不是按数组下标猜测：

```python
PROFILE = {"productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"}
OBSERVATION_ROLE = "OBSERVATION"
GEO_ROLE = "GEO1K"
REFLECTIVE_SOURCES = (("Data/EV_250_Aggr.1KM_RefSB", (1, 2, 3, 4)),
                      ("Data/EV_1KM_RefSB", tuple(range(5, 20))))
EMISSIVE_SOURCES = (("Data/EV_1KM_Emissive", (20, 21, 22, 23)),
                    ("Data/EV_250_Aggr.1KM_Emissive", (24, 25)))
GEO_PATHS = {"latitude": "Geolocation/Latitude", "longitude": "Geolocation/Longitude",
             "solar_zenith": "Geolocation/SolarZenith", "sensor_zenith": "Geolocation/SensorZenith",
             "qa": "Geolocation/QA_GEO", "day_night": "Timedata/DayNightFlag"}
```

`inspect_manifest()` 必须只接受 `.HDF`，按文件名和根属性分类，要求恰好一份 `1000M` 与一份 `GEO1K`，并逐项验证：卫星/载荷、日期、开始/结束时间、轨道号、版本、二维尺寸和必需数据集。候选 `packageId` 必须来自两文件共有的观测标识，`files` 必须保留原 manifest 的角色、序号和路径。删除旧的“每个 HDF 都生成候选”的行为。

- [ ] **Step 5: 配置真实样例 fixture 和只读一致性测试。**

将 `conftest.py` 中的 FY-3H fixture 改为只寻找一对文件：

```python
@pytest.fixture
def fy3h_mersi_manifest():
    root = _path_env("MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT")
    if root is None or not root.is_dir():
        pytest.skip("MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT is not configured")
    observation = next(root.glob("*_1000M_*.HDF"))
    geo = observation.with_name(observation.name.replace("_1000M_", "_GEO1K_"))
    if not geo.is_file():
        pytest.skip("FY-3H sample root lacks matching GEO1K")
    return manifest(observation, geo)
```

新增 `@pytest.mark.real_sample` 测试，确认 `2335` 配对、真实 `Geolocation/Latitude` 尺寸为 `(2000, 2048)`、`QA/QA_Frame_Flag` 为 200 帧、真实 GEO 数据集路径存在。测试只读取 `otherfile`，禁止向其中写入文件。

- [ ] **Step 6: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi.py -q`

预期：合成夹具测试通过；配置 `MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT` 时真实样例测试通过，否则仅该测试跳过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py rs-algorithm-worker/tests/conftest.py rs-algorithm-worker/tests/fy3h_fixture.py rs-algorithm-worker/tests/test_fy3h_mersi.py
git commit -m "feat: pair FY-3H MERSI 1000M and GEO1K inputs"
```

### Task 2: 定义 FY-3H 参数、DAG 和 Java 可消费的阶段契约

**文件：**
- 新增：`rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_profile.py`
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi.py`
- 修改：`rs-algorithm-worker/tests/test_registry.py`

- [ ] **Step 1: 写出参数和计划失败测试。**

```python
def test_fy3h_plan_declares_only_authorized_input_roles_and_outputs(candidate):
    plan = build_plan(candidate, {
        "targetCrs": "EPSG:4326", "resolutionDegrees": 0.01,
        "continuousResampling": "nearest", "bands": list(range(1, 26)),
    })
    stages = {item["stageKey"]: item for item in plan["stages"]}
    assert plan["version"] == "plan-v2"
    assert stages["DECODE_MERSI_L1"]["inputs"] == [
        {"key": "observation", "source": "RAW_INPUT", "artifactType": "OBSERVATION", "required": True},
        {"key": "geo", "source": "RAW_INPUT", "artifactType": "GEO1K", "required": True},
    ]
    assert {item["artifactType"] for item in stages["WRITE_MERSI_STANDARD_COG"]["outputs"]} == {
        "STANDARD_MERSI_COG", "STANDARD_MERSI_QUALITY_COG", "STANDARD_MERSI_SUMMARY_JSON"
    }

def test_fy3h_rejects_even_saliency_window_and_quality_override(candidate):
    with pytest.raises(ValueError, match="odd"):
        build_plan(candidate, {"saliency": {"enabled": True, "source": "CH01_REFLECTANCE", "windowSize": 4, "threshold": 0.5}})
    with pytest.raises(ValueError, match="unsupported FY-3H parameter"):
        build_plan(candidate, {"qualityPolicy": "ignore"})
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi.py tests\test_registry.py -q`

预期：失败，因为没有 FY-3H 参数解析器，且 registry 对 FY-3H 仍返回通用 `plan-v1`。

- [ ] **Step 3: 实现只接受白名单字段的参数解析。**

`parse_fy3h_parameters(raw)` 只接受下列顶层字段：`targetCrs`、`resolutionDegrees`、`roi`、`continuousResampling`、`bands`、`features`、`fusion`、`saliency`。实现以下不变量：

```python
ALLOWED_RESAMPLING = {"nearest"}

def require_odd_window(value: int, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 3 or value % 2 == 0:
        raise ValueError(f"{label} window size must be an odd integer of at least three")
    return value
```

允许 `EPSG:4326` 配合 `resolutionDegrees`；投影 CRS 必须配合 `resolutionMeters`，两种分辨率字段不得同时出现。ROI 固定为 WGS84 `[minLon, minLat, maxLon, maxLat]`，应有限、在经纬度范围内且最大值严格大于最小值。通道只能是唯一的 `1..25`。特征只允许公共 `statistics`、`gradient`、`local_variance`；融合所选 RGB 通道必须在选择通道中；显著性源必须为选择通道的标准变量或已声明的特征，窗口必须为奇数。不要允许参数替换定标系数、质量位或无效值规则。

- [ ] **Step 4: 生成完整 `plan-v2`。**

在适配器中复用 HY-2D 的 `_stage`、`_input`、`_output` 表示法或等价局部函数，依照本文“固定处理契约”写出六个固定阶段，并将规范化后的参数 JSON 用 `json.dumps(..., sort_keys=True, separators=(",", ":"))` 写入每一个 `parameterSnapshot`。只有 `enabled=True` 的三类算法才追加到计划；它们输入 `STANDARD_MERSI_COG` 或相应声明的特征产物，输出均为 `FINAL`。

- [ ] **Step 5: 补充 registry 计划路由测试。**

```python
def test_registry_builds_fy3h_plan_v2_for_recognized_pair(synthetic_fy3h_pair):
    observed, geo = synthetic_fy3h_pair
    manifest_value = manifest(observed, geo)
    candidate = dispatch({"operation": "inspect"}, manifest_value)["candidates"][0]
    response = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate, "parameterSnapshot": {"targetCrs": "EPSG:4326", "resolutionDegrees": 0.01},
    }}, manifest_value)
    assert response["version"] == "plan-v2"
    assert response["stages"][0]["stageKey"] == "VALIDATE_FY3H_PAIR"
```

- [ ] **Step 6: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi.py tests\test_registry.py -q`

预期：所有合成输入、参数拒绝和 `plan-v2` 声明测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_profile.py rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy3h_mersi.py rs-algorithm-worker/tests/test_fy3h_mersi.py rs-algorithm-worker/tests/test_registry.py
git commit -m "feat: declare FY-3H MERSI processing plan"
```

### Task 3: 建立中间模型、HDF 解码与官方定标单元

**文件：**
- 新增：`rs-algorithm-worker/src/rs_algorithm_worker/models_fy3h.py`
- 新增：`rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/fy3h_fixture.py`
- 新增：`rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py`

- [ ] **Step 1: 写出可见光和热红外定标失败测试。**

```python
def test_reflective_calibration_masks_fill_and_uses_per_band_coefficients():
    values = np.array([[100, 65535]], dtype=np.uint16)
    result = calibrate_reflective(values, slope=1.0, intercept=0.0,
        coefficients=np.array([1.0, 0.0, 0.0]), solar_zenith_degrees=np.array([[0.0, 0.0]]),
        earth_sun_distance_ratio=1.0, solar_irradiance=1000.0)
    assert result.reflectance[0, 0] == pytest.approx(1.0)
    assert np.isnan(result.reflectance[0, 1])
    assert result.radiance[0, 0] == pytest.approx(1000.0 / np.pi)

def test_thermal_calibration_uses_scan_coefficients_and_tbb_transform():
    result = calibrate_emissive(raw=np.array([[100]], dtype=np.uint16), slope=0.01, intercept=0.0,
        scan_coefficients=np.array([[[1.0], [0.0], [0.0], [0.0]]], dtype=np.float32),
        equivalent_wavenumber_cm=1000.0, tbb_a=1.0, tbb_b=0.0, scan_index=np.array([[0]]))
    assert np.isfinite(result.radiance[0, 0])
    assert np.isfinite(result.brightness_temperature[0, 0])
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py -q`

预期：失败，因为 FY-3H 中间模型与定标函数尚不存在。

- [ ] **Step 3: 定义可序列化的中间模型。**

在 `models_fy3h.py` 定义至少以下对象：

```python
@dataclass(frozen=True)
class DecodedMersi:
    dn_by_channel: Mapping[int, np.ndarray]
    longitude: np.ndarray
    latitude: np.ndarray
    solar_zenith_degrees: np.ndarray
    sensor_zenith_degrees: np.ndarray
    frame_flags: np.ndarray
    geo_flags: np.ndarray
    scan_index: np.ndarray
    metadata: Mapping[str, Any]

@dataclass(frozen=True)
class CalibratedMersi:
    reflectance_by_channel: Mapping[int, np.ndarray]
    radiance_by_channel: Mapping[int, np.ndarray]
    brightness_temperature_by_channel: Mapping[int, np.ndarray]
    metadata: Mapping[str, Any]
```

以 `.npz` 存储浮点数组，以 JSON 存储元数据与通道索引。序列化函数必须使用 `allow_pickle=False` 读取，并拒绝缺失的经纬度、通道或元数据版本。

- [ ] **Step 4: 实现有界 HDF 解码。**

`decode_mersi_pair(observation_path, geo_path, rows_per_window)` 必须按 `iter_dataset_windows()` 的窗口读四组观测、五个必需导航数组和质量数组，不能一次性同时读取全部 25 通道。解码阶段要保存原始 DN、每数据集的 `Slope/Intercept/valid_range/FillValue`、根属性和扫描索引；对于 200 帧、2000 行的真实样例，`scan_index` 必须使连续的 10 行映射到同一个帧索引。读取角度的真实编码、`scale_factor` 或 `Slope/Intercept` 必须先从 GEO1K 属性读取；缺少或非有限时立即报错，不默认假设单位。

- [ ] **Step 5: 实现物理定标并锁定公式版本。**

反射通道严格实现：

```python
dn = raw.astype(np.float32) * np.float32(slope) + np.float32(intercept)
ref_star = (cal2 * dn * dn + cal1 * dn + cal0) * np.float32(0.01)
reflectance = ref_star / np.cos(np.deg2rad(solar_zenith_degrees)) * earth_sun_distance_ratio ** 2
radiance = ref_star * solar_irradiance / np.float32(np.pi)
```

在实现代码前，人工视觉复核 L1 PDF 中 TOA 辐亮度公式的原页；将核对结果和 `fy3h-mersi-l1-v1` 写入模块常量及摘要。若官方 PDF 视觉复核结果不同，先更新本计划和设计文档，再修改公式，不得凭 PDF 文本抽取结果推测。

热红外函数必须将 `IR_Cal_Coeff` 按通道、探元和扫描帧索引应用到每一个像元，再执行官方 Planck 反演并以 `TBB_Trans_Coefficient` 的 A/B 转换亮温。函数必须拒绝非正辐亮度、非有限波数或没有对应扫描系数的数据，并将其交由质量原因而非生成虚假温度。

- [ ] **Step 6: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py tests\test_operators_hdf.py -q`

预期：定标公式、填充值、扫描帧索引和序列化的单元测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/models_fy3h.py rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py rs-algorithm-worker/tests/fy3h_fixture.py rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py
git commit -m "feat: decode and calibrate FY-3H MERSI L1"
```

### Task 4: 合成官方质量规则并验证异常 GEO 扫描

**文件：**
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/models_fy3h.py`
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi.py`

- [ ] **Step 1: 写出掩膜原因与帧扩展失败测试。**

```python
def test_quality_mask_preserves_reason_bits_and_expands_failed_frame_to_ten_rows():
    flags = np.array([0, 1 << 25], dtype=np.uint64)
    result = build_quality_mask(raw=np.array([[1], [65534], [1], [1], [1], [1], [1], [1], [1], [1],
                                               [1], [1], [1], [1], [1], [1], [1], [1], [1], [1]]),
        frame_flags=flags, geo_flags=np.zeros((2, 1), dtype=np.uint8), rows_per_scan=10)
    assert result.valid[0, 0]
    assert not result.valid[1, 0]
    assert not result.valid[10, 0]
    assert result.reason_bits[1, 0] & QUALITY_SATURATED
    assert result.reason_bits[10, 0] & QUALITY_FRAME_PREPROCESS_FAILURE
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py -q`

预期：失败，因为当前没有 FY-3H 专属质量位、帧行映射或摘要。

- [ ] **Step 3: 定义稳定的内部质量原因位。**

在 `models_fy3h.py` 中使用 `np.uint32` 位定义，不直接把来源 HDF 的位号泄漏为产品质量编码：

```python
QUALITY_MISSING = np.uint32(1 << 0)
QUALITY_SATURATED = np.uint32(1 << 1)
QUALITY_DEAD_DETECTOR = np.uint32(1 << 2)
QUALITY_OUT_OF_RANGE = np.uint32(1 << 3)
QUALITY_FRAME_CHANNEL_FAILURE = np.uint32(1 << 4)
QUALITY_FRAME_PREPROCESS_FAILURE = np.uint32(1 << 5)
QUALITY_CALIBRATION_FAILURE = np.uint32(1 << 6)
QUALITY_GEOLOCATION_FAILURE = np.uint32(1 << 7)
QUALITY_INVALID_NAVIGATION = np.uint32(1 << 8)
QUALITY_NON_DAYLIGHT = np.uint32(1 << 9)
```

`build_quality_mask()` 必须按通道处理 `65533/65534/65535`、`valid_range`、帧级 bit 0-24、bit 25、26、29、33 以及 GEO 质量。每项源规则以具名映射表实现并加入摘要 `sourceRules`，不得散落为魔法数。无效经纬度、有限范围外坐标、无效太阳/传感器角度和非白天反射像元必须标记。热红外夜间像元不能仅因夜间被掩膜。

- [ ] **Step 4: 输出质量摘要与真实 2350 回归测试。**

```python
assert summary["channelQuality"]["CH01"]["validPixelCount"] >= 0
assert summary["reasonCounts"]["GEOLOCATION_FAILURE"] > 0
assert summary["ruleVersion"] == "fy3h-mersi-l1-quality-v1"
```

真实样例测试要求 `MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT` 指向包含 `2335` 至 `2355` 的目录。读取 `2350` 对，并断言摘要记录了 GEO 异常扫描；不要求它抛异常或删除整景。

- [ ] **Step 5: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py tests\test_fy3h_mersi.py -q`

预期：合成掩膜、摘要和真实异常扫描测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/models_fy3h.py rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py rs-algorithm-worker/tests/test_fy3h_mersi.py
git commit -m "feat: apply FY-3H MERSI quality policy"
```

### Task 5: 实现逐像元导航网格化与标准 COG 输出

**文件：**
- 新增：`rs-algorithm-worker/src/rs_algorithm_worker/operators/swath_grid.py`
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/test_operators_spatial.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py`

- [ ] **Step 1: 写出科学层与质量层重采样分离测试。**

```python
def test_swath_grid_uses_only_valid_navigation_and_nearest_for_quality():
    values = np.array([[1.0, np.nan], [3.0, 5.0]], dtype=np.float32)
    quality = np.array([[0, 1], [0, 0]], dtype=np.uint32)
    longitude = np.array([[120.00, 120.01], [120.00, 120.01]])
    latitude = np.array([[30.01, 30.01], [30.00, 30.00]])
    science, mask = grid_swath(values, quality, longitude, latitude,
        target_crs="EPSG:4326", resolution=0.01, continuous_resampling="nearest")
    assert np.isfinite(science.values).any()
    assert set(np.unique(mask.values[~np.isnan(mask.values)])).issubset({0, 1})
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py tests\test_fy3h_mersi_pipeline.py -q`

预期：失败，因为还没有从非规则卫星扫描坐标到目标网格的公共算子。

- [ ] **Step 3: 在 `swath_grid.py` 实现可复用的最近邻 swath 网格算子。**

第一版固定采用有效源像元到目标格点中心的最近邻分配，避免对非规则扫描线错误使用仿射重投影。连续变量的 `continuousResampling="nearest"` 必须可用；`"bilinear"` 只能在实现了基于有效邻域的三角剖分/插值并有单独 NoData 测试后启用。先在参数解析中拒绝双线性，以保证本版本没有“声明可用但未实现”的方法。

```python
def grid_swath(values, quality_bits, longitude, latitude, *, target_crs, resolution, roi=None):
    valid = np.isfinite(values) & np.isfinite(longitude) & np.isfinite(latitude) & (quality_bits == 0)
    # pyproj.Transformer 将有效 WGS84 坐标转换为 target_crs。
    # scipy.spatial.cKDTree 仅以有效源点匹配每个目标中心。
    # 连续数据写 float32/NaN；质量层写 uint32，按同一个最近源像元复制。
    return science_raster, quality_raster
```

网格边界由有效坐标或 ROI 决定；长度单位必须与 CRS 和参数分辨率一致。网格算子必须拒绝无有效坐标、地理 CRS 配合米分辨率、投影 CRS 配合度分辨率以及空输出网格。为防止内存失控，目标点分块查询，并由参数限制最大输出行列数。

- [ ] **Step 4: 写标准 COG 和摘要。**

`WRITE_MERSI_STANDARD_COG` 产生三项声明产物：

```python
science = RasterData(values=stacked_19_reflectance_and_6_tbb, crs=crs, transform=transform,
    nodata=np.nan, band_metadata=tuple(metadata))
quality = RasterData(values=quality_bits.astype(np.uint32), crs=crs, transform=transform,
    nodata=np.uint32(0xFFFFFFFF), band_metadata=(BandMetadata("quality_mask", unit="bitmask"),))
```

科学 COG 波段描述为 `CH01_REFLECTANCE` 至 `CH19_REFLECTANCE`、`CH20_BRIGHTNESS_TEMPERATURE` 至 `CH25_BRIGHTNESS_TEMPERATURE`，并写入 SRF/根属性取得的中心波长与单位。质量 COG 单独输出且只用最近邻。摘要 JSON 必须有 `profileKey`、`ruleVersion`、`formulaVersion`、输入文件名、已解析参数、目标网格、各通道有效率、质量原因计数、产物 SHA-256；禁止包含本地绝对路径。

- [ ] **Step 5: 添加 COG 验收测试。**

```python
with rasterio.open(result.standard_cog) as dataset:
    assert dataset.count == 25
    assert dataset.descriptions[0] == "CH01_REFLECTANCE"
    assert dataset.descriptions[-1] == "CH25_BRIGHTNESS_TEMPERATURE"
    assert dataset.crs.to_string() == "EPSG:4326"
with rasterio.open(result.quality_cog) as dataset:
    assert dataset.count == 1
    assert dataset.descriptions == ("quality_mask",)
assert "C:\\" not in result.summary_path.read_text(encoding="utf-8")
```

- [ ] **Step 6: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py tests\test_operators_cog.py tests\test_fy3h_mersi_pipeline.py -q`

预期：网格、NoData、质量层和 COG 元数据测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/operators/swath_grid.py rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py rs-algorithm-worker/tests/test_operators_spatial.py rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py
git commit -m "feat: grid FY-3H MERSI swaths and write standard COGs"
```

### Task 6: 接入 Worker 阶段执行与可选公共算法

**文件：**
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/test_registry.py`

- [ ] **Step 1: 写出分阶段授权和输出哈希失败测试。**

```python
def test_quality_stage_rejects_raw_observation_when_only_declared_artifacts_are_authorized(tmp_path):
    with pytest.raises(ValueError, match="requires decoded and calibrated"):
        execute_fy3h_stage({"stageKey": "APPLY_MERSI_QUALITY", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
            "inputs": [{"role": "OBSERVATION", "path": str(tmp_path / "raw.HDF")}],
            "output": {"path": str(tmp_path / "output")},
        })

def test_fy3h_stage_returns_declared_relative_logical_artifacts(synthetic_fy3h_pair, tmp_path):
    result = execute_fy3h_stage(decode_payload(), decode_manifest(*synthetic_fy3h_pair, tmp_path))
    artifact = result["artifacts"][0]
    assert artifact["uri"] == "rs://output/task-1/decoded/mersi-l1.npz"
    assert artifact["checksum"].startswith("sha256:")
```

- [ ] **Step 2: 运行测试并确认失败。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py tests\test_registry.py -q`

预期：失败，因为 registry 尚未将 `FY-3H-MERSI` 的 `execute-stage` 请求路由到专用执行器。

- [ ] **Step 3: 实现每个固定阶段的受限输入读取。**

`execute_fy3h_stage(payload, manifest)` 必须用 `stageKey` 精确选择输入：

```python
STAGE_INPUTS = {
    "VALIDATE_FY3H_PAIR": {"OBSERVATION", "GEO1K"},
    "DECODE_MERSI_L1": {"OBSERVATION", "GEO1K"},
    "CALIBRATE_MERSI_L1": {"decoded"},
    "APPLY_MERSI_QUALITY": {"decoded", "calibrated"},
    "GRID_MERSI_SWATH": {"quality"},
    "WRITE_MERSI_STANDARD_COG": {"grid", "qualityGrid"},
}
```

在调用前将 manifest 中实际 `role` 集合与该集合比较，拒绝多余、缺失或重复授权输入。临时输出只能位于 `manifest["output"]["path"]` 下；响应中的 `uri` 只能由 `payload["outputUri"] + declared_relative_path` 组成。每个响应项由实际字节生成 `sizeBytes` 和 `sha256:`，不得返回绝对路径。

- [ ] **Step 4: 实现可选算法阶段。**

特征调用现有 `extract_features()`，仅允许参数已验证的指标；每个特征栅格输出 COG，统计写 JSON。融合调用 `fuse_bands(BandSet(...), FusionParameters(...))`，将输入 `CHxx_REFLECTANCE` 映射到用户选择的 RGB，输出类型 `MERSI_FUSION_COG` 并写 `displayProduct=true`。显著性调用 `detect_saliency()`，输出 `MERSI_SALIENCY_SCORE_COG` 和 `MERSI_SALIENCY_MASK_COG`；掩膜必须与评分栅格使用相同 CRS、transform、行列数，窗口由 Task 2 的奇数校验保障。

- [ ] **Step 5: 完成 registry 路由。**

在 `registry.py` 增加显式分支，优先检查 `payload["profileCode"] == "FY-3H-MERSI"`，与 HY-2D 路由相同：

```python
if operation == "execute-stage" and payload.get("profileCode") == "FY-3H-MERSI":
    return execute_fy3h_stage(payload, manifest)
```

`build-plan` 必须对由 `inspect_manifest()` 返回且 `profileKey` 正确的候选调用 FY-3H 的 `build_plan()`；非配对 HDF、伪造 `packageId` 或错误 profile 不得落入旧的通用 FY-3H 分支。

- [ ] **Step 6: 运行测试并提交。**

运行：`cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py tests\test_fy3h_mersi.py tests\test_registry.py tests\test_algorithms_baseline.py -q`

预期：所有阶段仅使用声明输入，正常响应含逻辑 URI、大小与哈希；算法分支不影响标准 COG。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy3h_mersi_pipeline.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py rs-algorithm-worker/tests/test_registry.py
git commit -m "feat: execute FY-3H MERSI processing stages"
```

### Task 7: 复核 Java 阶段授权、产物提升与登记

**文件：**
- 修改：`htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java`
- 修改：`htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- 修改：`htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ArtifactRegistrationServiceTest.java`

- [ ] **Step 1: 写出 FY-3H 输入最小授权测试。**

```java
ProcessingStage decode = new ProcessingStage("DECODE_MERSI_L1", "DECODE_MERSI_L1", List.of(), List.of(
    new StageInput("observation", StageInput.Source.RAW_INPUT, "OBSERVATION", true),
    new StageInput("geo", StageInput.Source.RAW_INPUT, "GEO1K", true)
), List.of(new StageOutput("DECODED_MERSI_L1", "decoded/mersi-l1.npz", StageOutput.Retention.INTERMEDIATE)), "{}");

assertThat(resolver.resolve(task(List.of(decode), List.of(observation, geo, unrelated)), decode, List.of(stage), List.of()))
    .extracting(InputAsset::role).containsExactly("OBSERVATION", "GEO1K");
```

- [ ] **Step 2: 运行测试并确认当前契约支持它。**

运行：`mvn -pl htht-modules/htht-multisource -Dtest=StageInputResolverTest test`

预期：新测试应通过。若失败，只允许修改 `StageInputResolver.java` 以修复“按声明角色精确解析一个输入”的行为，并追加针对该缺陷的回归断言；禁止让未声明资产进入 grant。

- [ ] **Step 3: 添加上游 Artifact 与输出精确匹配测试。**

```java
ProcessingStage quality = new ProcessingStage("APPLY_MERSI_QUALITY", "APPLY_MERSI_QUALITY",
    List.of("DECODE_MERSI_L1", "CALIBRATE_MERSI_L1"), List.of(
        new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_MERSI_L1", true),
        new StageInput("calibrated", StageInput.Source.UPSTREAM_ARTIFACT, "CALIBRATED_MERSI_RASTER", true)
    ), List.of(
        new StageOutput("QUALITY_MERSI_RASTER", "quality/mersi.npz", StageOutput.Retention.INTERMEDIATE),
        new StageOutput("MERSI_QUALITY_SUMMARY_JSON", "quality/summary.json", StageOutput.Retention.INTERMEDIATE)
    ), "{}");
```

断言 `DispatchNextStageUseCase` 的 `ExecutionGrantRequest.inputs()` 仅含上述两个上游 Artifact；断言 `ArtifactRegistrationService` 拒绝路径不匹配、漏产物、额外产物和 SHA-256 不匹配的响应，并能提升 `STANDARD_MERSI_COG`、`STANDARD_MERSI_QUALITY_COG`、`STANDARD_MERSI_SUMMARY_JSON` 三项最终产物。

- [ ] **Step 4: 运行 Java 回归并提交。**

运行：`mvn -pl htht-modules/htht-multisource -Dtest=StageInputResolverTest,DispatchNextStageUseCaseTest,ArtifactRegistrationServiceTest test`

预期：Java 对现有 `plan-v2` 契约的 FY-3H 具体实例全部通过；若无生产代码改动，仅提交测试以记录契约。

```powershell
git add htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ArtifactRegistrationServiceTest.java
git commit -m "test: verify FY-3H stage artifact contract"
```

### Task 8: 用真实正常与异常场景完成端到端验收

**文件：**
- 修改：`rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py`
- 修改：`rs-algorithm-worker/tests/test_cli.py`
- 修改：`rs-algorithm-worker/tests/conftest.py`

- [ ] **Step 1: 写出真实 `2335` 全链路测试。**

```python
@pytest.mark.real_sample
def test_real_fy3h_2335_produces_standard_cogs_and_summary(fy3h_mersi_sample_root, tmp_path):
    observation, geo = pair_for_time(fy3h_mersi_sample_root, "2335")
    response = run_full_fy3h_plan(observation, geo, tmp_path, {
        "targetCrs": "EPSG:4326", "resolutionDegrees": 0.01, "bands": list(range(1, 26)),
    })
    artifacts = {item["type"]: item for item in response["artifacts"]}
    assert {"STANDARD_MERSI_COG", "STANDARD_MERSI_QUALITY_COG", "STANDARD_MERSI_SUMMARY_JSON"} <= artifacts.keys()
    assert all("FY3H_MERSI_GRAN" not in item["uri"] for item in artifacts.values())
```

- [ ] **Step 2: 运行并确认首次真实样例结果。**

运行：

```powershell
cd rs-algorithm-worker
$env:MULTISOURCE_FY3H_MERSI_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\FY3H_MERSI_20260701\FY3H_MERSI_20260701'
.\.venv\Scripts\python.exe -m pytest tests\test_fy3h_mersi_pipeline.py -m real_sample -q
```

预期：`2335` 的标准 COG、质量 COG 和摘要生成成功。若计算量超出单测时间预算，只允许将真实测试标记为 `real_sample` 并在 CI 中显式配置，不得删除真实验收。

- [ ] **Step 3: 添加真实 `2350` GEO 异常回归。**

```python
@pytest.mark.real_sample
def test_real_fy3h_2350_keeps_product_and_reports_geolocation_quality(fy3h_mersi_sample_root, tmp_path):
    observation, geo = pair_for_time(fy3h_mersi_sample_root, "2350")
    summary = run_through_quality_stage(observation, geo, tmp_path)
    assert summary["reasonCounts"]["GEOLOCATION_FAILURE"] > 0
    assert summary["channelQuality"]["CH01"]["validPixelCount"] > 0
```

这项测试确认出现部分定位异常时系统掩膜受影响扫描，而不是假装数据正常、整景崩溃或输出无质量说明的 COG。

- [ ] **Step 4: 验证 CLI 协议与完整 Worker 回归。**

在 `test_cli.py` 为 `inspect`、`build-plan` 和每个 `execute-stage` 加入 FY-3H 请求。所有请求使用 manifest 授权路径，输出写入 `tmp_path`，并断言响应 JSON 不含真实输入绝对路径。然后运行：

```powershell
cd rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest -q
cd ..
mvn -pl htht-modules/htht-multisource test
```

预期：完整 Python 与 Java 回归通过；只有未配置样例目录的 `real_sample` 测试可跳过。

- [ ] **Step 5: 人工检查 COG 与摘要并提交。**

用 `rasterio.open()` 检查真实 `2335` 科学 COG 的 25 波段描述、CRS、NoData、块结构和范围；检查质量 COG 的整数类型；检查摘要没有本地路径且有规则/公式版本、质量计数和 SHA-256。将检查命令和关键值记录在测试断言中，而不是依赖人工口头结论。

```powershell
git add rs-algorithm-worker/tests/test_fy3h_mersi_pipeline.py rs-algorithm-worker/tests/test_cli.py rs-algorithm-worker/tests/conftest.py
git commit -m "test: verify FY-3H MERSI real-scene processing"
```

## 完成检查

- [ ] `1000M + GEO1K` 只能作为同轨同版本成对候选被识别；错误配对在 Worker 检查前失败。
- [ ] 反射 1-19 和热红外 20-25 的定标参数均来自真实 HDF 和已复核的官方 L1 公式，公式版本被写入摘要。
- [ ] `65533/65534/65535`、有效范围、帧质量与 GEO 质量均生成可解释的统一质量原因位。
- [ ] 网格化只使用 `Geolocation/*` 逐像元导航；科学值、质量位和 NoData 遵循不同的明确规则。
- [ ] Java 对每阶段仅授权声明输入，Worker 仅返回相对逻辑产物，Java 校验并登记最终 Artifact。
- [ ] 正常 `2335` 场景完成完整主链路，异常 `2350` 场景保留有效部分并报告定位质量异常。
- [ ] 前端改造不在本计划内；其后续工作只需消费任务阶段状态与已登记的 Artifact 逻辑 URI。

## 计划自检

- 范围覆盖：输入配对、参数/DAG、HDF 解码、官方定标、质量、网格、COG、公共算法、Worker 路由、Java 契约和真实数据验收均有对应任务。
- 科学边界：TOA 辐亮度公式要求先视觉复核官方 PDF 原页；未实现的双线性 swath 插值在参数层拒绝，不会伪装为已支持。
- 类型一致：原始角色固定为 `OBSERVATION/GEO1K`；中间与最终 Artifact 类型与所有下游输入一致；所有 Worker 路径为相对路径和逻辑 URI。
- 前端边界：不包含控制器、DTO、Vue 页面或下载界面改动，避免与后续前端计划交叉。
