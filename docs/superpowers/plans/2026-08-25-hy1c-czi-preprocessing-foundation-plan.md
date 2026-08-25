# HY-1C CZI Preprocessing Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish reusable preprocessing and optional-algorithm contracts, provide tested baseline implementations for common operators, feature extraction, multi-band fusion, and saliency detection, then validate the complete flow with a real `HY-1C-CZI/v1` product.

**Architecture:** Product adapters remain thin, versioned descriptions of product evidence and product-specific mappings. Canonical Python data models and operator protocols separate file/product differences from reusable processing. Baseline algorithms are independent, replaceable capabilities that consume standardized raster/band data; they are not silently enabled for every product. Java remains the source of task state and stages, and adds stage-output promotion and authorized artifact input resolution so each DAG stage consumes only declared raw inputs or registered upstream artifacts.

**Tech Stack:** Java 17, Spring, MyBatis-Plus, PostgreSQL JSONB, Python 3.11+, pytest, h5py, NumPy, rasterio/GDAL, pyproj.

**Scope:** This plan implements (1) canonical data/operator contracts, (2) windowed common operator baselines, (3) baseline feature extraction, multi-band fusion, and contrast-based saliency capabilities, (4) the shared stage-artifact foundation, and (5) one complete HY-1C CZI L1B route that exercises the applicable capabilities. HY-2D, FY-3H, FY-4, optical commercial products, and SAR need their own applicability declarations and scientific baselines.

**Scientific gate:** Do not implement a calibration formula, HDF dataset path, geolocation array name, valid range, target grid, or resampling rule from a filename or inferred convention. The values in the HY-1C official product specification and supplied sample must be recorded in test fixtures before an operator is made available to users.

---

## Target File Structure

```text
htht-modules/htht-multisource/
  src/main/java/com/htht/multisource/
    application/port/out/ArtifactStorage.java                  # Promote and authorize task-owned artifacts
    application/port/out/model/StageInput.java                 # RAW_INPUT or UPSTREAM_ARTIFACT binding
    application/port/out/model/StageOutput.java                # Logical URI and retention for one output
    application/port/out/model/StageExecutionRequest.java      # Explicit resolved inputs and stage output
    application/usecase/service/DispatchNextStageService.java  # Resolve declared stage bindings
    domain/model/ProcessingStage.java                           # Persisted stage contract in the plan snapshot
    domain/model/Artifact.java                                  # Retention and source role of an artifact
    infrastructure/persistence/entity/ArtifactEntity.java       # Map additive artifact columns
    infrastructure/persistence/mapper/ArtifactMyBatisMapper.java
    infrastructure/persistence/mapping/TaskPersistenceMapper.java
    infrastructure/worker/LocalWorkerArtifactValidator.java    # Validate staged output before promotion
  src/test/java/...                                            # Domain, dispatch, persistence and Worker tests

htht-admin/
  src/main/java/com/htht/config/multisource/LocalTaskArtifactStorage.java
                                                                    # Controlled local promotion/resolution adapter
  src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java
                                                                    # Wire the new port and storage root
  src/test/java/...                                               # Promotion and Spring assembly tests

script/sql/migrations/
  V20260825_01__add_multisource_stage_artifact_contract.sql     # Forward-only database change

rs-algorithm-worker/
  pyproject.toml                                                # Add pinned raster and algorithm runtime dependencies
  src/rs_algorithm_worker/
    models.py                                                   # Canonical ProductContext and data values
    operators/base.py                                           # Operator protocol and result values
    operators/registry.py                                       # Versioned operator lookup
    product_adapters/hy1c_czi.py                               # HY-1C evidence, mapping and plan only
    operators/models.py                                        # Windowed raster and operator result values
    operators/hdf.py                                           # HDF window access
    operators/calibration.py                                   # Coefficient-based calibration
    operators/swath.py                                         # Latitude/longitude to target grid
    operators/crop.py                                          # Geographic ROI clipping
    operators/cog.py                                           # Atomic tiled COG write
    algorithms/base.py                                          # Algorithm capability protocol
    algorithms/features.py                                      # Statistics, gradients and texture baseline
    algorithms/fusion.py                                        # Percentile stretch and band stack baseline
    algorithms/saliency.py                                      # Contrast saliency baseline
    capabilities.py                                             # Applicability and parameter schemas
    registry.py                                                # Profile-key stage routing
  tests/
    fixtures/hy1c_czi_v1.json                                  # Reviewed product mapping, not guessed values
    test_models.py                                              # Canonical model validation
    test_operator_contracts.py                                  # Operator protocol and registry
    test_operators_*.py                                        # Synthetic, deterministic operator tests
    test_algorithms_*.py                                       # Synthetic algorithm baseline tests
    test_hy1c_czi_preprocess.py                                # Real-sample adapter and pipeline tests
    test_registry.py                                           # Contract and profile-routing tests
```

## Fixed Contracts

### Canonical data and operator contracts

