from __future__ import annotations

import json
import h5py
import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin
from rasterio.windows import Window
import rs_algorithm_worker.fy4b_agri_pipeline as pipeline
from rs_algorithm_worker.product_adapters.fy4b_agri import ROLE_ORDER, build_integrated_plan

from rs_algorithm_worker.fy4b_agri_pipeline import (
    FY4B_QUALITY_CALIBRATION_DEGRADED,
    FY4B_QUALITY_FILE_DEGRADED,
    FY4B_QUALITY_FILL,
    FY4B_QUALITY_NAVIGATION_FAILURE,
    FY4B_QUALITY_NIGHT,
    FY4B_QUALITY_OUT_OF_RANGE,
    FY4B_QUALITY_OUTSIDE_DISK,
    build_quality_mask,
    calibrate_emissive,
    calibrate_reflective,
    iter_dataset_windows,
)


def test_calibrate_reflective_applies_lookup_scale_and_offset_and_keeps_fill_nan():
    """Would fail if lookup indexing, scale/offset, or fill handling regressed."""
    values = np.array([[0, 1, -1], [2, 3, 4], [1, 0, 2]], dtype=np.int16)

    result = calibrate_reflective(
        values, lookup=np.array([0.0, 0.1, 0.2, 0.3, 0.4]), fill_value=-1,
        scale=2.0, offset=1.0, esun=np.pi,
    )

    np.testing.assert_allclose(
        result.reflectance,
        np.array([[1.0, 1.2, np.nan], [1.4, 1.6, 1.8], [1.2, 1.0, 1.4]], dtype=np.float32),
        equal_nan=True,
    )
    np.testing.assert_allclose(
        result.radiance,
        np.array([[1.0, 1.2, np.nan], [1.4, 1.6, 1.8], [1.2, 1.0, 1.4]], dtype=np.float32),
        equal_nan=True,
    )


def test_calibrate_emissive_uses_lookup_for_temperature_and_linear_radiance():
    """Would fail if thermal temperature were derived from the radiance formula."""
    result = calibrate_emissive(
        np.array([[0, 1, -1], [2, 3, 1], [0, 2, 3]], dtype=np.int16),
        lookup=np.array([200.0, 210.0, 220.0, 230.0]), fill_value=-1,
        scale=0.5, offset=10.0,
    )

    np.testing.assert_allclose(
        result.brightness_temperature,
        np.array([[200.0, 210.0, np.nan], [220.0, 230.0, 210.0], [200.0, 220.0, 230.0]], dtype=np.float32),
        equal_nan=True,
    )
    np.testing.assert_allclose(
        result.radiance,
        np.array([[10.0, 10.5, np.nan], [11.0, 11.5, 10.5], [10.0, 11.0, 11.5]], dtype=np.float32),
        equal_nan=True,
    )


def test_quality_mask_keeps_each_documented_reason_bit_independent():
    """Would fail if fill/range/navigation/calibration/file/disk bits changed meaning."""
    result = build_quality_mask(
        np.array([[-1, 0, 6], [1, 2, 3]], dtype=np.int16),
        fill_value=-1,
        valid_range=(0, 4),
        navigation_ok=False,
        calibration_ok=False,
        file_quality_ok=False,
        outside_disk=np.array([[False, False, True], [False, False, False]]),
    )

    assert (FY4B_QUALITY_FILL, FY4B_QUALITY_OUT_OF_RANGE, FY4B_QUALITY_NAVIGATION_FAILURE,
            FY4B_QUALITY_CALIBRATION_DEGRADED, FY4B_QUALITY_FILE_DEGRADED, FY4B_QUALITY_OUTSIDE_DISK) == (1, 2, 4, 8, 16, 32)
    np.testing.assert_array_equal(result.reason_bits, np.array([[31, 28, 62], [28, 28, 28]], dtype=np.uint32))


def test_iter_dataset_windows_preserves_complete_rows_and_final_boundary():
    """Would fail if a streaming window skipped or overran the final partial rows."""
    values = np.arange(15, dtype=np.int16).reshape(5, 3)

    windows = list(iter_dataset_windows(values, rows_per_window=2))

    assert [chunk.tolist() for chunk in windows] == [
        [[0, 1, 2], [3, 4, 5]], [[6, 7, 8], [9, 10, 11]], [[12, 13, 14]],
    ]


def test_iter_dataset_tiles_never_requests_a_full_dataset_slice():
    """Would fail if mandatory calibration materialized a whole HDF science dataset."""
    class BoundedDataset:
        shape = (5, 4)

        def __getitem__(self, key):
            rows, columns = key
            if rows.start is None or rows.stop - rows.start >= self.shape[0]:
                raise AssertionError("full row slice is forbidden")
            if columns.start is None or columns.stop - columns.start >= self.shape[1]:
                raise AssertionError("full column slice is forbidden")
            return np.arange((rows.stop - rows.start) * (columns.stop - columns.start), dtype=np.int16).reshape(
                rows.stop - rows.start, columns.stop - columns.start,
            )

    tiles = list(pipeline.iter_dataset_tiles(BoundedDataset(), rows_per_window=2, columns_per_window=2))

    assert [values.shape for _window, values in tiles] == [(2, 2), (2, 2), (2, 2), (2, 2), (1, 2), (1, 2)]


def test_single_chunk_compressed_hdf_is_materialized_once_for_tiled_reads(tmp_path):
    """Would fail if every 256 px tile caused the full gzip chunk to be decompressed again."""
    path = tmp_path / "single-chunk.hdf"
    values = np.arange(48, dtype=np.uint16).reshape(6, 8)
    with h5py.File(path, "w") as handle:
        dataset = handle.create_dataset(
            "channel", data=values, chunks=values.shape, compression="gzip",
        )
        dataset.attrs["FillValue"] = np.uint16(65535)

    with h5py.File(path, "r") as handle:
        dataset = handle["channel"]
        source = pipeline._tile_read_source(dataset)

        assert source is not dataset
        assert source.shape == dataset.shape
        assert source.attrs["FillValue"] == np.uint16(65535)
        np.testing.assert_array_equal(source[1:3, 2:5], values[1:3, 2:5])
        np.testing.assert_array_equal(source[3:5, 4:7], values[3:5, 4:7])


