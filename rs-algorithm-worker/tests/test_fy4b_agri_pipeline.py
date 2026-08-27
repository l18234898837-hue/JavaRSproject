from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest
import rasterio
from affine import Affine

from rs_algorithm_worker.fy4b_agri_pipeline import (
    FY4B_QUALITY_CALIBRATION_DEGRADED,
    FY4B_QUALITY_FILL,
    FY4B_QUALITY_NAVIGATION_FAILURE,
    FY4B_QUALITY_OUT_OF_RANGE,
    build_quality_mask,
    calibrate_emissive,
    calibrate_reflective,
    execute_fy4b_stage,
    read_and_calibrate_l1,
)
from fy4b_fixture import make_fy4b_channel_fixture


def test_calibration_uses_lookup_tables_and_quality_flags():
    reflective = calibrate_reflective(
        np.array([[0, 2, 65535]], dtype=np.uint16),
        lookup=np.array([0.0, 0.1, 0.2], dtype=np.float32),
        fill_value=65535, scale=1.0, offset=0.0, esun=1000.0,
    )
    thermal = calibrate_emissive(
        np.array([[1, 65535]], dtype=np.uint16),
        lookup=np.array([180.0, 181.0], dtype=np.float32),
        fill_value=65535, scale=0.5, offset=1.0,
    )
    quality = build_quality_mask(
        np.array([[2, 65535]], dtype=np.uint16), 65535, (0, 4095),
        navigation_ok=False, calibration_ok=False,
    )

    np.testing.assert_allclose(reflective.reflectance[0, :2], [0.0, 0.2])
    assert reflective.radiance[0, 1] == np.float32(0.2 * 1000.0 / np.pi)
    assert np.isnan(reflective.reflectance[0, 2])
    assert thermal.brightness_temperature[0, 0] == np.float32(181.0)
    assert thermal.radiance[0, 0] == np.float32(1.5)
    assert np.isnan(thermal.brightness_temperature[0, 1])
    assert quality.reason_bits[0, 0] & FY4B_QUALITY_NAVIGATION_FAILURE
    assert quality.reason_bits[0, 0] & FY4B_QUALITY_CALIBRATION_DEGRADED
    assert quality.reason_bits[0, 1] & FY4B_QUALITY_FILL


def test_windowed_l1_reader_applies_hdf_calibration_and_quality_metadata(tmp_path):
    path = make_fy4b_channel_fixture(tmp_path, channel=2)

    science, quality, summary = read_and_calibrate_l1(path, "L1_0500M", rows_per_window=2)

    # DN 2 uses CALChannel02[2] and the declared scale/offset. The night-side
    # pixel (DN 1, solar zenith 95 degrees) is intentionally invalidated.
    expected = (0.2 * 0.5 + 1.0) / np.cos(np.deg2rad(30.0))
    assert science[2].shape == (3, 3)
    assert science[2][0, 2] == pytest.approx(expected, rel=1e-6)
    assert np.isnan(science[2][0, 1])
    assert quality[2][2, 2] & FY4B_QUALITY_OUT_OF_RANGE
    assert summary["channels"]["2"]["units"] == "1"


