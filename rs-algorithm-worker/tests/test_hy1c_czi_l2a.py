import json
import subprocess
import sys
import zipfile
from pathlib import Path

import pytest
import h5py
import numpy as np
from PIL import Image

from rs_algorithm_worker import l2a_pipeline
from rs_algorithm_worker.algorithms.features import FeatureParameters, extract_features
from rs_algorithm_worker.l2a_profile import parse_l2a_parameters
from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.product_adapters.hy1c_czi_l2a import (
    build_l2a_plan,
    inspect_l2a_manifest,
    load_reviewed_mapping,
    validate_source_against_mapping,
)
from rs_algorithm_worker.registry import dispatch
from rs_algorithm_worker.l2a_pipeline import _validate_output_grid, execute_l2a_stage


def test_built_worker_wheel_contains_the_reviewed_l2a_mapping(tmp_path):
    project_root = Path(__file__).resolve().parents[1]
    wheel_dir = tmp_path / "wheel"
    subprocess.run(
        [sys.executable, "-m", "pip", "wheel", "--no-deps", "--wheel-dir", str(wheel_dir), str(project_root)],
        check=True,
        capture_output=True,
        text=True,
    )

    wheel = next(wheel_dir.glob("rs_algorithm_worker-*.whl"))
    with zipfile.ZipFile(wheel) as archive:
        payload = json.loads(archive.read("rs_algorithm_worker/resources/hy1c_czi_l2a_v1.json"))

    assert payload["mappingVersion"] == "HY-1C-CZI-L2A-v1"


def test_reviewed_l2a_mapping_declares_bands_navigation_and_quality():
    mapping = load_reviewed_mapping()

    assert tuple(mapping.band_ids) == ("B460", "B560", "B650", "B825")
    assert mapping.longitude_path.startswith("/")
    assert mapping.latitude_path.startswith("/")
    assert mapping.quality_flags_path.startswith("/")
    assert mapping.default_bad_flag_names


def test_full_swath_50m_grid_fits_disk_backed_work_budget():
    _validate_output_grid(21_257, 11_372, 4)


def test_grid_exceeding_disk_backed_work_budget_is_rejected():
    with pytest.raises(ValueError, match="output grid is too large"):
        _validate_output_grid(30_000, 30_000, 4)


@pytest.mark.real_sample
def test_l2a_sample_conforms_to_reviewed_mapping(hy1c_l2a_manifest):
    main = next(item["path"] for item in hy1c_l2a_manifest["inputs"] if item["role"] == "MAIN")
    validate_source_against_mapping(main, load_reviewed_mapping())


def test_l2a_parameters_resolve_auto_utm_and_preserve_nearest_resampling():
    result = parse_l2a_parameters({
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"}, "algorithms": {},
    }, centre_longitude=120.0, centre_latitude=30.0)

    assert result.resolved_crs == "EPSG:32651"
    assert result.resolution_meters == 50
    assert result.science_resampling == "nearest"


def test_l2a_rejects_fusion_band_not_selected():
    with pytest.raises(ValueError, match="fusion bands"):
        parse_l2a_parameters({
            "schemaVersion": "1", "operation": "STANDARDIZE_L2A", "bands": ["B460", "B560"],
            "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
            "quality": {"policy": "default"},
            "algorithms": {"fusion": {
                "enabled": True, "red": "B650", "green": "B560", "blue": "B460",
                "lowPercentile": 2, "highPercentile": 98,
            }},
        }, centre_longitude=120.0, centre_latitude=30.0)


@pytest.mark.parametrize(("name", "configuration", "message"), [
    ("features", {"enabled": True, "metrics": ["unknown"], "windowSize": 3}, "feature metrics"),
    ("fusion", {"enabled": True, "red": "B650", "green": "B560", "blue": "B460",
                "lowPercentile": 98, "highPercentile": 2}, "fusion percentiles"),
    ("saliency", {"enabled": True, "source": "B560", "threshold": 1.1, "windowSize": 5}, "saliency threshold"),
])
def test_l2a_rejects_invalid_enabled_algorithm_configuration(name, configuration, message):
    with pytest.raises(ValueError, match=message):
        parse_l2a_parameters({
            "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
            "bands": ["B460", "B560", "B650", "B825"],
            "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
            "quality": {"policy": "default"}, "algorithms": {name: configuration},
        }, centre_longitude=120.0, centre_latitude=30.0)


