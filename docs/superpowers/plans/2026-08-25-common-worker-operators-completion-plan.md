# Common Worker Operators Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the product-independent Python raster-processing foundation so HY-1C L2A, HY-1C L1B, and later optical products can reuse tested spatial operators and baseline algorithms without embedding product rules.

**Architecture:** Keep adapters responsible only for evidence, band mapping, and product policy. Operators accept `RasterData`, `SwathData`, explicit CRS/transform/NoData values, and caller-provided parameters; no operator may name a satellite, HDF dataset path, or scientific threshold. Baseline algorithms consume standardized aligned rasters, expose their method/version in results, and remain capability-gated.

**Tech Stack:** Python 3.11+, NumPy, SciPy, rasterio/GDAL, pyproj, h5py, pytest.

**Scope:** This continuation completes Worker common operators and baseline algorithm contracts only. It does not add a HY-1C L1B or L2A adapter, inspect a downloaded scene, define product calibration, choose a product target resolution, modify Java dispatch/artifact code, or alter current GF adapters.

**Current state:** `models.py`, HDF window reading, linear calibration, bit masking, simple nearest-neighbour gridding, and minimal feature/fusion/saliency baselines already exist. `reproject.py`, `align.py`, and `cog.py` do not exist; `crop.py` currently returns the entire input array instead of clipping pixels; raster dependencies are installed only in the local virtual environment and not declared in `pyproject.toml`.

---

## Public Contracts

All grid operators use a north-up `affine.Affine` transform and an explicit CRS string. A `RasterData` has either a two-dimensional value array and one `BandMetadata` item, or a band-first three-dimensional array and matching metadata. Operators preserve the band-first convention and preserve the source NoData value unless a documented output operation deliberately creates a new one.

```python
def reproject_raster(
    raster: RasterData,
    *,
    destination_crs: str,
    resolution: float | None = None,
    resampling: Resampling = Resampling.nearest,
) -> RasterData: ...

def align_to_reference(
    raster: RasterData,
    reference: RasterData,
    *,
    resampling: Resampling = Resampling.nearest,
) -> RasterData: ...

def crop_grid(raster: RasterData, *, roi: tuple[float, float, float, float]) -> RasterData: ...

def write_cog(raster: RasterData, output_path: Path, *, compression: str = "deflate") -> COGWriteResult: ...
```

`roi` is `(min_lon_or_x, min_lat_or_y, max_lon_or_x, max_lat_or_y)` in the raster CRS. It is not a WGS84 shortcut: callers must reproject an ROI before passing it to a non-WGS84 raster. All spatial functions reject a missing CRS/transform, blank/invalid CRS, non-finite bounds, non-positive requested resolution, unsupported resampling values, and non-intersecting crop bounds.

The COG writer writes only to its caller-authorized destination. It creates a sibling temporary file, validates the reopened raster, then atomically replaces the target. Its result contains the final path, byte size, SHA-256, band count, CRS, transform, bounds, NoData, and valid-pixel count. It must never return a local source path as a logical artifact URI.

The baseline algorithms are intentionally non-product-specific:

```python
FeatureParameters(metrics=("statistics", "gradient", "local_variance"), window_size=3)
FusionParameters(red="B3", green="B2", blue="B1", low_percentile=2.0, high_percentile=98.0)
SaliencyParameters(threshold=0.5, window_size=5)
```

The feature baseline reports per-band finite-pixel statistics and produces only explicitly requested gradients/local-variance rasters. Fusion requires an already aligned `BandSet` and performs display stretch only; it must not claim pan-sharpening. Saliency calculates NaN-aware local contrast and emits a normalized score plus a threshold mask; it remains `contrast-v1`, not a scientific classifier.

## Target File Structure