def test_calibration_stage_writes_native_geostationary_science_and_quality_cogs(tmp_path, monkeypatch):
    values = {2: np.arange(9, dtype=np.float32).reshape(3, 3)}
    quality = {2: np.zeros((3, 3), dtype=np.uint32)}
    monkeypatch.setattr(
        "rs_algorithm_worker.fy4b_agri_pipeline.read_and_calibrate_l1",
        lambda path, role, rows_per_window=256: (values, quality, {"role": role}),
    )
    grid = tmp_path / "nominal-grid.json"
    grid.write_text(json.dumps({
        "crs": "+proj=geos +lon_0=105 +h=35786000 +a=6377830 +b=6356471 +sweep=x +units=m +no_defs",
        "transform": [4008.0, 0, -6012.0, 0, -4008.0, 6012.0],
        "width": 3, "height": 3,
    }), encoding="utf-8")
    response = execute_fy4b_stage(
        _payload("CALIBRATE_AND_GEOREFERENCE_FY4B"),
        _manifest(tmp_path, {**{f"L1_{r}": tmp_path / "source.hdf" for r in ("0500M", "1000M", "2000M", "4000M")}, "FY4B_NOMINAL_GRID_JSON": grid}),
    )

    artifacts = {item["type"]: item for item in response["artifacts"]}
    assert {"FY4B_NATIVE_0500M_RASTER", "FY4B_NATIVE_0500M_QUALITY"} <= set(artifacts)
    with rasterio.open(tmp_path / "output" / "native" / "0500m.tif") as source:
        assert "+proj=geos" in source.crs.to_proj4()
        assert source.transform == Affine(4008.0, 0, -6012.0, 0, -4008.0, 6012.0)
        assert source.descriptions == ("C02_REFLECTANCE",)
    with rasterio.open(tmp_path / "output" / "native" / "quality-0500m.tif") as source:
        assert source.dtypes == ("uint32",)
        assert source.descriptions == ("quality_mask",)


def test_calibration_uses_each_fdi_file_nominal_grid(tmp_path, monkeypatch):
    import h5py

    def source(path: Path, sampling_angle: float):
        with h5py.File(path, "w") as handle:
            handle.attrs["NOMCenterLon"] = 105.0
            handle.attrs["NOMSatHeight"] = 35786000.0
            handle.attrs["dEA"] = 6378137.0
            handle.attrs["dObRecFlat"] = 298.257223563
            handle.attrs["dSamplingAngle"] = sampling_angle
            handle.attrs["dSteppingAngle"] = sampling_angle

    first, second = tmp_path / "500m.hdf", tmp_path / "1000m.hdf"
    source(first, 10.0)
    source(second, 20.0)
    monkeypatch.setattr(
        "rs_algorithm_worker.fy4b_agri_pipeline.read_and_calibrate_l1",
        lambda path, role, rows_per_window=256: (
            {2: np.ones((3 if role == "L1_0500M" else 2, 3 if role == "L1_0500M" else 2), dtype=np.float32)},
            {2: np.zeros((3 if role == "L1_0500M" else 2, 3 if role == "L1_0500M" else 2), dtype=np.uint32)},
            {"role": role},
        ),
    )
    nominal = tmp_path / "nominal-grid.json"
    nominal.write_text(json.dumps({
        "crs": "+proj=geos +lon_0=105 +h=35786000 +a=6378137 +b=6356752 +sweep=x +units=m +no_defs",
        "transform": [1, 0, 0, 0, -1, 0],
    }), encoding="utf-8")

    execute_fy4b_stage(
        _payload("CALIBRATE_AND_GEOREFERENCE_FY4B"),
        _manifest(tmp_path, {
            "L1_0500M": first, "L1_1000M": second, "L1_2000M": first, "L1_4000M": first,
            "FY4B_NOMINAL_GRID_JSON": nominal,
        }),
    )

    with rasterio.open(tmp_path / "output" / "native" / "0500m.tif") as half_km, \
            rasterio.open(tmp_path / "output" / "native" / "1000m.tif") as one_km:
        assert half_km.width == half_km.height == 3
        assert one_km.width == one_km.height == 2
        assert one_km.transform.a == pytest.approx(half_km.transform.a * 2.0)