All operators consume canonical values rather than adapter-specific HDF handles:

```python
@dataclass(frozen=True)
class RasterData:
    values: numpy.ndarray
    crs: str | None
    transform: Affine | None
    nodata: float | int | None
    band_metadata: tuple[BandMetadata, ...]

@dataclass(frozen=True)
class SwathData:
    values: numpy.ndarray
    longitude: numpy.ndarray
    latitude: numpy.ndarray
    metadata: Mapping[str, Any]

class Operator(Protocol):
    name: str
    version: str
    def validate(self, context: ProductContext, parameters: Mapping[str, Any]) -> None: ...
    def execute(self, context: ProductContext, inputs: Mapping[str, Any],
                parameters: Mapping[str, Any], output_dir: Path) -> OperatorResult: ...
```

`OperatorResult` contains typed outputs, statistics, warnings, algorithm version and a deterministic metadata snapshot. Operators must be independent of Java, HTTP, database repositories, and physical storage authorization. The Worker runtime supplies authorized local paths; operators only receive paths already resolved by the manifest validator.

`OperatorRegistry` and `CapabilityRegistry` are separate. The former resolves implementation names and versions; the latter declares whether a product profile and data shape may expose a capability. A capability is enabled only when its required bands/variables, spatial reference, and product-specific quality prerequisites are present.

### Optional algorithm contracts

The three algorithm capabilities consume standardized canonical data and expose replaceable implementations:

```text
FeatureExtractor.extract(RasterData | GridData, FeatureParameters) -> FeatureSet
BandFusionOperator.fuse(BandSet, FusionParameters) -> RasterData
SaliencyDetector.detect(RasterData | FeatureSet, SaliencyParameters) -> SaliencyResult
```

The first baseline implementations are intentionally general-purpose and must be labeled as baselines:

- Feature extraction: min/max/mean/std, valid-pixel ratio, gradient magnitude, local variance, and configured spectral indices only when a profile supplies the required band mapping.
- Fusion: percentile stretch, explicit band-to-RGB mapping, and alignment through the shared resampling operator. It does not claim pan-sharpening unless a separate algorithm is supplied.
- Saliency: normalized local-contrast score with fixed or adaptive threshold, producing a score raster and binary mask. It is a visual baseline, not a universal scientific detector.

Every algorithm result records input profile, required bands, parameters, method name, implementation version, and applicability status. Unsupported capabilities return `NOT_SUPPORTED` or `NOT_CHECKED`; they must not silently run on incompatible data.

### Stage inputs and outputs

Replace the implicit `candidate.files()` execution input with plan-declared bindings:

```java
public record StageInput(String key, Source source, String artifactType, boolean required) {
    public enum Source { RAW_INPUT, UPSTREAM_ARTIFACT }
}

public record StageOutput(String artifactType, String relativePath, Retention retention) {
    public enum Retention { INTERMEDIATE, FINAL }
}

public record ProcessingStage(
    String stageKey,
    String stageCode,
    List<String> dependsOnStageKeys,
    List<StageInput> inputs,
    List<StageOutput> outputs,
    String parameterSnapshot
) { }
```

`key` is an input asset role for `RAW_INPUT` and an upstream `artifactType` for `UPSTREAM_ARTIFACT`. The plan validator must reject duplicate keys, output paths containing `..`, upstream inputs without a dependency producing that type, and final outputs consumed by later stages. The Worker receives only the resolved `InputAsset` list plus its stage-specific output URI; it never resolves arbitrary artifact URIs.

### Artifact promotion

The Worker writes to `<work-root>/<execution-id>/output`. After checksum validation, Java moves or copies each file to one controlled task-owned storage location and only then persists its logical URI. The local implementation uses:

```text
<artifact-root>/<task-id>/work/<stage-key>/<attempt>/<relative-path>   # INTERMEDIATE
<artifact-root>/<task-id>/output/<relative-path>                       # FINAL
```

The promoted file is atomic: write/copy to a sibling temporary file, fsync where supported, then replace. A retry may replace only the same stage/attempt destination after matching the declared checksum; it must never overwrite another stage's artifact. `ArtifactStorage` grants only the exact upstream files declared for the next execution.

### HY-1C public parameters

The first public parameter snapshot is a JSON object with this exact shape:

```json
{
  "schemaVersion": "1",
  "operation": "STANDARDIZE",
  "bands": ["<reviewed-band-id>"],
  "targetCrs": "EPSG:4326",
  "resolution": <reviewed-positive-degree-value>,
  "resampling": "nearest",
  "roi": {"minLon": <number>, "minLat": <number>, "maxLon": <number>, "maxLat": <number>}
}
```

The final values for `bands` and `resolution` are taken from the reviewed fixture. `roi` is optional; omit it for full coverage. Reject unknown fields, duplicate bands, invalid CRS, non-positive resolution, an invalid bounding box, or a requested band absent from the profile mapping.

### HY-1C v1 DAG

