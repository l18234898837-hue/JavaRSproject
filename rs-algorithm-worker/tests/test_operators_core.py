import numpy as np
import pytest

from rs_algorithm_worker.operators.calibration import calibrate_linear
from rs_algorithm_worker.operators.crop import crop_raster
from rs_algorithm_worker.operators.grid import GridSpec, grid_nearest
from rs_algorithm_worker.operators.quality_mask import apply_quality_mask


def test_linear_calibration_preserves_fill_values_as_nan():
    dn = np.array([[0, 10], [65535, 20]], dtype=np.uint16)
    actual = calibrate_linear(dn, scale=0.1, offset=1.0, fill_value=65535)
    np.testing.assert_allclose(actual[0], [1.0, 2.0])
    assert np.isnan(actual[1, 0])


def test_quality_mask_marks_values_with_declared_bad_bit_as_nan():
    values = np.array([1.0, 2.0, 3.0], dtype=np.float32)
    quality = np.array([0b0000, 0b0100, 0b0001], dtype=np.uint8)
    actual, valid = apply_quality_mask(values, quality, bad_bits=0b0101)
    np.testing.assert_allclose(actual, [1.0, np.nan, np.nan], equal_nan=True)
    assert valid.tolist() == [True, False, False]


def test_nearest_gridder_marks_empty_cells_as_nan():
    result = grid_nearest(
        values=np.array([5.0]),
        longitude=np.array([120.25]),
        latitude=np.array([30.25]),
        grid=GridSpec(min_lon=120.0, min_lat=30.0, max_lon=121.0, max_lat=31.0, resolution=0.5),
    )
    assert result.values.shape == (2, 2)
    assert result.values[1, 0] == 5.0
    assert np.isnan(result.values).sum() == 3


def test_crop_rejects_non_intersecting_bounds():
    with pytest.raises(ValueError, match="does not intersect"):
        crop_raster(
            np.ones((2, 2), dtype=np.float32),
            bounds=(120.0, 30.0, 121.0, 31.0),
            roi=(130.0, 30.0, 131.0, 31.0),
        )
