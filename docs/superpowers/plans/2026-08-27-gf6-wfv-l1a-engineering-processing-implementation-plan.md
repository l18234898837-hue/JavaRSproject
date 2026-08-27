# GF6 WFV L1A Engineering Processing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GF6 WFV L1A processing profile that reads the supplied three-tile, eight-band product, performs XML-based radiometric calibration, no-DEM RPC nominal georeferencing, grid validation, ROI cropping, standard COG writing, and optional feature, fusion, and saliency products.

**Architecture:** Add a product-specific GF6 L1A adapter and pipeline around the existing `RasterData`, calibration, reprojection, alignment, crop, COG, and common-algorithm operators. The adapter owns evidence validation and product metadata; the pipeline owns windowed tile reads and stage execution; Java continues to authorize declared inputs and promote declared outputs. The existing `GF-6-WFV-BROWSE/v1` profile remains unchanged and is not reused for L1A data.

**Tech Stack:** Python 3.11+, NumPy, rasterio/GDAL, pyproj, Pillow only for browse comparison, pytest; Java Spring Boot multi-source module; Vue 3/TypeScript frontend; Maven and pnpm release gates.

**Spec:** Approved chat design in the current conversation: “GF6 WFV L1A 工程处理实现计划” (2026-08-27).

## Global Constraints

- The profile is `GF-6-WFV-L1A/v1` with `dataShape=RASTER_GRID` and must never be routed to `GF-6-WFV-BROWSE/v1`.
- The only science inputs are the supplied product package: three TIFF tiles, product XML, RPB RPC file, NAV file, TIL tile index, and optional SHP sidecars.
- Processing must be windowed and block-based; the approximately 13.5 GB TIFF payload must never be loaded as one NumPy array.
- XML `AGain` and `AOffset` are read-only product evidence; callers cannot override calibration coefficients, fill values, or band definitions.
- RPC output is explicitly `NOMINAL_RPC_NO_DEM`; no DEM, orthorectification accuracy, or absolute geolocation validation is claimed.
- Optional features, fusion, and saliency read standard outputs and must not modify `GF6_STANDARD_COG`.
- Saliency is `CANDIDATE_ONLY` and must not be presented as target recognition, change detection, or official quality assessment.
- All Worker paths are manifest-authorized; all returned artifacts use logical URIs and include size and SHA-256.
- Existing GF-6 browse behavior and tests must continue to pass unchanged.

---

## Product Evidence and Contracts

The real sample root used by integration tests is:

```text
D:\Lwy_project_space\RSImageProcess\otherfile\GF6_WFV_E107.6_N9.0_20260716_L1A1420724603
```

The package must identify:

```text
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-1.tiff
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-2.tiff
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-3.tiff
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.xml
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.rpb
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.nav
GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.til
```

The observed product facts to freeze in tests are:

- Satellite `GF6`, sensor `WFV`, level `LEVEL1A`, product ID `1420724603`.
- Eight `UInt16` bands; wavelengths B1 450-520 nm, B2 520-590 nm, B3 630-690 nm, B4 770-890 nm, B5 690-730 nm, B6 730-770 nm, B7 400-450 nm, B8 590-630 nm.
- Three tile widths `13952`, `23952`, `12817`, common height `17801`, total mosaic width `50721`.
- XML calibration arrays `AGain=0.0603,0.0485,0.0456,0.0298,0.05,0.0441,0.0677,0.0523` and zero `AOffset` values.
- XML formula `L(W.m-2.sr-1.um-1)=AGain*DN+AOffset`.
- XML footprint corners around `103.524703E-111.724085E, 6.670334N-10.753472N` and UTM zone 48 metadata.
- Each tile contains RPC metadata in its sidecar RPB; TIL defines tile order and column offsets.

## Target File Structure

