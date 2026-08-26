from __future__ import annotations

import json
import hashlib

import h5py
import numpy as np
import pytest
import rasterio
from affine import Affine

from fy3h_fixture import COLUMNS, ROWS, make_fy3h_pair
from rs_algorithm_worker.fy3h_mersi_pipeline import (
    build_quality_mask,
    summarize_quality,
    calibrate_emissive,
    calibrate_mersi,
    calibrate_reflective,
    decode_mersi_pair,
)
from rs_algorithm_worker.models_fy3h import (
    CalibratedMersi,
    DecodedMersi,
    read_calibrated_mersi,
    read_decoded_mersi,
    write_calibrated_mersi,
    write_decoded_mersi,
    QUALITY_FRAME_PREPROCESS_FAILURE,
    QUALITY_SATURATED,
)
from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.fy3h_mersi_pipeline import write_standard_mersi_cogs
from rs_algorithm_worker.fy3h_mersi_pipeline import execute_fy3h_stage
from rs_algorithm_worker.operators.cog import write_cog


def test_reflective_calibration_uses_official_formula_and_masks_fill_value():
    result = calibrate_reflective(
        np.array([[100, 65535]], dtype=np.uint16),
        slope=1,
        intercept=0,
        coefficients=np.array([1, 0, 0], dtype=np.float32),
        solar_zenith=np.array([[0, 0]], dtype=np.float32),
        earth_sun_distance_ratio=1,
        solar_irradiance=1000,
        fill_value=65535,
    )

    assert result.reflectance[0, 0] == pytest.approx(1.0)
    assert result.radiance[0, 0] == pytest.approx(1000 / np.pi)
    assert np.isnan(result.reflectance[0, 1])
    assert np.isnan(result.radiance[0, 1])


def test_emissive_calibration_uses_scan_coefficients_planck_and_tbb_transform():
    result = calibrate_emissive(
        np.array([[100]], dtype=np.uint16),
        slope=1,
        intercept=0,
        scan_coefficients=np.array([[0, 1, 0, 0]], dtype=np.float32),
        scan_index=np.array([0]),
        wave_number=1000,
        tbb_coefficients=np.array([1, 0], dtype=np.float32),
        fill_value=65535,
    )

    assert np.isfinite(result.radiance[0, 0])
    assert np.isfinite(result.brightness_temperature[0, 0])


