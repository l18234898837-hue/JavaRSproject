from affine import Affine
import numpy as np

from rs_algorithm_worker.fy4b_agri_navigation import apparent_reflectance, build_nominal_grid


def test_nominal_grid_is_north_up_geostationary_grid():
    grid = build_nominal_grid(
        nom_center_lon=105.0, nom_sat_height=35786000.0,
        earth_equatorial_radius=6377830.0, inverse_flattening=298.2572221,
        sampling_angle_micro_rad=112.0, stepping_angle_micro_rad=112.0,
        width=2748, height=2748,
    )

    assert "+proj=geos" in grid.crs
    assert grid.transform.e < 0
    assert grid.transform == Affine(4008.032, 0, -5507035.968, 0, -4008.032, 5507035.968)
    assert grid.status == "NOMINAL_UNVERIFIED"


def test_apparent_reflectance_masks_night_side_and_preserves_day_side():
    result = apparent_reflectance(
        np.array([[0.2, 0.2]], dtype=np.float32),
        earth_sun_distance_ratio=1.0,
        solar_zenith_degrees=np.array([[0.0, 90.0]], dtype=np.float32),
    )

    assert result[0, 0] == np.float32(0.2)
    assert np.isnan(result[0, 1])
