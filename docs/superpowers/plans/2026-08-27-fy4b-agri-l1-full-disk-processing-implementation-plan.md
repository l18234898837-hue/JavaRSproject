# FY-4B AGRI 全圆盘 L1 处理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为完整的 FY-4B AGRI 全圆盘 L1 五文件观测包提供可授权执行的输入核验、定标、名义定位、重投影、裁剪、COG、特征、伪彩融合和显著性候选检测。

**Architecture:** Java 仍负责处理计划验证、输入授权、阶段调度和逻辑产物登记；只修复其禁止消费 `FINAL` 上游产物的通用校验。Python Worker 将 FY-4B 的五个 HDF 识别为单一观测包，按窗口生成带有静止卫星名义网格的中间栅格，再生成用户可下载的 COG 和可选算法产物。真实海岸线控制点仅验证定位残差，不能让系统对 L1 数据宣称官方高精度几何校正。

**Tech Stack:** Java 17、JUnit 5、Python 3.11+、pytest、h5py、NumPy、Rasterio/GDAL、PyProj、SciPy。

**已批准规格:** `docs/superpowers/specs/2026-08-27-fy4b-agri-l1-processing-design.md`。

**范围边界:** 本计划不创建前端页面、不新增数据库迁移、不实现云/火点/降水/地表温度等 L2 专题反演，也不实现多时相变化检测、地形校正或云顶视差校正。

---

## 固定输入、参数与阶段契约

### 原始输入角色

一个可执行候选产品必须且只能有以下五个原始角色，各一份：

| 角色 | 文件名中的类型 | 分辨率 | 必需数据集 |
| --- | --- | --- | --- |
| `L1_0500M` | `FDI/MULT/NOM` | 500M | `Data/NOMChannel02`、`Calibration/CALChannel02`、`NOMObs/NOMObsTime` |
| `L1_1000M` | `FDI/MULT/NOM` | 1KM | `Data/NOMChannel01..03`、对应 `CALChannelXX`、`NOMObs/NOMObsTime` |
| `L1_2000M` | `FDI/MULT/NOM` | 2KM | `Data/NOMChannel01..08`、对应 `CALChannelXX`、`NOMObs/NOMObsTime` |
| `L1_4000M` | `FDI/MULT/NOM` | 4KM | `Data/NOMChannel01..15`、对应 `CALChannelXX`、`NOMObs/NOMObsTime` |
| `GEO_4000M` | `GEO/MULT/NOM` | 4KM | `Navigation/LineNumber`、`ColumnNumber`、四个角度层、`QA/NavQualityFlag` |

文件名必须匹配：

```text
FY4B-_AGRI--_N_DISK_<subSatellite>_L1-_<FDI-or-GEO>-_MULT_NOM_<start>_<end>_<resolution>_V<version>.HDF
```

其中五个文件的 `<subSatellite>`、`<start>`、`<end>`、`<version>` 以及根属性的卫星、仪器、观测时间必须完全一致。生产验证还必须确认全圆盘形状为 `21984`、`10992`、`5496`、`2748` 的正方形，并确认 GEO 为 `2748 x 2748`；小尺寸 HDF 只允许测试夹具使用。生产的 `inspect_manifest` 不得从 Manifest、参数快照或环境变量接受“跳过形状检查”的开关；小尺寸夹具只用于已经通过适配器边界后的纯函数和被 mock 的窗口读取测试。

### 公共参数快照

`parse_fy4b_parameters` 只接受下列 JSON 形状；缺失可选算法对象时按 `enabled=false` 补齐。`targetCrs` 只允许 `EPSG:4326` 或 `EPSG:4490`，`roi` 缺失时保留全圆盘有效区域。

```json
{
  "schemaVersion": "1",
  "targetCrs": "EPSG:4326",
  "roi": [100.0, 15.0, 125.0, 40.0],
  "features": {
    "enabled": true,
    "metrics": ["statistics", "gradient", "local_variance", "toa_nir_red_nd", "btd_c13_c14"],
    "windowSize": 5
  },
  "fusion": {"enabled": true, "mode": "NIR_RED_BLUE", "percentiles": [2, 98]},
  "saliency": {"enabled": true, "source": "btd_c13_c14", "threshold": 0.55, "windowSize": 5}
}
```

`roi` 为 WGS84 `[minLon, minLat, maxLon, maxLat]`，四个值有限且严格递增。特征仅允许 `statistics`、`gradient`、`local_variance`、`toa_nir_red_nd`、`btd_c13_c14`；窗口必须是大于等于 3 的奇数。融合只允许 `NIR_RED_BLUE`。显著性来源只允许 `C02_REFLECTANCE_500M`、`C03_REFLECTANCE_1000M`、`C13_BRIGHTNESS_TEMPERATURE_4000M`、`toa_nir_red_nd`、`btd_c13_c14`、`gradient:C02_REFLECTANCE_500M`、`local_variance:C02_REFLECTANCE_500M`。

海岸线控制点文件不是 Worker 的原始输入角色、阶段参数或运行时环境配置。它只由 `pytest` 的真实样例验收夹具在 Worker 产物生成后只读使用，独立生成验收报告。因此标准 COG 的业务 summary 始终使用 `NOMINAL_UNVERIFIED`；只有验收报告在达到阈值时才写 `COASTLINE_VALIDATED`。这既保留已验证状态，也不允许未授权的本地控制点影响生产任务。

### 产物与 DAG

```text
VALIDATE_FY4B_PACKAGE
  -> BUILD_FY4B_NOMINAL_GRID
  -> CALIBRATE_AND_GEOREFERENCE_FY4B
  -> REPROJECT_AND_CROP_FY4B
  -> WRITE_FY4B_STANDARD_COG
     -> EXTRACT_FY4B_FEATURES       # features.enabled
     -> FUSE_FY4B_BANDS             # fusion.enabled
     -> DETECT_FY4B_SALIENCY        # saliency.enabled；按 source 依赖 standard 或 features
```

阶段产物类型固定如下。`NATIVE_*` 与 `GRIDDED_*` 为 `INTERMEDIATE`；标准及可选算法产物为 `FINAL`。后续阶段可以消费依赖阶段的 `FINAL` 产物，Java 的输入解析仍必须解析为恰好一个任务所属文件。

