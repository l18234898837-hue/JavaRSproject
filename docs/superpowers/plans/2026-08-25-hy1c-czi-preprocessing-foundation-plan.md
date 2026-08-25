# HY-1C CZI Preprocessing Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first scientific preprocessing route for `HY-1C-CZI/v1`: validate an L1B product package, radiometrically calibrate and geolocate its selected bands, apply an optional ROI, and write a standards-compliant COG with traceable intermediate artifacts.

**Architecture:** Product adapters remain thin, versioned descriptions of product evidence and product-specific mappings. Reusable Python operators perform data access, calibration, swath geolocation, crop, and COG writing. Java remains the source of task state and stages, but adds stage-output promotion and authorized artifact input resolution so each DAG stage consumes only declared raw inputs or registered upstream artifacts.

**Tech Stack:** Java 17, Spring, MyBatis-Plus, PostgreSQL JSONB, Python 3.11+, pytest, h5py, NumPy, rasterio/GDAL, pyproj.

**Scope:** This plan implements the shared stage-artifact foundation and one complete HY-1C CZI L1B route. HY-2D and FY-3H reuse these boundaries but need their own product specifications, plans, and validation baselines; they are deliberately separate follow-up plans.

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
  pyproject.toml                                                # Add pinned raster runtime dependencies
  src/rs_algorithm_worker/
    product_adapters/hy1c_czi.py                               # HY-1C evidence, mapping and plan only
    operators/models.py                                        # Windowed raster and operator result values
    operators/hdf.py                                           # HDF window access
    operators/calibration.py                                   # Coefficient-based calibration
    operators/swath.py                                         # Latitude/longitude to target grid
    operators/crop.py                                          # Geographic ROI clipping
    operators/cog.py                                           # Atomic tiled COG write
    registry.py                                                # Profile-key stage routing
  tests/
    fixtures/hy1c_czi_v1.json                                  # Reviewed product mapping, not guessed values
    test_operators_*.py                                        # Synthetic, deterministic operator tests
    test_hy1c_czi_preprocess.py                                # Real-sample adapter and pipeline tests
    test_registry.py                                           # Contract and profile-routing tests
```

## Fixed Contracts

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
```

`VALIDATE_SOURCE` consumes raw `MAIN`, `METADATA`, and `BROWSE`; it produces no data artifact. `CALIBRATE_CZI` consumes `MAIN` and outputs `CALIBRATED_CZI` as a lossless intermediate. `GEOLOCATE_CZI` consumes `CALIBRATED_CZI` plus raw `MAIN` for approved latitude/longitude arrays and outputs `GEOLOCATED_CZI`. `CROP_CZI` consumes `GEOLOCATED_CZI` and outputs `CROPPED_CZI`. `WRITE_STANDARD_COG` consumes `CROPPED_CZI` and writes final `STANDARD_COG` plus `STANDARD_METADATA_JSON`.

If the product specification proves that L1B values are already calibrated, the reviewed profile must omit `CALIBRATE_CZI` and name the source quantity honestly. It must not run an identity operation named calibration.

---

### Task 1: Record the Scientific HY-1C v1 Mapping Before Coding Operators

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

### Task 2: Make the Java Plan Describe Real Stage Inputs and Outputs

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

### Task 3: Promote Worker Outputs and Authorize Upstream Artifact Reads

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

### Task 4: Resolve Declared Stage Inputs During Dispatch

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

### Task 5: Add Windowed Generic Raster Operators

**Files:**
- Modify: `rs-algorithm-worker/pyproject.toml`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/__init__.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/models.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/hdf.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/calibration.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/swath.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/crop.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/cog.py`
- Test: `rs-algorithm-worker/tests/test_operators_calibration.py`
- Test: `rs-algorithm-worker/tests/test_operators_swath.py`
- Test: `rs-algorithm-worker/tests/test_operators_cog.py`

- [ ] **Step 1: Add failing synthetic calibration and nodata tests.**

```python
def test_linear_calibration_preserves_fill_values():
    dn = numpy.array([[0, 10], [65535, 20]], dtype=numpy.uint16)
    actual = calibrate_linear(dn, scale=0.1, offset=1.0, fill_value=65535)
    numpy.testing.assert_allclose(actual[:1], [[1.0, 2.0]])
    assert numpy.isnan(actual[1, 0])
```

Add range clipping and a test that an `already_calibrated` mapping returns float values without claiming a calibration transform.

- [ ] **Step 2: Add failing window and geolocation tests.**

```python
def test_swath_gridder_marks_cells_without_observations_as_nan():
    grid = grid_nearest(values=numpy.array([1.0]), lon=numpy.array([120.0]), lat=numpy.array([30.0]), grid=grid_spec())
    assert numpy.isnan(grid.values).sum() > 0
```

The first release uses deterministic nearest-neighbour gridding to the approved EPSG:4326 grid. It must not silently interpolate values.

- [ ] **Step 3: Run operator tests and confirm imports fail.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_calibration.py tests\test_operators_swath.py tests\test_operators_cog.py -q`

Expected: collection failure because `operators` does not exist.

- [ ] **Step 4: Add runtime dependencies and minimal windowed implementations.**

Add `numpy`, `rasterio`, and `pyproj` with bounded versions to `pyproject.toml`. `hdf.py` iterates `h5py.Dataset` slices in configured row windows. `calibration.py` uses float32 output and NaN internally. `swath.py` checks matching value/latitude/longitude shapes and finite coordinate bounds before binning. `crop.py` rejects a non-intersecting ROI. `cog.py` writes a temporary tiled GeoTIFF, builds overviews, validates it with rasterio, then atomically replaces the final staged path.

- [ ] **Step 5: Run focused tests and commit.**