| File | Responsibility |
| --- | --- |
| `rs-algorithm-worker/src/rs_algorithm_worker/models_gf6.py` | Immutable GF6 evidence, tile, calibration, RPC, and output metadata models. |
| `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py` | Strict package inspection, metadata parsing, candidate and plan construction. |
| `rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py` | Stage orchestration and artifact response generation. |
| `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_mosaic.py` | Windowed TIL-driven tile reads and mosaic iteration. |
| `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_rpc.py` | RPC metadata parsing and no-DEM nominal grid construction. |
| `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_raster_io.py` | Block iteration, dtype/NoData conversion, and output metadata helpers. |
| `rs-algorithm-worker/src/rs_algorithm_worker/registry.py` | Explicit GF6 L1A routing for inspect, build-plan, and execute-stage. |
| `rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py` | GF6 L1A capability declarations. |
| `rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py` | Synthetic package validation and plan tests. |
| `rs-algorithm-worker/tests/test_gf6_wfv_mosaic.py` | Tile order, offsets, overlap, and windowing tests. |
| `rs-algorithm-worker/tests/test_gf6_wfv_calibration.py` | Per-band XML calibration tests. |
| `rs-algorithm-worker/tests/test_gf6_wfv_rpc.py` | RPC parse, nominal grid, bounds, and failure tests. |
| `rs-algorithm-worker/tests/test_gf6_wfv_pipeline.py` | Stage contracts, optional branches, and artifact tests. |
| `rs-algorithm-worker/tests/test_gf6_wfv_real_sample.py` | Environment-controlled real product checks. |
| `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProductProfile.java` | Java profile and capability contract changes. |
| `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/StageInputResolver.java` | Java declared stage-input resolution. |
| `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java` | Java worker envelope and stage contract encoding. |
| `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskDetailView.java` | Java API-safe task and artifact response model. |
| `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java` | Java plan contract tests. |
| `rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.ts` | Frontend upload slots, parameter snapshot, and validation. |
| `rsimageprocess-view/src/features/multisource/useProcessingTask.ts` | Existing task state machine integration for the new profile. |

---

## Task 1: Freeze the GF6 L1A Evidence Model

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/models_gf6.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py`
- Test fixture helper: `rs-algorithm-worker/tests/conftest.py`

**Interfaces:**
- Produces `Gf6TileSpec`, `Gf6CalibrationSpec`, `Gf6RpcSpec`, and `Gf6ProductEvidence` dataclasses consumed by every later GF6 task.
- `Gf6ProductEvidence.package_id`, `.product_id`, `.tiles`, `.xml_path`, `.rpc_path`, `.nav_path`, `.til_path`, `.bands`, `.footprint`, `.calibration`, and `.metadata_snapshot` are immutable.

- [ ] **Step 1: Write failing model tests.** Assert eight unique bands, three tile specs, positive dimensions, finite calibration coefficients, closed footprint, and rejection of mismatched lengths.
- [ ] **Step 2: Run the focused test.** Run `..\\.venv\\Scripts\\python.exe -m pytest tests/test_gf6_wfv_l1a_adapter.py -q`; expect collection failure because the models do not exist.
- [ ] **Step 3: Implement immutable models.** Validate `UInt16` source assumptions, positive dimensions, finite numeric values, tile offsets, and the fixed `NOMINAL_RPC_NO_DEM` status field.
- [ ] **Step 4: Add a synthetic evidence factory.** Build three small raster fixtures and XML/TIL/RPB/NAV files under pytest `tmp_path`, keeping all paths outside Git.
- [ ] **Step 5: Run the focused model tests.** Confirm all model tests pass and malformed evidence is rejected with stable `GF6_EVIDENCE_INVALID` messages.
- [ ] **Step 6: Commit.** `git add rs-algorithm-worker/src/rs_algorithm_worker/models_gf6.py rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py rs-algorithm-worker/tests/conftest.py && git commit -m "feat: add GF6 L1A evidence models"`

## Task 2: Implement Strict Product Inspection and Profile Routing

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py`

**Interfaces:**
- `inspect_manifest(manifest: dict) -> list[dict]` returns exactly one `GF-6-WFV-L1A/v1` candidate for the supplied package.
- `build_plan(product: dict, parameters: dict) -> dict` accepts the exact `gf6-wfv-l1a-v1` parameter schema and returns a versioned plan.
- Registry routing chooses this adapter from explicit product evidence and never from the old browse roles alone.