```text
FY4B_INPUT_VALIDATION_JSON
FY4B_NOMINAL_GRID_JSON
FY4B_NATIVE_0500M_RASTER             FY4B_NATIVE_0500M_QUALITY
FY4B_NATIVE_1000M_RASTER             FY4B_NATIVE_1000M_QUALITY
FY4B_NATIVE_2000M_RASTER             FY4B_NATIVE_2000M_QUALITY
FY4B_NATIVE_4000M_RASTER             FY4B_NATIVE_4000M_QUALITY
FY4B_CALIBRATION_SUMMARY_JSON
FY4B_GRIDDED_0500M_RASTER            FY4B_GRIDDED_0500M_QUALITY
FY4B_GRIDDED_1000M_RASTER            FY4B_GRIDDED_1000M_QUALITY
FY4B_GRIDDED_2000M_RASTER            FY4B_GRIDDED_2000M_QUALITY
FY4B_GRIDDED_4000M_RASTER            FY4B_GRIDDED_4000M_QUALITY
FY4B_RED_500M_COG                    FY4B_RED_500M_QUALITY_COG
FY4B_VIS_NIR_1000M_COG               FY4B_VIS_NIR_1000M_QUALITY_COG
FY4B_SPECTRAL_2000M_COG              FY4B_SPECTRAL_2000M_QUALITY_COG
FY4B_MULTISPECTRAL_4000M_COG         FY4B_MULTISPECTRAL_4000M_QUALITY_COG
FY4B_STANDARD_SUMMARY_JSON
FY4B_FEATURE_COG                     FY4B_FEATURE_SUMMARY_JSON
FY4B_FALSE_COLOR_COG
FY4B_SALIENCY_SCORE_COG              FY4B_SALIENCY_MASK_COG
FY4B_SALIENCY_SUMMARY_JSON
```

## 目标文件结构

```text
htht-modules/htht-multisource/
  src/main/java/com/htht/multisource/domain/model/ProcessingPlanValidator.java
  src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java

rs-algorithm-worker/
  src/rs_algorithm_worker/
    product_adapters/fy4b_agri.py        # 五文件识别和 `plan-v2` DAG
    fy4b_agri_profile.py                 # 参数白名单与阶段输入/输出声明
    fy4b_agri_navigation.py              # 名义 geos 网格和控制点残差
    fy4b_agri_pipeline.py                # 所有 FY-4B 阶段执行器
    registry.py                           # inspect/build-plan/execute-stage 路由
  tests/
    fy4b_fixture.py                       # 最小和完整形状 HDF 夹具工厂
    test_fy4b_agri.py                     # 适配器和参数/DAG 测试
    test_fy4b_agri_navigation.py          # CRS、网格、控制点测试
    test_fy4b_agri_pipeline.py            # 定标、质量、COG、算法和阶段测试
    test_fy4b_agri_real_sample.py         # 只读真实五文件验收
    test_registry.py                       # Worker 路由与逻辑 URI 契约
    test_cli.py                            # 端到端 CLI Manifest 契约
    conftest.py                            # FY-4B 实样目录和控制点环境变量夹具
```

现有 `operators/hdf.py`、`operators/reproject.py`、`operators/crop.py`、`operators/cog.py`、`algorithms/features.py`、`algorithms/fusion.py` 和 `algorithms/saliency.py` 直接复用；本计划不复制其通用实现。

### Task 1: 允许声明依赖的 FINAL 产物进入下游阶段

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingPlanValidator.java:43-62`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java:95-110`

- [ ] **Step 1: 将当前“FINAL 产物被下游消费”拒绝测试改为允许测试。**

把第 108-110 行的断言替换为：

```java
assertThatCode(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(finalProducer, consumer)))
    .doesNotThrowAnyException();
```

保留同一个 `consumer`，使测试仍确认该输入必须来自显式依赖 `FINAL`。

- [ ] **Step 2: 运行单个测试并确认失败。**

Run:

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject
mvn -pl htht-modules/htht-multisource -am -Dtest=ProcessingPlanValidatorTest -DfailIfNoTests=false test
```

Expected: 失败信息包含 `Processing plan final artifact cannot be consumed downstream.`。

- [ ] **Step 3: 删除仅按 retention 拒绝输入的分支，保留依赖和类型校验。**

在 `validateStageBindings` 中保留 producer 查找与 artifact type 查找，删除下列块：

```java
if (output.retention() == StageOutput.Retention.FINAL) {
    throw new IllegalArgumentException("Processing plan final artifact cannot be consumed downstream.");
}
```

不得放宽以下限制：上游产物类型必须由直接依赖阶段产生；输入 key 仍唯一；输出类型仍全计划唯一；`StageInputResolver` 仍要求恰好一份任务所属资产。

- [ ] **Step 4: 重新运行定向 Java 测试。**

Run the Step 2 command.

Expected: `ProcessingPlanValidatorTest` 全部通过，重复输出、缺失依赖、循环依赖和非法相对路径测试仍通过。

- [ ] **Step 5: 提交通用契约修复。**

```powershell
git add htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingPlanValidator.java htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java
git commit -m "fix: allow declared final artifacts in downstream stages"
```

### Task 2: 建立 FY-4B 五文件测试夹具与严格证据识别

**Files:**
- Create: `rs-algorithm-worker/tests/fy4b_fixture.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py:1-25`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri.py:1-12`
- Modify: `rs-algorithm-worker/tests/conftest.py:71-77`

- [ ] **Step 1: 写入五文件完整性、时间一致性和角色顺序的失败测试。**

在 `test_fy4b_agri.py` 使用 `make_fy4b_package(tmp_path)` 建立五个合成 HDF，并加入：

```python
def test_inspect_requires_the_complete_ordered_fy4b_package(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    candidate = inspect_manifest(manifest)[0]
    assert [item["role"] for item in candidate["files"]] == [
        "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
    ]
    assert candidate["profileKey"] == {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}

def test_inspect_rejects_a_package_with_mismatched_observation_end(tmp_path):
    manifest = make_fy4b_package(tmp_path, end_by_role={"GEO_4000M": "20260701002959"})
    with pytest.raises(ValueError, match="matching FY-4B AGRI five-file package"):
        inspect_manifest(manifest)
```

再加入缺少 `L1_2000M`、500M 缺少 `Data/NOMChannel02`、4KM 缺少 `Data/NOMChannel15` 和 GEO 缺少 `Navigation/NOMSunZenith` 的拒绝测试。

- [ ] **Step 2: 运行测试并确认现有单 HDF 适配器不满足契约。**