def test_single_chunk_compressed_hdf_materializes_only_the_selected_roi_and_keeps_global_indices(tmp_path):
    path = tmp_path / "single-chunk-roi.hdf"
    values = np.arange(80, dtype=np.uint16).reshape(8, 10)
    with h5py.File(path, "w") as handle:
        handle.create_dataset("channel", data=values, chunks=values.shape, compression="gzip")

    selected = Window(3, 2, 4, 3)
    with h5py.File(path, "r") as handle:
        source = pipeline._tile_read_source(handle["channel"], selected)
        tiles = list(
            pipeline.iter_dataset_tiles(source, rows_per_window=2, columns_per_window=2, window=selected),
        )

    assert source.shape == values.shape
    assert source.values.shape == (3, 4)
    assert [window for window, _values in tiles] == [
        Window(3, 2, 2, 2), Window(5, 2, 2, 2),
        Window(3, 4, 2, 1), Window(5, 4, 2, 1),
    ]
    reconstructed = np.block([
        [tiles[0][1], tiles[1][1]],
        [tiles[2][1], tiles[3][1]],
    ])
    np.testing.assert_array_equal(reconstructed, values[2:5, 3:7])


def test_multi_chunk_hdf_keeps_bounded_streaming_reads(tmp_path):
    path = tmp_path / "multi-chunk.hdf"
    with h5py.File(path, "w") as handle:
        handle.create_dataset(
            "channel", data=np.arange(64, dtype=np.uint16).reshape(8, 8),
            chunks=(2, 2), compression="gzip",
        )

    with h5py.File(path, "r") as handle:
        dataset = handle["channel"]
        assert pipeline._tile_read_source(dataset) is dataset


def test_roi_source_window_scales_independently_across_all_four_native_resolutions():
    roi = [16.0, 16.0, 32.0, 32.0]
    expected = {
        64: Window(16, 32, 16, 16),
        32: Window(8, 16, 8, 8),
        16: Window(4, 8, 4, 4),
        8: Window(2, 4, 2, 2),
    }

    for size, window in expected.items():
        result = pipeline._source_window_for_roi(
            crs="EPSG:4326",
            transform=from_origin(0, 64, 64 / size, 64 / size),
            width=size,
            height=size,
            roi=roi,
            padding=0,
        )
        assert result == window


def test_quality_mask_propagates_geo_navigation_failure_and_outside_disk_bits():
    """Would fail if GEO NavQualityFlag or invalid GEO navigation stopped affecting L1 quality."""
    result = pipeline.build_quality_mask_from_geo(
        np.array([[1, 1], [1, 1]], dtype=np.int16),
        fill_value=-1,
        valid_range=(0, 4),
        l1_navigation_ok=True,
        geo_navigation_ok=False,
        geo_line=np.array([[0, -1], [0, 0]], dtype=np.int16),
        geo_column=np.array([[0, 0], [0, 0]], dtype=np.int16),
    )

    np.testing.assert_array_equal(
        result.reason_bits,
        np.array([[FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE | FY4B_QUALITY_OUTSIDE_DISK],
                  [FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE]], dtype=np.uint32),
    )
    invalid_navigation = pipeline.build_quality_mask_from_geo(
        np.array([[1]], dtype=np.int16), fill_value=-1, valid_range=(0, 4),
        l1_navigation_ok=True, geo_navigation_ok=True,
        geo_line=np.array([[-1]], dtype=np.int16), geo_column=np.array([[0]], dtype=np.int16),
    )
    np.testing.assert_array_equal(invalid_navigation.reason_bits, np.array([[36]], dtype=np.uint32))


def test_native_quality_raster_combines_geo_flag_and_geo_disk_mask(tmp_path):
    """Would fail if the required GEO file were ignored during native L1 quality generation."""
    l1, geo = tmp_path / "l1.hdf", tmp_path / "geo.hdf"
    with h5py.File(l1, "w") as handle:
        data, calibration, qa = handle.create_group("Data"), handle.create_group("Calibration"), handle.create_group("QA")
        channel = data.create_dataset("NOMChannel02", data=np.ones((2, 2), dtype=np.int16))
        channel.attrs["FillValue"] = -1
        channel.attrs["valid_range"] = np.array([0, 4], dtype=np.int16)
        calibration.create_dataset("CALChannel02", data=np.arange(5, dtype=np.float32))
        qa.create_dataset("NavQualityFlag", data=np.zeros(2, dtype=np.uint8))
        qa.create_dataset("CalQualityFlag", data=np.zeros(2, dtype=np.uint8))
        handle.attrs["Data Quality"] = 0
    with h5py.File(geo, "w") as handle:
        navigation, qa = handle.create_group("Navigation"), handle.create_group("QA")
        navigation.create_dataset("LineNumber", data=np.array([[0, -1], [0, 0]], dtype=np.int16))
        navigation.create_dataset("ColumnNumber", data=np.zeros((2, 2), dtype=np.int16))
        qa.create_dataset("NavQualityFlag", data=np.array([0, 1], dtype=np.uint8))

    science, quality = tmp_path / "science.tif", tmp_path / "quality.tif"
    pipeline._write_native_l1_windows(l1, geo, "L1_0500M", science, quality, fallback=("EPSG:4326", from_origin(0, 2, 1, 1)))

    with rasterio.open(quality) as dataset:
        np.testing.assert_array_equal(dataset.read(1), [[4, 36], [4, 4]])


def test_native_roi_is_calibrated_before_full_disk_materialization_and_preserves_full_grid_alignment(tmp_path):
    l1, geo = tmp_path / "l1-500m.hdf", tmp_path / "geo-4000m.hdf"
    _write_reflective_l1(l1, channels=(2,), shape=(64, 64), earth_sun_ratio=1.0)
    _write_geo_solar_zenith(
        geo,
        encoded=np.full((8, 8), 60.0, dtype=np.float32),
        fill_value=65535.0,
        valid_range=(0.0, 180.0),
        slope=1.0,
        intercept=0.0,
    )
    science, quality = tmp_path / "science-roi.tif", tmp_path / "quality-roi.tif"

    pipeline._write_native_l1_windows(
        l1,
        geo,
        "L1_0500M",
        science,
        quality,
        fallback=("EPSG:4326", from_origin(0, 64, 1, 1)),
        roi=[30.0, 30.0, 32.0, 32.0],
    )

    with rasterio.open(science) as dataset:
        assert (dataset.width, dataset.height) == (10, 10)
        assert dataset.transform == from_origin(26, 36, 1, 1)
        assert pipeline._full_source_grid(dataset) == (from_origin(0, 64, 1, 1), 64, 64)
        target_transform, target_width, target_height = pipeline._target_grid(
            dataset,
            {"targetCrs": "EPSG:4326", "roi": [30.0, 30.0, 32.0, 32.0]},
        )
        assert (target_width, target_height) == (2, 2)
        assert target_transform == from_origin(30, 32, 1, 1)
        np.testing.assert_allclose(dataset.read(1), 0.4, rtol=1e-6)
    with rasterio.open(quality) as dataset:
        assert (dataset.width, dataset.height) == (10, 10)
        assert not dataset.read(1).any()