- [ ] **Step 1: Write failing inspection tests.** Cover the real package shape, missing tile, duplicate tile, bad TIL offset, wrong satellite/sensor/level, incomplete AGain list, invalid RPB, and accidental routing to `gf6_wfv_browse`.
- [ ] **Step 2: Run the tests to verify failure.** Run `..\\.venv\\Scripts\\python.exe -m pytest tests/test_gf6_wfv_l1a_adapter.py -q`; expect missing-module or unsupported-profile failures.
- [ ] **Step 3: Implement XML/TIL/RPB/NAV/SHP parsing.** Use `xml.etree.ElementTree` for XML, line-oriented parsing for TIL/RPB, and rasterio/GDAL metadata APIs for TIFF dimensions and band types. Do not infer missing fields from filenames.
- [ ] **Step 4: Implement evidence validation.** Require all three TIFFs, XML, RPB, NAV, and TIL; verify product identity, exact tile geometry, 8 bands, finite RPC coefficients, and footprint consistency.
- [ ] **Step 5: Add explicit registry branch.** Detect GF6 L1A from the validated candidate/product code, route `inspect`, `build-plan`, and `execute-stage` to the new adapter/pipeline, and leave browse routing unchanged.
- [ ] **Step 6: Run adapter and existing GF6 tests.** Run `pytest tests/test_gf6_wfv_l1a_adapter.py tests/test_gf6_wfv_browse.py -q`; expect all tests to pass.
- [ ] **Step 7: Commit.** `git add rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py rs-algorithm-worker/src/rs_algorithm_worker/registry.py rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py && git commit -m "feat: recognize GF6 WFV L1A packages"`

## Task 3: Build the Windowed TIL Mosaic Reader

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_mosaic.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_raster_io.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_mosaic.py`

**Interfaces:**
- `iter_gf6_mosaic_windows(evidence: Gf6ProductEvidence, window_size: tuple[int, int]) -> Iterator[MosaicWindow]` yields output windows in row-major order.
- `read_gf6_window(evidence, window: MosaicWindow, bands: Sequence[int]) -> np.ndarray` returns `(band, rows, cols)` `UInt16` data for one output window.
- `write_gf6_mosaic(evidence, destination: Path, profile: RasterProfile) -> WriteResult` writes a tiled source-DN GeoTIFF/COG without whole-scene allocation.

- [ ] **Step 1: Write failing synthetic mosaic tests.** Use three tiny tiles with unique constants and TIL offsets; assert output width, tile order, overlap policy, and band order.
- [ ] **Step 2: Run the focused mosaic tests.** Run `pytest tests/test_gf6_wfv_mosaic.py -q`; expect missing operator failures.
- [ ] **Step 3: Implement TIL window mapping.** Convert output windows to intersecting tile windows using explicit `ULColOffset`/`ULRowOffset`; reject gaps, negative offsets, and inconsistent extents.
- [ ] **Step 4: Implement rasterio window reads.** Open one tile at a time, read only the intersecting block, copy into a preallocated output window, and keep the source dtype until calibration.
- [ ] **Step 5: Define overlap handling.** Treat the two-pixel TIL overlap as a declared duplicate region; retain the left tile for columns before its boundary and the right tile after the boundary, and test the exact boundary columns.
- [ ] **Step 6: Add block-size and memory tests.** Monkeypatch tile reads to record window sizes and assert no read exceeds the configured block and no array has full-scene shape.
- [ ] **Step 7: Run tests and commit.** `pytest tests/test_gf6_wfv_mosaic.py -q` then commit with `git commit -m "feat: add windowed GF6 tile mosaic reader"`.

## Task 4: Add XML-Based Radiometric Calibration

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/operators/calibration.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_calibration.py`

**Interfaces:**
- `calibrate_gf6_window(dn: np.ndarray, calibration: Gf6CalibrationSpec, fill_value: int | None) -> np.ndarray` returns Float32 radiance with shape `(band, rows, cols)`.
- `build_gf6_calibration_summary(evidence) -> dict` returns formula, units, coefficients, input/output dtype, and invalid-value policy.

