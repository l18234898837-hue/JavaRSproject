# HY-1C CZI L2A v1 Implementation Plan

> **同步说明（2026-08-25）：** 本文件保留最初的 L2A 算法路线与产物定义；其中“前置条件”所列的阶段契约、真实样例审查、L2A 参数和解码/质量掩膜基础代码已经合并到 `main`。真正待完成的 Java 产物提升、最小输入授权、后续 Worker 阶段、API 与 Vue 对接，请以同目录的 `2026-08-25-hy1c-czi-l2a-java-frontend-handoff-plan.md` 为唯一执行顺序。`hy1c-czi-l2a-v1` 分支已删除。

> **For agentic workers:** REQUIRED SUB-SKILL: Use \`superpowers:subagent-driven-development\` (recommended) or \`superpowers:executing-plans\` to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** Implement the first real product route, \`HY-1C-CZI/L2A/v1\`: review and decode L2A data, apply quality control, generate a parameterized standard COG, and expose feature extraction, display fusion, and saliency as optional downstream branches.

**Architecture:** The L2A adapter contains only reviewed HDF5 evidence and product mapping. Common operators decode, mask, grid, crop, write COGs, and run algorithms. Java owns the DAG, resolved input artifacts, authorization, promotion, and task state; the frontend only persists the declared task parameters.

**Tech Stack:** Java 17, Spring, MyBatis-Plus, PostgreSQL JSONB, Python 3.11+, h5py, NumPy, rasterio/GDAL, pyproj, SciPy, pytest, JUnit 5.

---

## Scope and Gates

This plan supports L2A only. It does not reuse L1B calibration semantics, infer L2A variables from filenames, implement ocean-colour retrieval products, claim pan-sharpening, or label saliency as a scientific classifier.

Do not start Task 3 until all of these gates are satisfied:

1. \`docs/superpowers/plans/2026-08-25-common-worker-operators-completion-plan.md\` is complete and its Worker regression passes. Reprojection/alignment, crop and atomic COG writing must be available.
2. Java has the stage-artifact contract from \`docs/superpowers/plans/2026-08-25-hy1c-czi-preprocessing-foundation-plan.md\`. At present, \`ProcessingStage\` has only dependency keys and \`DispatchNextStageService\` sends every stage all raw inputs, so a true L2A DAG cannot safely run.
3. \`otherfile/H1C_OPER_CZI_L2A_20260715T024305_20260715T024401_41109_10.tar.gz\` is extracted to a non-versioned test directory. The HDF5, XML and JPEG files must not be committed.

If a gate is not met, stop there. Do not replace upstream artifacts with arbitrary raw paths or add an identity operation called calibration.

## Public Parameter Contract

The task API receives this JSON in \`parameterSnapshot\`. Defaults may be pre-filled by the frontend, but the persisted task snapshot contains concrete values.

\`\`\`json
{
  "schemaVersion": "1",
  "operation": "STANDARDIZE_L2A",
  "bands": ["B460", "B560", "B650", "B825"],
  "grid": {
    "targetCrs": "AUTO_UTM",
    "resolutionMeters": 50,
    "scienceResampling": "nearest"
  },
  "roiWgs84": {"minLon": 120.0, "minLat": 30.0, "maxLon": 121.0, "maxLat": 31.0},
  "quality": {"policy": "default"},
  "algorithms": {
    "features": {"enabled": false, "metrics": ["statistics", "gradient", "local_variance"], "windowSize": 3},
    "fusion": {"enabled": false, "red": "B650", "green": "B560", "blue": "B460", "lowPercentile": 2.0, "highPercentile": 98.0},
    "saliency": {"enabled": false, "source": "B560", "threshold": 0.5, "windowSize": 5}
  }
}
\`\`\`

\`AUTO_UTM\` is resolved once from the scene or ROI centre and persisted as a concrete EPSG CRS in COG metadata. \`resolutionMeters\` is accepted only for \`AUTO_UTM\` or an explicit projected metre-based CRS in v1. The 50 m value is a UI/API default for this profile, not a public-operator constant. Scientific reflectance and masks always use nearest neighbour. Fusion is a display product; bilinear may later be allowed only for a derived preview, never for \`STANDARD_COG\`.

The reviewed mapping fixture defines \`quality.policy = "default"\`, including every excluded flag name and bit position. Source code must not contain a product quality-bit integer.

## DAG and Artifact Contract

\`\`\`text
VALIDATE_SOURCE
  -> DECODE_SCALE_AND_NODATA
  -> APPLY_L2_FLAGS
  -> GEOLOCATE_AND_GRID
  -> CROP_OPTIONAL
  -> WRITE_STANDARD_COG

CROP_OPTIONAL -> EXTRACT_CZI_FEATURES       (optional)
CROP_OPTIONAL -> FUSE_CZI_BANDS             (optional)
CROP_OPTIONAL -> DETECT_CZI_SALIENCY        (optional)
\`\`\`

| Stage | Declared inputs | Declared outputs |
| --- | --- | --- |
| \`VALIDATE_SOURCE\` | raw \`MAIN\`, \`METADATA\`, \`BROWSE\` | validation metadata |
| \`DECODE_SCALE_AND_NODATA\` | raw \`MAIN\` | \`DECODED_CZI_RASTER\` |
| \`APPLY_L2_FLAGS\` | decoded raster, raw \`MAIN\` | \`QUALITY_MASKED_CZI_RASTER\`, \`VALID_MASK\` |
| \`GEOLOCATE_AND_GRID\` | masked raster, raw \`MAIN\` | \`GRIDDED_CZI_RASTER\` |
| \`CROP_OPTIONAL\` | gridded raster | \`CROPPED_CZI_RASTER\` |
| \`WRITE_STANDARD_COG\` | cropped raster, valid mask | \`STANDARD_COG\`, \`STANDARD_METADATA_JSON\` |
| \`EXTRACT_CZI_FEATURES\` | cropped raster, valid mask | \`FEATURE_COG\`, \`FEATURE_METADATA_JSON\` |
| \`FUSE_CZI_BANDS\` | cropped raster, valid mask | \`FUSION_COG\`, \`FUSION_PREVIEW_PNG\` |
| \`DETECT_CZI_SALIENCY\` | cropped raster or a declared feature artifact, valid mask | \`SALIENCY_SCORE_COG\`, \`SALIENCY_MASK_COG\`, \`SALIENCY_PREVIEW_PNG\` |

Use the generic intermediate-raster artifact format from the common Worker layer. It must preserve band-first values, band metadata, CRS, affine transform, NoData, valid mask, and method metadata. Every stage can consume only a declared raw input or declared upstream artifact.

## Target Files

\`\`\`text
rs-algorithm-worker/
  src/rs_algorithm_worker/
    product_adapters/hy1c_czi_l2a.py
    product_adapters/hy1c_czi.py
    l2a_profile.py
    l2a_pipeline.py
    registry.py
    capabilities.py
  tests/
    fixtures/hy1c_czi_l2a_v1.json
    test_hy1c_czi_l2a.py
    test_registry.py

htht-modules/htht-multisource/
  src/main/java/com/htht/multisource/
    domain/model/ProcessingStage.java
    application/port/out/model/StageInput.java
    application/port/out/model/StageOutput.java
    application/usecase/service/DispatchNextStageService.java
    infrastructure/worker/WorkerResultTranslator.java
  src/test/java/...    # stage binding and worker-gateway tests
\`\`\`

No separate frontend module exists in this repository. The owning frontend repository must emit the public JSON above; do not create a second UI here.

### Task 1: Freeze L2A Evidence in a Reviewed Fixture

**Files:**
- Create: \`rs-algorithm-worker/tests/fixtures/hy1c_czi_l2a_v1.json\`
- Create: \`rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py\`
- Create: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`
- Modify: \`rs-algorithm-worker/tests/conftest.py\`

- [ ] **Step 1: Write the failing mapping-contract test.**

\`\`\`python
from rs_algorithm_worker.product_adapters.hy1c_czi_l2a import load_reviewed_mapping

def test_reviewed_l2a_mapping_declares_bands_navigation_and_quality():
    mapping = load_reviewed_mapping()
    assert tuple(mapping.band_ids) == ("B460", "B560", "B650", "B825")
    assert mapping.longitude_path.startswith("/")
    assert mapping.latitude_path.startswith("/")
    assert mapping.quality_flags_path.startswith("/")
    assert mapping.default_bad_flag_names
\`\`\`

- [ ] **Step 2: Run it and confirm failure.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: FAIL because neither the fixture nor L2A mapping loader exists.

- [ ] **Step 3: Inspect the real L2A HDF5 and review it against the supplied product document.**

List every HDF5 dataset path, shape, dtype, and attributes with \`h5py\`. Review it against \`otherfile/H1C_OPER_CZI_L1B_20260715T023726_20260715T023821_41109_10/HY-1CD数据格式说明（L2-L3）.pdf\`. Record the document title/version and sample product ID in the fixture. The fixture must contain the four CZI band paths and wavelengths, each band’s scale/offset/fill/range attributes, latitude/longitude paths and scaling attributes, \`l2_flags\` path, exact flag-name-to-bit map, and the default bad-flag list.

- [ ] **Step 4: Implement fixture and HDF5 conformance validation.**

\`load_reviewed_mapping()\` parses immutable data and rejects duplicate bands, missing required CZI IDs, non-absolute HDF5 paths, missing NoData/range policy, or an empty quality policy. \`validate_source_against_mapping(h5_path, mapping)\` verifies each dataset/attribute exists and that all band, navigation, and flags shapes agree.

- [ ] **Step 5: Add real-scene conformance coverage.**

\`\`\`python
@pytest.mark.real_sample
def test_l2a_sample_conforms_to_reviewed_mapping(hy1c_l2a_manifest):
    main = next(Path(item["path"]) for item in hy1c_l2a_manifest["inputs"] if item["role"] == "MAIN")
    validate_source_against_mapping(main, load_reviewed_mapping())
\`\`\`

Add \`MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT\` to \`tests/conftest.py\`; it requires one \`.h5\`, one \`.xml\`, and one \`.jpg\`. Only the real-scene test may skip when the variable is missing.

- [ ] **Step 6: Run tests and commit.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: fixture tests pass, and the real-scene test passes once the environment variable is set.

\`\`\`powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py rs-algorithm-worker/tests/fixtures/hy1c_czi_l2a_v1.json rs-algorithm-worker/tests/test_hy1c_czi_l2a.py rs-algorithm-worker/tests/conftest.py
git commit -m "feat: record reviewed HY-1C CZI L2A mapping"
\`\`\`

### Task 2: Add the Parameterized Output-Grid Contract

**Files:**
- Create: \`rs-algorithm-worker/src/rs_algorithm_worker/l2a_profile.py\`
- Modify: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`

