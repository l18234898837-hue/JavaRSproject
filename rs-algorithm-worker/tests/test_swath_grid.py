from __future__ import annotations

import numpy as np

from rs_algorithm_worker.operators.swath_grid import (
    QUALITY_NODATA,
    grid_swath,
    grid_swath_with_mapping,
    prepare_swath_grid,
)


def test_swath_grid_unwraps_a_scene_across_the_date_line() -> None:
    science, quality = grid_swath(
        values=np.array([[1.0, 2.0], [3.0, 4.0]], dtype=np.float32),
        quality_bits=np.zeros((2, 2), dtype=np.uint32),
        longitude=np.array([[179.5, -179.5], [179.5, -179.5]]),
        latitude=np.array([[1.0, 1.0], [0.0, 0.0]]),
        target_crs="EPSG:4326",
        resolution=0.5,
    )

    assert science.values.shape == (3, 3)
    assert science.transform.c >= 179.0
    assert quality.values.shape == science.values.shape


def test_swath_grid_preserves_nearest_source_quality_bits() -> None:
    _, quality = grid_swath(
        values=np.ones((2, 2), dtype=np.float32),
        quality_bits=np.array([[0, 8], [0, 0]], dtype=np.uint32),
        longitude=np.array([[0.0, 1.0], [0.0, 1.0]]),
        latitude=np.array([[1.0, 1.0], [0.0, 0.0]]),
        target_crs="EPSG:4326",
        resolution=0.5,
    )

    assert np.any(quality.values == 8)


def test_reused_swath_mapping_keeps_science_and_quality_on_the_same_source_pixel() -> None:
    longitude = np.array([[0.0, 1.0], [0.0, 1.0]])
    latitude = np.array([[1.0, 1.0], [0.0, 0.0]])
    mapping = prepare_swath_grid(
        longitude,
        latitude,
        target_crs="EPSG:4326",
        resolution=0.5,
    )
    science, quality = grid_swath_with_mapping(
        np.array([[10.0, 20.0], [30.0, 40.0]], dtype=np.float32),
        np.array([[8, 0], [0, 0]], dtype=np.uint32),
        mapping,
    )

    rejected = quality.values == 8
    assert rejected.any()
    assert np.isnan(science.values[rejected]).all()


def test_swath_mapping_filters_bad_navigation_and_masks_an_outside_roi() -> None:
    longitude = np.array([[0.0, 1.0], [999.0, 1.0]])
    latitude = np.array([[1.0, 1.0], [0.0, 0.0]])
    mapping = prepare_swath_grid(
        longitude,
        latitude,
        target_crs="EPSG:4326",
        resolution=0.5,
        roi=(10.0, 10.0, 11.0, 11.0),
        navigation_valid=np.array([[True, True], [False, True]]),
        maximum_distance=1.0,
    )
    science, quality = grid_swath_with_mapping(
        np.ones((2, 2), dtype=np.float32),
        np.zeros((2, 2), dtype=np.uint32),
        mapping,
    )

    assert np.isnan(science.values).all()
    assert np.all(quality.values == QUALITY_NODATA)