- [ ] **Step 1: Write failing calibration tests.** Assert each of eight bands applies its own gain, zero offset is preserved, integer fill values become NaN, finite input remains finite, and coefficient count mismatches fail.
- [ ] **Step 2: Run the focused tests.** Run `pytest tests/test_gf6_wfv_calibration.py -q`; expect failure until the GF6 wrapper exists.
- [ ] **Step 3: Implement the window calibration wrapper.** Reuse `calibrate_linear` per band, convert to Float32, and apply only product-declared invalid values; do not accept caller coefficients.
- [ ] **Step 4: Implement calibration summary metadata.** Include `formulaVersion="xml-again-aoffset-v1"`, `unit="W.m-2.sr-1.um-1"`, eight wavelength ranges, gains, offsets, and `fillPolicy`.
- [ ] **Step 5: Run calibration and generic operator tests.** Confirm no regression in existing calibration tests.
- [ ] **Step 6: Commit.** `git add rs-algorithm-worker/src/rs_algorithm_worker/operators/calibration.py rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py rs-algorithm-worker/tests/test_gf6_wfv_calibration.py && git commit -m "feat: calibrate GF6 WFV L1A radiance"`

## Task 5: Implement No-DEM RPC Nominal Georeferencing

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_rpc.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_rpc.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/models_gf6.py`

**Interfaces:**
- `parse_gf6_rpc(path: Path) -> Gf6RpcSpec` parses the 20-term numerator/denominator coefficients and offsets/scales.
- `build_nominal_rpc_grid(evidence, target_crs: str, resolution: float, height_m: float) -> NominalRpcGrid` returns target transform, dimensions, sampled lon/lat bounds, and status `NOMINAL_RPC_NO_DEM`.
- `rpc_grid_metadata(grid: NominalRpcGrid) -> dict` serializes status and fixed-height assumptions.

- [ ] **Step 1: Write failing RPC tests.** Test valid RPB parsing, non-finite coefficients, denominator singularity, invalid CRS/resolution, fixed-height effect, and product footprint containment.
- [ ] **Step 2: Run the focused tests.** Run `pytest tests/test_gf6_wfv_rpc.py -q`; expect missing RPC operator failures.
- [ ] **Step 3: Implement RPC parsing.** Normalize RPB keys to rasterio RPC metadata names and preserve source offsets/scales and coefficient order exactly.
- [ ] **Step 4: Implement nominal grid sampling.** Sample tile/mosaic corners and row/column checkpoints at `height_m`, transform WGS84 to the requested CRS with pyproj, and derive a north-up target grid.
- [ ] **Step 5: Validate grid geometry.** Reject non-finite coordinates, inverted extents, impossible dimensions, and grids outside the XML/SHP footprint tolerance.
- [ ] **Step 6: Mark the limitation in metadata.** Serialize `navigationStatus="NOMINAL_RPC_NO_DEM"`, `heightMode="CONSTANT"`, `heightMeters`, and `accuracyStatus="UNVALIDATED"`.
- [ ] **Step 7: Run RPC tests and commit.** `git commit -m "feat: add GF6 no-DEM RPC nominal grid"`.

## Task 6: Compose the Standard COG and Crop Stages

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/operators/gf6_raster_io.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_pipeline.py`

**Interfaces:**
- `execute_gf6_stage(stage_code: str, inputs: Sequence[Path], parameters: dict, output_dir: Path) -> Gf6StageResult` dispatches only declared GF6 stage keys.
- Stage keys are `INSPECT_GF6_WFV_L1A`, `MOSAIC_GF6_TILES`, `CALIBRATE_GF6_WFV`, `BUILD_GF6_RPC_GRID`, `ALIGN_GF6_BANDS`, `CROP_GF6_RASTER`, and `WRITE_GF6_STANDARD_COG`.
- `Gf6StageResult.artifacts` contains `{type, uri, sizeBytes, checksum}` records only.