def test_native_reflectance_uses_scaled_geo_solar_zenith_per_l1_window_and_flags_invalid_angles(tmp_path):
    """Would fail if GEO solar angles were ignored, read at the wrong resolution, or left in encoded units."""
    l1, geo = tmp_path / "l1-500m.hdf", tmp_path / "geo-4000m.hdf"
    _write_reflective_l1(l1, channels=(2,), shape=(4, 6), earth_sun_ratio=2.0)
    _write_geo_solar_zenith(
        geo,
        encoded=np.array([[0, 100, 400], [-20, -110, 200]], dtype=np.float32),
        fill_value=-20.0,
        valid_range=(-100.0, 400.0),
        slope=0.1,
        intercept=50.0,
    )
    with h5py.File(geo, "r+") as handle:
        handle["Navigation/LineNumber"][0, 2] = -1

    science, quality = tmp_path / "science.tif", tmp_path / "quality.tif"
    pipeline._write_native_l1_windows(
        l1, geo, "L1_0500M", science, quality,
        fallback=("EPSG:4326", from_origin(0, 4, 1, 1)),
    )

    expected_science = np.array([
        [1.244579, 1.244579, 1.6, 1.6, np.nan, np.nan],
        [1.244579, 1.244579, 1.6, 1.6, np.nan, np.nan],
        [np.nan, np.nan, np.nan, np.nan, 2.339044, 2.339044],
        [np.nan, np.nan, np.nan, np.nan, 2.339044, 2.339044],
    ], dtype=np.float32)
    solar_and_disk_failure = (
        FY4B_QUALITY_NAVIGATION_FAILURE | FY4B_QUALITY_OUTSIDE_DISK | FY4B_QUALITY_NIGHT
    )
    expected_quality = np.array([
        [0, 0, 0, 0, solar_and_disk_failure, solar_and_disk_failure],
        [0, 0, 0, 0, solar_and_disk_failure, solar_and_disk_failure],
        [FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE,
         FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE, 0, 0],
        [FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE,
         FY4B_QUALITY_NAVIGATION_FAILURE, FY4B_QUALITY_NAVIGATION_FAILURE, 0, 0],
    ], dtype=np.uint32)
    with rasterio.open(science) as dataset:
        np.testing.assert_allclose(dataset.read(1), expected_science, rtol=1e-6, equal_nan=True)
    with rasterio.open(quality) as dataset:
        np.testing.assert_array_equal(dataset.read(1), expected_quality)
    assert pipeline._raster_summary(science)["quality"]["validPixelRatio"] == pytest.approx(0.5)


def test_native_reflectance_distinguishes_night_from_navigation_and_satellite_disk_fill(tmp_path):
    """Night is valid geometry, while satellite-zenith fill is outside the Earth disk."""
    l1, geo = tmp_path / "l1-500m.hdf", tmp_path / "geo-4000m.hdf"
    _write_reflective_l1(l1, channels=(2,), shape=(1, 3), earth_sun_ratio=1.0)
    _write_geo_solar_zenith(
        geo,
        encoded=np.array([[60.0, 120.0, 65535.0]], dtype=np.float32),
        fill_value=65535.0,
        valid_range=(0.0, 180.0),
        slope=1.0,
        intercept=0.0,
        satellite_zenith=np.array([[30.0, 30.0, 65535.0]], dtype=np.float32),
    )

    science, quality = tmp_path / "science.tif", tmp_path / "quality.tif"
    pipeline._write_native_l1_windows(
        l1, geo, "L1_0500M", science, quality,
        fallback=("EPSG:4326", from_origin(0, 1, 1, 1)),
    )

    with rasterio.open(science) as dataset:
        np.testing.assert_allclose(dataset.read(1), [[0.4, np.nan, np.nan]], rtol=1e-6, equal_nan=True)
    with rasterio.open(quality) as dataset:
        np.testing.assert_array_equal(dataset.read(1), [[
            0,
            FY4B_QUALITY_NIGHT,
            FY4B_QUALITY_NAVIGATION_FAILURE | FY4B_QUALITY_OUTSIDE_DISK,
        ]])


def test_native_reflectance_rejects_invalid_earth_sun_distance_ratio(tmp_path):
    l1, geo = tmp_path / "l1-500m.hdf", tmp_path / "geo-4000m.hdf"
    _write_reflective_l1(l1, channels=(2,), shape=(1, 1), earth_sun_ratio=np.nan)
    _write_geo_solar_zenith(
        geo,
        encoded=np.array([[60.0]], dtype=np.float32),
        fill_value=65535.0,
        valid_range=(0.0, 180.0),
        slope=1.0,
        intercept=0.0,
    )

    with pytest.raises(ValueError, match="Earth/Sun Distance Ratio"):
        pipeline._write_native_l1_windows(
            l1, geo, "L1_0500M", tmp_path / "science.tif", tmp_path / "quality.tif",
            fallback=("EPSG:4326", from_origin(0, 1, 1, 1)),
        )


def test_solar_zenith_window_reads_only_the_nearest_bounded_source_block():
    """Would fail if GEO solar-angle scaling materialized the full 4 km dataset for an L1 tile."""
    class BoundedSolarDataset:
        shape = (1000, 1000)
        attrs = {
            "FillValue": 65535.0,
            "valid_range": np.array([0.0, 160.0], dtype=np.float32),
            "Slope": 0.5,
            "Intercept": 10.0,
        }

        def __init__(self):
            self.requests = []

        def __getitem__(self, key):
            rows, columns = key
            if rows.start is None or rows.stop - rows.start >= self.shape[0]:
                raise AssertionError("full GEO row slice is forbidden")
            if columns.start is None or columns.stop - columns.start >= self.shape[1]:
                raise AssertionError("full GEO column slice is forbidden")
            self.requests.append(key)
            return np.array([[0.0, 20.0], [40.0, 60.0]], dtype=np.float32)

    dataset = BoundedSolarDataset()

    angles, invalid = pipeline._solar_zenith_window(
        dataset, Window(200, 100, 4, 4), target_height=2000, target_width=2000,
    )

    np.testing.assert_array_equal(angles, [
        [10.0, 10.0, 20.0, 20.0],
        [10.0, 10.0, 20.0, 20.0],
        [30.0, 30.0, 40.0, 40.0],
        [30.0, 30.0, 40.0, 40.0],
    ])
    assert not invalid.any()
    assert len(dataset.requests) == 1