```text
rs-algorithm-worker/
  pyproject.toml
  src/rs_algorithm_worker/
    models.py                              # Tighten public raster validation for spatial operators
    capabilities.py                        # Common algorithm capability declarations
    operators/
      grid.py                              # Deterministic nearest swath binning
      crop.py                              # Grid crop with transformed output origin
      reproject.py                         # CRS-to-CRS raster reprojection
      align.py                             # Reproject a raster onto a reference grid
      cog.py                               # Atomic, validated Cloud Optimized GeoTIFF output
      spatial.py                           # Shared raster CRS/transform/band helpers
    algorithms/
      features.py                          # Statistics, gradients, local variance, explicit index support
      fusion.py                            # Aligned RGB/false-colour display fusion
      saliency.py                          # NaN-aware local-contrast baseline
  tests/
    test_operators_grid.py
    test_operators_spatial.py
    test_operators_cog.py
    test_algorithms_baseline.py
    test_capabilities_common.py
```

### Task 1: Declare and Verify the Common Raster Runtime

**Files:**
- Modify: `rs-algorithm-worker/pyproject.toml`
- Create: `rs-algorithm-worker/tests/test_runtime_dependencies.py`

- [ ] **Step 1: Write the failing import-contract test.**

```python
import importlib.metadata


def test_common_raster_runtime_dependencies_are_declared():
    requirements = importlib.metadata.requires("rs-algorithm-worker") or []
    normalized = "\n".join(requirements).lower()
    for package in ("numpy", "rasterio", "pyproj", "scipy"):
        assert package in normalized
```

- [ ] **Step 2: Run the test and verify the expected failure.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_runtime_dependencies.py -q`

Expected: FAIL because `numpy`, `rasterio`, `pyproj`, and `scipy` are absent from the project dependency metadata.

- [ ] **Step 3: Add bounded runtime dependencies.**

Update `[project].dependencies` to include:

```toml
"numpy>=1.26,<3",
"rasterio>=1.4,<2",
"pyproj>=3.6,<4",
"scipy>=1.14,<2",
```

Keep existing Pillow, h5py, and netCDF4 requirements. Do not add a product SDK or GDAL wheel pin: rasterio owns the Python/GDAL compatibility boundary.

- [ ] **Step 4: Reinstall the editable Worker and rerun the test.**

Run:

```powershell
cd rs-algorithm-worker
.\.venv\Scripts\python.exe -m pip install -e ".[test]"
.\.venv\Scripts\python.exe -m pytest tests\test_runtime_dependencies.py -q
```

Expected: PASS.

- [ ] **Step 5: Commit the reproducible runtime declaration.**

```powershell
git add rs-algorithm-worker/pyproject.toml rs-algorithm-worker/tests/test_runtime_dependencies.py
git commit -m "build: declare common raster runtime dependencies"
```

### Task 2: Add Shared Raster Preconditions and Correct Grid Cropping

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/spatial.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/operators/crop.py`
- Modify: `rs-algorithm-worker/tests/test_operators_spatial.py`

- [ ] **Step 1: Write failing crop and validation tests.**

```python
from affine import Affine
import numpy as np
import pytest

from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.operators.crop import crop_grid


def raster() -> RasterData:
    return RasterData(
        values=np.arange(16, dtype=np.float32).reshape(4, 4),
        crs="EPSG:4326",
        transform=Affine(1, 0, 120, 0, -1, 34),
        nodata=np.nan,
        band_metadata=(BandMetadata("B1"),),
    )


def test_crop_grid_returns_intersecting_pixels_and_window_transform():
    result = crop_grid(raster(), roi=(121, 31, 123, 33))
    assert result.values.shape == (2, 2)
    assert result.values.tolist() == [[5.0, 6.0], [9.0, 10.0]]
    assert result.transform == Affine(1, 0, 121, 0, -1, 33)


def test_crop_grid_rejects_raster_without_grid_metadata():
    with pytest.raises(ValueError, match="CRS and transform"):
        crop_grid(RasterData(np.ones((2, 2)), None, None, None, (BandMetadata("B1"),)), roi=(0, 0, 1, 1))
```