Run:

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri.py -q
```

Expected: 失败，因为现有适配器只接受一个 `MAIN` HDF 并调用通用 `build_plan`。

- [ ] **Step 3: 创建可控 HDF 工厂并实现文件分类。**

在 `fy4b_fixture.py` 定义 `make_fy4b_package(tmp_path, *, end_by_role=None, missing_datasets=())`。它为适配器测试创建文档规定的五种完整 shape 的 chunked HDF dataset（只使用 HDF fill value，不写入全圆盘像元，避免占用大文件）；每个文件写入相同的根属性：`Satellite Name=FY-4B`、`Sensor Name=AGRI`、`Dataset Name=MULT`、`OBIType=DISK`、`NOMCenterLon=105.0`、`NOMSatHeight=35786000.0`、观测时间和版本；各文件按上表创建数据集、有效范围、`FillValue=65535`、`CALChannelXX`、`CALIBRATION_COEF(SCALE+OFFSET)`、`ESUN` 与 QA 数组。另定义 `make_fy4b_channel_fixture(tmp_path)`，仅为 `read_and_calibrate_l1` 等已越过适配器边界的纯读取函数创建 3 x 3 chunked 通道，绝不将它送入 `inspect_manifest`。

在 `fy4b_agri.py` 定义下列常量和函数：

```python
PROFILE_KEY = {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}
ROLE_ORDER = ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M")
CHANNELS_BY_ROLE = {
    "L1_0500M": (2,), "L1_1000M": (1, 2, 3),
    "L1_2000M": tuple(range(1, 9)), "L1_4000M": tuple(range(1, 16)),
}
FULL_DISK_SHAPES = {
    "L1_0500M": (21984, 21984), "L1_1000M": (10992, 10992),
    "L1_2000M": (5496, 5496), "L1_4000M": (2748, 2748),
    "GEO_4000M": (2748, 2748),
}
```

实现 `inspect_manifest`、`inspect_package`、`_classify_file`、`_validate_l1` 和 `_validate_geo`。`inspect_package` 只能从 Manifest 输入路径读取，按文件名和根属性共同分类，拒绝重复角色、额外 HDF、错误分辨率、非 `DISK`、缺少数据集、不符合 `FULL_DISK_SHAPES` 或不匹配的身份字段。成功候选的 `metadataSnapshot` 写入 `subSatellite`, `observingBeginning`, `observingEnding`, `version`, `availableChannelsByResolution`, `sourceDataQuality`；绝不写本地绝对路径。

- [ ] **Step 4: 将实样夹具改为完整目录契约。**

在 `conftest.py` 新增（样例根目录可含多个时次；当存在多个完整五文件包时，必须设置 `MULTISOURCE_FY4B_SAMPLE_PACKAGE_ID=<subSatellite>_<start>_<end>_<version>` 精确选择一个，禁止按文件排序任取一个）：

```python
@pytest.fixture
def fy4b_manifest():
    root = _path_env("MULTISOURCE_FY4B_SAMPLE_ROOT")
    if root is None or not root.is_dir():
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_ROOT is not configured")
    groups = group_fy4b_hdfs(root.rglob("FY4B-_AGRI--_N_DISK_*_L1-_*_MULT_NOM_*_V*.HDF"))
    package_id = os.getenv("MULTISOURCE_FY4B_SAMPLE_PACKAGE_ID")
    paths = select_complete_fy4b_package(groups, package_id)
    if paths is None:
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_ROOT lacks one selected complete five-file package")
    return {"inputs": [
        {"path": str(path), "role": "UNCLASSIFIED", "ordinal": index,
         "storageUri": f"file:///{path.name}"}
        for index, path in enumerate(paths)
    ]}
```

在同一文件实现 `group_fy4b_hdfs` 和 `select_complete_fy4b_package`：前者用适配器相同的文件名正则抽取 `subSatellite`、`start`、`end`、`version`，以四元组组成 package ID，并为每个 ID 收集五种文件；后者仅在所选 ID 恰有 `L1_0500M`、`L1_1000M`、`L1_2000M`、`L1_4000M`、`GEO_4000M` 各一份时返回按 `ROLE_ORDER` 排列的路径。未设置 package ID 时只接受唯一完整组；设置后必须精确命中该组；其他情况返回 `None` 使夹具跳过并给出上述原因。适配器识别候选时重新返回五个正式角色；测试不依赖下载文件的目录名以外的本地路径。

- [ ] **Step 5: 重新运行适配器测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri.py -q
```

Expected: 合成五文件测试通过；真实样例测试在环境变量未配置时显示 `skipped`。

```powershell
git add rs-algorithm-worker/tests/fy4b_fixture.py rs-algorithm-worker/tests/test_fy4b_agri.py rs-algorithm-worker/tests/conftest.py rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py
git commit -m "feat: validate FY-4B AGRI five-file packages"
```

### Task 3: 冻结 FY-4B 参数、阶段输入与输出声明

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_profile.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri.py`

- [ ] **Step 1: 写参数拒绝和 `plan-v2` 失败测试。**

```python
def test_build_plan_freezes_fy4b_parameters_and_declares_all_raw_roles(tmp_path):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]
    plan = build_plan(candidate, {
        "schemaVersion": "1", "targetCrs": "EPSG:4326",
        "features": {"enabled": False}, "fusion": {"enabled": False},
        "saliency": {"enabled": False},
    })
    assert plan["version"] == "plan-v2"
    assert [item["artifactType"] for item in plan["stages"][0]["inputs"]] == [
        "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
    ]
    assert plan["stages"][-1]["stageKey"] == "WRITE_FY4B_STANDARD_COG"

@pytest.mark.parametrize("raw", [
    {"schemaVersion": "2", "targetCrs": "EPSG:4326"},
    {"schemaVersion": "1", "targetCrs": "EPSG:3857"},
    {"schemaVersion": "1", "targetCrs": "EPSG:4326", "roi": [120, 30, 120, 31]},
])
def test_parameter_parser_rejects_unapproved_fy4b_values(raw):
    with pytest.raises(ValueError, match="FY-4B parameter"):
        parse_fy4b_parameters(raw)
```

再断言 `toa_nir_red_nd` 或 `btd_c13_c14` 被选为显著性 source 时，`DETECT_FY4B_SALIENCY` 依赖 `EXTRACT_FY4B_FEATURES` 且输入类型为 `FY4B_FEATURE_COG`。

- [ ] **Step 2: 运行测试并确认缺少 FY-4B 参数解析和阶段 DAG。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri.py -q
```

Expected: collection 失败于 `fy4b_agri_profile`，或断言失败于当前通用两阶段计划。

- [ ] **Step 3: 实现严格参数解析器。**

在 `fy4b_agri_profile.py` 定义：

```python
DEFAULT_PARAMETERS = {
    "schemaVersion": "1", "targetCrs": "EPSG:4326", "roi": None,
    "features": {"enabled": False, "metrics": ("statistics",), "windowSize": 3},
    "fusion": {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": (2.0, 98.0)},
    "saliency": {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
}
```