def test_reprojection_runs_once_per_band_instead_of_once_per_output_tile(tmp_path, monkeypatch):
    source_path = tmp_path / "source.tif"
    destination_path = tmp_path / "destination.tif"
    transform = from_origin(100, 30, 0.01, 0.01)
    with rasterio.open(
        source_path, "w", driver="GTiff", width=520, height=520, count=2,
        dtype="float32", crs="EPSG:4326", transform=transform, nodata=np.nan,
    ) as dataset:
        dataset.write(np.ones((2, 520, 520), dtype=np.float32))
        dataset.set_band_description(1, "first")
        dataset.set_band_description(2, "second")

    calls = []
    original_reproject = pipeline.reproject

    def tracking_reproject(*args, **kwargs):
        calls.append(kwargs)
        return original_reproject(*args, **kwargs)

    monkeypatch.setattr(pipeline, "reproject", tracking_reproject)

    pipeline._reproject_raster_windows(
        source_path, destination_path, {"targetCrs": "EPSG:4326", "roi": None},
        pipeline.Resampling.bilinear,
    )

    assert len(calls) == 2
    with rasterio.open(destination_path) as dataset:
        assert dataset.descriptions == ("first", "second")
        np.testing.assert_allclose(dataset.read(), 1.0)


def test_target_grid_without_roi_is_clipped_to_science_valid_footprint(tmp_path):
    source_path = tmp_path / "source-with-footprint.tif"
    values = np.full((2, 10, 12), np.nan, dtype=np.float32)
    values[:, 3:8, 4:10] = 1.0
    with rasterio.open(
        source_path,
        "w",
        driver="GTiff",
        width=12,
        height=10,
        count=2,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_origin(100, 40, 0.1, 0.1),
        nodata=np.nan,
    ) as dataset:
        dataset.write(values)

    with rasterio.open(source_path) as source:
        transform, width, height = pipeline._target_grid(
            source,
            {"targetCrs": "EPSG:4326", "roi": None},
        )

    assert (width, height) == (6, 5)
    assert transform.c == pytest.approx(100.4)
    assert transform.f == pytest.approx(39.7)
    assert transform.a == pytest.approx(0.1)
    assert transform.e == pytest.approx(-0.1)


def test_toa_nir_red_nd_consumes_geo_corrected_c03_reflectance(tmp_path):
    """Would fail if the TOA feature consumed uncorrected C03 values after native calibration."""
    l1, geo = tmp_path / "l1-1000m.hdf", tmp_path / "geo-4000m.hdf"
    _write_reflective_l1(l1, channels=(1, 2, 3), shape=(2, 2), earth_sun_ratio=1.0)
    _write_geo_solar_zenith(
        geo,
        encoded=np.full((2, 2), 60.0, dtype=np.float32),
        fill_value=65535.0,
        valid_range=(0.0, 180.0),
        slope=1.0,
        intercept=0.0,
    )
    vis, quality = tmp_path / "vis-1000m.tif", tmp_path / "quality-1000m.tif"
    transform = from_origin(0, 2, 1, 1)
    pipeline._write_native_l1_windows(
        l1, geo, "L1_1000M", vis, quality, fallback=("EPSG:4326", transform),
    )
    red = tmp_path / "red-500m.tif"
    with rasterio.open(
        red, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
        crs="EPSG:4326", transform=transform, nodata=np.nan,
    ) as dataset:
        dataset.write(np.full((1, 2, 2), 0.2, dtype=np.float32))
        dataset.set_band_description(1, "C02_REFLECTANCE")

    artifacts = pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": vis, "FY4B_RED_500M_COG": red},
        tmp_path,
        {"features": {"enabled": True, "metrics": ["toa_nir_red_nd"], "windowSize": 3}},
    )

    with rasterio.open(dict(artifacts)["FY4B_FEATURE_COG"]) as dataset:
        assert dataset.descriptions == ("toa_nir_red_nd",)
        np.testing.assert_allclose(dataset.read(1), np.full((2, 2), 1.0 / 3.0, dtype=np.float32), rtol=1e-6)


def test_parameters_require_serialized_java_snapshot_and_reject_fallbacks():
    """Would fail if a Python-only dict/default/fallback payload crossed the Java boundary."""
    assert pipeline._parameters({"parameterSnapshot": '{"schemaVersion":"1"}'})["schemaVersion"] == "1"

    for payload in ({}, {"parameterSnapshot": {"schemaVersion": "1"}}, {"parameterSnapshot": "not-json"},
                    {"parameters": {"schemaVersion": "1"}}):
        with pytest.raises(ValueError, match="snapshot"):
            pipeline._parameters(payload)


def test_statistics_only_features_still_write_the_declared_feature_cog(tmp_path):
    """Would fail if an enabled statistics-only feature request omitted its FINAL COG."""
    standard = tmp_path / "standard.tif"
    with rasterio.open(
        standard, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
        crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan,
    ) as dataset:
        dataset.write(np.array([[[1.0, 2.0], [3.0, 4.0]]], dtype=np.float32))
        dataset.set_band_description(1, "C01_REFLECTANCE")

    artifacts = pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": standard},
        tmp_path,
        {"features": {"enabled": True, "metrics": ["statistics"]}},
    )

    assert [kind for kind, _path in artifacts] == ["FY4B_FEATURE_COG", "FY4B_FEATURE_SUMMARY_JSON"]
    assert (tmp_path / "features" / "fy4b-features.tif").is_file()


def test_feature_summary_exposes_user_facing_explainability(tmp_path):
    standard = tmp_path / "standard.tif"
    with rasterio.open(
        standard, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
        crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan,
    ) as dataset:
        dataset.write(np.ones((1, 2, 2), dtype=np.float32))
        dataset.set_band_description(1, "C03_REFLECTANCE")

    pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": standard},
        tmp_path,
        {"features": {"enabled": True, "metrics": ["statistics"], "windowSize": 3}},
    )
    summary = json.loads((tmp_path / "features" / "summary.json").read_text(encoding="utf-8"))
    assert summary["explainability"]["algorithm"] == "FY-4B AGRI 特征提取"
    assert "statistics" in summary["explainability"]["metricDefinitions"]


