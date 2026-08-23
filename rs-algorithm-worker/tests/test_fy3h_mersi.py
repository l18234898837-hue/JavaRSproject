import pytest

from rs_algorithm_worker.product_adapters.fy3h_mersi import build_plan, inspect_manifest


@pytest.mark.real_sample
def test_fy3h_splits_five_granules(fy3h_manifest):
    candidates = inspect_manifest(fy3h_manifest)
    assert len(candidates) == 5
    assert {item["dataShape"] for item in candidates} == {"SWATH_RASTER"}
    assert {item["supportStatus"] for item in candidates} == {"SUPPORTED"}
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"


def test_fy3h_rejects_non_hdf_evidence(tmp_path):
    bad = tmp_path / "not-a-granule.HDF"
    bad.write_text("not HDF", encoding="utf-8")
    with pytest.raises(ValueError, match="FY-3H MERSI evidence"):
        inspect_manifest({"inputs": [{"path": str(bad), "role": "MAIN", "ordinal": 0}]})