```text
VALIDATE_SOURCE
  -> CALIBRATE_CZI
  -> GEOLOCATE_CZI
  -> CROP_CZI
  -> WRITE_STANDARD_COG
  -> EXTRACT_CZI_FEATURES             # selected by capabilities/parameters
  -> FUSE_CZI_BANDS                   # selected by capabilities/parameters
  -> DETECT_CZI_SALIENCY              # selected by capabilities/parameters
```

`VALIDATE_SOURCE` consumes raw `MAIN`, `METADATA`, and `BROWSE`; it produces no data artifact. `CALIBRATE_CZI` consumes `MAIN` and outputs `CALIBRATED_CZI` as a lossless intermediate. `GEOLOCATE_CZI` consumes `CALIBRATED_CZI` plus raw `MAIN` for approved latitude/longitude arrays and outputs `GEOLOCATED_CZI`. `CROP_CZI` consumes `GEOLOCATED_CZI` and outputs `CROPPED_CZI`. `WRITE_STANDARD_COG` consumes `CROPPED_CZI` and writes final `STANDARD_COG` plus `STANDARD_METADATA_JSON`.

The three optional stages consume `CROPPED_CZI`, never raw HDF data. `EXTRACT_CZI_FEATURES` produces `FEATURE_RASTER` and `FEATURE_METADATA_JSON`; `FUSE_CZI_BANDS` produces `FUSION_COG` and `FUSION_PREVIEW_PNG`; `DETECT_CZI_SALIENCY` consumes either `CROPPED_CZI` or the declared feature artifact and produces `SALIENCY_SCORE_COG`, `SALIENCY_MASK_COG`, and `SALIENCY_PREVIEW_PNG`. The HY-1C adapter exposes a branch only when reviewed band mapping and public parameters meet that capability's prerequisites. Stages may run independently after `CROP_CZI`; fusion must not depend on feature extraction, and saliency has an explicit source selector.

If the product specification proves that L1B values are already calibrated, the reviewed profile must omit `CALIBRATE_CZI` and name the source quantity honestly. It must not run an identity operation named calibration.

---

### Task 1: Freeze Canonical Models, Operator Protocols, and Applicability Registry

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/models.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/base.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/registry.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/base.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py`
- Create: `rs-algorithm-worker/tests/test_models.py`
- Create: `rs-algorithm-worker/tests/test_operator_contracts.py`

- [ ] **Step 1: Write failing model and protocol tests.**

```python
def test_raster_data_rejects_band_shape_mismatch():
    with pytest.raises(ValueError, match="band metadata"):
        RasterData(values=numpy.zeros((2, 3, 4)), crs="EPSG:4326",
                   transform=None, nodata=None, band_metadata=(BandMetadata("B1"),))

def test_registry_rejects_unknown_operator_version():
    with pytest.raises(KeyError, match="operator version"):
        OPERATOR_REGISTRY.resolve("calibration", "does-not-exist")
```

Add tests for `SwathData` shape mismatches, invalid CRS, missing capability prerequisites, and an algorithm capability marked `NOT_SUPPORTED` for an incompatible data shape.

- [ ] **Step 2: Run the focused tests.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_models.py tests\test_operator_contracts.py -q`

Expected: collection failure because the canonical model and registries do not exist.

- [ ] **Step 3: Implement immutable canonical models and registries.**

Implement `RasterData`, `SwathData`, `GridData`, `BandSet`, `FeatureSet`, `SaliencyResult`, `OperatorResult`, and `ProductContext` with shape, finite-value, metadata, and version validation. Implement `OperatorRegistry` and `CapabilityRegistry` with explicit `(name, version)` keys. Do not add product-specific HDF paths to these modules.

- [ ] **Step 4: Add capability declarations.**

Represent each capability with supported data shapes, required bands/variables, parameter schema version, output types, and quality prerequisites. A capability lookup must return `SUPPORTED`, `NOT_SUPPORTED`, or `NOT_CHECKED` with reasons; it must never infer support from a file extension.

- [ ] **Step 5: Run tests and commit the contracts.**

Run the Step 2 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/models.py rs-algorithm-worker/src/rs_algorithm_worker/operators/base.py rs-algorithm-worker/src/rs_algorithm_worker/operators/registry.py rs-algorithm-worker/src/rs_algorithm_worker/algorithms/base.py rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py rs-algorithm-worker/tests/test_models.py rs-algorithm-worker/tests/test_operator_contracts.py
git commit -m "feat: add canonical worker data and capability contracts"
```

### Task 2: Implement Windowed Common Operators

**Files:**
- Modify: `rs-algorithm-worker/pyproject.toml`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/hdf.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/calibration.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/quality_mask.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/swath.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/grid.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/reproject.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/align.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/crop.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/cog.py`
- Create: `rs-algorithm-worker/tests/test_operators_calibration.py`
- Create: `rs-algorithm-worker/tests/test_operators_grid.py`
- Create: `rs-algorithm-worker/tests/test_operators_spatial.py`
- Create: `rs-algorithm-worker/tests/test_operators_cog.py`