def test_fusion_writes_explainability_summary(tmp_path):
    source = tmp_path / "standard.tif"
    profile = {
        "driver": "GTiff", "width": 2, "height": 2, "count": 3, "dtype": "float32",
        "crs": "EPSG:4326", "transform": from_origin(0, 2, 1, 1), "nodata": np.nan,
    }
    with rasterio.open(source, "w", **profile) as dataset:
        dataset.write(np.ones((3, 2, 2), dtype=np.float32))
        for index, name in enumerate(("C01_REFLECTANCE", "C02_REFLECTANCE", "C03_REFLECTANCE"), 1):
            dataset.set_band_description(index, name)

    artifacts = pipeline._write_fy4b_fusion(
        {"FY4B_VIS_NIR_1000M_COG": source},
        tmp_path,
        {"fusion": {"percentiles": [2.0, 98.0]}},
    )
    assert "FY4B_FUSION_SUMMARY_JSON" in dict(artifacts)
    summary = json.loads((tmp_path / "fusion" / "summary.json").read_text(encoding="utf-8"))
    assert summary["explainability"]["algorithm"] == "FY-4B AGRI 假彩色融合"


def test_staged_statistics_only_features_do_not_require_optional_red_upstream_artifact(tmp_path):
    standard = tmp_path / "standard.tif"
    with rasterio.open(
        standard, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
        crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan,
    ) as dataset:
        dataset.write(np.ones((1, 2, 2), dtype=np.float32))
        dataset.set_band_description(1, "C01_REFLECTANCE")

    output = tmp_path / "stage-output"
    response = pipeline.execute_fy4b_stage(
        {
            "stageKey": "EXTRACT_FY4B_FEATURES",
            "outputUri": "rs://output/fy4b",
            "parameterSnapshot": json.dumps({
                "schemaVersion": "1",
                "features": {"enabled": True, "metrics": ["statistics"]},
            }),
        },
        {"inputs": [{"path": str(standard), "role": "FY4B_VIS_NIR_1000M_COG"}], "output": {"path": str(output)}},
    )

    assert response["status"] == "SUCCEEDED"
    assert (output / "features" / "fy4b-features.tif").is_file()


def test_staged_btd_features_pass_4000m_thermal_upstream_artifact(tmp_path, monkeypatch):
    standard = tmp_path / "standard.tif"
    thermal = tmp_path / "thermal.tif"
    for path in (standard, thermal):
        with rasterio.open(
            path, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
            crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan,
        ) as dataset:
            dataset.write(np.ones((1, 2, 2), dtype=np.float32))
    seen = {}

    def write_features(sources, directory, _parameters):
        seen.update(sources)
        result = directory / "features" / "fy4b-features.tif"
        result.parent.mkdir(parents=True, exist_ok=True)
        result.write_bytes(b"feature")
        return [("FY4B_FEATURE_COG", result)]

    monkeypatch.setattr(pipeline, "_write_fy4b_features", write_features)
    response = pipeline.execute_fy4b_stage(
        {
            "stageKey": "EXTRACT_FY4B_FEATURES",
            "outputUri": "rs://output/fy4b",
            "parameterSnapshot": json.dumps({
                "schemaVersion": "1",
                "features": {"enabled": True, "metrics": ["btd_c13_c14"]},
            }),
        },
        {"inputs": [
            {"path": str(standard), "role": "FY4B_VIS_NIR_1000M_COG"},
            {"path": str(thermal), "role": "FY4B_MULTISPECTRAL_4000M_COG"},
        ], "output": {"path": str(tmp_path / "stage-output")}},
    )

    assert response["status"] == "SUCCEEDED"
    assert set(seen) == {"FY4B_VIS_NIR_1000M_COG", "FY4B_MULTISPECTRAL_4000M_COG"}


def test_staged_c02_saliency_reads_declared_red_upstream_artifact(tmp_path, monkeypatch):
    source = tmp_path / "red.tif"
    with rasterio.open(
        source, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32",
        crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan,
    ) as dataset:
        dataset.write(np.ones((1, 2, 2), dtype=np.float32))
        dataset.set_band_description(1, "C02_REFLECTANCE_500M")
    output = tmp_path / "stage-output"
    seen = []

    def write_saliency(standard, directory, _parameters):
        seen.append(standard)
        result = directory / "saliency" / "score.tif"
        result.parent.mkdir(parents=True, exist_ok=True)
        result.write_bytes(b"score")
        return [("FY4B_SALIENCY_SCORE_COG", result)]

    monkeypatch.setattr(pipeline, "_write_fy4b_saliency", write_saliency)
    response = pipeline.execute_fy4b_stage(
        {
            "stageKey": "DETECT_FY4B_SALIENCY",
            "outputUri": "rs://output/fy4b",
            "parameterSnapshot": json.dumps({
                "schemaVersion": "1",
                "features": {"enabled": True, "metrics": ["statistics"]},
                "saliency": {"enabled": True, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
            }),
        },
        {"inputs": [{"path": str(source), "role": "FY4B_RED_500M_COG"}], "output": {"path": str(output)}},
    )

    assert response["status"] == "SUCCEEDED"
    assert set(seen[0]) == {"FY4B_RED_500M_COG"}


def test_btd_feature_writes_c13_minus_c14_band(tmp_path):
    """Would fail if the declared thermal BTD feature silently fell back to source pixels."""
    standard, thermal = tmp_path / "standard.tif", tmp_path / "thermal.tif"
    profile = {"driver": "GTiff", "width": 2, "height": 2, "dtype": "float32", "crs": "EPSG:4326", "transform": from_origin(0, 2, 1, 1), "nodata": np.nan}
    with rasterio.open(standard, "w", count=1, **profile) as dataset:
        dataset.write(np.ones((1, 2, 2), dtype=np.float32))
        dataset.set_band_description(1, "C01_REFLECTANCE")
    with rasterio.open(thermal, "w", count=2, **profile) as dataset:
        dataset.write(np.array([[[280.0, 282.0], [284.0, 286.0]], [[270.0, 271.0], [272.0, 273.0]]], dtype=np.float32))
        dataset.set_band_description(1, "C13_BRIGHTNESS_TEMPERATURE")
        dataset.set_band_description(2, "C14_BRIGHTNESS_TEMPERATURE")

    artifacts = pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": standard, "FY4B_MULTISPECTRAL_4000M_COG": thermal},
        tmp_path,
        {"features": {"enabled": True, "metrics": ["btd_c13_c14"]}},
    )

    feature_path = dict(artifacts)["FY4B_FEATURE_COG"]
    with rasterio.open(feature_path) as dataset:
        assert dataset.descriptions == ("btd_c13_c14",)
        np.testing.assert_allclose(dataset.read(1), [[10.0, 11.0], [12.0, 13.0]])