- [ ] **Step 1: Write the failing parameter test.**

\`\`\`python
from rs_algorithm_worker.l2a_profile import parse_l2a_parameters

def test_l2a_parameters_resolve_auto_utm_and_preserve_nearest_resampling():
    result = parse_l2a_parameters({
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"}, "algorithms": {}
    }, centre_longitude=120.0, centre_latitude=30.0)
    assert result.resolved_crs == "EPSG:32651"
    assert result.resolution_meters == 50
    assert result.science_resampling == "nearest"
\`\`\`

- [ ] **Step 2: Run it and confirm failure.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: FAIL because \`parse_l2a_parameters\` does not exist.

- [ ] **Step 3: Implement strict parameter parsing.**

Accept only the documented fields. Resolve UTM with \`floor((longitude + 180) / 6) + 1\`, using EPSG 326xx north of the equator and 327xx south. Validate explicit projected CRS through pyproj. Reject geographic CRS with \`resolutionMeters\`, unknown or duplicate bands, non-positive resolution, malformed ROI, unknown quality policy, and any scientific resampling except \`nearest\`. Persist requested and resolved CRS in output metadata.

- [ ] **Step 4: Add invalid optional-algorithm coverage.**

\`\`\`python
def test_l2a_rejects_fusion_band_not_selected():
    with pytest.raises(ValueError, match="fusion bands"):
        parse_l2a_parameters({
            "schemaVersion": "1", "operation": "STANDARDIZE_L2A", "bands": ["B460", "B560"],
            "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
            "quality": {"policy": "default"},
            "algorithms": {"fusion": {"enabled": True, "red": "B650", "green": "B560", "blue": "B460"}}
        }, centre_longitude=120.0, centre_latitude=30.0)
\`\`\`

- [ ] **Step 5: Run and commit.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: PASS.

\`\`\`powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/l2a_profile.py rs-algorithm-worker/tests/test_hy1c_czi_l2a.py
git commit -m "feat: add explicit L2A grid parameter contract"
\`\`\`

### Task 3: Implement the Thin L2A Adapter and Plan Selection

**Files:**
- Modify: \`rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py\`
- Modify: \`rs-algorithm-worker/src/rs_algorithm_worker/registry.py\`
- Modify: \`rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py\`
- Modify: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`
- Modify: \`rs-algorithm-worker/tests/test_registry.py\`

- [ ] **Step 1: Write failing profile and DAG tests.**

\`\`\`python
def test_l2a_adapter_selects_l2a_and_builds_standardization_dag(hy1c_l2a_manifest):
    candidate = inspect_l2a_manifest(hy1c_l2a_manifest)
    assert candidate["profileKey"] == {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"}
    plan = build_l2a_plan(candidate, PARAMETER_SNAPSHOT)
    assert [stage["stageCode"] for stage in plan["stages"][:6]] == [
        "VALIDATE_SOURCE", "DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS",
        "GEOLOCATE_AND_GRID", "CROP_OPTIONAL", "WRITE_STANDARD_COG",
    ]
\`\`\`

- [ ] **Step 2: Run focused tests and confirm failure.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py tests\\test_registry.py -q\`

Expected: FAIL because the current HY-1C route returns a generic two-stage plan.

- [ ] **Step 3: Implement evidence-based product routing.**

L2A inspection requires the HDF5/XML/JPEG package and validates reviewed product-level evidence. It returns only the \`HY-1C-CZI/L2A-v1\` candidate. Keep \`product_adapters/hy1c_czi.py\` isolated for L1B evidence; a verified L2A file must never enter that adapter.

- [ ] **Step 4: Build the fixed and optional branches.**

Emit the six standardization stages always. Add features, fusion, or saliency only when the selected bands and reviewed mapping satisfy their parameter requirements. Capability declarations remain generic: fusion receives validated chosen band names at execution time, rather than encoding CZI names into \`COMMON_CAPABILITIES\`.

- [ ] **Step 5: Add isolation tests, run, and commit.**

Test that L1B evidence cannot select \`L2A-v1\`; disabled algorithms are absent; enabled fusion requires B460/B560/B650; saliency source must be a selected band or declared feature artifact.

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py tests\\test_hy1c_czi.py tests\\test_registry.py -q\`

Expected: PASS.

\`\`\`powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_l2a.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py rs-algorithm-worker/tests/test_hy1c_czi_l2a.py rs-algorithm-worker/tests/test_registry.py
git commit -m "feat: add HY-1C CZI L2A processing profile"
\`\`\`

### Task 4: Make Java Stages Artifact-Aware

**Files:**
- Modify: \`htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingStage.java\`
- Create: \`htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageInput.java\`
- Create: \`htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageOutput.java\`
- Modify: \`htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java\`
- Modify: \`htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerResultTranslator.java\`
- Test: matching domain, gateway, and dispatch tests

- [ ] **Step 1: Write a failing upstream-binding test.**

\`\`\`java
assertThat(plan.stages().get(2).inputs()).containsExactly(
    new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_CZI_RASTER", true),
    new StageInput("main", StageInput.Source.RAW_INPUT, "MAIN", true)
);
\`\`\`

- [ ] **Step 2: Run it and confirm failure.**

Run: \`mvn -pl htht-modules/htht-multisource -Dtest=ProcessingStageTest test\`

Expected: FAIL because \`ProcessingStage\` has no input/output bindings.

- [ ] **Step 3: Implement validated bindings.**

Create \`RAW_INPUT\` and \`UPSTREAM_ARTIFACT\` input sources, plus \`StageOutput(artifactType, relativePath, retention)\`. Reject duplicate keys, absolute or traversal output paths, an upstream artifact absent from dependency outputs, and any final output consumed downstream. Extend the Worker-plan translator to preserve all L2A bindings.

- [ ] **Step 4: Resolve only declared artifacts at dispatch.**

Update \`DispatchNextStageService\` to resolve raw assets by role and upstream artifacts by type, issue a grant for that exact list, and place only that list in \`StageExecutionRequest\`. Test that \`APPLY_L2_FLAGS\` receives decoded data plus \`MAIN\`, but not XML, browse or any downstream artifact.

- [ ] **Step 5: Run and commit.**

Run: \`mvn -pl htht-modules/htht-multisource test\`

Expected: PASS.

\`\`\`powershell
git add htht-modules/htht-multisource
git commit -m "feat: bind processing stages to declared artifacts"
\`\`\`

### Task 5: Compose the Six L2A Standardization Stages

**Files:**
- Create: \`rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py\`
- Modify: \`rs-algorithm-worker/src/rs_algorithm_worker/registry.py\`
- Modify: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`