实现 `parse_fy4b_parameters`、`stage`、`raw_input`、`artifact_input` 和 `output`。解析器拒绝未知顶层或嵌套字段、布尔值伪装的数字、重复 metric、非有限阈值、无效 percentiles 和与显著性 source 不匹配的 feature 配置。`stage` 返回 `stageKey`、`stageCode`、`dependsOnStageKeys`、`inputs`、`outputs`、`parameterSnapshot`；`raw_input` 返回 `source="RAW_INPUT"`，`artifact_input` 返回 `source="UPSTREAM_ARTIFACT"`，两者都写入 `key`、`artifactType`、`required=true`；`output` 返回 `artifactType`、`relativePath` 和 `retention`。用 `json.dumps(parameters, sort_keys=True, separators=(",", ":"))` 写入每一阶段的 `parameterSnapshot`。

- [ ] **Step 4: 在适配器中声明固定阶段与产物。**

`build_plan` 必须校验 `PROFILE_KEY`、`GEOSTATIONARY_RASTER`、`SUPPORTED` 和五角色顺序，再按“固定输入、参数与阶段契约”章节生成 DAG。`WRITE_FY4B_STANDARD_COG` 输出四个 science COG、四个 quality COG 和 summary，保留 `FINAL`。三个可选阶段只在相应 `enabled=true` 时追加，融合固定使用 `FY4B_VIS_NIR_1000M_COG`；显著性按 source 消费对应标准 COG 或 `FY4B_FEATURE_COG`。

- [ ] **Step 5: 重新运行参数和计划测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri.py -q
```

Expected: 所有合成参数、DAG、依赖和输出类型断言通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_profile.py rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/fy4b_agri.py rs-algorithm-worker/tests/test_fy4b_agri.py
git commit -m "feat: declare FY-4B AGRI processing stages"
```

### Task 4: 实现官方定标、文件级质量和窗口化原生栅格读取

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`
- Modify: `rs-algorithm-worker/tests/fy4b_fixture.py`
- Create: `rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py`

- [ ] **Step 1: 写反射、热红外和质量掩膜的失败测试。**

```python
def test_reflective_calibration_uses_lookup_and_marks_fill_value():
    result = calibrate_reflective(
        np.array([[0, 2, 65535]], dtype=np.uint16),
        lookup=np.array([0.0, 0.1, 0.2], dtype=np.float32), fill_value=65535,
        scale=0.1, offset=0.0, esun=1000.0,
    )
    np.testing.assert_allclose(result.reflectance[0, :2], [0.0, 0.2])
    assert np.isnan(result.reflectance[0, 2])
    assert result.radiance[0, 1] == pytest.approx(0.2 * 1000.0 / np.pi)

def test_emissive_calibration_uses_brightness_temperature_lookup():
    result = calibrate_emissive(
        np.array([[1, 65535]], dtype=np.uint16),
        lookup=np.array([180.0, 181.0], dtype=np.float32), fill_value=65535,
        scale=0.5, offset=1.0,
    )
    assert result.brightness_temperature[0, 0] == pytest.approx(181.0)
    assert result.radiance[0, 0] == pytest.approx(1.5)
    assert np.isnan(result.brightness_temperature[0, 1])

def test_quality_marks_channel_navigation_and_calibration_failure():
    quality = build_quality_mask(np.array([[2, 65535]], dtype=np.uint16), 65535, (0, 4095),
                                 navigation_ok=False, calibration_ok=False, file_quality_ok=False)
    assert quality.reason_bits[0, 0] != 0
    assert quality.reason_bits[0, 1] != 0
```

- [ ] **Step 2: 运行测试并确认函数不存在。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py -q
```

Expected: collection 失败，因为 `fy4b_agri_pipeline.py` 不存在。

- [ ] **Step 3: 实现物理量转换和质量位。**

定义稳定质量 bit：`FY4B_QUALITY_FILL=1`、`FY4B_QUALITY_OUT_OF_RANGE=2`、`FY4B_QUALITY_NAVIGATION_FAILURE=4`、`FY4B_QUALITY_CALIBRATION_DEGRADED=8`、`FY4B_QUALITY_FILE_DEGRADED=16`、`FY4B_QUALITY_OUTSIDE_DISK=32`。`calibrate_reflective` 和 `calibrate_emissive` 都必须先排除填充值、负 DN 和超出 HDF `valid_range` 的值，再按 DN 对 `CALChannelXX` 查表。若查表长度不足以覆盖有效 DN，抛出 `ValueError("FY-4B calibration table does not cover valid DN")`。

反射实现返回 `reflectance` 与 `radiance`；表观反射率在 Task 5 获得太阳天顶角后计算。热红外实现返回 `brightness_temperature` 与 `radiance`。`CalQualityFlag` 中反射波段 Bit0 或热红外波段 Bit1 为降级时对应通道必须设置质量 bit；`NavQualityFlag != 0` 设置导航失败 bit；根 `Data Quality != 0` 设置文件质量 bit。不要把这些通道级标志广播成云类别。

- [ ] **Step 4: 加入窗口读取和序列化边界测试。**

使用 `make_fy4b_channel_fixture` 的 3 x 3 chunked `Data/NOMChannelXX`，测试已经通过输入验证后的读取单元；不得以该小文件执行 `inspect_manifest`。实现 `read_and_calibrate_l1(path, role, rows_per_window)`：它通过 `iter_dataset_windows` 逐窗口读取，预分配每个通道的 `float32` science 和 `uint32` quality 目标数组，返回 `{channel: scienceArray}`、`{channel: qualityArray}` 和不含本地路径的 metadata。测试使用 monkeypatch 记录 HDF dataset 切片，断言未出现整幅 dataset 索引读取。

在 `test_fy4b_agri_pipeline.py` 定义仅供测试使用的 `execution_manifest`、`stage_payload`、`manifest_with_nominal_grid_artifact`、`manifest_with_gridded_fy4b_artifacts` 和 `manifest_with_standard_fy4b_cogs`。前者将适配器已识别的五角色和临时 `output.path` 放入 Manifest；`stage_payload` 固定写入合法 `profileCode="FY-4B-AGRI"`、`outputUri="rs://output/task-101"` 和已解析参数；其余三个辅助函数只把前一阶段在临时输出目录实际生成的逻辑 URI、类型和校验和作为上游输入。它们不得伪造本地路径、未声明 artifact type 或 `file:///` URI。

- [ ] **Step 5: 运行定标测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py -q
```

Expected: 定标公式、填充值、质量 bit 和窗口读取测试全部通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py rs-algorithm-worker/tests/fy4b_fixture.py rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py
git commit -m "feat: calibrate FY-4B AGRI L1 inputs"
```