def test_derived_saliency_uses_declared_feature_cog_not_thermal_standard(tmp_path):
    """Would fail if a derived saliency source bypassed its feature artifact."""
    feature, thermal = tmp_path / "features.tif", tmp_path / "thermal.tif"

    source = pipeline._saliency_source(
        {"FY4B_FEATURE_COG": feature, "FY4B_MULTISPECTRAL_4000M_COG": thermal},
        "btd_c13_c14",
    )

    assert source == feature


def test_feature_writer_uses_task1_qualified_500m_gradient_description(tmp_path):
    """Would fail if a legal gradient:C02_REFLECTANCE_500M source had no matching feature band."""
    vis, red = tmp_path / "vis.tif", tmp_path / "red.tif"
    profile = {"driver": "GTiff", "width": 2, "height": 2, "count": 1, "dtype": "float32", "crs": "EPSG:4326", "transform": from_origin(0, 2, 1, 1), "nodata": np.nan}
    for path, values, description in ((vis, [[1.0, 1.0], [1.0, 1.0]], "C01_REFLECTANCE"),
                                      (red, [[1.0, 2.0], [3.0, 4.0]], "C02_REFLECTANCE")):
        with rasterio.open(path, "w", **profile) as dataset:
            dataset.write(np.array([values], dtype=np.float32))
            dataset.set_band_description(1, description)

    artifacts = pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": vis, "FY4B_RED_500M_COG": red}, tmp_path,
        {"features": {"enabled": True, "metrics": ["gradient"]}},
    )

    with rasterio.open(dict(artifacts)["FY4B_FEATURE_COG"]) as dataset:
        assert "gradient:C02_REFLECTANCE_500M" in dataset.descriptions