- [ ] **Step 2: Run the focused test and verify it fails for missing behavior.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py -q`

Expected: FAIL because `crop_grid` and spatial precondition helpers do not exist.

- [ ] **Step 3: Implement `spatial.py` and `crop_grid`.**

Implement `require_grid(raster)` to return validated `CRS` and `Affine` values after rejecting missing metadata. Implement `band_count`, `as_band_first`, and `restore_band_dimensions` helpers so every spatial operator handles two-dimensional and band-first three-dimensional rasters identically. In `crop_grid`, use `rasterio.windows.from_bounds`, intersect it with the full raster window, round offsets/lengths deterministically, read array slices, and compute the transform with `raster.transform * Affine.translation(column_offset, row_offset)`.

Keep existing `crop_raster` only as a deprecated bounds-intersection helper for any existing caller. Its result must not be used by new grid operators.

- [ ] **Step 4: Add a non-intersecting and multiband test, then run the focused suite.**

```python
def test_crop_grid_preserves_band_first_shape():
    source = RasterData(
        values=np.ones((2, 4, 4), dtype=np.float32), crs="EPSG:4326",
        transform=Affine(1, 0, 120, 0, -1, 34), nodata=-9999.0,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2")),
    )
    assert crop_grid(source, roi=(120, 30, 122, 32)).values.shape == (2, 2, 2)
```

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py -q`

Expected: PASS.

- [ ] **Step 5: Commit the shared spatial boundary.**

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/operators/spatial.py rs-algorithm-worker/src/rs_algorithm_worker/operators/crop.py rs-algorithm-worker/tests/test_operators_spatial.py
git commit -m "feat: add reusable raster grid cropping"
```

### Task 3: Add Reprojection and Grid Alignment

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/reproject.py`
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/align.py`
- Modify: `rs-algorithm-worker/tests/test_operators_spatial.py`

- [ ] **Step 1: Write failing reprojection and alignment tests.**

```python
from rasterio.enums import Resampling

from rs_algorithm_worker.operators.align import align_to_reference
from rs_algorithm_worker.operators.reproject import reproject_raster


def test_reproject_raster_changes_crs_and_retains_band_metadata():
    result = reproject_raster(raster(), destination_crs="EPSG:3857", resolution=100_000.0)
    assert result.crs == "EPSG:3857"
    assert result.values.ndim == 2
    assert result.band_metadata == raster().band_metadata
    assert result.transform is not None


def test_align_to_reference_uses_exact_reference_grid():
    reference = RasterData(
        values=np.zeros((3, 3), dtype=np.float32), crs="EPSG:4326",
        transform=Affine(0.5, 0, 120, 0, -0.5, 32), nodata=-9999.0,
        band_metadata=(BandMetadata("reference"),),
    )
    result = align_to_reference(raster(), reference, resampling=Resampling.nearest)
    assert result.values.shape == (3, 3)
    assert result.crs == reference.crs
    assert result.transform == reference.transform
```

- [ ] **Step 2: Run the test and confirm that imports fail.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py -q`

Expected: collection failure because `reproject.py` and `align.py` are absent.

- [ ] **Step 3: Implement reprojection with rasterio.**

Use `rasterio.warp.calculate_default_transform` with source bounds and optional resolution to obtain destination transform, width, and height. Allocate a float32 destination array using `np.nan` when source NoData is `None`, otherwise the declared NoData. Reproject each band through `rasterio.warp.reproject`, passing source/destination transforms, CRS values, NoData values, and the explicit `Resampling` enum. Return a `RasterData` with unmodified `BandMetadata`.

- [ ] **Step 4: Implement reference-grid alignment.**

Allocate dimensions from `reference.values.shape[-2:]`. Call `reproject` for every source band with the source grid and the reference CRS/transform/dimensions. Copy the reference grid metadata only for geometry: retain the source band metadata and source NoData. Reject a reference without a valid grid or a raster with no valid grid.

- [ ] **Step 5: Add a NoData preservation test and run the focused suite.**