- [ ] **Step 1: Write failing synthetic operator tests.**

Cover calibration with fill values, quality-bit masking, nearest-neighbour swath/grid conversion, CRS reprojection, band alignment, ROI clipping, and atomic tiled COG output. Each test uses small in-memory arrays and verifies shape, CRS/transform, NoData and warnings.

- [ ] **Step 2: Run the operator tests and confirm missing implementations.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_calibration.py tests\test_operators_grid.py tests\test_operators_spatial.py tests\test_operators_cog.py -q`

Expected: collection failure because the common operators do not exist.

- [ ] **Step 3: Add pinned runtime dependencies.**

Add bounded versions of `numpy`, `rasterio`, `pyproj`, and `scipy` to `pyproject.toml`. Keep HDF access through `h5py` and use row/window iteration; do not load a complete source scene by default.

- [ ] **Step 4: Implement the smallest correct operator behavior.**

`hdf.py` exposes windowed dataset reads. `calibration.py` applies linear scale/offset or an explicitly declared already-calibrated mode. `quality_mask.py` applies declared bit masks and returns valid-pixel statistics. `swath.py` validates matching value/latitude/longitude shapes. `grid.py` performs deterministic nearest-neighbour binning with explicit empty-cell NoData. `reproject.py` and `align.py` use rasterio/pyproj transforms. `crop.py` rejects non-intersecting ROI. `cog.py` writes a tiled, compressed temporary GeoTIFF, validates it, and atomically replaces the staged output.

- [ ] **Step 5: Run tests and commit the common operators.**

Run the Step 2 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/pyproject.toml rs-algorithm-worker/src/rs_algorithm_worker/operators rs-algorithm-worker/tests/test_operators_*.py
git commit -m "feat: add windowed reusable preprocessing operators"
```

### Task 3: Implement Baseline Feature, Fusion, and Saliency Algorithms

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/features.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/fusion.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/saliency.py`
- Create: `rs-algorithm-worker/tests/test_algorithms_features.py`
- Create: `rs-algorithm-worker/tests/test_algorithms_fusion.py`
- Create: `rs-algorithm-worker/tests/test_algorithms_saliency.py`

- [ ] **Step 1: Write failing baseline tests.**

```python
def test_feature_baseline_reports_valid_pixel_statistics():
    result = extract_features(raster, FeatureParameters(metrics=("statistics",)))
    assert result.statistics["validPixelRatio"] == 1.0
    assert {"min", "max", "mean", "std"}.issubset(result.statistics["band-1"])

def test_fusion_baseline_stacks_explicit_bands_after_percentile_stretch():
    result = fuse_bands(bands, FusionParameters(red="B3", green="B2", blue="B1"))
    assert result.values.shape[0] == 3
    assert result.band_metadata[0].name == "red"

def test_saliency_baseline_returns_score_and_threshold_mask():
    result = detect_saliency(raster, SaliencyParameters(threshold=0.5))
    assert result.score.values.shape == raster.values.shape[-2:]
    assert result.mask.dtype == numpy.bool_
```

Also test missing-band rejection, unsupported data-shape status, constant-image saliency, and deterministic repeated output.

- [ ] **Step 2: Run the algorithm tests and confirm missing implementations.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_algorithms_*.py -q`

Expected: collection failure because the three algorithm modules do not exist.

- [ ] **Step 3: Implement the baseline algorithms behind the protocols.**

`features.py` provides statistics, gradient magnitude, local variance, and optional index formulas only when a `BandSet` mapping supplies the required bands. `fusion.py` performs percentile stretch and explicit RGB/false-colour stacking, delegating resampling to `align.py`; it does not implement pan-sharpening. `saliency.py` computes a normalized local-contrast score and fixed/adaptive threshold mask; it is labeled `CONTRAST_BASELINE` and does not claim a trained scientific model.

- [ ] **Step 4: Register algorithms and capability restrictions.**

Register versions such as `features/statistics-v1`, `fusion/percentile-rgb-v1`, and `saliency/contrast-v1`. Declare applicability per data shape and required bands. HY-2/CFOSAT dynamic products must return `NOT_SUPPORTED` for image-texture and RGB fusion capabilities unless a later dynamic-data profile is added.

- [ ] **Step 5: Run tests and commit the algorithm baselines.**

