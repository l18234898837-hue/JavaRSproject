import h5py
import json
import numpy as np
import pytest

from fy3h_fixture import make_fy3h_pair
from rs_algorithm_worker.product_adapters.fy3h_mersi import build_integrated_plan, build_plan, inspect_manifest


PROFILE = {"productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"}


def _manifest(*paths):
    return {"inputs": [{"path": str(path), "role": "MAIN", "ordinal": ordinal}
                       for ordinal, path in enumerate(paths)]}


def test_fy3h_recognizes_one_matching_1000m_and_geo1k_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)

    candidates = inspect_manifest(_manifest(geo, observation))

    assert len(candidates) == 1
    candidate = candidates[0]
    assert candidate["profileKey"] == PROFILE
    assert [(item["role"], item["ordinal"], item["path"]) for item in candidate["files"]] == [
        ("OBSERVATION", 1, str(observation)),
        ("GEO1K", 0, str(geo)),
    ]
    assert build_plan(candidate)["stages"][0]["stageCode"] == "VALIDATE_FY3H_PAIR"


def test_fy3h_recovers_original_names_from_uuid_storage_paths(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    stored_observation = observation.with_name("c916fe6d-45a9-47df-a19c-03582f83e140.HDF")
    stored_geo = geo.with_name("75bfb183-8dc9-4b80-b5a2-508f9fb482af.HDF")
    observation.rename(stored_observation)
    geo.rename(stored_geo)
    manifest = {"inputs": [
        {"path": str(stored_observation), "role": "OBSERVATION", "ordinal": 0},
        {"path": str(stored_geo), "role": "GEO1K", "ordinal": 0},
    ]}

    candidate = inspect_manifest(manifest)[0]

    assert candidate["packageId"].endswith("_V0")
    assert [(item["role"], item["path"]) for item in candidate["files"]] == [
        ("OBSERVATION", str(stored_observation)),
        ("GEO1K", str(stored_geo)),
    ]


def test_fy3h_rejects_manifest_roles_that_swap_the_hdf_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    manifest = {"inputs": [
        {"path": str(observation), "role": "GEO1K", "ordinal": 0},
        {"path": str(geo), "role": "OBSERVATION", "ordinal": 0},
    ]}

    with pytest.raises(ValueError, match="manifest role"):
        inspect_manifest(manifest)


def test_fy3h_keeps_direct_original_filename_compatibility_without_file_name_attribute(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    for path in (observation, geo):
        with h5py.File(path, "r+") as handle:
            del handle.attrs["File Name"]

    candidate = inspect_manifest(_manifest(observation, geo))[0]

    assert candidate["packageId"].endswith("_V0")


def test_fy3h_plan_declares_the_fixed_pair_processing_contract(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    candidate = inspect_manifest(_manifest(observation, geo))[0]

    plan = build_plan(candidate, {
        "targetCrs": "EPSG:4326",
        "resolutionDegrees": 0.01,
        "continuousResampling": "nearest",
        "bands": list(range(1, 26)),
    })

    stages = {stage["stageKey"]: stage for stage in plan["stages"]}
    assert plan["version"] == "plan-v2"
    assert list(stages) == [
        "VALIDATE_FY3H_PAIR", "DECODE_MERSI_L1", "CALIBRATE_MERSI_L1",
        "APPLY_MERSI_QUALITY", "GRID_MERSI_SWATH", "WRITE_MERSI_STANDARD_COG",
    ]
    assert stages["DECODE_MERSI_L1"]["inputs"] == [
        {"key": "observation", "source": "RAW_INPUT", "artifactType": "OBSERVATION", "required": True},
        {"key": "geo", "source": "RAW_INPUT", "artifactType": "GEO1K", "required": True},
    ]
    assert {output["artifactType"] for output in stages["WRITE_MERSI_STANDARD_COG"]["outputs"]} == {
        "STANDARD_MERSI_COG", "STANDARD_MERSI_QUALITY_COG", "STANDARD_MERSI_SUMMARY_JSON",
    }
    assert stages["WRITE_MERSI_STANDARD_COG"]["inputs"] == [
        {"key": "grid", "source": "UPSTREAM_ARTIFACT", "artifactType": "GRIDDED_MERSI_RASTER", "required": True},
        {"key": "qualityGrid", "source": "UPSTREAM_ARTIFACT", "artifactType": "GRIDDED_MERSI_QUALITY_MASK", "required": True},
    ]
    snapshots = {stage["parameterSnapshot"] for stage in stages.values()}
    assert len(snapshots) == 1
    snapshot = snapshots.pop()
    assert json.loads(snapshot)["bands"] == list(range(1, 26))
    assert snapshot == json.dumps(json.loads(snapshot), sort_keys=True, separators=(",", ":"))


def test_fy3h_integrated_plan_matches_the_single_dispatch_contract(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    candidate = inspect_manifest(_manifest(observation, geo))[0]

    plan = build_integrated_plan(candidate, {"bands": [1, 2, 3]})

    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_MERSI_L1"]
    stage = plan["stages"][0]
    assert [item["artifactType"] for item in stage["inputs"]] == ["OBSERVATION", "GEO1K"]
    assert {item["artifactType"] for item in stage["outputs"]} == {
        "STANDARD_MERSI_COG", "STANDARD_MERSI_QUALITY_COG", "STANDARD_MERSI_SUMMARY_JSON",
    }


def test_fy3h_rejects_even_saliency_window_and_quality_override(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    candidate = inspect_manifest(_manifest(observation, geo))[0]

    with pytest.raises(ValueError, match="odd"):
        build_plan(candidate, {"saliency": {
            "enabled": True, "source": "CH01_REFLECTANCE", "windowSize": 4, "threshold": 0.5,
        }})
    with pytest.raises(ValueError, match="unsupported FY-3H parameter"):
        build_plan(candidate, {"qualityPolicy": "ignore"})


def test_fy3h_statistics_only_features_do_not_declare_a_nonexistent_cog_or_feature_saliency(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    candidate = inspect_manifest(_manifest(observation, geo))[0]

    plan = build_plan(candidate, {"bands": [1], "features": {
        "enabled": True, "metrics": ["statistics"],
    }})

    stages = {stage["stageKey"]: stage for stage in plan["stages"]}
    assert [output["artifactType"] for output in stages["EXTRACT_MERSI_FEATURES"]["outputs"]] == [
        "MERSI_FEATURE_SUMMARY_JSON",
    ]
    with pytest.raises(ValueError, match="saliency source"):
        build_plan(candidate, {"bands": [1], "features": {
            "enabled": True, "metrics": ["statistics"],
        }, "saliency": {
            "enabled": True, "source": "statistics:CH01_REFLECTANCE", "windowSize": 3, "threshold": 0.5,
        }})


def test_fy3h_rejects_thermal_channels_for_rgb_fusion(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    candidate = inspect_manifest(_manifest(observation, geo))[0]

    with pytest.raises(ValueError, match="reflectance"):
        build_plan(candidate, {"bands": [1, 2, 20], "fusion": {
            "enabled": True, "red": 20, "green": 2, "blue": 1,
        }})


def test_fy3h_rejects_cross_orbit_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path, orbit=3948)
    with h5py.File(geo, "r+") as handle:
        handle.attrs["Orbit Number"] = [3949]

    with pytest.raises(ValueError, match="matching 1000M and GEO1K"):
        inspect_manifest(_manifest(observation, geo))


@pytest.mark.parametrize(("mismatch", "value"), [
    ("time", "23:40:00.575"),
    ("filename_version", "V1"),
    ("dimensions", (19, 16)),
])
def test_fy3h_rejects_mismatched_pair_metadata_or_grid(tmp_path, mismatch, value):
    observation, geo = make_fy3h_pair(tmp_path)
    if mismatch == "time":
        with h5py.File(geo, "r+") as handle:
            handle.attrs["Observing Beginning Time"] = value
    elif mismatch == "filename_version":
        renamed = geo.with_name(geo.name.replace("_V0.HDF", f"_{value}.HDF"))
        geo.rename(renamed)
        geo = renamed
        with h5py.File(geo, "r+") as handle:
            handle.attrs["File Name"] = geo.name
    else:
        with h5py.File(geo, "r+") as handle:
            del handle["Geolocation/Latitude"]
            handle["Geolocation"].create_dataset("Latitude", data=np.zeros(value, dtype=np.float32))

    with pytest.raises(ValueError, match="matching 1000M and GEO1K"):
        inspect_manifest(_manifest(observation, geo))


@pytest.mark.parametrize("field", ["Altitude", "LandSeaMask", "LandCover"])
def test_fy3h_rejects_geo_navigation_field_with_different_spatial_dimensions(tmp_path, field):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(geo, "r+") as handle:
        del handle[f"Geolocation/{field}"]
        handle["Geolocation"].create_dataset(field, data=np.zeros((19, 16), dtype=np.float32))

    with pytest.raises(ValueError, match="matching 1000M and GEO1K"):
        inspect_manifest(_manifest(observation, geo))


@pytest.mark.parametrize(("path", "shape"), [
    ("Data/EV_250_Aggr.1KM_RefSB", (3, 20, 16)),
    ("Calibration/VIS_Cal_Coeff", (19, 2)),
    ("Calibration/IR_Cal_Coeff", (6, 4, 1)),
    ("QA/QA_Frame_Flag", (1,)),
])
def test_fy3h_rejects_invalid_observation_channel_calibration_or_frame_layout(tmp_path, path, shape):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(observation, "r+") as handle:
        del handle[path]
        parent, name = path.rsplit("/", 1)
        handle[parent].create_dataset(name, data=np.zeros(shape, dtype=np.float32))

    with pytest.raises(ValueError, match="FY-3H MERSI evidence"):
        inspect_manifest(_manifest(observation, geo))


@pytest.mark.parametrize(("name", "value"), [
    ("EarthSun Distance Ratio", np.array([], dtype=np.float64)),
    ("Solar_Irradiance", np.full(19, np.nan, dtype=np.float32)),
    ("Effect_Center_WaveLength", np.ones(24, dtype=np.float32)),
    ("TBB_Trans_Coefficient", np.ones(11, dtype=np.float32)),
])
def test_fy3h_rejects_invalid_calibration_root_attributes(tmp_path, name, value):
    observation, geo = make_fy3h_pair(tmp_path)
    with h5py.File(observation, "r+") as handle:
        handle.attrs[name] = value

    with pytest.raises(ValueError, match="FY-3H MERSI evidence"):
        inspect_manifest(_manifest(observation, geo))


@pytest.mark.parametrize("name", ["not-a-granule.HDF", "not-a-granule.txt"])
def test_fy3h_rejects_single_or_non_hdf_evidence(tmp_path, name):
    path = tmp_path / name
    path.write_text("not HDF", encoding="utf-8")

    with pytest.raises(ValueError, match="matching 1000M and GEO1K|FY-3H MERSI evidence"):
        inspect_manifest(_manifest(path))


@pytest.mark.real_sample
def test_fy3h_real_sample_pair_has_expected_roles_and_geolocation_shape(fy3h_manifest):
    inputs = fy3h_manifest["inputs"][:2]
    candidates = inspect_manifest({"inputs": inputs})

    assert len(candidates) == 1
    candidate = candidates[0]
    assert candidate["profileKey"] == PROFILE
    assert [item["role"] for item in candidate["files"]] == ["OBSERVATION", "GEO1K"]
    with h5py.File(candidate["files"][0]["path"], "r") as handle:
        assert handle["QA/QA_Frame_Flag"].shape == (200,)
    with h5py.File(candidate["files"][1]["path"], "r") as handle:
        assert handle["Geolocation/Latitude"].shape == (2000, 2048)