### Task 5: 建立名义静止卫星网格、表观反射率和控制点残差报告

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_navigation.py`
- Create: `rs-algorithm-worker/tests/test_fy4b_agri_navigation.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`

- [ ] **Step 1: 写网格、角度和控制点失败测试。**

```python
def test_nominal_grid_uses_geos_crs_and_north_up_transform():
    grid = build_nominal_grid(nom_center_lon=105.0, nom_sat_height=35786000.0,
                              earth_equatorial_radius=6377830.0, inverse_flattening=298.257,
                              sampling_angle_micro_rad=14.0, stepping_angle_micro_rad=14.0,
                              width=8, height=8)
    assert "+proj=geos" in grid.crs
    assert grid.transform.e < 0

def test_toa_reflectance_masks_night_and_invalid_solar_angles():
    result = apparent_reflectance(np.array([[0.2]], dtype=np.float32),
                                 earth_sun_distance_ratio=1.0,
                                 solar_zenith_degrees=np.array([[90.0]], dtype=np.float32))
    assert np.isnan(result[0, 0])

def test_control_residual_requires_eight_valid_controls(tmp_path):
    controls = tmp_path / "controls.json"
    controls.write_text(json.dumps({"version": "1", "controls": []}), encoding="utf-8")
    with pytest.raises(ValueError, match="at least eight"):
        evaluate_coast_controls(controls, lambda lon, lat: (0.0, 0.0))
```

- [ ] **Step 2: 运行测试并确认导航模块缺失。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_navigation.py -q
```

Expected: collection 失败，因为 `fy4b_agri_navigation` 尚不存在。

- [ ] **Step 3: 实现可审计的名义网格。**

定义：

```python
@dataclass(frozen=True)
class NominalGrid:
    crs: str
    transform: Affine
    width: int
    height: int
    status: str
    parameters: dict[str, float]
```

实现 `build_nominal_grid`，它接收 `nom_center_lon`、`nom_sat_height`、`earth_equatorial_radius`、`inverse_flattening`、`sampling_angle_micro_rad`、`stepping_angle_micro_rad`、`width` 和 `height`。构造 `+proj=geos +lon_0=<NOMCenterLon> +h=<NOMSatHeight> +a=<dEA> +b=<derived-semiminor-axis> +sweep=x +units=m +no_defs`。像元 x 坐标由列相对中心乘 `dSamplingAngle * 1e-6 * NOMSatHeight` 得到；y 坐标由中心行减去行号乘 `dSteppingAngle * 1e-6 * NOMSatHeight` 得到。用 `Affine(pixel_width, 0, x_origin, 0, -pixel_height, y_origin)` 保存 north-up 网格。初始 `status` 固定为 `NOMINAL_UNVERIFIED`。

真实 `GEO_4000M` 的 `LineNumber`、`ColumnNumber` 是 4KM 网格索引：二维 shape 为 `2748 x 2748`，每个值只能为填充值 `-1` 或 `0..2747` 的整数；每一个有效像元必须分别等于其数组行号和列号。500M/1KM/2KM/4KM 的 `21984/10992/5496/2748` shape 比例为 `8/4/2/1`，各分辨率网格直接由各自根属性的采样/步进角生成。不得把 GEO 的 4KM 行列索引误当作 500M 索引，也不得要求它们能被 8、4 或 2 整除。任一 GEO 索引或 shape 违反该契约时拒绝输入，而不是插值修复。

- [ ] **Step 4: 加入表观反射率与控制点报告。**

`apparent_reflectance` 仅在 `0 <= NOMSunZenith < 90` 时计算 `rho * d**2 / cos(theta)`，其他像元返回 `NaN` 并设置 `FY4B_QUALITY_OUTSIDE_DISK` 或质量扩展位。控制点 JSON 的严格形状为：

```json
{"version":"1","controls":[{"id":"coast-01","longitude":121.5,"latitude":31.2,"observedLine500m":1024.0,"observedColumn500m":2048.0}]}
```

`evaluate_coast_controls` 验证至少八个唯一 `id`、有限经纬度与非负观测行列号，调用经纬度到 500M 像素的反算函数，输出 `count`、`maxPixels`、`rmsePixels` 和每点 `lineResidualPixels`/`columnResidualPixels`。不拟合仿射或多项式修正；报告通过条件由真实样例测试中的显式工程阈值决定。

- [ ] **Step 5: 运行导航测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_navigation.py tests\test_fy4b_agri_pipeline.py -q
```

Expected: geos CRS、north-up 变换、太阳角掩膜、GEO 对齐和控制点 JSON 验证全部通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_navigation.py rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py rs-algorithm-worker/tests/test_fy4b_agri_navigation.py
git commit -m "feat: add FY-4B nominal navigation validation"
```

### Task 6: 执行验证、原生 COG 和重投影裁剪阶段

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py`

- [ ] **Step 1: 写阶段输入角色和中间产物失败测试。**

```python
def test_validate_stage_requires_exactly_the_five_raw_roles(tmp_path):
    manifest = execution_manifest(make_fy4b_package(tmp_path))
    response = execute_fy4b_stage(stage_payload("VALIDATE_FY4B_PACKAGE"), manifest)
    assert response["artifacts"][0]["type"] == "FY4B_INPUT_VALIDATION_JSON"
    assert response["artifacts"][0]["uri"].startswith("rs://output/task-101/")

def test_calibration_stage_writes_four_native_rasters_and_quality_outputs(tmp_path):
    manifest = manifest_with_nominal_grid_artifact(make_fy4b_package(tmp_path))
    response = execute_fy4b_stage(stage_payload("CALIBRATE_AND_GEOREFERENCE_FY4B"), manifest)
    assert {item["type"] for item in response["artifacts"]} >= {
        "FY4B_NATIVE_0500M_RASTER", "FY4B_NATIVE_4000M_RASTER",
        "FY4B_NATIVE_0500M_QUALITY", "FY4B_NATIVE_4000M_QUALITY",
    }
```

两个阶段执行测试必须 monkeypatch `read_and_calibrate_l1`，让它只返回 `make_fy4b_channel_fixture` 生成的 3 x 3 science/quality 数组和无路径 metadata；不得扫描 `make_fy4b_package` 中的全圆盘 fill dataset。再测试 `REPROJECT_AND_CROP_FY4B` 只接受八个声明的 `native*` 上游角色，拒绝原始 HDF、重复 role、文件不存在和不符合逻辑输出 URI 正则的值。

- [ ] **Step 2: 运行测试并确认阶段尚未受 Worker 调度。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py -q
```

Expected: 失败于 `execute_fy4b_stage` 缺失或不支持 FY-4B stage。

- [ ] **Step 3: 实现四个基础阶段。**