- [ ] **Step 1: Write failing decode and quality tests with a synthetic HDF5 fixture.**

\`\`\`python
def test_decode_masks_fill_and_out_of_range_values(synthetic_l2a_h5, tmp_path):
    result = execute_l2a_stage("DECODE_SCALE_AND_NODATA", [synthetic_l2a_h5], PARAMETERS, tmp_path)
    assert np.isnan(result.raster.values[:, 0, 0]).all()
    assert np.isfinite(result.raster.values[:, 1, 1]).all()

def test_quality_stage_applies_reviewed_bad_bits(decoded_artifact, synthetic_l2a_h5, tmp_path):
    result = execute_l2a_stage("APPLY_L2_FLAGS", [decoded_artifact, synthetic_l2a_h5], PARAMETERS, tmp_path)
    assert not result.valid_mask[0, 0]
    assert result.valid_mask[1, 1]
\`\`\`

- [ ] **Step 2: Run and confirm failure.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: FAIL because no L2A stage executor exists.

- [ ] **Step 3: Implement each stage by composing public operators.**

\`DECODE_SCALE_AND_NODATA\` reads reviewed band windows, applies reviewed scale/offset, and changes FillValue or invalid ranges to NaN. \`APPLY_L2_FLAGS\` decodes only the fixture-defined default policy and calls \`apply_quality_mask\`. \`GEOLOCATE_AND_GRID\` decodes reviewed navigation, resolves the task grid, and uses the common nearest-neighbour swath-grid path. \`CROP_OPTIONAL\` passes through without ROI or uses \`crop_grid\` after transforming the WGS84 ROI. \`WRITE_STANDARD_COG\` calls \`write_cog\` and writes profile, mapping revision, requested/resolved CRS, resolution, resampling, bands, valid-pixel counts, and quality policy metadata.

Read HDF5 in bounded windows. Do not materialize all raw bands, navigation and flags at once; log the configured tile/window shape.

- [ ] **Step 4: Add output-grid tests.**

\`\`\`python
def test_standard_cog_has_resolved_grid_and_czi_band_names(l2a_stage_artifacts, tmp_path):
    result = execute_l2a_stage("WRITE_STANDARD_COG", l2a_stage_artifacts, PARAMETERS, tmp_path)
    with rasterio.open(result.standard_cog) as dataset:
        assert dataset.crs.to_string() == "EPSG:32651"
        assert dataset.count == 4
        assert dataset.descriptions == ("B460", "B560", "B650", "B825")
\`\`\`

Also test absent attributes, shape mismatches, invalid ROI, a non-selected requested band, and an undeclared stage input.

- [ ] **Step 5: Run and commit.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py tests\\test_operators_hdf.py tests\\test_operators_spatial.py tests\\test_operators_cog.py -q\`

Expected: PASS.

\`\`\`powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests/test_hy1c_czi_l2a.py
git commit -m "feat: execute HY-1C CZI L2A preprocessing stages"
\`\`\`

### Task 6: Attach Feature, Fusion and Saliency Branches

**Files:**
- Modify: \`rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py\`
- Modify: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`