Run the Step 2 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/algorithms rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py rs-algorithm-worker/tests/test_algorithms_*.py
git commit -m "feat: add baseline feature fusion and saliency capabilities"
```

### Task 4: Record the Scientific HY-1C v1 Mapping Before Product Execution

**Files:**
- Create: `rs-algorithm-worker/tests/fixtures/hy1c_czi_v1.json`
- Modify: `rs-algorithm-worker/tests/test_hy1c_czi.py`
- Modify: `rs-algorithm-worker/tests/conftest.py`

- [ ] **Step 1: Add the reviewed mapping fixture and a failing schema test.**

```python
def test_hy1c_profile_fixture_has_reviewed_processing_evidence(profile_fixture):
    assert profile_fixture["profile"] == "HY-1C-CZI/v1"
    assert profile_fixture["datasets"]["science"]
    assert profile_fixture["datasets"]["longitude"]
    assert profile_fixture["datasets"]["latitude"]
    assert profile_fixture["validRange"] == [profile_fixture["validRange"][0], profile_fixture["validRange"][1]]
    assert profile_fixture["calibration"]["mode"] in {"linear", "already_calibrated"}
```

The JSON fixture must contain the exact HDF dataset paths, band identifiers and order, coefficient source, output unit, fill value, valid range, latitude and longitude dataset paths, output resolution, and the product-spec document/version that supplied each fact. Do not put unreviewed or inferred values in the fixture.

- [ ] **Step 2: Run the focused test.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi.py -q`

Expected: FAIL because the reviewed fixture and fixture loader do not exist.

- [ ] **Step 3: Add the fixture loader and verify the real sample exposes every declared HDF path.**

```python
with h5py.File(next(path for path in hy1c_paths if path.suffix == ".h5"), "r") as source:
    for dataset_path in profile_fixture["datasets"].values():
        assert dataset_path in source
```

Do not replace this with a filename check. The test must report the missing dataset path and profile version.

- [ ] **Step 4: Re-run the HY-1C inspection tests.**

Run: `$env:MULTISOURCE_HY1C_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L1B_20260715T023726_20260715T023821_41109_10'; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi.py -q`

Expected: all fixture-schema and evidence tests pass. Stop the plan if an official mapping cannot be supplied.

- [ ] **Step 5: Commit the scientific contract.**

```powershell
git add rs-algorithm-worker/tests/fixtures rs-algorithm-worker/tests/test_hy1c_czi.py rs-algorithm-worker/tests/conftest.py
git commit -m "test: record HY-1C CZI v1 processing evidence"
```