@pytest.mark.real_sample
def test_l2a_adapter_selects_l2a_and_builds_standardization_dag(hy1c_l2a_manifest):
    candidate = inspect_l2a_manifest(hy1c_l2a_manifest)
    plan = build_l2a_plan(candidate, {
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"}, "algorithms": {},
    })

    assert candidate["profileKey"] == {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"}
    assert [stage["stageCode"] for stage in plan["stages"][:6]] == [
        "VALIDATE_SOURCE", "DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS",
        "GEOLOCATE_AND_GRID", "CROP_OPTIONAL", "WRITE_STANDARD_COG",
    ]


@pytest.mark.real_sample
def test_registry_routes_selected_l2a_candidate_to_the_l2a_plan(hy1c_l2a_manifest):
    candidate = inspect_l2a_manifest(hy1c_l2a_manifest)
    response = dispatch({
        "operation": "build-plan",
        "payload": {
            "candidate": candidate,
            "requestedPlanVersion": "plan-v3",
            "parameterSnapshot": {
                "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
                "bands": ["B460", "B560", "B650", "B825"],
                "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
                "quality": {"policy": "default"}, "algorithms": {},
            },
        },
    }, hy1c_l2a_manifest)

    assert response["version"] == "plan-v3"
    assert response["stages"][0]["stageKey"] == "VALIDATE_SOURCE"


@pytest.fixture
def synthetic_l2a_h5(tmp_path):
    path = tmp_path / "synthetic-l2a.h5"
    with h5py.File(path, "w") as handle:
        handle.attrs["Product Name"] = "H1C_OPER_CZI_L2A_SYNTHETIC"
        handle.attrs["Satellite Name"] = "HY-1C"
        handle.attrs["Sensor Name"] = "CZI"
        handle.attrs["Number of Bands"] = "4"
        handle.attrs["Scene Center Longitude"] = "120.0"
        handle.attrs["Scene Center Latitude"] = "30.0"
        geo = handle.create_group("Geophysical Data")
        for wavelength in (460, 560, 650, 825):
            dataset = geo.create_dataset(f"Rrc{wavelength}", data=np.array([[np.nan, 0.02], [0.2, 0.03]], dtype=np.float32))
            dataset.attrs.update({"Slope": 1.0, "Offset": 0.0, "FillValue": np.nan, "ValidMin": -0.015, "ValidMax": 0.115})
        flags = geo.create_dataset("l2_flags", data=np.array([[1, 0], [0, 0]], dtype=np.uint32))
        flags.attrs["Flag Names"] = b"ATMFAIL,LAND,BADANC,HISUNGLINT,HILT,HISENZ,SPARE,NEGLW,STLIGHT,CLOUD,SPARE,TURBID,HISOLZ,HITAUA,LOWLW,CHLFAIL,SPARE,ABSAER,SPARE,MAXAERITER,MODSUNGLINT,CHLRANGE,ATMWARN,DARKPIXEL,SEAICE,NAVFAIL,FILTER,BADSST,BADSSTALG,SPARE,SPARE,SPARE"
        nav = handle.create_group("Navigation Data")
        for name, values in (("Longitude", [[1200000, 1200100], [1200000, 1200100]]), ("Latitude", [[300100, 300100], [300000, 300000]])):
            dataset = nav.create_dataset(name, data=np.array(values, dtype=np.int32))
            dataset.attrs.update({"Slope": 0.0001, "Offset": 0.0, "ValidMin": -180.0 if name == "Longitude" else -90.0, "ValidMax": 180.0 if name == "Longitude" else 90.0})
        sensor = handle.create_group("Sensor Band parameters")
        sensor.create_dataset("Wavelength", data=np.array([[460], [560], [650], [825]], dtype=np.int32))
    return path


@pytest.fixture
def synthetic_l2a_manifest(synthetic_l2a_h5, tmp_path):
    metadata = tmp_path / "synthetic-l2a.xml"
    metadata.write_text("<ProductMetadata />", encoding="utf-8")
    browse = tmp_path / "synthetic-l2a.jpg"
    Image.new("RGB", (2, 2), (10, 20, 30)).save(browse)
    return {
        "inputs": [
            {"path": str(synthetic_l2a_h5), "role": "MAIN", "ordinal": 0,
             "storageUri": "rs://input/synthetic-l2a.h5"},
            {"path": str(metadata), "role": "METADATA", "ordinal": 0,
             "storageUri": "rs://input/synthetic-l2a.xml"},
            {"path": str(browse), "role": "BROWSE", "ordinal": 0,
             "storageUri": "rs://input/synthetic-l2a.jpg"},
        ]
    }


def test_registry_inspection_identifies_a_valid_l2a_package(synthetic_l2a_manifest):
    response = dispatch({"operation": "inspect", "payload": {}}, synthetic_l2a_manifest)

    assert response["candidates"][0]["profileKey"] == {
        "productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"
    }
    assert [file["role"] for file in response["candidates"][0]["files"]] == [
        "MAIN", "METADATA", "BROWSE"
    ]


def _standard_l2a_snapshot():
    return {
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"},
        "algorithms": {
            "features": {"enabled": False, "metrics": ["statistics"], "windowSize": 3},
            "fusion": {"enabled": False, "red": "B650", "green": "B560", "blue": "B460",
                       "lowPercentile": 2, "highPercentile": 98},
            "saliency": {"enabled": False, "source": "B560", "threshold": 0.5, "windowSize": 5},
        },
    }


def test_l2a_plan_has_one_executable_standardization_stage(synthetic_l2a_manifest):
    candidate = inspect_l2a_manifest(synthetic_l2a_manifest)

    plan = build_l2a_plan(candidate, _standard_l2a_snapshot())

    assert plan["version"] == "plan-v2"
    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_L2A"]
    assert {output["artifactType"] for output in plan["stages"][0]["outputs"]} == {
        "STANDARD_COG", "STANDARD_METADATA_JSON"
    }


def test_l2a_staged_plan_declares_real_dag(synthetic_l2a_manifest):
    from rs_algorithm_worker.product_adapters.hy1c_czi_l2a import build_l2a_staged_plan

    candidate = inspect_l2a_manifest(synthetic_l2a_manifest)
    plan = build_l2a_staged_plan(candidate, _standard_l2a_snapshot())

    assert plan["version"] == "plan-v3"
    assert [stage["stageKey"] for stage in plan["stages"]][:6] == [
        "VALIDATE_SOURCE", "DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS",
        "GEOLOCATE_AND_GRID", "CROP_CZI", "WRITE_STANDARD_COG",
    ]


def test_staged_feature_extraction_streams_cropped_input_and_matches_feature_values(tmp_path, monkeypatch):
    values = np.arange(4 * 9 * 11, dtype=np.float32).reshape(4, 9, 11) / 100
    values[:, 4, 5] = np.nan
    source = RasterData(
        values, "EPSG:4326", (1, 0, 0, 0, -1, 9), np.nan,
        tuple(BandMetadata(name) for name in ("B460", "B560", "B650", "B825")),
    )
    cropped = tmp_path / "cropped-czi.npz"
    l2a_pipeline._write_raster_intermediate(cropped, source, None, "CROP_CZI")
    expected = extract_features(
        source, FeatureParameters(("gradient", "local_variance"), window_size=3),
    )

    def reject_full_intermediate_read(*_args, **_kwargs):
        raise AssertionError("feature stage must read bounded source windows")

    monkeypatch.setattr(l2a_pipeline, "_read_raster_intermediate", reject_full_intermediate_read)
    output = tmp_path / "output"
    response = l2a_pipeline.execute_l2a_staged_stage(
        {
            "stageKey": "EXTRACT_CZI_FEATURES",
            "outputUri": "rs://output/features",
            "parameterSnapshot": json.dumps({
                **_standard_l2a_snapshot(),
                "algorithms": {"features": {
                    "enabled": True, "metrics": ["gradient", "local_variance"], "windowSize": 3,
                }},
            }),
        },
        {"output": {"path": str(output)}, "inputs": [{"role": "cropped", "path": str(cropped)}]},
    )

    assert response["status"] == "SUCCEEDED"
    import rasterio
    with rasterio.open(output / "features" / "czi-features.tif") as dataset:
        assert dataset.descriptions == tuple(expected.rasters)
        for index, raster in enumerate(expected.rasters.values(), start=1):
            np.testing.assert_allclose(dataset.read(index), raster.values, equal_nan=True)


def test_staged_saliency_streams_cropped_input_and_writes_outputs(tmp_path, monkeypatch):
    values = np.arange(4 * 9 * 11, dtype=np.float32).reshape(4, 9, 11) / 100
    values[:, 4, 5] = np.nan
    source = RasterData(
        values, "EPSG:4326", (1, 0, 0, 0, -1, 9), np.nan,
        tuple(BandMetadata(name) for name in ("B460", "B560", "B650", "B825")),
    )
    cropped = tmp_path / "cropped-czi.npz"
    l2a_pipeline._write_raster_intermediate(cropped, source, None, "CROP_CZI")

    def reject_full_intermediate_read(*_args, **_kwargs):
        raise AssertionError("saliency stage must read bounded source windows")

    monkeypatch.setattr(l2a_pipeline, "_read_raster_intermediate", reject_full_intermediate_read)
    output = tmp_path / "output"
    snapshot = _standard_l2a_snapshot()
    snapshot["algorithms"] = {
        **snapshot["algorithms"],
        "saliency": {"enabled": True, "source": "B460", "threshold": 0.5, "windowSize": 3},
    }
    response = l2a_pipeline.execute_l2a_staged_stage(
        {
            "stageKey": "DETECT_CZI_SALIENCY",
            "outputUri": "rs://output/saliency",
            "parameterSnapshot": json.dumps(snapshot),
        },
        {"output": {"path": str(output)}, "inputs": [{"role": "source", "path": str(cropped)}]},
    )

    assert response["status"] == "SUCCEEDED"
    assert {item["type"] for item in response["artifacts"]} == {
        "SALIENCY_SCORE_COG", "SALIENCY_MASK_COG", "SALIENCY_PREVIEW_PNG", "SALIENCY_METADATA_JSON",
    }


def test_staged_saliency_handles_preview_blocks_misaligned_with_preview_scale(tmp_path, monkeypatch):
    # A 4097-row raster uses a preview scale of three. The block beginning at
    # column 512 is not aligned to that scale, exposing the edge-size mismatch.
    values = np.zeros((1, 4097, 652), dtype=np.float32)
    source = RasterData(
        values, "EPSG:4326", (1, 0, 0, 0, -1, 4097), np.nan,
        (BandMetadata("B460"),),
    )
    cropped = tmp_path / "cropped-czi.npz"
    l2a_pipeline._write_raster_intermediate(cropped, source, None, "CROP_CZI")

    def fake_write_cog_blocks(*, output_path, **_kwargs):
        return l2a_pipeline.COGWriteResult(output_path, "0" * 64, 0, 0)

    monkeypatch.setattr(l2a_pipeline, "write_cog_blocks", fake_write_cog_blocks)
    snapshot = _standard_l2a_snapshot()
    snapshot["algorithms"] = {
        **snapshot["algorithms"],
        "saliency": {"enabled": True, "source": "B460", "threshold": 0.5, "windowSize": 3},
    }

    response = l2a_pipeline.execute_l2a_staged_stage(
        {
            "stageKey": "DETECT_CZI_SALIENCY",
            "outputUri": "rs://output/saliency",
            "parameterSnapshot": json.dumps(snapshot),
        },
        {"output": {"path": str(tmp_path / "output")}, "inputs": [{"role": "source", "path": str(cropped)}]},
    )

    assert response["status"] == "SUCCEEDED"


def test_registry_executes_l2a_staged_validation_with_logical_artifact(synthetic_l2a_manifest, tmp_path):
    output_dir = tmp_path / "output"
    manifest = synthetic_l2a_manifest | {"output": {"path": str(output_dir)}}

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "profileCode": "HY-1C-CZI",
            "profileVersion": "L2A-v1",
            "planVersion": "plan-v3",
            "stageKey": "VALIDATE_SOURCE",
            "parameterSnapshot": json.dumps(_standard_l2a_snapshot()),
            "outputUri": "rs://output/task-101/validation",
        },
    }, manifest)

    assert response["status"] == "SUCCEEDED"
    assert response["artifacts"][0]["type"] == "HY1C_INPUT_VALIDATION_JSON"
    assert response["artifacts"][0]["uri"] == "rs://output/task-101/validation/validation/source.json"
    assert response["artifacts"][0]["checksum"].startswith("sha256:")
    assert (output_dir / "validation" / "source.json").is_file()


