import json
import os
from pathlib import Path

import h5py
import pytest

from fy4b_fixture import make_fy4b_package
from rs_algorithm_worker.fy4b_agri_profile import parse_fy4b_parameters
from rs_algorithm_worker.product_adapters.fy4b_agri import build_integrated_plan, build_plan, inspect_manifest


@pytest.mark.real_sample
def test_fy4b_emits_geostationary_candidate():
    root_text = os.getenv("MULTISOURCE_FY4B_SAMPLE_ROOT")
    root = Path(root_text) if root_text else None
    paths = sorted(root.glob("*.HDF")) if root and root.is_dir() else []
    if len(paths) != 5:
        pytest.skip("MULTISOURCE_FY4B_SAMPLE_ROOT is absent or incomplete")
    manifest = {"inputs": [
        {"path": str(path), "role": "UNCLASSIFIED", "ordinal": index,
         "storageUri": f"file:///{path.name}"}
        for index, path in enumerate(paths)
    ]}
    candidates = inspect_manifest(manifest)
    assert len(candidates) == 1
    assert candidates[0]["dataShape"] == "GEOSTATIONARY_RASTER"
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"


def test_inspect_requires_the_complete_ordered_fy4b_package(tmp_path):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]

    assert candidate["profileKey"] == {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}
    assert [item["role"] for item in candidate["files"]] == [
        "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
    ]