在 `execute_fy4b_stage(payload, manifest)` 中使用与 FY-3H 相同的逻辑 URI、SHA-256 和受控输出目录规则，实现：

```text
VALIDATE_FY4B_PACKAGE          -> validation/package.json
BUILD_FY4B_NOMINAL_GRID        -> navigation/nominal-grid.json
CALIBRATE_AND_GEOREFERENCE_FY4B -> native/0500m.tif, native/1000m.tif,
                                   native/2000m.tif, native/4000m.tif,
                                   native/quality-*.tif, calibration/summary.json
REPROJECT_AND_CROP_FY4B        -> grid/0500m.tif, grid/1000m.tif,
                                   grid/2000m.tif, grid/4000m.tif,
                                   grid/quality-*.tif
```

`CALIBRATE_AND_GEOREFERENCE_FY4B` 必须先读取 `FY4B_NOMINAL_GRID_JSON`，以 `RasterData` 写入窗口化、带波段描述和单位的中间 COG：C01-C06 的名称为 `Cxx_REFLECTANCE`，C07-C15 为 `Cxx_BRIGHTNESS_TEMPERATURE`。500M 只输出 `C02_REFLECTANCE`；1KM 输出 C01-C03；2KM 输出 C01-C08；4KM 输出 C01-C15。质量 COG 使用 `uint32` 和 `quality_mask` 描述。

`REPROJECT_AND_CROP_FY4B` 对连续 science 使用 `rasterio.enums.Resampling.bilinear`，对 quality 使用 `Resampling.nearest`，先调用 `reproject_raster` 到目标 CRS，再将 WGS84 ROI 转换为目标 CRS 后调用 `crop_grid`。ROI 不相交必须抛出 `ValueError("FY-4B ROI does not intersect raster")`。

- [ ] **Step 4: 验证中间 COG 的空间和安全属性。**

在测试中用 Rasterio 重开每一个中间 COG，断言 CRS、transform、NoData、band descriptions、tiled block 和质量 `uint32` 类型；读取每一个 Worker response 的 JSON，断言不包含 `tmp_path`、源 HDF 路径或 `file:///`。

- [ ] **Step 5: 运行阶段测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py tests\test_operators_cog.py tests\test_operators_spatial.py -q
```

Expected: FY-4B 阶段、COG 和通用空间算子测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py
git commit -m "feat: execute FY-4B calibration and reprojection stages"
```

### Task 7: 输出标准 COG、质量摘要和名义定位状态

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py`

- [ ] **Step 1: 写标准输出失败测试。**

```python
def test_standard_stage_emits_all_final_cogs_and_a_safe_summary(tmp_path):
    manifest = manifest_with_gridded_fy4b_artifacts(tmp_path)
    response = execute_fy4b_stage(stage_payload("WRITE_FY4B_STANDARD_COG"), manifest)
    types = {item["type"] for item in response["artifacts"]}
    assert {"FY4B_RED_500M_COG", "FY4B_VIS_NIR_1000M_COG",
            "FY4B_SPECTRAL_2000M_COG", "FY4B_MULTISPECTRAL_4000M_COG",
            "FY4B_STANDARD_SUMMARY_JSON"}.issubset(types)
    summary = json.loads((tmp_path / "output" / "standard" / "summary.json").read_text())
    assert summary["navigationStatus"] == "NOMINAL_UNVERIFIED"
    assert "path" not in json.dumps(summary)
```

- [ ] **Step 2: 运行测试并确认标准阶段不存在。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py::test_standard_stage_emits_all_final_cogs_and_a_safe_summary -q
```

Expected: 失败于不支持的 stage。

- [ ] **Step 3: 实现 `WRITE_FY4B_STANDARD_COG`。**

使用 `write_cog` 将四个 `GRIDDED_*_RASTER` 和四个 quality 输入写到 `standard/`，按产物表返回九个最终 COG/JSON。summary 必须包含：

```json
{
  "profileKey": {"productCode": "FY-4B-AGRI", "profileVersion": "v1"},
  "formulaVersion": "fy4b-agri-l1-v1",
  "qualityRuleVersion": "fy4b-agri-l1-quality-v1",
  "navigationStatus": "NOMINAL_UNVERIFIED"
}
```

summary 还必须记录 `source.observingBeginning`、`source.observingEnding`、`source.subSatellite`；四种分辨率完整波段列表；以及每个逻辑产物的 SHA-256、尺寸、CRS 和质量统计。不得记录物理路径、授权令牌或 Manifest URI。

- [ ] **Step 4: 为 FINAL 下游输入补充回归测试。**

构建带 `FY4B_MULTISPECTRAL_4000M_COG` 的下一阶段 Manifest，执行一个开启 features 的计划。断言 Java Task 1 的计划验证允许该 DAG，Worker 在 `standard4km` role 下读取 COG，而不是回读原始 HDF。

- [ ] **Step 5: 运行标准产物测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py -q
```

Expected: 标准 COG、quality COG、summary 和无本地路径响应测试通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py
git commit -m "feat: publish FY-4B standard COG products"
```

### Task 8: 增加 FY-4B 特征、近红外伪彩和显著性候选阶段

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri.py`

- [ ] **Step 1: 写可选能力的失败测试。**

```python
def test_feature_stage_writes_toa_normalized_difference_and_thermal_difference(tmp_path):
    manifest = manifest_with_standard_fy4b_cogs(tmp_path)
    response = execute_fy4b_stage(stage_payload("EXTRACT_FY4B_FEATURES", features_enabled()), manifest)
    assert {item["type"] for item in response["artifacts"]} == {
        "FY4B_FEATURE_COG", "FY4B_FEATURE_SUMMARY_JSON",
    }
    with rasterio.open(tmp_path / "output" / "features" / "fy4b-features.tif") as source:
        assert set(source.descriptions) >= {"toa_nir_red_nd", "btd_c13_c14"}

def test_fusion_stage_labels_c03_c02_c01_as_false_color(tmp_path):
    response = execute_fy4b_stage(stage_payload("FUSE_FY4B_BANDS", fusion_enabled()),
                                  manifest_with_standard_fy4b_cogs(tmp_path))
    assert response["artifacts"][0]["type"] == "FY4B_FALSE_COLOR_COG"

def test_saliency_stage_rejects_feature_source_without_feature_artifact(tmp_path):
    with pytest.raises(ValueError, match="requires exactly features"):
        execute_fy4b_stage(stage_payload("DETECT_FY4B_SALIENCY", saliency_for("btd_c13_c14")),
                           manifest_with_standard_fy4b_cogs(tmp_path))
