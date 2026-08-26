import h5py
import numpy as np
import pytest

from fy3h_fixture import make_fy3h_pair
from rs_algorithm_worker.product_adapters.fy3h_mersi import build_plan, inspect_manifest


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
    assert build_plan(candidate)["stages"][0]["stageCode"] == "VALIDATE_SOURCE"


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
    else:
        with h5py.File(geo, "r+") as handle:
            del handle["Geolocation/Latitude"]
            handle["Geolocation"].create_dataset("Latitude", data=np.zeros(value, dtype=np.float32))

    with pytest.raises(ValueError, match="matching 1000M and GEO1K"):
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
def test_fy3h_real_sample_pair_has_expected_roles_and_geolocation_shape(fy3h_mersi_manifest):
    candidates = inspect_manifest(fy3h_mersi_manifest)

    assert len(candidates) == 1
    candidate = candidates[0]
    assert candidate["profileKey"] == PROFILE
    assert [item["role"] for item in candidate["files"]] == ["OBSERVATION", "GEO1K"]
    with h5py.File(candidate["files"][0]["path"], "r") as handle:
        assert handle["QA/QA_Frame_Flag"].shape == (200,)
    with h5py.File(candidate["files"][1]["path"], "r") as handle:
        assert handle["Geolocation/Latitude"].shape == (2000, 2048)