def test_uuid_physical_names_use_manifest_original_names_for_inspect_and_build_plan(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    for index, item in enumerate(manifest["inputs"]):
        source = Path(item["path"])
        physical = source.with_name(f"00000000-0000-0000-0000-{index:012d}.HDF")
        source.rename(physical)
        item["path"] = str(physical)
        item["originalFileName"] = source.name

    candidate = inspect_manifest(manifest)[0]
    plan = build_integrated_plan(candidate)

    assert candidate["files"][0]["originalFileName"].startswith("FY4B-_AGRI--")
    assert plan["stages"][0]["stageKey"] == "STANDARDIZE_FY4B_AGRI"


def test_inspect_rejects_missing_duplicate_and_role_kind_mismatch(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    with pytest.raises(ValueError, match="complete five-file package"):
        inspect_manifest({"inputs": manifest["inputs"][:-1]})
    with pytest.raises(ValueError, match="duplicate roles"):
        inspect_manifest({"inputs": manifest["inputs"] + [manifest["inputs"][0]]})

    manifest["inputs"][3]["role"] = "GEO_4000M"
    with pytest.raises(ValueError, match="role does not match"):
        inspect_manifest(manifest)


def test_inspect_rejects_mixed_observation_time_and_invalid_required_dataset_shape(tmp_path):
    with pytest.raises(ValueError, match="matching FY-4B AGRI five-file package"):
        inspect_manifest(make_fy4b_package(tmp_path, end_by_role={"GEO_4000M": "20260701002959"}))

    shape_root = tmp_path / "shape"
    shape_root.mkdir()
    manifest = make_fy4b_package(shape_root)
    path = manifest["inputs"][0]["path"]
    with h5py.File(path, "r+") as handle:
        del handle["Data/NOMChannel02"]
        handle["Data"].create_dataset("NOMChannel02", shape=(2, 2), dtype="u2")
    with pytest.raises(ValueError, match="invalid full-disk shape"):
        inspect_manifest(manifest)


def test_inspect_rejects_hdf_observation_outside_filename_nominal_window(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    with h5py.File(manifest["inputs"][0]["path"], "r+") as handle:
        handle.attrs["Observing Ending Time"] = "00:15:00"

    with pytest.raises(ValueError, match="outside filename nominal interval"):
        inspect_manifest(manifest)


def test_parse_fy4b_parameters_normalizes_defaults_and_rejects_unknown_fields():
    assert parse_fy4b_parameters({}) == {
        "schemaVersion": "1",
        "targetCrs": "EPSG:4326",
        "roi": None,
        "features": {"enabled": False, "metrics": ["statistics"]},
        "fusion": {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]},
        "saliency": {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
    }
    with pytest.raises(ValueError, match="parameter object is invalid"):
        parse_fy4b_parameters({"unexpected": True})


def test_parse_fy4b_parameters_uses_disabled_defaults_and_feature_saliency_whitelist():
    normalized = parse_fy4b_parameters({
        "features": {"enabled": True, "metrics": ["gradient"]},
        "fusion": {"enabled": False, "percentiles": [10, 90]},
        "saliency": {"enabled": True, "source": "gradient:C02_REFLECTANCE_500M"},
    })

    assert normalized["fusion"] == {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]}
    assert normalized["saliency"]["source"] == "gradient:C02_REFLECTANCE_500M"
    assert parse_fy4b_parameters({"saliency": {"enabled": False, "threshold": 0.9}})["saliency"] == {
        "enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3,
    }


def test_parse_fy4b_parameters_requires_enabled_selected_feature_for_derived_saliency_sources():
    with pytest.raises(ValueError, match="saliency source is invalid"):
        parse_fy4b_parameters({
            "features": {"enabled": False, "metrics": ["toa_nir_red_nd"]},
            "saliency": {"enabled": True, "source": "toa_nir_red_nd"},
        })
    with pytest.raises(ValueError, match="saliency source is invalid"):
        parse_fy4b_parameters({
            "features": {"enabled": True, "metrics": ["statistics"]},
            "saliency": {"enabled": True, "source": "gradient:C02_REFLECTANCE_500M"},
        })
    assert parse_fy4b_parameters({
        "features": {"enabled": False, "metrics": ["statistics"]},
        "saliency": {"enabled": True, "source": "C13_BRIGHTNESS_TEMPERATURE_4000M"},
    })["saliency"]["source"] == "C13_BRIGHTNESS_TEMPERATURE_4000M"


@pytest.mark.parametrize("raw", [["invalid"], "invalid", 0])
def test_integrated_plan_rejects_non_object_parameter_snapshots(tmp_path, raw):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]

    with pytest.raises(ValueError, match="parameter object is invalid"):
        build_integrated_plan(candidate, raw)


@pytest.mark.parametrize("raw", [
    {"targetCrs": []},
    {"features": {"metrics": [[]]}},
    {"saliency": {"enabled": True, "source": []}},
])
def test_parse_fy4b_parameters_rejects_wrong_type_whitelist_values_as_value_errors(raw):
    with pytest.raises(ValueError):
        parse_fy4b_parameters(raw)


def test_integrated_plan_declares_one_stage_nine_mandatory_outputs_and_enabled_optional_outputs(tmp_path):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]
    plan = build_integrated_plan(candidate, {"schemaVersion": "1", "features": {"enabled": False, "metrics": ["statistics"]}})

    assert plan["version"] == "plan-v2"
    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_FY4B_AGRI"]
    stage = plan["stages"][0]
    assert [item["artifactType"] for item in stage["inputs"]] == [
        "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
    ]
    assert {item["artifactType"] for item in stage["outputs"]} == {
        "FY4B_RED_500M_COG", "FY4B_RED_500M_QUALITY_COG", "FY4B_VIS_NIR_1000M_COG",
        "FY4B_VIS_NIR_1000M_QUALITY_COG", "FY4B_SPECTRAL_2000M_COG",
        "FY4B_SPECTRAL_2000M_QUALITY_COG", "FY4B_MULTISPECTRAL_4000M_COG",
        "FY4B_MULTISPECTRAL_4000M_QUALITY_COG", "FY4B_STANDARD_SUMMARY_JSON",
    }
    assert json.loads(stage["parameterSnapshot"])["targetCrs"] == "EPSG:4326"

    optional = build_integrated_plan(candidate, {
        "features": {"enabled": True, "metrics": ["statistics"]},
        "fusion": {"enabled": True},
        "saliency": {"enabled": True},
    })["stages"][0]["outputs"]
    assert {item["artifactType"] for item in optional} >= {
        "FY4B_FEATURE_COG", "FY4B_FEATURE_SUMMARY_JSON", "FY4B_FALSE_COLOR_COG",
        "FY4B_SALIENCY_SCORE_COG", "FY4B_SALIENCY_MASK_COG", "FY4B_SALIENCY_SUMMARY_JSON",
    }


def test_integrated_plan_rejects_candidate_with_invalid_file_contract(tmp_path):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]
    valid_files = candidate["files"]
    invalid_file_lists = [
        valid_files + [valid_files[0]],
        ["not-a-file-entry", *valid_files[1:]],
        [{**valid_files[0], "ordinal": 4}, *valid_files[1:]],
        [{**valid_files[0], "ordinal": False}, *valid_files[1:]],
        [{**valid_files[0], "path": ""}, *valid_files[1:]],
        [{**valid_files[0], "role": "GEO_4000M"}, *valid_files[1:]],
    ]
    for files in invalid_file_lists:
        with pytest.raises(ValueError, match="complete FY-4B AGRI candidate"):
            build_integrated_plan({**candidate, "files": files}, None)
