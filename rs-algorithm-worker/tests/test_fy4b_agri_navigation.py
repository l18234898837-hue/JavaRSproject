from __future__ import annotations

from affine import Affine
import numpy as np

from rs_algorithm_worker.fy4b_agri_navigation import apparent_reflectance, build_nominal_grid


def test_build_nominal_grid_uses_geostationary_crs_centered_pixel_grid():
    """Would fail if the grid used a non-geostationary CRS or shifted origin."""
    grid = build_nominal_grid(
        nom_center_lon=105.0,
        nom_sat_height=1_000_000.0,
        earth_equatorial_radius=10.0,
        inverse_flattening=2.0,
        sampling_angle_micro_rad=10.0,
        stepping_angle_micro_rad=20.0,
        width=3,
        height=2,
    )

    assert grid.status == "NOMINAL_UNVERIFIED"
    assert grid.crs == "+proj=geos +lon_0=105.0 +h=1000000.0 +a=10.0 +b=5.0 +sweep=x +units=m +no_defs"
    assert grid.transform == Affine(10.0, 0.0, -15.0, 0.0, -20.0, 20.0)
    assert (grid.width, grid.height) == (3, 2)


def test_apparent_reflectance_masks_night_side_and_applies_solar_zenith_correction():
    """Would fail if solar geometry were ignored or night-side pixels became finite."""
    result = apparent_reflectance(
        rho=[[0.2, 0.2, 0.2]],
        earth_sun_distance_ratio=1.0,
        solar_zenith_degrees=[[0.0, 60.0, 90.0]],
    )

    np.testing.assert_allclose(result[0, :2], [0.2, 0.4], rtol=1e-6)
    assert str(result[0, 2]) == "nan"