def test_registry_executes_every_l2a_plan_v3_stage(synthetic_l2a_manifest, tmp_path):
    snapshot = _standard_l2a_snapshot()
    snapshot["roiWgs84"] = {
        "minLon": 120.0,
        "minLat": 30.0,
        "maxLon": 120.01,
        "maxLat": 30.01,
    }
    snapshot["algorithms"] = {
        "features": {"enabled": True, "metrics": ["statistics", "gradient"], "windowSize": 3},
        "fusion": {
            "enabled": True,
            "red": "B650",
            "green": "B560",
            "blue": "B460",
            "lowPercentile": 0,
            "highPercentile": 100,
        },
        "saliency": {"enabled": True, "source": "B560", "threshold": 0.5, "windowSize": 3},
    }
    output_dir = tmp_path / "output"
    raw_inputs = synthetic_l2a_manifest["inputs"]

    def run(stage_key, inputs):
        return dispatch({
            "operation": "execute-stage",
            "payload": {
                "profileCode": "HY-1C-CZI",
                "profileVersion": "L2A-v1",
                "planVersion": "plan-v3",
                "stageKey": stage_key,
                "parameterSnapshot": json.dumps(snapshot),
                "outputUri": "rs://output/task-102",
            },
        }, {"inputs": inputs, "output": {"path": str(output_dir)}})

    run("VALIDATE_SOURCE", raw_inputs)
    run("DECODE_SCALE_AND_NODATA", [raw_inputs[0]])
    run("APPLY_L2_FLAGS", [
        {"role": "decoded", "path": str(output_dir / "decoded" / "czi.npz")},
        raw_inputs[0],
    ])
    run("GEOLOCATE_AND_GRID", [
        {"role": "quality", "path": str(output_dir / "quality" / "czi.npz")},
        raw_inputs[0],
    ])
    run("CROP_CZI", [{"role": "grid", "path": str(output_dir / "grid" / "czi.npz")}])
    standard = run("WRITE_STANDARD_COG", [
        {"role": "cropped", "path": str(output_dir / "crop" / "czi.npz")}
    ])
    features = run("EXTRACT_CZI_FEATURES", [
        {"role": "cropped", "path": str(output_dir / "crop" / "czi.npz")}
    ])
    fusion = run("FUSE_CZI_BANDS", [
        {"role": "cropped", "path": str(output_dir / "crop" / "czi.npz")}
    ])
    saliency = run("DETECT_CZI_SALIENCY", [
        {"role": "source", "path": str(output_dir / "crop" / "czi.npz")}
    ])

    assert {item["type"] for item in standard["artifacts"]} == {
        "STANDARD_COG",
        "STANDARD_METADATA_JSON",
    }
    assert {item["type"] for item in features["artifacts"]} == {
        "FEATURE_COG",
        "FEATURE_METADATA_JSON",
    }
    assert {item["type"] for item in fusion["artifacts"]} == {
        "FUSION_COG",
        "FUSION_PREVIEW_PNG",
        "FUSION_METADATA_JSON",
    }
    assert {item["type"] for item in saliency["artifacts"]} == {
        "SALIENCY_SCORE_COG",
        "SALIENCY_MASK_COG",
        "SALIENCY_PREVIEW_PNG",
        "SALIENCY_METADATA_JSON",
    }
    validation_metadata = json.loads(
        (output_dir / "validation" / "source.json").read_text(encoding="utf-8")
    )
    standard_metadata = json.loads(
        (output_dir / "standard" / "czi-l2a.json").read_text(encoding="utf-8")
    )
    feature_metadata = json.loads(
        (output_dir / "features" / "czi-features.json").read_text(encoding="utf-8")
    )
    fusion_metadata = json.loads(
        (output_dir / "fusion" / "czi-rgb.json").read_text(encoding="utf-8")
    )
    saliency_metadata = json.loads(
        (output_dir / "saliency" / "metadata.json").read_text(encoding="utf-8")
    )
    assert validation_metadata["explainability"]["algorithm"] == "HY-1C CZI L2A 输入产品校验"
    assert standard_metadata["explainability"]["algorithm"] == "HY-1C CZI L2A 标准化处理"
    assert standard_metadata["explainability"]["qualityPolicy"]["excludedFlags"]
    assert feature_metadata["explainability"]["algorithm"] == "HY-1C CZI L2A 特征提取"
    assert fusion_metadata["explainability"]["algorithm"] == "HY-1C CZI L2A RGB 融合"
    assert saliency_metadata["explainability"]["algorithm"] == "HY-1C CZI L2A 局部对比显著性检测"