```

- [ ] **Step 2: 运行测试并确认 FY-4B optional stages 不存在。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_pipeline.py tests\test_fy4b_agri.py -q
```

Expected: 失败于不支持的 FY-4B stage 或缺少声明产物。

- [ ] **Step 3: 实现特征阶段。**

从 500M COG 读取 C02，从 1KM COG 读取 C01-C03，从 4KM COG 读取 C13/C14。复用 `extract_features` 生成 statistics、gradient、local_variance。仅当分母有限且绝对值大于 `1e-6` 时，计算：

```python
toa_nir_red_nd = (c03_toa - c02_toa) / (c03_toa + c02_toa)
btd_c13_c14 = c13_brightness_temperature - c14_brightness_temperature
```

其余位置为 `NaN`。feature COG 的 band descriptions 固定为请求的通用 metric 和这两个产品特征名；summary 记录来源波段、单位、有效像元数、算法版本和 `classification="NONE"`。

- [ ] **Step 4: 实现融合和显著性阶段。**

融合必须从 `FY4B_VIS_NIR_1000M_COG` 选择 `C03_REFLECTANCE`、`C02_REFLECTANCE`、`C01_REFLECTANCE`，以这三个明确的 source band 构造 `BandSet` 和 `FusionParameters(red="C03_REFLECTANCE", green="C02_REFLECTANCE", blue="C01_REFLECTANCE", percentiles=parameters["fusion"]["percentiles"])` 调用 `fuse_bands`，并为输出波段/summary 写入 `displayProduct="NIR_RED_BLUE_FALSE_COLOR"`。

显著性根据参数 source 读取一个标准 band 或一个 feature band，调用 `detect_saliency`。写入 score COG、`uint8` mask COG 和 JSON summary；summary 固定写入 `algorithm="contrast-v1"`、source、threshold、windowSize、candidatePixelCount，且写入 `semanticLabel="NONE"`。

- [ ] **Step 5: 运行可选算法测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri.py tests\test_fy4b_agri_pipeline.py tests\test_algorithms_baseline.py -q
```

Expected: 选项分支、特征公式、伪彩标签、显著性分数/mask 和通用算法回归全部通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/fy4b_agri_pipeline.py rs-algorithm-worker/tests/test_fy4b_agri.py rs-algorithm-worker/tests/test_fy4b_agri_pipeline.py
git commit -m "feat: add FY-4B feature fusion and saliency stages"
```

### Task 9: 接入 Worker 注册表、CLI 全链路与授权角色检查

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py:11-75`
- Modify: `rs-algorithm-worker/tests/test_registry.py`
- Modify: `rs-algorithm-worker/tests/test_cli.py`

- [ ] **Step 1: 写 inspect、build-plan、execute-stage 路由失败测试。**

```python
def test_registry_routes_a_complete_fy4b_package_to_its_plan_and_stage_runner(tmp_path):
    manifest = execution_manifest(make_fy4b_package(tmp_path))
    candidate = dispatch({"operation": "inspect"}, manifest)["candidates"][0]
    plan = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate, "parameterSnapshot": standard_fy4b_parameters(),
    }}, manifest)
    assert plan["stages"][0]["stageKey"] == "VALIDATE_FY4B_PACKAGE"
    response = dispatch({"operation": "execute-stage", "payload": {
        "profileCode": "FY-4B-AGRI", "stageKey": "VALIDATE_FY4B_PACKAGE",
        "parameterSnapshot": standard_fy4b_parameters(), "outputUri": "rs://output/task-101",
    }}, manifest)
    assert response["status"] == "SUCCEEDED"
```

再写 CLI 测试：请求中五个 storage URI 与 Manifest 五个输入逐一一致时，inspect 回应中的候选文件只包含逻辑 `storageUriSnapshot`；少一个 URI、重复 role 或未经授权 role 时 CLI 返回 `INVALID_ASSET_MANIFEST` 或 `INVALID_REQUEST`，回应不含本地路径。

- [ ] **Step 2: 运行测试并确认注册表仍回退到通用计划/空产物。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_registry.py tests\test_cli.py -q
```

Expected: FY-4B plan 仍为单一 `VALIDATE_SOURCE`，或 execute-stage 返回空 artifacts。

- [ ] **Step 3: 实现显式 FY-4B 路由。**

在 `registry.py`：

```python
from .fy4b_agri_pipeline import execute_fy4b_stage

if adapter is fy4b_agri:
    candidate = _recognized_fy4b_candidate(manifest, payload.get("candidate", {}).get("packageId"))
    return fy4b_agri.build_plan(candidate, _parameter_snapshot(payload))

if payload.get("profileCode") == "FY-4B-AGRI":
    return execute_fy4b_stage(payload, manifest)
```

实现 `_recognized_fy4b_candidate`，仅接受 `inspect_manifest(manifest)` 返回的相同 `packageId`。不要使用候选请求中携带的 paths、roles 或 metadata 覆盖重新识别的结果。`_adapter_for_manifest` 必须识别含 `fy4b` 或 `agri` 的完整 HDF 集合，但实际 HDF 验证仍由适配器完成。

- [ ] **Step 4: 扩展 CLI 正向与负向测试。**

调用现有 `_invoke_cli` 走完整 `inspect -> build-plan -> execute-stage`，检查 response 中的 URI 形如 `rs://output/task-101/validation/package.json`，artifact 的 SHA-256 与真实输出相符，且 response JSON 中不出现合成 HDF 的父目录。

- [ ] **Step 5: 运行边界测试并提交。**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_contracts.py tests\test_cli.py tests\test_registry.py tests\test_fy4b_agri.py tests\test_fy4b_agri_pipeline.py -q
```

Expected: Worker Manifest 授权、FY-4B 路由和逻辑产物契约全部通过。

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests/test_registry.py rs-algorithm-worker/tests/test_cli.py
git commit -m "feat: route FY-4B stages through the worker"
```

### Task 10: 使用真实五文件样例和海岸线控制点进行只读验收

**Files:**
- Create: `rs-algorithm-worker/tests/test_fy4b_agri_real_sample.py`
- Modify: `rs-algorithm-worker/tests/conftest.py`
- Modify: `rs-algorithm-worker/tests/test_fy4b_agri_navigation.py`
- Modify: `docs/deployment/multisource-backend-deployment.md`

- [ ] **Step 1: 写真实样例预检与控制点失败测试。**