def test_decode_mersi_pair_reads_windows_and_maps_each_ten_rows_to_one_scan(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(observation, "r+") as handle:
        handle["Data/EV_250_Aggr.1KM_RefSB"][0, :, :] = 100
        handle["Calibration/VIS_Cal_Coeff"][0, :] = [1, 0, 0]
        handle["Calibration/IR_Cal_Coeff"][:, 1, :] = 1
    with h5py.File(geo, "r+") as handle:
        handle["Geolocation/Latitude"][:, :] = 10
        handle["Geolocation/Longitude"][:, :] = 20
        handle["Geolocation/SolarZenith"][:, :] = 3000
        handle["Geolocation/SensorZenith"][:, :] = 2000

    decoded = decode_mersi_pair(observation, geo, rows_per_window=7)

    assert decoded.dn_by_channel[1].shape == (ROWS, COLUMNS)
    assert decoded.latitude[0, 0] == pytest.approx(10)
    assert decoded.solar_zenith_degrees[0, 0] == pytest.approx(30)
    np.testing.assert_array_equal(decoded.scan_index, np.repeat(np.arange(2), 10))
    assert decoded.frame_flags.shape == (2,)
    assert decoded.geo_flags.shape == (2, 1)
    np.testing.assert_array_equal(decoded.calibration["valid_range"][0], [0, 65534])


def test_decoded_mersi_npz_and_json_round_trip_without_pickle(tmp_path):
    decoded = DecodedMersi(
        dn_by_channel={1: np.array([[100]], dtype=np.uint16)},
        longitude=np.array([[20]], dtype=np.float32),
        latitude=np.array([[10]], dtype=np.float32),
        solar_zenith_degrees=np.array([[30]], dtype=np.float32),
        sensor_zenith_degrees=np.array([[20]], dtype=np.float32),
        frame_flags=np.array([0], dtype=np.uint16),
        geo_flags=np.array([[0]], dtype=np.uint8),
        scan_index=np.array([0]),
        metadata={"formulaVersion": "fy3h-mersi-l1-v1"},
    )
    path = tmp_path / "decoded.npz"

    write_decoded_mersi(path, decoded)
    actual = read_decoded_mersi(path)

    np.testing.assert_array_equal(actual.dn_by_channel[1], decoded.dn_by_channel[1])
    assert actual.metadata["formulaVersion"] == "fy3h-mersi-l1-v1"
    with np.load(path, allow_pickle=False) as values:
        assert "longitude" in values.files


def test_decoded_mersi_reader_rejects_missing_coordinate_channel_or_metadata_version(tmp_path):
    path = tmp_path / "decoded.npz"
    np.savez_compressed(path, dn__1=np.ones((1, 1), dtype=np.uint16), latitude=np.ones((1, 1)),
                        solar_zenith_degrees=np.ones((1, 1)), sensor_zenith_degrees=np.ones((1, 1)),
                        frame_flags=np.array([0]), geo_flags=np.array([[0]]), scan_index=np.array([0]))
    path.with_suffix(".json").write_text(json.dumps({"modelVersion": "fy3h-mersi-model-v1", "metadata": {}}), encoding="utf-8")

    with pytest.raises(ValueError, match="longitude|channel|metadata version"):
        read_decoded_mersi(path)


def test_calibrated_mersi_npz_and_json_round_trip_without_pickle(tmp_path):
    calibrated = CalibratedMersi(
        reflectance_by_channel={1: np.array([[0.2]], dtype=np.float32)},
        radiance_by_channel={1: np.array([[1.0]], dtype=np.float32), 20: np.array([[2.0]], dtype=np.float32)},
        brightness_temperature_by_channel={20: np.array([[280.0]], dtype=np.float32)},
        metadata={"formulaVersion": "fy3h-mersi-l1-v1"},
    )
    path = tmp_path / "calibrated.npz"

    write_calibrated_mersi(path, calibrated)
    actual = read_calibrated_mersi(path)

    assert actual.brightness_temperature_by_channel[20][0, 0] == pytest.approx(280.0)
    assert actual.metadata["formulaVersion"] == "fy3h-mersi-l1-v1"


def test_decode_rejects_navigation_angle_without_scale_or_slope(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(geo, "r+") as handle:
        attributes = handle["Geolocation/SolarZenith"].attrs
        del attributes["Slope"]
        del attributes["Intercept"]

    with pytest.raises(ValueError, match="navigation scale and offset"):
        decode_mersi_pair(observation, geo, rows_per_window=10)


def test_calibrate_mersi_applies_hdf_thermal_coefficients_by_scan_frame(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(observation, "r+") as handle:
        handle["Data/EV_1KM_Emissive"][0, :, :] = 100
        handle["Calibration/IR_Cal_Coeff"][:, 1, :] = 1
        wavelengths = np.asarray(handle.attrs["Effect_Center_WaveLength"], dtype=np.float32)
        wavelengths[19] = 10.0
        handle.attrs["Effect_Center_WaveLength"] = wavelengths

    decoded = decode_mersi_pair(observation, geo, rows_per_window=7)
    calibrated = calibrate_mersi(decoded)
    expected = calibrate_emissive(
        decoded.dn_by_channel[20], slope=1, intercept=0,
        scan_coefficients=np.array([[0, 1, 0, 0], [0, 1, 0, 0]], dtype=np.float32),
        scan_index=decoded.scan_index, wave_number=1000,
        tbb_coefficients=np.array([1, 1], dtype=np.float32), fill_value=65535,
    )

    assert np.isfinite(calibrated.radiance_by_channel[20]).all()
    assert np.isfinite(calibrated.brightness_temperature_by_channel[20]).all()
    np.testing.assert_allclose(calibrated.brightness_temperature_by_channel[20], expected.brightness_temperature)


def test_quality_mask_preserves_reason_bits_and_expands_failed_frame_to_ten_rows():
    result = build_quality_mask(
        raw=np.array([[1], [65534]] + [[1]] * 18, dtype=np.uint16),
        frame_flags=np.array([0, 1 << 25], dtype=np.uint64),
        geo_flags=np.zeros((2, 1), dtype=np.uint8),
        rows_per_scan=10,
    )

    assert result.valid[0, 0]
    assert not result.valid[1, 0]
    assert not result.valid[10, 0]
    assert result.reason_bits[1, 0] & QUALITY_SATURATED
    assert result.reason_bits[10, 0] & QUALITY_FRAME_PREPROCESS_FAILURE
    summary = summarize_quality({1: result})
    assert summary["ruleVersion"] == "fy3h-mersi-l1-quality-v1"
    assert summary["channelQuality"]["CH01"]["validPixelCount"] == 9
    assert summary["reasonCounts"]["FRAME_PREPROCESS_FAILURE"] == 10


def test_standard_mersi_cogs_have_declared_bands_and_safe_summary(tmp_path):
    names = tuple([f"CH{band:02d}_REFLECTANCE" for band in range(1, 20)] +
                  [f"CH{band:02d}_BRIGHTNESS_TEMPERATURE" for band in range(20, 26)])
    science = RasterData(np.ones((25, 2, 2), dtype=np.float32), "EPSG:4326", Affine(0.01, 0, 120, 0, -0.01, 31),
                         np.nan, tuple(BandMetadata(name) for name in names))
    quality = RasterData(np.zeros((2, 2), dtype=np.uint32), "EPSG:4326", science.transform, np.uint32(0xFFFFFFFF),
                         (BandMetadata("quality_mask", unit="bitmask"),))

    result = write_standard_mersi_cogs(science, quality, tmp_path, {"inputFiles": ["sample.HDF"]})

    with rasterio.open(result.standard_cog) as dataset:
        assert dataset.count == 25
        assert dataset.descriptions[0] == "CH01_REFLECTANCE"
        assert dataset.descriptions[-1] == "CH25_BRIGHTNESS_TEMPERATURE"
        assert dataset.crs.to_string() == "EPSG:4326"
    with rasterio.open(result.quality_cog) as dataset:
        assert dataset.descriptions == ("quality_mask",)
    assert "C:\\" not in result.summary_path.read_text(encoding="utf-8")


def test_execute_feature_stage_writes_requested_feature_bands_and_statistics_to_declared_artifacts(tmp_path):
    standard = _standard_cog(tmp_path / "input" / "standard.tif")
    output = tmp_path / "output"

    response = execute_fy3h_stage({
        "stageKey": "EXTRACT_MERSI_FEATURES",
        "outputUri": "rs://output/task-features",
        "parameterSnapshot": {"bands": [1], "features": {
            "enabled": True, "metrics": ["statistics", "gradient", "local_variance"], "windowSize": 3,
        }},
    }, {"output": {"path": str(output)}, "inputs": [{"role": "standard", "path": str(standard)}]})

    _assert_stage_artifacts(response, "rs://output/task-features", {
        "MERSI_FEATURE_COG": "features/mersi.tif",
        "MERSI_FEATURE_SUMMARY_JSON": "features/summary.json",
    }, output)
    with rasterio.open(output / "features" / "mersi.tif") as dataset:
        assert dataset.descriptions == ("gradient:CH01_REFLECTANCE", "local_variance:CH01_REFLECTANCE")
        assert dataset.crs.to_string() == "EPSG:4326"
    summary = json.loads((output / "features" / "summary.json").read_text(encoding="utf-8"))
    assert summary["CH01_REFLECTANCE"]["mean"] == pytest.approx(8.5)
    assert str(standard) not in json.dumps(summary)


def test_execute_fusion_stage_maps_selected_rgb_bands_and_marks_display_product(tmp_path):
    standard = _standard_cog(tmp_path / "input" / "standard.tif")
    output = tmp_path / "output"

    response = execute_fy3h_stage({
        "stageKey": "FUSE_MERSI_BANDS",
        "outputUri": "rs://output/task-fusion",
        "parameterSnapshot": {"bands": [1, 2, 3], "fusion": {
            "enabled": True, "red": 3, "green": 2, "blue": 1, "percentiles": [0, 100],
        }},
    }, {"output": {"path": str(output)}, "inputs": [{"role": "standard", "path": str(standard)}]})

    _assert_stage_artifacts(response, "rs://output/task-fusion", {"MERSI_FUSION_COG": "fusion/mersi-rgb.tif"}, output)
    assert response["artifacts"][0]["displayProduct"] is True
    with rasterio.open(output / "fusion" / "mersi-rgb.tif") as dataset:
        assert dataset.descriptions == ("red", "green", "blue")
        assert dataset.crs.to_string() == "EPSG:4326"
        assert dataset.transform == Affine(0.01, 0, 120, 0, -0.01, 31)


@pytest.mark.parametrize("inputs", [
    lambda standard: [{"role": "standard", "path": str(standard)}, {"role": "standard", "path": str(standard)}],
    lambda standard: [{"role": "standard", "path": str(standard)}, {"role": "extra", "path": str(standard)}],
])
def test_execute_optional_stage_rejects_duplicate_or_extra_input_roles(tmp_path, inputs):
    standard = _standard_cog(tmp_path / "input" / "standard.tif")

    with pytest.raises(ValueError, match="requires exactly|must be unique"):
        execute_fy3h_stage({
            "stageKey": "FUSE_MERSI_BANDS",
            "outputUri": "rs://output/task-fusion",
            "parameterSnapshot": {"bands": [1, 2, 3], "fusion": {
                "enabled": True, "red": 3, "green": 2, "blue": 1,
            }},
        }, {"output": {"path": str(tmp_path / "output")}, "inputs": inputs(standard)})


def test_execute_saliency_stage_uses_declared_standard_or_feature_source_and_writes_matching_grids(tmp_path):
    standard = _standard_cog(tmp_path / "input" / "standard.tif")
    feature_output = tmp_path / "feature-output"
    execute_fy3h_stage({
        "stageKey": "EXTRACT_MERSI_FEATURES",
        "outputUri": "rs://output/task-features",
        "parameterSnapshot": {"bands": [1], "features": {"enabled": True, "metrics": ["gradient"]}},
    }, {"output": {"path": str(feature_output)}, "inputs": [{"role": "standard", "path": str(standard)}]})

    for source, role, path in [
        ("CH01_REFLECTANCE", "standard", standard),
        ("gradient:CH01_REFLECTANCE", "features", feature_output / "features" / "mersi.tif"),
    ]:
        output = tmp_path / role
        response = execute_fy3h_stage({
            "stageKey": "DETECT_MERSI_SALIENCY",
            "outputUri": f"rs://output/task-saliency-{role}",
            "parameterSnapshot": {"bands": [1], "features": {"enabled": True, "metrics": ["gradient"]}, "saliency": {
                "enabled": True, "source": source, "windowSize": 3, "threshold": 0.5,
            }},
        }, {"output": {"path": str(output)}, "inputs": [{"role": role, "path": str(path)}]})

        _assert_stage_artifacts(response, f"rs://output/task-saliency-{role}", {
            "MERSI_SALIENCY_SCORE_COG": "saliency/score.tif",
            "MERSI_SALIENCY_MASK_COG": "saliency/mask.tif",
        }, output)
        with rasterio.open(output / "saliency" / "score.tif") as score, rasterio.open(output / "saliency" / "mask.tif") as mask:
            assert (score.crs, score.transform, score.height, score.width) == (mask.crs, mask.transform, mask.height, mask.width)


def _standard_cog(path):
    values = np.stack([np.arange(16, dtype=np.float32).reshape(4, 4) + channel for channel in range(1, 26)])
    metadata = tuple(BandMetadata(f"CH{channel:02d}_{'REFLECTANCE' if channel <= 19 else 'BRIGHTNESS_TEMPERATURE'}")
                     for channel in range(1, 26))
    path.parent.mkdir(parents=True)
    return write_cog(RasterData(values, "EPSG:4326", Affine(0.01, 0, 120, 0, -0.01, 31), np.nan, metadata), path).path


def _assert_stage_artifacts(response, output_uri, expected, output):
    assert response["status"] == "SUCCEEDED"
    artifacts = {artifact["type"]: artifact for artifact in response["artifacts"]}
    assert set(artifacts) == set(expected)
    for kind, relative_path in expected.items():
        artifact = artifacts[kind]
        content = (output / relative_path).read_bytes()
        assert artifact["uri"] == f"{output_uri}/{relative_path}"
        assert artifact["sizeBytes"] == len(content)
        assert artifact["checksum"] == "sha256:" + hashlib.sha256(content).hexdigest()