def test_registry_standardizes_l2a_into_archiveable_output_artifacts(synthetic_l2a_manifest, tmp_path):
    snapshot = _standard_l2a_snapshot()
    snapshot["roiWgs84"] = {"minLon": 119.999, "minLat": 29.999, "maxLon": 120.011, "maxLat": 30.011}
    snapshot["algorithms"] = {
        "features": {"enabled": True, "metrics": ["statistics", "gradient", "local_variance"], "windowSize": 3},
        "fusion": {"enabled": True, "red": "B650", "green": "B560", "blue": "B460",
                   "lowPercentile": 2, "highPercentile": 98},
        "saliency": {"enabled": True, "source": "B560", "threshold": 0.5, "windowSize": 5},
    }
    output_dir = tmp_path / "output"
    manifest = synthetic_l2a_manifest | {"output": {"path": str(output_dir)}}

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "profileCode": "HY-1C-CZI", "profileVersion": "L2A-v1", "stageKey": "STANDARDIZE_L2A",
            "parameterSnapshot": json.dumps(snapshot), "outputUri": "rs://output/task-101",
        },
    }, manifest)

    assert response["status"] == "SUCCEEDED"
    assert {artifact["type"] for artifact in response["artifacts"]} == {
        "STANDARD_COG", "STANDARD_METADATA_JSON", "FEATURE_COG", "FEATURE_METADATA_JSON",
        "FUSION_COG", "FUSION_PREVIEW_PNG", "SALIENCY_SCORE_COG", "SALIENCY_MASK_COG",
        "FUSION_METADATA_JSON", "SALIENCY_PREVIEW_PNG", "SALIENCY_METADATA_JSON",
    }
    assert all(artifact["uri"].startswith("rs://output/task-101/") for artifact in response["artifacts"])
    assert all(artifact["sizeBytes"] > 0 and artifact["checksum"].startswith("sha256:")
               for artifact in response["artifacts"])
    assert (output_dir / "standard" / "czi-l2a.tif").is_file()
    assert (output_dir / "standard" / "czi-l2a.json").is_file()


def test_decode_masks_fill_and_out_of_range_values(synthetic_l2a_h5, tmp_path):
    result = execute_l2a_stage("DECODE_SCALE_AND_NODATA", [synthetic_l2a_h5], {}, tmp_path)

    assert np.isnan(result.raster.values[:, 0, 0]).all()
    assert np.isnan(result.raster.values[:, 1, 0]).all()
    assert np.isfinite(result.raster.values[:, 0, 1]).all()


def test_quality_stage_applies_reviewed_bad_bits(synthetic_l2a_h5, tmp_path):
    decoded = execute_l2a_stage("DECODE_SCALE_AND_NODATA", [synthetic_l2a_h5], {}, tmp_path)
    result = execute_l2a_stage("APPLY_L2_FLAGS", [decoded, synthetic_l2a_h5], {}, tmp_path)

    assert not result.valid_mask[0, 0]
    assert result.valid_mask[0, 1]
