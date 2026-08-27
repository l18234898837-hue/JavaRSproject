from pathlib import Path

import numpy as np
import rasterio
from rasterio.transform import from_origin

from rs_algorithm_worker.gf6_processing import (
    calibrate_gf6,
    crop_gf6,
    detect_gf6_saliency,
    extract_gf6_features,
    fuse_gf6_bands,
    mosaic_gf6_tiles,
    rpc_resample_gf6,
)


def _tile(path: Path, value: int, width: int = 2, height: int = 2) -> None:
    with rasterio.open(path, "w", driver="GTiff", width=width, height=height, count=2, dtype="uint16", transform=from_origin(0, height, 1, 1)) as ds:
        ds.write(np.full((2, height, width), value, dtype=np.uint16))


def test_mosaic_uses_declared_offsets_and_preserves_bands(tmp_path):
    left, right = tmp_path / "a.tif", tmp_path / "b.tif"
    _tile(left, 10)
    _tile(right, 20)
    result = mosaic_gf6_tiles([(left, 0), (right, 2)], height=2, width=4, bands=2)
    assert result.shape == (2, 2, 4)
    assert np.all(result[:, :, :2] == 10)
    assert np.all(result[:, :, 2:] == 20)


def test_calibration_applies_per_band_gain_and_fill():
    dn = np.array([[[1, 65535]], [[2, 65535]]], dtype=np.uint16)
    result = calibrate_gf6(dn, gains=[2, 3], offsets=[1, -1], fill_value=65535)
    assert result.dtype == np.float32
    assert np.array_equal(result[:, :, 0], [[3], [5]])
    assert np.isnan(result[:, :, 1]).all()


def test_crop_gf6_updates_transform_and_rejects_non_intersection():
    values = np.arange(16, dtype=np.float32).reshape(1, 4, 4)
    cropped, transform = crop_gf6(values, from_origin(100, 104, 1, 1), (101, 101, 103, 103))
    assert cropped.shape == (1, 2, 2)
    assert transform.c == 101 and transform.f == 103


def test_rpc_resample_returns_target_grid_metadata():
    values = np.ones((1, 2, 2), dtype=np.float32)
    result = rpc_resample_gf6(values, source_crs="EPSG:4326", source_transform=from_origin(100, 2, 1, 1), target_crs="EPSG:3857", resolution=100000)
    assert result.values.shape[0] == 1
    assert result.crs == "EPSG:3857"
    assert result.navigation_status == "NOMINAL_RPC_NO_DEM"


def test_features_fusion_and_saliency_are_deterministic():
    values = np.stack([np.arange(16).reshape(4, 4), np.arange(16, 0, -1).reshape(4, 4)], axis=0).astype(np.float32)
    features = extract_gf6_features(values, metrics=("statistics", "gradient", "local_variance"))
    fusion = fuse_gf6_bands(values, rgb=(0, 1, 0))
    score, mask = detect_gf6_saliency(values[0], window_size=3, threshold=0.5)
    assert set(features) == {"statistics", "gradient", "local_variance"}
    assert fusion.shape == (3, 4, 4) and fusion.dtype == np.uint8
    assert score.shape == mask.shape == (4, 4)
    assert np.array_equal(mask, detect_gf6_saliency(values[0], window_size=3, threshold=0.5)[1])