- [ ] **Step 1: Write failing stage contract tests.** Assert stage ordering, declared input roles, standard output CRS/transform/NoData, non-intersecting ROI rejection, and logical URI safety.
- [ ] **Step 2: Run the focused pipeline tests.** Run `pytest tests/test_gf6_wfv_pipeline.py -q`; expect missing stage execution.
- [ ] **Step 3: Implement stage dispatch.** Reopen and validate only manifest-authorized files; reject unknown stage keys and undeclared extra inputs.
- [ ] **Step 4: Implement `ALIGN_GF6_BANDS`.** Compare band dimensions and grid transforms; return `SAME_GRID_CONFIRMED` for the supplied same-grid bands and use existing `align_to_reference` only when an explicit target grid differs.
- [ ] **Step 5: Implement crop.** Convert WGS84 ROI through the nominal grid, intersect with the raster extent, and use `crop_grid`/window reads; return a stable `ROI_DOES_NOT_INTERSECT` failure.
- [ ] **Step 6: Implement standard COG writing.** Reuse the existing atomic COG writer, set Float32 radiance, eight band descriptions, units, NoData, transform, CRS, tiling, and metadata tags.
- [ ] **Step 7: Compute artifact checksums.** Hash the final bytes after atomic write and return logical URIs under the current stage output URI.
- [ ] **Step 8: Run focused and common COG tests, then commit.** `git commit -m "feat: write GF6 standard calibrated COG"`.

## Task 7: Add Optional Features, Fusion, and Saliency Branches

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/gf6_wfv_pipeline.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py`
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_pipeline.py` (extend)

**Interfaces:**
- `EXTRACT_GF6_FEATURES` produces `GF6_FEATURES_COG` and summary metadata using `statistics-v1`, `gradient-v1`, `local-variance-v1`, and explicitly requested band difference/ratio metrics.
- `FUSE_GF6_BANDS` produces `GF6_RGB_FUSION_COG`, `GF6_RGB_FUSION_JPEG`, and `GF6_FUSION_SUMMARY_JSON`.
- `DETECT_GF6_SALIENCY` produces `GF6_SALIENCY_SCORE_COG`, `GF6_SALIENCY_MASK_COG`, and `GF6_SALIENCY_SUMMARY_JSON`.

- [ ] **Step 1: Write failing optional-stage tests.** Cover selected-band dependencies, valid B3/B2/B1 true color, valid B4/B3/B2 NIR false color, percentile determinism, odd saliency windows, threshold bounds, and standard COG immutability.
- [ ] **Step 2: Run tests to verify missing branches.** Run the optional tests and confirm the new stages are not yet executable.
- [ ] **Step 3: Implement feature extraction.** Process calibrated COG windows, use existing common feature operators, write independent bands, and attach method/version and source-band metadata.
- [ ] **Step 4: Implement fusion.** Percentile-stretch only selected radiance bands, preserve NoData, write deterministic RGB COG/JPEG, and record the exact R/G/B mapping.
- [ ] **Step 5: Implement saliency.** Compute local contrast from a selected band or feature, normalize score to `[0,1]`, threshold to a mask, and mark interpretation `CANDIDATE_ONLY`.
- [ ] **Step 6: Register capabilities.** Add only `features/statistics-v1`, `features/gradient-v1`, `features/local-variance-v1`, `fusion/percentile-rgb-v1`, and `saliency/contrast-v1` for `GF-6-WFV-L1A`; do not register calibration as a user-overridable capability.
- [ ] **Step 7: Run optional tests and commit.** `git commit -m "feat: add GF6 feature fusion and saliency branches"`.