```python
def test_alignment_preserves_declared_nodata_for_uncovered_cells():
    reference = RasterData(
        values=np.zeros((4, 4), dtype=np.float32), crs="EPSG:4326",
        transform=Affine(1, 0, 118, 0, -1, 35), nodata=-9999.0,
        band_metadata=(BandMetadata("reference"),),
    )
    result = align_to_reference(raster(), reference)
    assert result.nodata == raster().nodata
    assert np.isnan(result.values).any()
```

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_spatial.py -q`

Expected: PASS.

- [ ] **Step 6: Commit spatial resampling.**

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/operators/reproject.py rs-algorithm-worker/src/rs_algorithm_worker/operators/align.py rs-algorithm-worker/tests/test_operators_spatial.py
git commit -m "feat: add reusable raster reprojection and alignment"
```

### Task 4: Write and Validate Atomic Cloud Optimized GeoTIFFs

**Files:**
- Create: `rs-algorithm-worker/src/rs_algorithm_worker/operators/cog.py`
- Create: `rs-algorithm-worker/tests/test_operators_cog.py`

- [ ] **Step 1: Write the failing atomic COG test.**

```python
from affine import Affine
import rasterio

from rs_algorithm_worker.operators.cog import write_cog


def test_write_cog_writes_readable_tiled_raster_with_metadata(tmp_path):
    source = RasterData(
        values=np.arange(32 * 32, dtype=np.float32).reshape(32, 32),
        crs="EPSG:4326", transform=Affine(0.01, 0, 120, 0, -0.01, 31),
        nodata=-9999.0, band_metadata=(BandMetadata("B1", 460.0, "1/sr"),),
    )
    result = write_cog(source, tmp_path / "standard.tif")
    with rasterio.open(result.path) as dataset:
        assert dataset.driver == "GTiff"
        assert dataset.crs.to_string() == "EPSG:4326"
        assert dataset.count == 1
        assert dataset.descriptions == ("B1",)
        assert dataset.nodata == -9999.0
        assert dataset.is_tiled
    assert result.sha256
    assert result.valid_pixel_count == 1024
```

- [ ] **Step 2: Run the test and verify the expected collection failure.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_cog.py -q`

Expected: FAIL because module `operators.cog` does not exist.

- [ ] **Step 3: Implement `COGWriteResult` and `write_cog`.**

Validate grid metadata through `require_grid`. Create a temporary target with `tempfile.NamedTemporaryFile(delete=False, dir=output_path.parent, suffix=".tif")`, close it before rasterio opens it, and remove it if a write fails. Write band-first data through the GeoTIFF driver with `tiled=True`, `blockxsize=16`, `blockysize=16`, `compress="deflate"`, predictor selected from the dtype, and `BIGTIFF="IF_SAFER"`. Set each band description and unit tag. Build nearest overviews when both dimensions are at least 32 pixels, reopen the temporary file, validate its CRS, transform, count, dimensions, tiling, NoData, and band descriptions, then use `os.replace` for the final atomic promotion.

Compute SHA-256 by reading the final destination in binary chunks. Count valid pixels with `np.isfinite` and exclude a finite declared NoData value.

- [ ] **Step 4: Add failure-cleanup and multi-band tests.**

```python
def test_write_cog_rejects_target_outside_existing_parent(tmp_path):
    with pytest.raises(ValueError, match="parent directory"):
        write_cog(source, tmp_path / "missing" / "standard.tif")


def test_write_cog_preserves_band_order(tmp_path):
    source = RasterData(
        values=np.ones((2, 32, 32), dtype=np.float32), crs="EPSG:4326",
        transform=Affine(1, 0, 0, 0, -1, 32), nodata=None,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2")),
    )
    with rasterio.open(write_cog(source, tmp_path / "two-band.tif").path) as dataset:
        assert dataset.descriptions == ("B1", "B2")
```

- [ ] **Step 5: Run the COG test suite and commit.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_operators_cog.py -q`

Expected: PASS.

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/operators/cog.py rs-algorithm-worker/tests/test_operators_cog.py
git commit -m "feat: add atomic common COG writer"
```

### Task 5: Complete Product-Neutral Feature, Fusion, and Saliency Baselines

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/features.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/fusion.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/saliency.py`
- Modify: `rs-algorithm-worker/tests/test_algorithms_baseline.py`

