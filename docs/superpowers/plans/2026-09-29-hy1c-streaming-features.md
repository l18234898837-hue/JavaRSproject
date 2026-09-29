# HY-1C Streaming Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Process large HY-1C CZI feature rasters in bounded windows without full-scene feature allocation.

**Architecture:** The HY-1C feature stage will materialize the compressed intermediate science array into a request-local NPY file and read it via NumPy memory mapping. It will calculate each feature inside halo-aware tiles and write each result directly to a tiled raster before conversion to the existing COG contract.

**Tech Stack:** Python 3.11, NumPy, SciPy ndimage, Rasterio, pytest.

**Spec:** `docs/superpowers/specs/2026-09-29-hy1c-streaming-features-design.md`

## Global Constraints

- Preserve existing HY-1C feature artifact types, relative paths, metadata fields, and numerical semantics.
- Use existing project dependencies only.
- Keep working memory proportional to a fixed tile size and requested output bands, not full raster dimensions.
- Preserve user changes outside files listed below.

---

### Task 1: Halo-Aware Feature Tile Functions

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/algorithms/features.py`
- Test: `rs-algorithm-worker/tests/test_algorithms_baseline.py`

**Interfaces:**
- Produces: `feature_tile(values: np.ndarray, metrics: tuple[str, ...], window_size: int, core: tuple[slice, slice]) -> dict[str, np.ndarray]`.
- Consumes: a halo-including 2D float array and a core slice relative to that array.

- [ ] **Step 1: Write the failing test**

```python
def test_feature_tile_matches_full_image_features_across_a_block_boundary():
    values = np.arange(35, dtype=np.float32).reshape(5, 7)
    values[1, 3] = np.nan
    expected = extract_features(_single_band(values), FeatureParameters(("gradient", "local_variance"), 3))
    tile = feature_tile(values[:, 1:6], ("gradient", "local_variance"), 3, (slice(0, 5), slice(1, 5)))
    np.testing.assert_allclose(tile["gradient"], expected.rasters["gradient:B1"].values[:, 2:6], equal_nan=True)
    np.testing.assert_allclose(tile["local_variance"], expected.rasters["local_variance:B1"].values[:, 2:6], equal_nan=True)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_algorithms_baseline.py -k feature_tile_matches -q`

Expected: FAIL because `feature_tile` is not defined.

- [ ] **Step 3: Write minimal implementation**

```python
def feature_tile(values, metrics, window_size, core):
    result = {}
    if "gradient" in metrics:
        result["gradient"] = _gradient(values)[core]
    if "local_variance" in metrics:
        result["local_variance"] = _local_variance(values, window_size)[core]
    return result
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_algorithms_baseline.py -k feature_tile_matches -q`

Expected: PASS.

### Task 2: Stream HY-1C Feature Inputs and Outputs

**Files:**
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/l2a_pipeline.py`
- Modify: `rs-algorithm-worker/src/rs_algorithm_worker/operators/cog.py`
- Test: `rs-algorithm-worker/tests/test_hy1c_czi_l2a.py`

**Interfaces:**
- Consumes: the existing `CROPPED_CZI` NPZ artifact and HY-1C feature configuration.
- Produces: existing `FEATURE_COG` and `FEATURE_METADATA_JSON` artifacts.

- [ ] **Step 1: Write the failing test**

```python
def test_hy1c_features_stream_source_windows_and_match_small_fixture(tmp_path):
    source = _write_cropped_intermediate(tmp_path, values=_four_band_fixture())
    response = execute_l2a_staged_stage(_feature_payload(), _feature_manifest(source, tmp_path))
    with rasterio.open(_artifact_path(response, "FEATURE_COG")) as dataset:
        assert dataset.descriptions == (
            "gradient:B460", "local_variance:B460", "gradient:B560", "local_variance:B560",
            "gradient:B650", "local_variance:B650", "gradient:B825", "local_variance:B825",
        )
        np.testing.assert_allclose(dataset.read(1), _expected_gradient_b460(), equal_nan=True)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_hy1c_czi_l2a.py -k features_stream_source_windows -q`

Expected: FAIL because the staged feature path still reads the complete NPZ array and calls `_feature_artifacts`.

- [ ] **Step 3: Write minimal implementation**

```python
def _stream_czi_feature_artifacts(output_uri, output_dir, cropped_path, configuration):
    source = _materialize_npz_values_as_memmap(cropped_path, output_dir / "work")
    with _open_feature_cog(output_dir / "features" / "czi-features.tif", source, configuration) as writer:
        for window in _feature_windows(source.shape[-2:]):
            halo = _feature_halo(configuration)
            tile, core = _read_halo_window(source, window, halo)
            writer.write(_feature_tile_outputs(tile, core, configuration), window=window)
    return _feature_artifacts_from_written_cog(output_uri, output_dir, configuration)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_hy1c_czi_l2a.py -k features_stream_source_windows -q`

Expected: PASS.

### Task 3: Verify the HY-1C Regression and Output Contract

**Files:**
- Test: `rs-algorithm-worker/tests/test_algorithms_baseline.py`
- Test: `rs-algorithm-worker/tests/test_hy1c_czi_l2a.py`

- [ ] **Step 1: Add a bounded-read test**

```python
def test_hy1c_feature_streamer_never_reads_the_full_source_array(monkeypatch, tmp_path):
    source = _write_large_cropped_intermediate(tmp_path, shape=(4, 1024, 1024))
    reads = []
    monkeypatch.setattr(l2a_pipeline, "_read_feature_source_window", lambda array, window: reads.append(window) or array[window])
    execute_l2a_staged_stage(_feature_payload(), _feature_manifest(source, tmp_path))
    assert all(window[-2].stop - window[-2].start < 1024 for window in reads)
```

- [ ] **Step 2: Run test to verify it fails before the bounded reader is used**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_hy1c_czi_l2a.py -k never_reads_the_full_source_array -q`

Expected: FAIL before the streaming path calls the bounded reader.

- [ ] **Step 3: Implement only the bounded reads needed by the test**

Use the same `_read_feature_source_window` helper for every source band and tile. Do not call `np.asarray` on the full memory-mapped values member.

- [ ] **Step 4: Run complete relevant tests**

Run: `.venv\\Scripts\\python.exe -m pytest tests/test_algorithms_baseline.py tests/test_hy1c_czi_l2a.py -q`

Expected: PASS with existing warnings only.