def test_standard_stage_copies_all_declared_cogs_and_safe_summary(tmp_path):
    grid_paths = {}
    for resolution, channels in (("0500M", ("C02_REFLECTANCE",)), ("1000M", ("C01_REFLECTANCE", "C02_REFLECTANCE", "C03_REFLECTANCE")), ("2000M", ("C01_REFLECTANCE",)), ("4000M", ("C13_BRIGHTNESS_TEMPERATURE", "C14_BRIGHTNESS_TEMPERATURE"))):
        path = tmp_path / f"{resolution}.tif"
        _write_test_cog(path, channels)
        grid_paths[f"FY4B_GRIDDED_{resolution}_RASTER"] = path
        quality = tmp_path / f"quality-{resolution}.tif"
        _write_test_cog(quality, tuple("quality_mask" for _ in channels), dtype="uint32", nodata=np.uint32(0xFFFFFFFF))
        grid_paths[f"FY4B_GRIDDED_{resolution}_QUALITY"] = quality
    response = execute_fy4b_stage(_payload("WRITE_FY4B_STANDARD_COG"), _manifest(tmp_path, grid_paths))

    types = {item["type"] for item in response["artifacts"]}
    assert {"FY4B_RED_500M_COG", "FY4B_VIS_NIR_1000M_COG", "FY4B_SPECTRAL_2000M_COG", "FY4B_MULTISPECTRAL_4000M_COG", "FY4B_STANDARD_SUMMARY_JSON"} <= types
    summary = json.loads((tmp_path / "output" / "standard" / "summary.json").read_text(encoding="utf-8"))
    assert summary["navigationStatus"] == "NOMINAL_UNVERIFIED"
    assert str(tmp_path) not in json.dumps(summary)


def test_reproject_stage_crops_science_and_quality_to_wgs84_roi(tmp_path):
    science = tmp_path / "native-science.tif"
    quality = tmp_path / "native-quality.tif"
    _write_test_cog(science, ("C02_REFLECTANCE",))
    _write_test_cog(quality, ("quality_mask",), dtype="uint32", nodata=np.uint32(0xFFFFFFFF))
    payload = _payload("REPROJECT_AND_CROP_FY4B")
    payload["parameterSnapshot"]["roi"] = [100.05, 29.75, 100.25, 29.95]

    response = execute_fy4b_stage(payload, _manifest(tmp_path, {
        **{f"FY4B_NATIVE_{r}_RASTER": science for r in ("0500M", "1000M", "2000M", "4000M")},
        **{f"FY4B_NATIVE_{r}_QUALITY": quality for r in ("0500M", "1000M", "2000M", "4000M")},
    }))

    assert {
        "FY4B_GRIDDED_0500M_RASTER", "FY4B_GRIDDED_0500M_QUALITY",
    }.issubset({item["type"] for item in response["artifacts"]})
    with rasterio.open(tmp_path / "output" / "grid" / "0500m.tif") as cropped, \
            rasterio.open(tmp_path / "output" / "grid" / "quality-0500m.tif") as quality_cropped:
        assert cropped.width == cropped.height == 2
        assert quality_cropped.dtypes == ("uint32",)
        assert quality_cropped.width == quality_cropped.height == 2


def test_reproject_stage_rejects_non_intersecting_roi(tmp_path):
    science = tmp_path / "native-science.tif"
    _write_test_cog(science, ("C02_REFLECTANCE",))
    payload = _payload("REPROJECT_AND_CROP_FY4B")
    payload["parameterSnapshot"]["roi"] = [-120.0, -20.0, -110.0, -10.0]

    with pytest.raises(ValueError, match="FY-4B ROI does not intersect raster"):
        execute_fy4b_stage(payload, _manifest(tmp_path, {
            **{f"FY4B_NATIVE_{r}_RASTER": science for r in ("0500M", "1000M", "2000M", "4000M")},
            **{f"FY4B_NATIVE_{r}_QUALITY": science for r in ("0500M", "1000M", "2000M", "4000M")},
        }))


def test_feature_stage_computes_cross_resolution_physical_metrics(tmp_path):
    one_km = tmp_path / "1km.tif"; red = tmp_path / "500m.tif"; thermal = tmp_path / "4km.tif"
    _write_values(one_km, [2.0, 4.0, 6.0], ("C01_REFLECTANCE", "C02_REFLECTANCE", "C03_REFLECTANCE"))
    _write_values(red, [2.0], ("C02_REFLECTANCE",))
    _write_values(thermal, [280.0, 275.0], ("C13_BRIGHTNESS_TEMPERATURE", "C14_BRIGHTNESS_TEMPERATURE"))
    payload = _payload("EXTRACT_FY4B_FEATURES")
    payload["parameterSnapshot"]["features"] = {"enabled": True, "metrics": ["toa_nir_red_nd", "btd_c13_c14"], "windowSize": 3}
    response = execute_fy4b_stage(payload, _manifest(tmp_path, {
        "FY4B_VIS_NIR_1000M_COG": one_km, "FY4B_RED_500M_COG": red, "FY4B_MULTISPECTRAL_4000M_COG": thermal,
    }))
    assert response["status"] == "SUCCEEDED"
    with rasterio.open(tmp_path / "output" / "features" / "fy4b-features.tif") as result:
        names = result.descriptions
        nd = result.read(names.index("toa_nir_red_nd") + 1)
        btd = result.read(names.index("btd_c13_c14") + 1)
        assert nd[1, 1] == pytest.approx((6.0 - 2.0) / 8.0)
        assert btd[1, 1] == pytest.approx(5.0)