## Task 8: Freeze the GF6 L1A Plan and Worker Envelope

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/gf6_wfv_l1a.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/registry.py`
- Modify: `rs-algorithm-worker/tests/test_gf6_wfv_l1a_adapter.py`

**Interfaces:**
- Public profile: `productCode="GF-6-WFV-L1A"`, `profileVersion="v1"`.
- Public parameter snapshot: `schemaVersion="gf6-wfv-l1a-v1"`, `targetCrs`, `resolution`, `rpcHeightMode`, `rpcHeight`, `roi`, `bands`, `features`, `fusion`, and `saliency`.
- Plan stages and dependencies:

```text
INSPECT_GF6_WFV_L1A
  -> MOSAIC_GF6_TILES
  -> CALIBRATE_GF6_WFV
  -> BUILD_GF6_RPC_GRID
  -> ALIGN_GF6_BANDS
  -> CROP_GF6_RASTER
  -> WRITE_GF6_STANDARD_COG
       -> EXTRACT_GF6_FEATURES (optional)
       -> FUSE_GF6_BANDS (optional)
       -> DETECT_GF6_SALIENCY (optional)
```

- [ ] **Step 1: Write failing parameter and DAG tests.** Reject unknown fields, invalid bands, non-positive resolution, invalid ROI, non-constant RPC height mode, missing fusion bands, unselected saliency source, and unsupported operation names.
- [ ] **Step 2: Run the focused contract tests.** Confirm parameter validation and DAG generation fail before implementation.
- [ ] **Step 3: Implement strict parser and plan builder.** Return normalized immutable parameters and explicit `dependsOnStageKeys`; include optional stages only when enabled.
- [ ] **Step 4: Add operation boundary checks.** Reject `RADIOMETRIC_CALIBRATION` as a caller-selected operation while still executing calibration as a fixed product stage; reject atmospheric correction, arbitrary spectral indices, terrain correction, and model-based classification.
- [ ] **Step 5: Run adapter, registry, and contract tests and commit.** `git commit -m "feat: declare GF6 WFV L1A processing DAG"`.

## Task 9: Add Real-Sample Acceptance and External GDAL Checks

**Files:**
- Create: `rs-algorithm-worker/tests/test_gf6_wfv_real_sample.py`
- Modify: `rs-algorithm-worker/tests/conftest.py`
- Create: `docs/validation/gf6-wfv-l1a-acceptance.md`

**Interfaces:**
- Environment variable: `MULTISOURCE_GF6_WFV_L1A_SAMPLE_ROOT`.
- Test helper: `inspect_gf6_real_sample(root: Path) -> dict`.
- Acceptance report contains product ID, dimensions, bands, calibration summary, RPC status, output hashes, and no local paths.

- [ ] **Step 1: Write the environment-controlled real-sample test.** Skip only when the variable is unset; fail when it is set but the package is invalid.
- [ ] **Step 2: Run preflight against the supplied directory.** Assert one candidate, total size `50721 x 17801`, eight `UInt16` source bands, three tiles, and XML product ID `1420724603`.
- [ ] **Step 3: Run a bounded window execution.** Process a small ROI/window through calibration, nominal RPC grid, and one standard COG so CI does not need to write the entire 13.5 GB scene.
- [ ] **Step 4: Run the full-scene streaming path locally.** Execute the full mosaic/COG pipeline with a temporary output root and record peak memory and elapsed time.
- [ ] **Step 5: Validate outputs with GDAL.** Run `D:\\OSGeo4W\\apps\\gdal-dev\\bin\\gdalinfo.exe -json` and assert CRS, transform, dimensions, eight bands, NoData, and band descriptions.
- [ ] **Step 6: Compare the RGB fusion against the supplied browse JPG.** Check orientation, footprint ordering, and broad visual correspondence; record this as a visual sanity check, not an accuracy claim.
- [ ] **Step 7: Write the acceptance report and commit tests/docs.** Exclude physical input/output paths from the report.

## Task 10: Integrate Java Authorization, Persistence, and API Contracts

**Files:**
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProductProfile.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/ProcessingPlan.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/StageInputResolver.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/WorkerContractCodec.java`
- Modify: `htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/model/TaskDetailView.java`
- Modify: `htht-modules/htht-multisource/src/test/java/com/htht/multisource/domain/model/ProcessingPlanValidatorTest.java`
- Create: `script/sql/migrations/V20260828_01__seed_gf6_wfv_l1a_profile.sql`
- Modify: `htht-modules/htht-multisource/README.md` with runtime parameters.