- [ ] **Step 1: Write failing behavior tests.**

```python
def test_feature_baseline_reports_gradient_and_local_variance_when_requested():
    result = extract_features(_bands().raster, FeatureParameters(metrics=("gradient", "local_variance"), window_size=3))
    assert set(result.rasters) == {"gradient:B1", "gradient:B2", "gradient:B3", "local_variance:B1", "local_variance:B2", "local_variance:B3"}
    assert result.rasters["gradient:B1"].values.shape == (2, 2)


def test_fusion_rejects_a_bandset_with_misaligned_band_grids():
    with pytest.raises(ValueError, match="aligned"):
        fuse_bands(misaligned_bands(), FusionParameters(red="B3", green="B2", blue="B1"))


def test_saliency_uses_local_contrast_not_only_scene_mean():
    raster = one_band([[0, 0, 0], [0, 1, 0], [0, 0, 0]])
    result = detect_saliency(raster, SaliencyParameters(threshold=0.5, window_size=3))
    assert result.mask[1, 1]
    assert not result.mask[0, 0]
```

- [ ] **Step 2: Run the algorithm test and verify it fails.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_algorithms_baseline.py -q`

Expected: FAIL because feature rasters, alignment validation, and local-window saliency are not implemented.

- [ ] **Step 3: Extend feature extraction without changing default statistics behavior.**

Add `window_size: int = 3` to `FeatureParameters` and reject even or smaller-than-three window sizes when local variance is requested. Add `rasters: Mapping[str, RasterData]` to `FeatureSet` with an empty default. For requested `gradient`, use `np.gradient` per finite-filled band and restore invalid pixels to NaN. For requested `local_variance`, use `scipy.ndimage.uniform_filter` over both finite-value and finite-count arrays to calculate `E[x^2] - E[x]^2`, setting zero-count cells to NaN. Keep statistics output backward compatible.

- [ ] **Step 4: Make fusion and saliency geometry-aware.**

Keep `BandSet` as a single aligned band-first `RasterData`; add validation that it has a CRS and transform whenever fusion produces georeferenced output. Preserve the current percentile stretch and explicit RGB mapping, but convert non-finite source pixels to zero only after calculating finite percentiles. Add an explicit helper for callers to align separate rasters via `align_to_reference`; fusion must reject unaligned inputs rather than resample silently.

For saliency, replace global-mean difference with absolute difference from a NaN-aware local mean derived from `uniform_filter`. Normalize by the maximum finite contrast. Preserve a zero score and all-false mask for a constant or all-invalid image. Name the result version `contrast-v1`.

- [ ] **Step 5: Add determinism and invalid-input tests, then run the suite.**

```python
def test_saliency_returns_deterministic_zero_score_for_constant_image():
    source = one_band([[5, 5], [5, 5]])
    first = detect_saliency(source, SaliencyParameters(threshold=0.1, window_size=3))
    second = detect_saliency(source, SaliencyParameters(threshold=0.1, window_size=3))
    np.testing.assert_array_equal(first.score.values, second.score.values)
    assert not first.mask.any()
```

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_algorithms_baseline.py -q`

Expected: PASS.

- [ ] **Step 6: Commit the completed baseline behavior.**

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/algorithms rs-algorithm-worker/tests/test_algorithms_baseline.py
git commit -m "feat: complete common raster analysis baselines"
```

### Task 6: Register Common Capability Declarations and Run the Worker Regression

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py`
- Create: `rs-algorithm-worker/tests/test_capabilities_common.py`
- Modify: `rs-algorithm-worker/tests/test_operator_contracts.py`

- [ ] **Step 1: Write the failing capability tests.**