### Task 5: Make the Java Plan Describe Real Stage Inputs and Outputs

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageInput.java`
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageOutput.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingStage.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingPlanValidator.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerResultTranslator.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

- [ ] **Step 1: Write failing plan-validation tests.**

```java
@Test
void rejectsUpstreamArtifactWithoutProducingDependency() {
    var stage = stage("WRITE", List.of(), List.of(new StageInput("CALIBRATED_CZI", UPSTREAM_ARTIFACT,
        "CALIBRATED_CZI", true)), List.of(finalOutput("STANDARD_COG", "standard.tif")));
    assertThatThrownBy(() -> new ProcessingPlan("plan-v2", "rules-2026.08", List.of(stage)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("upstream artifact");
}
```

Add cases for duplicate input keys, `../escape.tif`, duplicate produced artifact types, and a final artifact consumed by a dependent stage.

- [ ] **Step 2: Run the focused Java tests.**

Run: `mvn -pl htht-modules/htht-multisource -am -Dtest=ProcessingPlanValidatorTest,WorkerContractCodecTest -DfailIfNoTests=false test`

Expected: compilation failure for missing `StageInput`/`StageOutput` values.

- [ ] **Step 3: Implement immutable stage contract values and plan validation.**

`StageInput` validates a non-blank key and artifact type; `RAW_INPUT` requires no producing dependency, while `UPSTREAM_ARTIFACT` requires one. `StageOutput` requires a relative slash-separated path with no `.` or `..` segment. Preserve the old three-field `ProcessingStage` constructor only for existing GF tests, mapping it to empty inputs, outputs and `{}` parameters; remove it only after all profiles return explicit contracts.

- [ ] **Step 4: Extend Worker plan decoding.**

Decode `inputs`, `outputs`, and `parameterSnapshot` from Worker `build-plan` payload. Require arrays, reject absent fields for `plan-v2`, and retain decoding of `plan-v1` only for the existing GF browse profile.

- [ ] **Step 5: Run the focused tests and commit.**

Run the Step 2 command. Expected: PASS with no database access.

```powershell
git add htht-modules/htht-multisource/src/main htht-modules/htht-multisource/src/test
git commit -m "feat: declare multisource stage inputs and outputs"
```

### Task 6: Promote Worker Outputs and Authorize Upstream Artifact Reads

**Files:**
- Create: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/ArtifactStorage.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/Artifact.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java`
- Create: `htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactStorage.java`
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Create: `script/sql/migrations/V20260825_01__add_multisource_stage_artifact_contract.sql`
- Test: `htht-admin/src/test/java/com/htht/config/multisource/LocalTaskArtifactStorageTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/ArtifactRegistrationServiceTest.java`

- [ ] **Step 1: Write failing promotion-security tests.**

```java
@Test
void promotesOnlyAValidatedFileBelowTheExecutionOutputDirectory() {
    Artifact artifact = storage.promote(taskId, stageId, executionId, intermediateOutput(), candidate("calibrated.npz"));
    assertThat(storage.resolveForGrant(grant, List.of(artifact))).containsExactly(artifactInput(artifact));
}

@Test
void rejectsTraversalAndAnotherTasksArtifact() {
    assertThatThrownBy(() -> storage.promote(taskId, stageId, executionId, intermediateOutput(), candidate("../escape")))
        .isInstanceOf(IllegalArgumentException.class);
}
```

Add tests for checksum mismatch, retry idempotency, source deletion after promotion, final/intermediate layout, and grants that omit an artifact required by the next stage.

- [ ] **Step 2: Run focused tests.**

Run: `mvn -pl htht-admin,htht-modules/htht-multisource -am -Dtest=LocalTaskArtifactStorageTest,ArtifactRegistrationServiceTest -DfailIfNoTests=false test`

Expected: compilation failure because `ArtifactStorage` and the local adapter do not exist.

- [ ] **Step 3: Implement storage promotion and persistence.**

Define `ArtifactStorage.promote(...)` and `ArtifactStorage.toAuthorizedInputs(...)`. Promotion receives only a prior-validated `ArtifactCandidate`, creates an `InputAsset` with source type `TASK_ARTIFACT`, and stores the promoted checksum and relative path. Add `retention` and `relative_path` columns to `rs_task_artifact` using a forward-only migration; do not modify old module tables. Register the promoted artifact only after the atomic file operation succeeds.

- [ ] **Step 4: Implement the local adapter and wire it.**

`LocalTaskArtifactStorage` must resolve artifact paths below its configured artifact root by task ID and persisted relative path, then put only authorized paths in the Worker manifest. It must not accept an API path, a URI supplied by the Worker, or a path outside its artifact root. Inject it in Admin configuration without adding an Admin dependency to `htht-multisource`.

- [ ] **Step 5: Run focused tests and commit.**

Run the Step 2 command. Expected: PASS.

```powershell
git add htht-admin htht-modules/htht-multisource script/sql/migrations
git commit -m "feat: promote and authorize multisource stage artifacts"
```

### Task 7: Resolve Declared Stage Inputs During Dispatch

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/model/StageExecutionRequest.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalProcessWorkerGateway.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java`
- Test: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/WorkerContractCodecTest.java`

- [ ] **Step 1: Write failing dispatch tests for the CZI handoff.**

```java
@Test
void geolocateStageReceivesOnlyDeclaredRawAndCalibratedInputs() {
    dispatch(taskWithCziPlan());
    assertThat(dispatcher.lastRequest().inputs()).extracting(InputAsset::role)
        .containsExactlyInAnyOrder("MAIN", "CALIBRATED_CZI");
}
```

Add a failure case where `CALIBRATED_CZI` was not registered by the successful dependency; the stage must become failed with `MISSING_UPSTREAM_ARTIFACT` and must not launch a Worker.

- [ ] **Step 2: Run focused tests.**

Run: `mvn -pl htht-modules/htht-multisource -am -Dtest=DispatchNextStageUseCaseTest,WorkerContractCodecTest -DfailIfNoTests=false test`

Expected: test failure because dispatch always supplies every `candidate.files()` value.

- [ ] **Step 3: Implement deterministic input/output resolution.**

For `RAW_INPUT`, match candidate files by role and fail when a required role is missing. For `UPSTREAM_ARTIFACT`, query registered artifacts for a successful dependency and match the declared artifact type. Build a per-stage output URI under the task output root and pass only resolved inputs to `ExecutionGrantRequest`, `StageExecutionRequest`, codec and local manifest writer. A retry uses the same logical stage output URI but a new execution staging directory.

- [ ] **Step 4: Promote stage outputs before accepting success.**

Call `ArtifactStorage.promote` inside `ArtifactRegistrationService` after `LocalWorkerArtifactValidator` succeeds. A `SUCCEEDED` Worker receipt with a failed promotion must be recorded as a failed stage, never as success with a dangling URI.

- [ ] **Step 5: Run focused regression and commit.**

Run the Step 2 command plus `mvn -pl htht-admin,htht-modules/htht-multisource -am -Dtest=LocalProcessWorkerGatewayTest -DfailIfNoTests=false test`.

Expected: PASS.

```powershell
git add htht-modules/htht-multisource/src/main htht-modules/htht-multisource/src/test
git commit -m "feat: dispatch multisource stages with declared inputs"
```

### Task 8: Replace the HY-1C Minimal Adapter Plan With the v2 Scientific DAG

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/tests/test_hy1c_czi.py`
- Modify: `rs-algorithm-worker/tests/test_registry.py`

- [ ] **Step 1: Write a failing plan test with explicit stage contracts.**

```python
def test_hy1c_standardize_plan_declares_persistent_handoffs(hy1c_candidate, standardize_parameters):
    plan = build_plan(hy1c_candidate, standardize_parameters)
    assert [stage["stageKey"] for stage in plan["stages"]] == [
        "VALIDATE_SOURCE", "CALIBRATE_CZI", "GEOLOCATE_CZI", "CROP_CZI", "WRITE_STANDARD_COG",
        "EXTRACT_CZI_FEATURES", "FUSE_CZI_BANDS", "DETECT_CZI_SALIENCY"
    ]
    assert plan["stages"][2]["inputs"] == [
        {"key": "MAIN", "source": "RAW_INPUT", "artifactType": "", "required": True},
        {"key": "CALIBRATED_CZI", "source": "UPSTREAM_ARTIFACT", "artifactType": "CALIBRATED_CZI", "required": True},
    ]
```

Assert that the plan includes `plan-v2`, reviewed mapping version, each output path, stage-specific parameter snapshots, and only exposes optional branches whose capability prerequisites are satisfied. Add a test showing that a missing required fusion band removes `FUSE_CZI_BANDS` with an explicit `NOT_SUPPORTED` reason rather than producing a malformed plan.

- [ ] **Step 2: Run the HY-1C and registry tests.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi.py tests\test_registry.py -q`

Expected: FAIL because the common `base.build_plan` emits only its current minimal two-stage plan.

- [ ] **Step 3: Implement evidence-based profile routing.**

Make `inspect` test all registered adapters and report only candidates supported by their evidence. For `build-plan` and `execute-stage`, route solely by selected `profileCode`, `profileVersion`, and `packageId`; do not select an adapter by extension at execution time. HY-1C reads only manifest-listed `MAIN`, `METADATA`, and `BROWSE` assets and emits no physical path in a response.

- [ ] **Step 4: Implement the v2 plan builder.**

Validate the exact public parameter object, including an `optionalCapabilities` list and algorithm parameter objects whose schemas are owned by `CapabilityRegistry`. Embed only the reviewed mapping key and requested business parameters in each stage snapshot. Do not embed absolute paths, data arrays, or a copied raw metadata document. Return `CALIBRATE_CZI` only if the reviewed mapping says `linear`; otherwise build the honest no-calibration branch and test it separately. Add optional branch stages only after `CapabilityRegistry` returns `SUPPORTED`.

- [ ] **Step 5: Re-run tests and commit.**

Run the Step 2 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests
git commit -m "feat: plan HY-1C CZI standardization stages"
```

### Task 9: Execute HY-1C Stages and Write Standard Outputs

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi_execution.py`
- Modify: `rs-algorithm-worker/tests/test_hy1c_czi_preprocess.py`
- Modify: `rs-algorithm-worker/tests/test_registry.py`

- [ ] **Step 1: Write failing stage-execution tests.**

```python
def test_write_standard_cog_emits_only_declared_final_artifacts(stage_manifest, hy1c_execution_request):
    response = dispatch(hy1c_execution_request("WRITE_STANDARD_COG"), stage_manifest)
    assert [item["type"] for item in response["artifacts"]] == ["STANDARD_COG", "STANDARD_METADATA_JSON"]
    assert all(item["uri"].startswith("rs://output/") for item in response["artifacts"])
```

Add execution tests for `EXTRACT_CZI_FEATURES`, `FUSE_CZI_BANDS`, and `DETECT_CZI_SALIENCY`. For each stage, test that a missing required upstream artifact, corrupt HDF, malformed parameters, unavailable capability, or non-intersecting ROI returns a stable non-retryable code and produces no artifact.

- [ ] **Step 2: Run the execution tests.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi_preprocess.py tests\test_registry.py -q`

Expected: FAIL because non-GF execution currently returns success with no artifacts.

- [ ] **Step 3: Implement each stage as a small execution function.**

`VALIDATE_SOURCE` repeats product evidence and mapping checks. `CALIBRATE_CZI` reads configured band windows and writes a lossless intermediate with a sidecar metadata JSON. `GEOLOCATE_CZI` reads its declared calibrated input and raw navigation arrays, then produces an EPSG:4326 intermediate. `CROP_CZI` applies the validated optional ROI. `WRITE_STANDARD_COG` writes a tiled, compressed COG and a JSON sidecar containing profile/mapping/operator versions, source checksums, CRS, transform, bounds, band metadata, unit, NoData, valid-pixel count, and parameters. The optional stages delegate only to the registered baseline algorithms and write their declared feature, fusion, or saliency artifacts with versions and parameters included in the metadata.

Every output must be first written beneath the manifest output directory. Return only logical URI, type, byte size, and SHA-256 after reading the final staged file. Do not catch arbitrary exceptions as success.

- [ ] **Step 4: Add an end-to-end real-sample test.**

Run the standardization sequence and each supported optional branch with promoted outputs represented as next-stage manifest inputs. Assert `rasterio.open` can read every COG, its CRS is EPSG:4326, dimensions are positive, declared NoData is present, band description and unit match the reviewed fixture, and all sidecars have no local filesystem paths. Assert fusion is three-band RGB, feature output records its metric list, and saliency output contains score and mask artifacts.

- [ ] **Step 5: Run Worker regression and commit.**

Run: `$env:MULTISOURCE_HY1C_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L1B_20260715T023726_20260715T023821_41109_10'; .\.venv\Scripts\python.exe -m pytest -q`

Expected: all Worker tests pass; real-sample tests run rather than skip.

```powershell
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: execute HY-1C CZI preprocessing and algorithm DAG"
```

### Task 10: Integrate the Complete HY-1C Task Through Java and Verify It

**Files:**
- Modify: `htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java`
- Modify: `htht-admin/src/test/java/com/htht/config/multisource/MultiSourceAdapterConfigurationTest.java`
- Create: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/infrastructure/worker/Hy1cCziLocalWorkerIntegrationTest.java`
- Modify: `docs/deployment/multisource-backend-deployment.md`

- [ ] **Step 1: Write a failing Java-to-Worker integration test.**

```java
@Test
void completesTheHy1cPlanWithPromotedIntermediates() {
    TaskId taskId = fixture.createHy1cTask();
    fixture.dispatchUntilTerminal(taskId);
    assertThat(fixture.task(taskId).lifecycleStatus()).isEqualTo(SUCCEEDED);
    assertThat(fixture.artifacts(taskId)).extracting(Artifact::type)
        .contains("CALIBRATED_CZI", "GEOLOCATED_CZI", "CROPPED_CZI", "STANDARD_COG", "STANDARD_METADATA_JSON",
            "FEATURE_RASTER", "FUSION_COG", "SALIENCY_SCORE_COG", "SALIENCY_MASK_COG");
}
```

Gate this test behind `MULTISOURCE_HY1C_SAMPLE_ROOT` and the configured Python 3.11 Worker runtime. It must use logical input assets and the server-generated manifest, never a direct product path in the request.

- [ ] **Step 2: Run the integration test and confirm it fails before wiring.**

Run: `mvn -pl htht-admin,htht-modules/htht-multisource -am -Dtest=Hy1cCziLocalWorkerIntegrationTest -DfailIfNoTests=false test`

Expected: FAIL until the Worker command, artifact root, and sample asset resolver are correctly configured.

- [ ] **Step 3: Wire configuration and document runtime requirements.**

Add explicit properties for `artifact-root`, Python executable, Worker module, GDAL/rasterio runtime, max window memory, and HY-1C enablement. Startup must reject an artifact root that cannot be created or is inside the upload root. The deployment document must list the exact Python package lock/update procedure and the requirement for the reviewed HY-1C product specification.

- [ ] **Step 4: Run focused and broad regression suites.**

Run:

```powershell
mvn -pl htht-modules/htht-multisource,htht-admin -am test
cd rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest -q
```

Expected: all configured tests pass; only environment-gated integration tests may skip when their explicit variables are absent.

- [ ] **Step 5: Validate output with external tooling and commit.**

Run `gdalinfo -json <promoted-standard-cog>` and compare its CRS, bounds, pixel size, band count, band unit, NoData and checksum with the sidecar metadata. Commit only the code, tests, migration, deployment documentation, and reviewed fixture:

```powershell
git add htht-admin htht-modules/htht-multisource rs-algorithm-worker docs/deployment script/sql/migrations
git commit -m "feat: integrate HY-1C CZI standard preprocessing"
```

## Follow-up Plans

1. **HY-2D altimeter:** reuse artifact handoff, quality mask, gridder and writer contracts; add safe TAR extraction, variable/unit mapping, time-aware bin averaging or an explicitly approved gridding method, and NetCDF primary output. Its algorithms should be dynamic-data statistics and anomaly detection, not the raster texture/RGB/saliency baselines.
2. **FY-3H MERSI:** reuse HDF, calibration, swath, alignment, COG, feature, fusion and saliency contracts; add multi-resolution band groups, calibration LUT handling, and per-band resampling rules.
3. **FY-4 AGRI, GF optical and SAR:** each requires its own product-spec-backed adapter plan. Navigation, RPC and terrain correction must not be added to the HY-1C operators by assumption. SAR requires separate speckle, polarimetric-feature, and terrain-correction capability declarations.

## Plan Self-Review

- **Coverage:** includes canonical models, common operator interfaces and baselines, three optional algorithm capabilities, stage-to-stage artifact contracts, local storage authorization, HY-1C adapter/DAG, Worker execution, Java integration, real-sample testing, COG validation and deployment configuration.
- **Scope:** implements baseline capabilities and validates them with one full optical swath product. HY-2D and FY-3H remain separate scientific product implementations rather than being hidden in generic tasks.
- **Consistency:** the v2 plan contract, `StageInput`/`StageOutput` names, `TASK_ARTIFACT` source type, intermediate types, capability statuses, and HY-1C stage keys are used consistently throughout.
- **Scientific gate:** required values are blocked on a reviewed fixture rather than replaced with invented dataset names, coefficients, or thresholds.
