import pytest
import h5py

from fy4b_fixture import make_fy4b_package
from rs_algorithm_worker.product_adapters.fy4b_agri import build_plan, inspect_manifest


@pytest.mark.real_sample
def test_fy4b_emits_geostationary_candidate(fy4b_manifest):
    candidates = inspect_manifest(fy4b_manifest)
    assert len(candidates) == 1
    assert candidates[0]["dataShape"] == "GEOSTATIONARY_RASTER"
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"


def test_inspect_requires_the_complete_ordered_fy4b_package(tmp_path):
    candidate = inspect_manifest(make_fy4b_package(tmp_path))[0]

    assert [item["role"] for item in candidate["files"]] == [
        "L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M",
    ]
    assert candidate["profileKey"] == {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}


def test_inspect_rejects_fy4b_package_with_mismatched_observation_end(tmp_path):
    manifest = make_fy4b_package(tmp_path, end_by_role={"GEO_4000M": "20260701002959"})

    with pytest.raises(ValueError, match="matching FY-4B AGRI five-file package"):
        inspect_manifest(manifest)


def test_inspect_rejects_fy4b_package_with_a_missing_or_extra_role(tmp_path):
    manifest = make_fy4b_package(tmp_path)

    with pytest.raises(ValueError, match="complete five-file package"):
        inspect_manifest({"inputs": [
            item for item in manifest["inputs"] if "_2000M_" not in item["path"]
        ]})
    with pytest.raises(ValueError, match="complete five-file package"):
        inspect_manifest({"inputs": manifest["inputs"] + [manifest["inputs"][0]]})


@pytest.mark.parametrize(("attribute", "value"), [
    ("Observing Beginning Date", "2026-02-30"),
    ("Observing Beginning Time", "25:00:00"),
    ("Observing Beginning Time", "NaN"),
])
def test_inspect_rejects_invalid_observation_timestamp(tmp_path, attribute, value):
    manifest = make_fy4b_package(tmp_path)
    with h5py.File(manifest["inputs"][0]["path"], "r+") as handle:
        handle.attrs[attribute] = value

    with pytest.raises(ValueError, match="observation time"):
        inspect_manifest(manifest)


def test_inspect_rejects_observation_period_with_end_before_beginning(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    with h5py.File(manifest["inputs"][0]["path"], "r+") as handle:
        handle.attrs["Observing Beginning Time"] = "00:15:00"

    with pytest.raises(ValueError, match="observation period"):
        inspect_manifest(manifest)


@pytest.mark.parametrize("missing_dataset", [
    "Data/NOMChannel02",
    "Data/NOMChannel15",
    "Navigation/NOMSunZenith",
])
def test_inspect_rejects_fy4b_package_with_required_dataset_missing(tmp_path, missing_dataset):
    manifest = make_fy4b_package(tmp_path, missing_datasets=(missing_dataset,))

    with pytest.raises(ValueError, match="FY-4B AGRI"):
        inspect_manifest(manifest)