- [ ] **Step 1: Write failing optional-stage tests.**

\`\`\`python
def test_fusion_uses_czi_true_colour_and_does_not_modify_standard_cog(grid_artifact, tmp_path):
    result = execute_l2a_stage("FUSE_CZI_BANDS", [grid_artifact], FUSION_PARAMETERS, tmp_path)
    assert result.source_bands == ("B650", "B560", "B460")
    assert result.metadata["displayProduct"] is True

def test_saliency_score_and_mask_have_identical_grid(grid_artifact, tmp_path):
    result = execute_l2a_stage("DETECT_CZI_SALIENCY", [grid_artifact], SALIENCY_PARAMETERS, tmp_path)
    assert result.score.transform == result.mask.transform
    assert result.score.crs == result.mask.crs
\`\`\`

- [ ] **Step 2: Run and confirm failure.**

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -q\`

Expected: FAIL because L2A does not yet handle optional stages.

- [ ] **Step 3: Implement features without product science claims.**

Call \`extract_features\` on \`CROPPED_CZI_RASTER\` and selected metrics only. Write requested gradient/local-variance feature rasters as COGs and write metadata containing \`statistics-v1\`, parameters, bands, valid ratio and source checksum. Do not add CZI spectral indices without a reviewed formula.

- [ ] **Step 4: Implement display fusion and local-contrast saliency.**

Call \`fuse_bands\` with requested IDs; B650/B560/B460 is the default true-colour configuration. Mark output \`displayProduct: true\` and \`method: percentile-rgb-v1\`. For saliency, accept one validated standardized band or a declared feature artifact, call \`detect_saliency\`, and write score/mask COGs on the unchanged standard grid with \`method: contrast-v1\`. Neither path modifies \`STANDARD_COG\`.

- [ ] **Step 5: Test invalid inputs, run and commit.**

\`\`\`python
def test_l2a_rejects_unknown_saliency_source(grid_artifact, tmp_path):
    with pytest.raises(ValueError, match="saliency source"):
        execute_l2a_stage("DETECT_CZI_SALIENCY", [grid_artifact], {"source": "B999"}, tmp_path)
\`\`\`

Run: \`cd rs-algorithm-worker; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py tests\\test_algorithms_baseline.py tests\\test_capabilities_common.py -q\`

Expected: PASS.

\`\`\`powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py rs-algorithm-worker/tests/test_hy1c_czi_l2a.py
git commit -m "feat: add L2A baseline algorithm branches"
\`\`\`

### Task 7: Verify the Real Scene and Backend Route

**Files:**
- Modify: \`rs-algorithm-worker/tests/test_hy1c_czi_l2a.py\`
- Modify: \`rs-algorithm-worker/tests/test_cli.py\`
- Modify: Java Worker gateway/controller integration tests

- [ ] **Step 1: Write the failing real-scene end-to-end assertion.**

\`\`\`python
@pytest.mark.real_sample
def test_l2a_real_scene_outputs_standard_cog_and_metadata(l2a_request, l2a_manifest, tmp_path):
    response = invoke_worker(l2a_request, l2a_manifest, tmp_path)
    artifacts = {item["type"]: item for item in response["payload"]["artifacts"]}
    assert {"STANDARD_COG", "STANDARD_METADATA_JSON"}.issubset(artifacts)
    assert all("H1C_OPER" not in item["uri"] for item in artifacts.values())
\`\`\`

- [ ] **Step 2: Run it with the extracted package and confirm initial failure.**

Run: \`cd rs-algorithm-worker; $env:MULTISOURCE_HY1C_CZI_L2A_SAMPLE_ROOT='X:\\extracted-l2a'; .\\.venv\\Scripts\\python.exe -m pytest tests\\test_hy1c_czi_l2a.py -m real_sample -q\`

Expected: FAIL until Tasks 1-6 are complete. With the variable present, it must not silently skip.

- [ ] **Step 3: Verify Java input authorization and promotion.**

Create a task using the exact public parameter JSON, dispatch every stage, and assert every Worker manifest contains only declared raw/upstream inputs. Assert final URIs are logical task-owned URIs, checksums/sizes match promoted files, and task success occurs only after all selected branches succeed.

- [ ] **Step 4: Run full regression.**

\`\`\`powershell
cd rs-algorithm-worker
.\\.venv\\Scripts\\python.exe -m pytest -q
cd ..
mvn -pl htht-modules/htht-multisource test
\`\`\`

Expected: PASS. Only real-scene tests may skip when their sample-root variable is unset.

- [ ] **Step 5: Manually verify UI/API handoff and commit.**

Submit defaults and then an explicit projected CRS plus ROI. Confirm the persisted JSON equals the request, COG metadata contains resolved CRS/resolution, and fusion/saliency are labelled preview/baseline products.

\`\`\`powershell
git add rs-algorithm-worker/tests htht-modules/htht-multisource/src/test htht-admin/src/test
git commit -m "test: verify HY-1C CZI L2A processing route"
\`\`\`

## Completion Criteria

- The reviewed fixture proves every dataset path, scale/offset, FillValue/range rule, navigation rule and quality bit used by L2A.
- L1B and L2A cannot select each other's adapter semantics.
- CRS, resolution, ROI, resampling, bands, quality policy and optional algorithms are explicit task parameters, not hidden adapter settings.
- The standard COG has named CZI bands, a resolved CRS, declared NoData and quality provenance.
- Feature extraction, fusion and saliency are optional downstream branches with explicit baseline/display metadata.
- Each Java stage receives only its declared raw/upstream artifacts.
- Worker and Java synthetic regressions pass; the supplied real scene completes when configured.

## Plan Self-Review

- **Coverage:** reviewed L2A mapping, parameter grid policy, product routing, preprocessing, three optional algorithms, backend artifact flow and real-data acceptance are all covered.
- **Scientific boundary:** unverified paths, flags and scale values are recorded from the real sample and document before use; no L1B rule is inferred.
- **Separation:** common operators remain product-neutral, adapter rules live in the fixture, and Java controls authorization/lifecycle.
- **TDD:** every implementation task begins with a concrete failing test, has a focused command and ends in a discrete commit.