def test_saliency_stage_rejects_undeclared_feature_source(tmp_path):
    payload = _payload("DETECT_FY4B_SALIENCY")
    payload["parameterSnapshot"]["saliency"] = {"enabled": True, "source": "btd_c13_c14", "threshold": 0.5, "windowSize": 3}
    with pytest.raises(ValueError, match="exactly features"):
        execute_fy4b_stage(payload, _manifest(tmp_path, {}))


def test_fusion_stage_uses_declared_c03_c02_c01_order(tmp_path):
    source = tmp_path / "1km.tif"
    _write_values(source, [1.0, 2.0, 3.0], ("C01_REFLECTANCE", "C02_REFLECTANCE", "C03_REFLECTANCE"))
    payload = _payload("FUSE_FY4B_BANDS")
    payload["parameterSnapshot"]["fusion"] = {"enabled": True, "mode": "NIR_RED_BLUE", "percentiles": [2, 98]}

    response = execute_fy4b_stage(payload, _manifest(tmp_path, {"FY4B_VIS_NIR_1000M_COG": source}))

    assert response["artifacts"][0]["type"] == "FY4B_FALSE_COLOR_COG"
    with rasterio.open(tmp_path / "output" / "fusion" / "false-color.tif") as result:
        assert result.descriptions == (
            "NIR_RED_BLUE_FALSE_COLOR_R", "NIR_RED_BLUE_FALSE_COLOR_G", "NIR_RED_BLUE_FALSE_COLOR_B",
        )


def _payload(stage_key: str):
    return {"profileCode": "FY-4B-AGRI", "stageKey": stage_key, "outputUri": "rs://output/task-101", "parameterSnapshot": {"schemaVersion": "1", "targetCrs": "EPSG:4326", "features": {"enabled": False}, "fusion": {"enabled": False}, "saliency": {"enabled": False}}}


def _manifest(root: Path, inputs: dict[str, Path]):
    return {"output": {"path": str(root / "output")}, "inputs": [{"role": role, "artifactType": role, "path": str(path)} for role, path in inputs.items()]}


def _write_test_cog(path: Path, descriptions: tuple[str, ...], *, dtype="float32", nodata=np.nan):
    path.parent.mkdir(parents=True, exist_ok=True)
    values = np.stack([np.full((3, 3), index + 1, dtype=dtype) for index in range(len(descriptions))])
    with rasterio.open(path, "w", driver="GTiff", width=3, height=3, count=len(descriptions), dtype=dtype, crs="EPSG:4326", transform=Affine(0.1, 0, 100, 0, -0.1, 30), nodata=nodata) as dataset:
        dataset.write(values)
        for index, name in enumerate(descriptions, 1):
            dataset.set_band_description(index, name)


def _write_values(path: Path, bands: list[float], descriptions: tuple[str, ...]):
    path.parent.mkdir(parents=True, exist_ok=True)
    values = np.stack([np.full((3, 3), value, dtype=np.float32) for value in bands])
    with rasterio.open(path, "w", driver="GTiff", width=3, height=3, count=len(bands), dtype="float32", crs="EPSG:4326", transform=Affine(0.1, 0, 100, 0, -0.1, 30), nodata=np.nan) as dataset:
        dataset.write(values)
        for index, name in enumerate(descriptions, 1):
            dataset.set_band_description(index, name)