def test_500m_derived_features_align_to_1000m_feature_grid_before_write_and_saliency(tmp_path):
    """Would fail if valid 4x4 500 m features were stacked directly with the 2x2 1000 m feature grid."""
    vis, red = tmp_path / "vis-1000m.tif", tmp_path / "red-500m.tif"
    with rasterio.open(vis, "w", driver="GTiff", width=2, height=2, count=1, dtype="float32", crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan) as dataset:
        dataset.write(np.array([[[1.0, 2.0], [3.0, 4.0]]], dtype=np.float32))
        dataset.set_band_description(1, "C01_REFLECTANCE")
    with rasterio.open(red, "w", driver="GTiff", width=4, height=4, count=1, dtype="float32", crs="EPSG:4326", transform=from_origin(0, 2, 0.5, 0.5), nodata=np.nan) as dataset:
        dataset.write(np.arange(16, dtype=np.float32).reshape(1, 4, 4))
        dataset.set_band_description(1, "C02_REFLECTANCE")

    artifacts = pipeline._write_fy4b_features(
        {"FY4B_VIS_NIR_1000M_COG": vis, "FY4B_RED_500M_COG": red}, tmp_path,
        {"features": {"enabled": True, "metrics": ["gradient", "local_variance"], "windowSize": 3}},
    )
    feature_path = dict(artifacts)["FY4B_FEATURE_COG"]

    with rasterio.open(feature_path) as dataset:
        assert (dataset.width, dataset.height, dataset.transform) == (2, 2, from_origin(0, 2, 1, 1))
        assert "gradient:C02_REFLECTANCE_500M" in dataset.descriptions
        assert "local_variance:C02_REFLECTANCE_500M" in dataset.descriptions
    saliency = pipeline._write_fy4b_saliency(
        {"FY4B_FEATURE_COG": feature_path}, tmp_path,
        {"saliency": {"source": "gradient:C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3}},
    )
    assert dict(saliency)["FY4B_SALIENCY_SCORE_COG"].is_file()


def test_execute_fy4b_product_feeds_feature_artifacts_to_derived_saliency(tmp_path, monkeypatch):
    """Would fail if the integrated executor omitted feature outputs from the saliency artifact lookup."""
    output = tmp_path / "run"
    feature = output / "features" / "fy4b-features.tif"

    def phase(*_args, **_kwargs):
        return []

    def standard(*_args, **_kwargs):
        path = output / "standard" / "1000m.tif"; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"standard")
        return [("FY4B_VIS_NIR_1000M_COG", path)]

    def features(*_args, **_kwargs):
        feature.parent.mkdir(parents=True, exist_ok=True); feature.write_bytes(b"feature")
        summary = output / "features" / "summary.json"; summary.write_bytes(b"summary")
        return [("FY4B_FEATURE_COG", feature), ("FY4B_FEATURE_SUMMARY_JSON", summary)]

    def saliency(artifacts, *_args, **_kwargs):
        assert artifacts["FY4B_FEATURE_COG"] == feature
        score = output / "saliency" / "score.tif"; score.parent.mkdir(parents=True, exist_ok=True); score.write_bytes(b"score")
        return [("FY4B_SALIENCY_SCORE_COG", score)]

    monkeypatch.setattr(pipeline, "_validate_fy4b_package", phase)
    monkeypatch.setattr(pipeline, "_build_fy4b_nominal_grid", phase)
    monkeypatch.setattr(pipeline, "_calibrate_and_georeference_fy4b", phase)
    monkeypatch.setattr(pipeline, "_reproject_and_crop_fy4b", phase)
    monkeypatch.setattr(pipeline, "_write_fy4b_standard_cog", standard)
    monkeypatch.setattr(pipeline, "_write_fy4b_features", features)
    monkeypatch.setattr(pipeline, "_write_fy4b_saliency", saliency)

    pipeline.execute_fy4b_product(
        {"stageKey": "STANDARDIZE_FY4B_AGRI", "outputUri": "rs://output/feature", "parameterSnapshot": json.dumps({
            "features": {"enabled": True, "metrics": ["gradient"]},
            "saliency": {"enabled": True, "source": "gradient:C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
        })},
        {"output": {"path": str(output)}, "inputs": [{"role": role, "path": str(tmp_path / f"{role}.hdf")} for role in ROLE_ORDER]},
    )


@pytest.mark.parametrize("artifact_type,description,target_values", [
    ("FY4B_VIS_NIR_1000M_COG", "C03_REFLECTANCE", [[3.0, 3.0], [3.0, 3.0]]),
    ("FY4B_MULTISPECTRAL_4000M_COG", "C13_BRIGHTNESS_TEMPERATURE", [[13.0, 13.0], [13.0, 13.0]]),
])
def test_physical_saliency_reads_only_the_declared_band(tmp_path, monkeypatch, artifact_type, description, target_values):
    """Would fail if C03/C13 saliency averaged every band in its source COG."""
    source = tmp_path / "source.tif"
    with rasterio.open(source, "w", driver="GTiff", width=2, height=2, count=2, dtype="float32", crs="EPSG:4326", transform=from_origin(0, 2, 1, 1), nodata=np.nan) as dataset:
        dataset.write(np.array([[[99.0, 99.0], [99.0, 99.0]], target_values], dtype=np.float32))
        dataset.set_band_description(1, "UNRELATED")
        dataset.set_band_description(2, description)
    seen: list[np.ndarray] = []
    original = pipeline.detect_saliency

    def spy(raster, parameters):
        seen.append(np.array(raster.values, copy=True))
        return original(raster, parameters)

    monkeypatch.setattr(pipeline, "detect_saliency", spy)
    pipeline._write_fy4b_saliency(
        {artifact_type: source}, tmp_path,
        {"saliency": {"source": description + ("_1000M" if description.startswith("C03") else "_4000M"), "threshold": 0.5, "windowSize": 3}},
    )

    np.testing.assert_array_equal(seen, [np.array(target_values, dtype=np.float32)])


def test_execute_fy4b_product_runs_internal_phases_in_order_and_returns_only_finals(tmp_path, monkeypatch):
    """Would fail if phase order, raw GEO input, final filtering, or streaming hashing regressed."""
    output = tmp_path / "run"
    output.mkdir()
    geo_path = tmp_path / "geo.hdf"
    calls: list[str] = []
    final_path = output / "standard" / "0500m.tif"

    def phase(name):
        def run(*_args, **_kwargs):
            calls.append(name)
            intermediate = output / {
                "VALIDATE_FY4B_PACKAGE": "validation",
                "BUILD_FY4B_NOMINAL_GRID": "navigation",
                "CALIBRATE_AND_GEOREFERENCE_FY4B": "native",
                "REPROJECT_AND_CROP_FY4B": "grid",
            }.get(name, "calibration")
            intermediate.mkdir(exist_ok=True)
            return [(f"{name}_INTERMEDIATE", intermediate / "ignored.json")]
        return run

    def build_grid(path, *_args, **_kwargs):
        assert path == geo_path
        return phase("BUILD_FY4B_NOMINAL_GRID")()

    def write_standard(*_args, **_kwargs):
        calls.append("WRITE_FY4B_STANDARD_COG")
        final_path.parent.mkdir(exist_ok=True)
        final_path.write_bytes(b"abc")
        return [("FY4B_RED_500M_COG", final_path)]

    def not_called(*_args, **_kwargs):
        raise AssertionError("disabled optional phase must not run")

    monkeypatch.setattr(pipeline, "_validate_fy4b_package", phase("VALIDATE_FY4B_PACKAGE"))
    monkeypatch.setattr(pipeline, "_build_fy4b_nominal_grid", build_grid)
    monkeypatch.setattr(pipeline, "_calibrate_and_georeference_fy4b", phase("CALIBRATE_AND_GEOREFERENCE_FY4B"))
    monkeypatch.setattr(pipeline, "_reproject_and_crop_fy4b", phase("REPROJECT_AND_CROP_FY4B"))
    monkeypatch.setattr(pipeline, "_write_fy4b_standard_cog", write_standard)
    monkeypatch.setattr(pipeline, "_write_fy4b_features", not_called)
    monkeypatch.setattr(pipeline, "_write_fy4b_fusion", not_called)
    monkeypatch.setattr(pipeline, "_write_fy4b_saliency", not_called)
    monkeypatch.setattr(type(final_path), "read_bytes", lambda *_args, **_kwargs: (_ for _ in ()).throw(AssertionError("output hashing must stream")))

    response = pipeline.execute_fy4b_product(
        {"stageKey": "STANDARDIZE_FY4B_AGRI", "outputUri": "rs://output/task-2", "parameterSnapshot": '{"schemaVersion":"1"}'},
        {
            "output": {"path": str(output)},
            "inputs": [
                {"role": "L1_0500M", "path": str(tmp_path / "500.hdf")},
                {"role": "L1_1000M", "path": str(tmp_path / "1000.hdf")},
                {"role": "L1_2000M", "path": str(tmp_path / "2000.hdf")},
                {"role": "L1_4000M", "path": str(tmp_path / "4000.hdf")},
                {"role": "GEO_4000M", "path": str(geo_path)},
            ],
        },
    )

    assert calls == [
        "VALIDATE_FY4B_PACKAGE",
        "BUILD_FY4B_NOMINAL_GRID",
        "CALIBRATE_AND_GEOREFERENCE_FY4B",
        "REPROJECT_AND_CROP_FY4B",
        "WRITE_FY4B_STANDARD_COG",
    ]
    assert response["artifacts"] == [{
        "uri": "rs://output/task-2/standard/0500m.tif",
        "type": "FY4B_RED_500M_COG",
        "sizeBytes": 3,
        "checksum": "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    }]
    assert not any("native/" in artifact["uri"] or "grid/" in artifact["uri"] for artifact in response["artifacts"])
    assert not any((output / name).exists() for name in ("validation", "navigation", "native", "calibration", "grid"))


def test_execute_fy4b_stage_validation_uses_the_complete_raw_role_order(tmp_path, monkeypatch):
    """The staged validation entrypoint must reach package validation with all five raw roles."""
    output = tmp_path / "run"
    validation_path = output / "validation" / "package.json"

    def validate(manifest, directory):
        assert set(item["role"] for item in manifest["inputs"]) == set(ROLE_ORDER)
        assert directory == output.resolve()
        validation_path.parent.mkdir(parents=True, exist_ok=True)
        validation_path.write_text("{}", encoding="utf-8")
        return [("FY4B_INPUT_VALIDATION_JSON", validation_path)]

    monkeypatch.setattr(pipeline, "_validate_fy4b_package", validate)
    response = pipeline.execute_fy4b_stage(
        {
            "stageKey": "VALIDATE_FY4B_PACKAGE",
            "outputUri": "rs://output/task",
            "parameterSnapshot": "{}",
        },
        {
            "output": {"path": str(output)},
            "inputs": [
                {"role": role, "path": str(tmp_path / f"{role}.hdf")}
                for role in ROLE_ORDER
            ],
        },
    )

    assert response["status"] == "SUCCEEDED"
    assert response["artifacts"][0]["type"] == "FY4B_INPUT_VALIDATION_JSON"
    assert response["artifacts"][0]["uri"] == "rs://output/task/validation/package.json"


@pytest.mark.parametrize("parameters", [
    {},
    {"features": {"enabled": True, "metrics": ["statistics"]}},
    {"fusion": {"enabled": True, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]}},
    {"saliency": {"enabled": True, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3}},
    {
        "features": {"enabled": True, "metrics": ["statistics"]},
        "fusion": {"enabled": True, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]},
        "saliency": {"enabled": True, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
    },
])
def test_execute_fy4b_product_matches_exact_task1_final_artifact_contract(tmp_path, monkeypatch, parameters):
    """Would fail if the Java-visible executor leaked intermediates or drifted from Task 1 FINAL paths."""
    output_uri = "rs://output/contract"
    candidate = {
        "profileKey": {"productCode": "FY-4B-AGRI", "profileVersion": "v1"},
        "dataShape": "GEOSTATIONARY_RASTER", "supportStatus": "SUPPORTED",
        "files": [{"role": role, "ordinal": index, "path": str(tmp_path / f"{role}.hdf")} for index, role in enumerate(ROLE_ORDER)],
    }
    stage = build_integrated_plan(candidate, parameters)["stages"][0]
    expected = {(item["artifactType"], output_uri + "/" + item["relativePath"]) for item in stage["outputs"]}
    calls: list[str] = []

    def phase(name):
        def run(*_args, **_kwargs):
            calls.append(name)
            (tmp_path / "run" / name).mkdir(parents=True, exist_ok=True)
            return []
        return run

    def output_writer(name, prefix):
        def run(*_args, **_kwargs):
            calls.append(name)
            artifacts = []
            for item in stage["outputs"]:
                if item["relativePath"].startswith(prefix):
                    path = tmp_path / "run" / item["relativePath"]
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(item["artifactType"].encode("ascii"))
                    artifacts.append((item["artifactType"], path))
            return artifacts
        return run

    monkeypatch.setattr(pipeline, "_validate_fy4b_package", phase("VALIDATE_FY4B_PACKAGE"))
    monkeypatch.setattr(pipeline, "_build_fy4b_nominal_grid", phase("BUILD_FY4B_NOMINAL_GRID"))
    monkeypatch.setattr(pipeline, "_calibrate_and_georeference_fy4b", phase("CALIBRATE_AND_GEOREFERENCE_FY4B"))
    monkeypatch.setattr(pipeline, "_reproject_and_crop_fy4b", phase("REPROJECT_AND_CROP_FY4B"))
    monkeypatch.setattr(pipeline, "_write_fy4b_standard_cog", output_writer("WRITE_FY4B_STANDARD_COG", "standard/"))
    monkeypatch.setattr(pipeline, "_write_fy4b_features", output_writer("EXTRACT_FY4B_FEATURES", "features/"))
    monkeypatch.setattr(pipeline, "_write_fy4b_fusion", output_writer("FUSE_FY4B_BANDS", "fusion/"))
    monkeypatch.setattr(pipeline, "_write_fy4b_saliency", output_writer("DETECT_FY4B_SALIENCY", "saliency/"))

    response = pipeline.execute_fy4b_product(
        {"stageKey": "STANDARDIZE_FY4B_AGRI", "outputUri": output_uri, "parameterSnapshot": stage["parameterSnapshot"]},
        {"output": {"path": str(tmp_path / "run")}, "inputs": [{"role": role, "path": str(tmp_path / f"{role}.hdf")} for role in ROLE_ORDER]},
    )

    assert {(item["type"], item["uri"]) for item in response["artifacts"]} == expected
    assert all(item["uri"].startswith("rs://output/") for item in response["artifacts"])
    assert all((tmp_path / "run" / item["relativePath"]).is_file() for item in stage["outputs"])
    assert not any("native" in item["type"].lower() or "grid" in item["type"].lower() for item in response["artifacts"])


def _write_reflective_l1(
    path, *, channels: tuple[int, ...], shape: tuple[int, int], earth_sun_ratio: float,
) -> None:
    with h5py.File(path, "w") as handle:
        data, calibration, qa = handle.create_group("Data"), handle.create_group("Calibration"), handle.create_group("QA")
        for channel in channels:
            dataset = data.create_dataset(f"NOMChannel{channel:02d}", data=np.ones(shape, dtype=np.int16))
            dataset.attrs["FillValue"] = -1
            dataset.attrs["valid_range"] = np.array([0, 1], dtype=np.int16)
            calibration.create_dataset(f"CALChannel{channel:02d}", data=np.array([0.0, 0.2], dtype=np.float32))
        qa.create_dataset("NavQualityFlag", data=np.zeros(15, dtype=np.uint8))
        qa.create_dataset("CalQualityFlag", data=np.zeros(15, dtype=np.uint8))
        handle.attrs["Data Quality"] = 0
        handle.attrs["Earth/Sun Distance Ratio"] = earth_sun_ratio


def _write_geo_solar_zenith(
    path, *, encoded: np.ndarray, fill_value: float, valid_range: tuple[float, float],
    slope: float, intercept: float, satellite_zenith: np.ndarray | None = None,
) -> None:
    with h5py.File(path, "w") as handle:
        navigation, qa = handle.create_group("Navigation"), handle.create_group("QA")
        rows, columns = np.indices(encoded.shape, dtype=np.int16)
        navigation.create_dataset("LineNumber", data=rows)
        navigation.create_dataset("ColumnNumber", data=columns)
        solar = navigation.create_dataset("NOMSunZenith", data=encoded)
        solar.attrs["FillValue"] = fill_value
        solar.attrs["valid_range"] = np.asarray(valid_range, dtype=np.float32)
        solar.attrs["Slope"] = slope
        solar.attrs["Intercept"] = intercept
        if satellite_zenith is not None:
            satellite = navigation.create_dataset("NOMSatelliteZenith", data=satellite_zenith)
            satellite.attrs["FillValue"] = fill_value
            satellite.attrs["valid_range"] = np.asarray(valid_range, dtype=np.float32)
        qa.create_dataset("NavQualityFlag", data=np.zeros(15, dtype=np.uint8))
