from affine import Affine
from rasterio.windows import Window

from rs_algorithm_worker.operators.gf6_rpc import NominalRpcGrid, crop_nominal_rpc_grid, window_transform
from rs_algorithm_worker.gf6_wfv_pipeline import calculate_global_stretches


def test_rpc_output_window_uses_the_nominal_target_grid_transform():
    grid = NominalRpcGrid(
        crs="EPSG:4326",
        transform=Affine(0.01, 0, 100, 0, -0.01, 10),
        width=100,
        height=50,
        bounds=(100, 9.5, 101, 10),
        height_m=0.0,
    )

    assert window_transform(grid.transform, Window(20, 10, 5, 5)) == Affine(0.01, 0, 100.2, 0, -0.01, 9.9)


def test_roi_crops_nominal_grid_and_preserves_target_crs():
    grid = NominalRpcGrid(
        crs="EPSG:4326",
        transform=Affine(0.01, 0, 100, 0, -0.01, 10),
        width=100,
        height=50,
        bounds=(100, 9.5, 101, 10),
        height_m=0.0,
    )

    cropped = crop_nominal_rpc_grid(grid, (100.2, 9.6, 100.4, 9.8))

    assert cropped.crs == "EPSG:4326"
    assert (cropped.width, cropped.height) == (20, 20)
    assert cropped.transform == Affine(0.01, 0, 100.2, 0, -0.01, 9.8)


def test_global_fusion_stretches_are_shared_by_every_output_window():
    stretches = calculate_global_stretches(
        [
            ([1.0, 2.0, 3.0, 4.0], [10.0, 20.0, 30.0, 40.0], [100.0, 200.0, 300.0, 400.0]),
            ([5.0, 6.0, 7.0, 8.0], [50.0, 60.0, 70.0, 80.0], [500.0, 600.0, 700.0, 800.0]),
        ],
        percentiles=(0.0, 100.0),
    )

    assert stretches == ((1.0, 8.0), (10.0, 80.0), (100.0, 800.0))


def test_global_fusion_stretches_ignore_standard_cog_nodata():
    stretches = calculate_global_stretches(
        [([-9999.0, 1.0], [-9999.0, 10.0], [-9999.0, 100.0])],
        percentiles=(0.0, 100.0),
    )

    assert stretches == ((1.0, 1.0), (10.0, 10.0), (100.0, 100.0))


def test_roi_outside_nominal_grid_is_rejected():
    grid = NominalRpcGrid(
        crs="EPSG:4326",
        transform=Affine(0.01, 0, 100, 0, -0.01, 10),
        width=100,
        height=50,
        bounds=(100, 9.5, 101, 10),
        height_m=0.0,
    )

    try:
        crop_nominal_rpc_grid(grid, (110.0, 20.0, 111.0, 21.0))
    except ValueError as error:
        assert str(error) == "ROI_DOES_NOT_INTERSECT"
    else:
        raise AssertionError("expected non-intersecting ROI to be rejected")