```python
from rs_algorithm_worker.capabilities import COMMON_CAPABILITIES, CapabilityStatus


def test_common_raster_capabilities_expose_supported_baselines():
    result = COMMON_CAPABILITIES.check(
        "fusion", "percentile-rgb-v1",
        ProductContext(profile="TEST/v1", data_shape="RASTER_GRID"),
        available_bands=("B1", "B2", "B3"),
    )
    assert result.status is CapabilityStatus.SUPPORTED


def test_common_fusion_requires_three_declared_input_bands():
    result = COMMON_CAPABILITIES.check(
        "fusion", "percentile-rgb-v1",
        ProductContext(profile="TEST/v1", data_shape="RASTER_GRID"),
        available_bands=("B1", "B2"),
    )
    assert result.status is CapabilityStatus.NOT_SUPPORTED
    assert "B3" in result.reason
```

- [ ] **Step 2: Run the test and confirm the missing registry declaration.**

Run: `cd rs-algorithm-worker; .\.venv\Scripts\python.exe -m pytest tests\test_capabilities_common.py -q`

Expected: FAIL because `COMMON_CAPABILITIES` is absent.

- [ ] **Step 3: Declare only reusable raster capabilities.**

Add `COMMON_CAPABILITIES = CapabilityRegistry((...))` with these exact entries:

```python
CapabilitySpec("features", "statistics-v1", frozenset({"RASTER_GRID", "SWATH_RASTER"}), ())
CapabilitySpec("fusion", "percentile-rgb-v1", frozenset({"RASTER_GRID"}), ("B1", "B2", "B3"))
CapabilitySpec("saliency", "contrast-v1", frozenset({"RASTER_GRID"}), ())
```

Do not declare HY-1C, HY-2D, FY-3H, or FY-4B support in this module. Product adapters later translate their reviewed band mapping to the generic prerequisites.

- [ ] **Step 4: Run focused and full Worker regressions.**

Run:

```powershell
cd rs-algorithm-worker
.\.venv\Scripts\python.exe -m pytest tests\test_models.py tests\test_operator_contracts.py tests\test_operators_hdf.py tests\test_operators_core.py tests\test_operators_grid.py tests\test_operators_spatial.py tests\test_operators_cog.py tests\test_algorithms_baseline.py tests\test_capabilities_common.py -q
.\.venv\Scripts\python.exe -m pytest -q
```

Expected: the focused public suite passes; existing product tests remain unchanged and any explicitly environment-gated real-sample test is skipped only when its environment variable is absent.

- [ ] **Step 5: Commit capability declarations.**

```powershell
git add rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py rs-algorithm-worker/tests/test_capabilities_common.py rs-algorithm-worker/tests/test_operator_contracts.py
git commit -m "feat: declare common raster algorithm capabilities"
```

## Completion Criteria

- The Worker dependency metadata installs every package required by public spatial and algorithm modules.
- Grid crop, reproject, and align functions are array-shape safe, preserve metadata, and reject missing grid context.
- The COG writer produces an atomically promoted, reopenable, tiled GeoTIFF with correct CRS, transform, band descriptions, NoData, and checksum metadata.
- Feature, fusion, and saliency baselines are deterministic and describe their limitations through explicit parameters, version fields, and capability checks.
- No public module imports a product adapter, uses a satellite-specific HDF path, or hard-codes a sensor resolution, calibration rule, quality-bit assignment, or acceptance threshold.
- All Worker tests pass before a product-specific L2A adapter plan begins.

## Plan Self-Review

- **Scope coverage:** covers all missing public Python functionality identified in the original plan: reproducible runtime dependencies, spatial operators, output writer, and baseline algorithm/capability completion. Product routing and scientific mapping are explicitly excluded.
- **TDD coverage:** every implementation task starts with a concrete failing test and a focused command, then specifies the minimal implementation and passing verification.
- **Consistency:** all new spatial APIs consume `RasterData`; alignment reuses the same `Affine`/CRS boundary; fusion is deliberately downstream of alignment instead of adding a hidden resampling path.
- **No inferred science:** every value in this plan is a generic implementation parameter or a test fixture value, not a HY-1C calibration, quality, grid, or validation rule.