```python
@pytest.mark.real_sample
def test_real_fy4b_package_matches_the_documented_five_file_contract(fy4b_manifest):
    candidate = inspect_manifest(fy4b_manifest)[0]
    assert [file["role"] for file in candidate["files"]] == list(ROLE_ORDER)
    assert candidate["metadataSnapshot"]["availableChannelsByResolution"]["L1_4000M"] == list(range(1, 16))

@pytest.mark.real_sample
def test_real_fy4b_controls_meet_the_explicit_engineering_threshold(fy4b_manifest, fy4b_coast_controls):
    report = evaluate_real_sample_controls(fy4b_manifest, fy4b_coast_controls)
    assert report["count"] >= 8
    assert report["maxPixels"] <= 2.0
    assert report["rmsePixels"] <= 1.0
    assert report["navigationStatus"] == "COASTLINE_VALIDATED"
```

阈值单位为 500M 像素，属于本项目的工程验收标准，不能描述为官方 FY-4B 定位精度。

- [ ] **Step 2: 配置真实环境并确认预检失败模式正确。**

下载完成前，运行命令应显示 `skipped`，普通回归不失败：

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_real_sample.py -m real_sample -q
```

发布验收命令必须设置样例根目录和控制点路径；根目录中有多个完整时次包时还必须设置精确选包 ID。任一必需变量缺失时测试夹具以明确原因跳过，发布人员不得把 skip 记为通过：

```powershell
$env:MULTISOURCE_FY4B_SAMPLE_ROOT = 'D:\Lwy_project_space\RSImageProcess\otherfile\FY4B'
$env:MULTISOURCE_FY4B_SAMPLE_PACKAGE_ID = '1050E_20260701220000_20260701221459_V0001'
$env:MULTISOURCE_FY4B_COAST_CONTROL_FILE = 'D:\Lwy_project_space\RSImageProcess\otherfile\FY4B\coast-controls-v1.json'
```

- [ ] **Step 3: 创建真实控制点文件并执行测量。**

将独立海岸线资料与 500M C02 图像叠加，人工选择至少八个分散在中国东部、朝鲜半岛、日本、台湾、海南、菲律宾、马来半岛、孟加拉湾或印尼海岸的清晰海陆边界点。每一条记录写入经纬度和同一点在 C02 图像中人工读取的 `observedLine500m`、`observedColumn500m`；不得使用导航计算结果反填 observed 像素。

保存为上述 `coast-controls-v1.json`。测试使用 JSON 中点的逆投影预测像素，与人工 observed 像素相减，产生逐点残差。少于八点、重复 ID、空值、超出 500M 行列范围、最大残差大于 2.0 或 RMSE 大于 1.0 时，验收失败并保持 `NOMINAL_UNVERIFIED`。

在 `test_fy4b_agri_real_sample.py` 实现测试专用的 `evaluate_real_sample_controls(fy4b_manifest, fy4b_coast_controls)`：先调用 `inspect_manifest` 并选择其唯一候选，再只读打开该候选的 `GEO_4000M` 根属性和导航层，调用 `build_nominal_grid` 构建 500M 网格，最后以经纬度反算函数调用 Task 5 的 `evaluate_coast_controls`。该辅助函数只把 `navigationStatus="COASTLINE_VALIDATED"` 附加到已达标的验收 report；不得重采样、拟合控制点或把 report 写回 Worker 的标准产物。

- [ ] **Step 4: 运行真实处理、海岸线验收和 COG 检查。**

在 `test_fy4b_agri_real_sample.py` 的测试临时输出目录中写出 `coastline-acceptance.json`，其中只含输入 HDF 的逻辑 package ID、控制点版本、阈值、逐点残差和汇总，不含本地路径。它由 `evaluate_coast_controls` 返回的结果创建，而不是传给 Worker。若 report 达标，写入 `navigationStatus="COASTLINE_VALIDATED"`；否则测试失败。标准 COG 的 `FY4B_STANDARD_SUMMARY_JSON` 仍必须保持 `navigationStatus="NOMINAL_UNVERIFIED"`，从而确保控制点文件没有改变生产任务的结果。

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_fy4b_agri_real_sample.py tests\test_fy4b_agri_navigation.py -m real_sample -q
```

Expected: 两个真实样例测试通过；实际 HDF 和控制点均只读；测试临时目录的 `coastline-acceptance.json` 标记 `COASTLINE_VALIDATED` 并记录 `controlReport` 的 count/maxPixels/rmsePixels，不写控制点文件路径；生产 `FY4B_STANDARD_SUMMARY_JSON` 保持 `NOMINAL_UNVERIFIED`。

- [ ] **Step 5: 更新部署变量说明、运行全量回归并提交。**

在 `docs/deployment/multisource-backend-deployment.md` 增加 `MULTISOURCE_FY4B_SAMPLE_ROOT`、`MULTISOURCE_FY4B_SAMPLE_PACKAGE_ID` 与 `MULTISOURCE_FY4B_COAST_CONTROL_FILE` 的测试用途、多个时次目录的精确选包条件和控制点阈值。不要将本机路径或实际控制点内容写入仓库。

Run:

```powershell
cd D:\Lwy_project_space\RSImageProcess\JavaRSproject\rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest -q
cd ..
mvn -pl htht-modules/htht-multisource -am -DfailIfNoTests=false test
git diff --check
```

Expected: Python 全量通过，未配置的其他实样测试仅按既有约定跳过；Java 模块测试通过；`git diff --check` 无输出。

```powershell
git add rs-algorithm-worker/tests/test_fy4b_agri_real_sample.py rs-algorithm-worker/tests/conftest.py rs-algorithm-worker/tests/test_fy4b_agri_navigation.py docs/deployment/multisource-backend-deployment.md
git commit -m "test: validate FY-4B AGRI real package and coastline controls"
```

## 执行完成清单

- [ ] `FY-4B-AGRI/v1` inspect 只在完整、身份一致的五文件观测包上返回 `SUPPORTED`。
- [ ] Java 允许只来自显式依赖的 FINAL COG 作为可选阶段输入，仍拒绝未声明和多份输入。
- [ ] C01-C06、C07-C15 分别按照官方查找表/线性属性转换，C07/C08 未被擅自拼接。
- [ ] 每个输出 COG 都有 CRS、north-up transform、波段描述、单位、NoData、tile 和 SHA-256。
- [ ] 质量输出只表达 L1 可证实的 DN/导航/定标/文件状态，不冒充云掩膜。
- [ ] `toa_nir_red_nd`、`btd_c13_c14`、伪彩和 `contrast-v1` 显著性均带有真实物理含义和非分类标签。
- [ ] 真实五文件样例与独立海岸线控制点通过明确的 `maxPixels <= 2.0`、`rmsePixels <= 1.0` 工程验收；验收报告达标时标记 `COASTLINE_VALIDATED`，未通过时不标记；生产标准 summary 不读取控制点文件并保持 `NOMINAL_UNVERIFIED`。
