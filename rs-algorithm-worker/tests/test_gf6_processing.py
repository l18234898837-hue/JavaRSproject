from __future__ import annotations

import numpy as np
import pytest

from rs_algorithm_worker.gf6_processing import (
    calibrate_gf6,
    crop_gf6,
    detect_gf6_saliency,
    extract_gf6_features,
    fuse_gf6_bands,
    mosaic_gf6_tiles,
    rpc_resample_gf6,
)
from rasterio.transform import from_origin
import rasterio


def test_calibration_applies_gain_offset_and_keeps_nodata_outside_formula():
    dn = np.array([[[1, 65535]], [[2, 65535]]], dtype=np.uint16)
    result = calibrate_gf6(dn, gains=[2, 3], offsets=[1, -1], fill_value=65535, nodata=-9999.0)

    assert result.dtype == np.float32
    assert np.array_equal(result[:, :, 0], [[3], [5]])
    assert np.array_equal(result[:, :, 1], [[-9999.0], [-9999.0]])


def test_crop_uses_grid_coordinates_and_rejects_no_overlap():
    values = np.arange(16, dtype=np.float32).reshape(1, 4, 4)
    cropped, transform = crop_gf6(values, from_origin(100, 104, 1, 1), (101, 101, 103, 103))

    assert cropped.shape == (1, 2, 2)
    assert (transform.c, transform.f) == (101, 103)


def test_mosaic_fusion_and_reprojection_helpers_preserve_their_public_contract(tmp_path):
    left, right = tmp_path / "left.tif", tmp_path / "right.tif"
    for path, value in ((left, 10), (right, 20)):
        with rasterio.open(path, "w", driver="GTiff", width=2, height=2, count=2, dtype="uint16", transform=from_origin(0, 2, 1, 1)) as dataset:
            dataset.write(np.full((2, 2, 2), value, dtype=np.uint16))
    mosaic = mosaic_gf6_tiles([(left, 0), (right, 2)], height=2, width=4, bands=2)
    reprojected = rpc_resample_gf6(np.ones((1, 2, 2), dtype=np.float32), source_crs="EPSG:4326", source_transform=from_origin(100, 2, 1, 1), target_crs="EPSG:3857", resolution=100000)

    assert np.all(mosaic[:, :, :2] == 10) and np.all(mosaic[:, :, 2:] == 20)
    assert fuse_gf6_bands(mosaic.astype(np.float32), rgb=(0, 1, 0)).shape == (3, 2, 4)
    assert reprojected.crs == "EPSG:3857" and reprojected.navigation_status == "NOMINAL_RPC_NO_DEM"


def test_local_variance_ignores_invalid_neighbours_and_masks_invalid_centres():
    values = np.full((1, 3, 3), 7.0, dtype=np.float32)
    values[0, 0, 0] = np.nan

    variance = extract_gf6_features(
        values,
        metrics=["local_variance"],
        window_size=3,
    )["local_variance"]

    assert np.isnan(variance[0, 0, 0])
    assert variance[0, 1, 1] == pytest.approx(0.0, abs=1e-6)


def test_saliency_ignores_invalid_neighbours_and_masks_invalid_centres():
    values = np.full((3, 3), 7.0, dtype=np.float32)
    values[0, 0] = np.nan

    score, mask = detect_gf6_saliency(values, window_size=3, threshold=0.5)

    assert np.isnan(score[0, 0])
    assert np.all(score[np.isfinite(values)] == 0.0)
    assert not mask.any()


@pytest.mark.parametrize(
    ("operation", "message"),
    [
        (
            lambda: extract_gf6_features(
                np.ones((1, 3, 3), dtype=np.float32),
                metrics=["local_variance"],
                window_size=257,
            ),
            "GF6 feature window size must be an odd integer between 3 and 255",
        ),
        (
            lambda: detect_gf6_saliency(
                np.ones((3, 3), dtype=np.float32),
                window_size=257,
            ),
            "GF6 saliency window size must be an odd integer between 3 and 255",
        ),
    ],
)
def test_neighbourhood_algorithms_reject_windows_above_the_memory_bound(operation, message):
    with pytest.raises(ValueError, match=message):
        operation()