**Interfaces:**
- Java product key is `GF-6-WFV-L1A/v1`; it is distinct from `GF-6-WFV-BROWSE/v1`.
- Stage input roles are declared raw package assets; downstream optional stages consume only declared FINAL standard artifacts.
- Final artifacts include `GF6_STANDARD_COG`, `GF6_CALIBRATION_SUMMARY_JSON`, optional feature/fusion/saliency outputs, and no raw input files.

- [ ] **Step 1: Write failing Java contract tests.** Cover profile registration, exact stage input/output declarations, product-specific parameter snapshot, task authorization, output promotion, and rejection of undeclared physical paths.
- [ ] **Step 2: Run focused Maven tests.** Run `mvn -pl htht-modules/htht-multisource,htht-admin -am -Dtest=*Gf6* test`; expect missing profile/seed/DTO behavior.
- [ ] **Step 3: Add profile and capability seed.** Publish `GF-6-WFV-L1A/v1` and only the reviewed fixed/optional capabilities; leave browse seed rows unchanged.
- [ ] **Step 4: Add stage contracts and input resolver bindings.** Map each stage to declared raw files or upstream artifacts and preserve task/execution isolation.
- [ ] **Step 5: Add API response fields.** Expose stage code/status, error code, artifact type/URI/checksum/size/retention/downloadable without local paths or manifest content.
- [ ] **Step 6: Add authorization and promotion tests.** Verify final COGs can be downloaded only by the owning task and intermediate work products cannot be downloaded.
- [ ] **Step 7: Run focused and multisource regressions and commit.** `git commit -m "feat: integrate GF6 L1A Java task contracts"`.

## Task 11: Add the GF6 L1A Frontend Template and Task Flow

**Files:**
- Create: `rsimageprocess-view/src/features/multisource/gf6WfvL1aTemplate.ts`
- Modify: `rsimageprocess-view/src/features/multisource/productTemplates.ts`
- Modify: `rsimageprocess-view/src/features/multisource/useProcessingTask.ts`
- Modify: `rsimageprocess-view/src/components/LeftPanel/MultiSourceSatelliteProcess.vue`
- Create/modify: frontend API type module following existing multisource API layout.

**Interfaces:**
- Upload roles: `MAIN_TIFF_TILE_1`, `MAIN_TIFF_TILE_2`, `MAIN_TIFF_TILE_3`, `METADATA_XML`, `RPC_RPB`, `NAV`, `TILE_INDEX_TIL`, optional `FOOTPRINT_SHP/DBF/SHX`.
- Snapshot builder: `buildGf6WfvL1aSnapshot(form) -> string`.
- Form defaults: target CRS `EPSG:4326`, positive resolution, constant RPC height `0`, all eight bands, optional branches disabled.

- [ ] **Step 1: Write failing frontend type/validation tests or pure module assertions.** Cover required roles, extension validation, common basename, optional SHP sidecars, positive resolution, selected fusion bands, and saliency dependencies.
- [ ] **Step 2: Run `pnpm typecheck` to establish the baseline.** Record unrelated existing errors separately.
- [ ] **Step 3: Implement the template and snapshot builder.** Keep role mapping and validation outside the Vue component; never expose physical paths or Worker operation names.
- [ ] **Step 4: Add parameter controls.** Provide band checklist, target CRS selection, resolution, constant height, ROI, feature metrics, fusion RGB selection, and saliency source/window/threshold.
- [ ] **Step 5: Connect the existing task state machine.** Use preflight, create, start, progress, cancel, retry, and final artifact download; stop polling on terminal state, reset, replacement, or unmount.
- [ ] **Step 6: Render limitations.** Always show `NOMINAL_RPC_NO_DEM` and `CANDIDATE_ONLY` labels for relevant outputs; do not call the result an orthorectified or scientifically validated product.
- [ ] **Step 7: Run `pnpm typecheck` and `pnpm build`, then commit.** `git commit -m "feat: add GF6 WFV L1A frontend workflow"`.

## Task 12: End-to-End Release Verification

**Files:**
- Modify: `docs/deployment/multisource-backend-deployment.md`
- Modify: `JavaRSproject/README.md` or the existing worker deployment README.
- Modify: `docs/validation/gf6-wfv-l1a-acceptance.md`