Run the Step 3 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/pyproject.toml rs-algorithm-worker/src/rs_algorithm_worker/operators rs-algorithm-worker/tests/test_operators_*.py
git commit -m "feat: add windowed raster preprocessing operators"
```

### Task 6: Replace the HY-1C Minimal Adapter Plan With the v2 Scientific DAG

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
        "VALIDATE_SOURCE", "CALIBRATE_CZI", "GEOLOCATE_CZI", "CROP_CZI", "WRITE_STANDARD_COG"
    ]
    assert plan["stages"][2]["inputs"] == [
        {"key": "MAIN", "source": "RAW_INPUT", "artifactType": "", "required": True},
        {"key": "CALIBRATED_CZI", "source": "UPSTREAM_ARTIFACT", "artifactType": "CALIBRATED_CZI", "required": True},
    ]
```

Assert that the plan includes `plan-v2`, reviewed mapping version, each output path, and stage-specific parameter snapshots.

- [ ] **Step 2: Run the HY-1C and registry tests.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi.py tests\test_registry.py -q`

Expected: FAIL because the common `base.build_plan` emits only its current minimal two-stage plan.

- [ ] **Step 3: Implement evidence-based profile routing.**

Make `inspect` test all registered adapters and report only candidates supported by their evidence. For `build-plan` and `execute-stage`, route solely by selected `profileCode`, `profileVersion`, and `packageId`; do not select an adapter by extension at execution time. HY-1C reads only manifest-listed `MAIN`, `METADATA`, and `BROWSE` assets and emits no physical path in a response.

- [ ] **Step 4: Implement the v2 plan builder.**

Validate the exact public parameter object. Embed only the reviewed mapping key and requested business parameters in each stage snapshot. Do not embed absolute paths, data arrays, or a copied raw metadata document. Return `CALIBRATE_CZI` only if the reviewed mapping says `linear`; otherwise build the honest no-calibration branch and test it separately.

- [ ] **Step 5: Re-run tests and commit.**

Run the Step 2 command. Expected: PASS.

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy1c_czi.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests
git commit -m "feat: plan HY-1C CZI standardization stages"
```

### Task 7: Execute HY-1C Stages and Write Standard Outputs

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

For each stage, test that a missing required upstream artifact, corrupt HDF, malformed parameters, or non-intersecting ROI returns a stable non-retryable code and produces no artifact.

- [ ] **Step 2: Run the execution tests.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_hy1c_czi_preprocess.py tests\test_registry.py -q`

Expected: FAIL because non-GF execution currently returns success with no artifacts.

- [ ] **Step 3: Implement each stage as a small execution function.**

`VALIDATE_SOURCE` repeats product evidence and mapping checks. `CALIBRATE_CZI` reads configured band windows and writes a lossless intermediate with a sidecar metadata JSON. `GEOLOCATE_CZI` reads its declared calibrated input and raw navigation arrays, then produces an EPSG:4326 intermediate. `CROP_CZI` applies the validated optional ROI. `WRITE_STANDARD_COG` writes a tiled, compressed COG and a JSON sidecar containing profile/mapping/operator versions, source checksums, CRS, transform, bounds, band metadata, unit, NoData, valid-pixel count, and parameters.

Every output must be first written beneath the manifest output directory. Return only logical URI, type, byte size, and SHA-256 after reading the final staged file. Do not catch arbitrary exceptions as success.

- [ ] **Step 4: Add an end-to-end real-sample test.**

Run the full five-stage sequence with promoted outputs represented as the next-stage manifest inputs. Assert `rasterio.open` can read the COG, its CRS is EPSG:4326, dimensions are positive, declared NoData is present, band description and unit match the reviewed fixture, and the sidecar has no local filesystem paths.

- [ ] **Step 5: Run Worker regression and commit.**

Run: `$env:MULTISOURCE_HY1C_SAMPLE_ROOT='D:\Lwy_project_space\RSImageProcess\otherfile\H1C_OPER_CZI_L1B_20260715T023726_20260715T023821_41109_10'; .\.venv\Scripts\python.exe -m pytest -q`

Expected: all Worker tests pass; real-sample tests run rather than skip.

```powershell
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: execute HY-1C CZI preprocessing DAG"
```

### Task 8: Integrate the Complete HY-1C Task Through Java and Verify It

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
        .contains("CALIBRATED_CZI", "GEOLOCATED_CZI", "CROPPED_CZI", "STANDARD_COG", "STANDARD_METADATA_JSON");
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

1. **HY-2D altimeter:** reuse artifact handoff, quality mask and writer contracts; add safe TAR extraction, variable/unit mapping, time-aware bin averaging or an explicitly approved gridding method, and NetCDF primary output.
2. **FY-3H MERSI:** reuse HDF, calibration, swath and COG operators; add multi-resolution band groups, calibration LUT handling, band alignment, and per-band resampling rules.
3. **FY-4 AGRI, GF optical and SAR:** each requires its own product-spec-backed adapter plan. Navigation, RPC and terrain correction must not be added to the HY-1C operators by assumption.

## Plan Self-Review

- **Coverage:** includes the missing stage-to-stage artifact contract, local storage authorization, operator implementation, HY-1C adapter/DAG, Worker execution, Java integration, real-sample testing, COG validation and deployment configuration.
- **Scope:** intentionally stops after one full optical swath product. HY-2D and FY-3H are separate scientific implementations rather than being hidden in generic tasks.
- **Consistency:** the v2 plan contract, `StageInput`/`StageOutput` names, `TASK_ARTIFACT` source type, intermediate types, and five HY-1C stage keys are used consistently throughout.
- **Scientific gate:** required values are blocked on a reviewed fixture rather than replaced with invented dataset names, coefficients, or thresholds.