- [ ] **Step 1: Run the Python focused suite.** `..\\.venv\\Scripts\\python.exe -m pytest tests/test_gf6_wfv_l1a_adapter.py tests/test_gf6_wfv_mosaic.py tests/test_gf6_wfv_calibration.py tests/test_gf6_wfv_rpc.py tests/test_gf6_wfv_pipeline.py -q`.
- [ ] **Step 2: Run the full Worker regression.** `..\\.venv\\Scripts\\python.exe -m pytest -q`; existing browse, HY-1C, HY-2D, FY-3H, and FY-4B tests must remain green.
- [ ] **Step 3: Run the Java regression.** `mvn -pl htht-modules/htht-multisource,htht-admin -am test`.
- [ ] **Step 4: Run frontend gates.** From `rsimageprocess-view`, run `pnpm typecheck` and `pnpm build`.
- [ ] **Step 5: Execute one real end-to-end task.** Upload the current package, preflight one candidate, create/start one task, poll to terminal state, and download final COG/JSON/optional outputs.
- [ ] **Step 6: Inspect the ZIP and browser responses.** Confirm no raw TIFF, local path, Worker manifest, RPC coefficients, or stack trace is exposed; confirm only authorized final products are downloadable.
- [ ] **Step 7: Update deployment documentation.** Document `MULTISOURCE_GF6_WFV_L1A_SAMPLE_ROOT` for tests, worker memory/block-size settings, GDAL availability, and the no-DEM limitation.
- [ ] **Step 8: Perform final plan self-review and commit release evidence.** Search the plan and code for unfinished markers, unrestricted path joins, and claims of orthorectification accuracy; commit only the intended files.

## Completion Criteria

- `GF-6-WFV-L1A/v1` is recognized only from a complete, internally consistent package.
- Three TIFFs are streamed according to TIL into a `50721 x 17801` eight-band mosaic without whole-scene memory allocation.
- XML-based radiance calibration produces eight Float32 bands with recorded coefficients, units, and invalid-value policy.
- RPC processing produces a valid target grid and explicitly reports `NOMINAL_RPC_NO_DEM` and `UNVALIDATED` accuracy.
- Same-grid bands are confirmed without unnecessary resampling; any resampling uses the existing alignment operator and is recorded.
- ROI cropping rejects non-intersecting extents and writes valid tiled COG outputs with CRS, transform, NoData, descriptions, and checksums.
- Features, RGB/NIR fusion, and contrast saliency are independently versioned and cannot modify the standard COG.
- Java authorization, artifact promotion, API DTOs, frontend task state, and final downloads are integrated without exposing local paths.
- Existing `GF-6-WFV-BROWSE/v1` behavior and all current Worker/Java/frontend regressions remain passing.

## Explicit Non-Goals

- DEM-based orthorectification, terrain correction, or independently validated RPC accuracy.
- Official GF6 product certification, atmospheric correction, surface reflectance, cloud mask interpretation, target classification, or change detection.
- Automatic control-point fitting or sub-pixel band registration.
- Loading the entire scene into memory or silently downsampling the source.
- Reusing the old JPG/JGW/JSON browse adapter for this L1A package.

## Plan Self-Review

- **Spec coverage:** Product evidence, streaming I/O, calibration, RPC limitation, alignment, crop, COG, optional branches, Worker routing, Java authorization, frontend controls, and real-data acceptance each have a dedicated task.
- **Placeholder scan:** No unfinished markers or vague “handle appropriately” steps are used; each task names files, interfaces, tests, commands, and expected outcomes.
- **Type consistency:** `Gf6ProductEvidence`, `Gf6CalibrationSpec`, `Gf6RpcSpec`, `NominalRpcGrid`, `MosaicWindow`, `Gf6StageResult`, `build_gf6_plan`, and `execute_gf6_stage` are defined once and reused consistently.
- **Scope check:** The tasks form one dependent processing chain with independently testable deliverables; the old browse profile remains a separate compatibility boundary.
