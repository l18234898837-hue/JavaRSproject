import pytest

from rs_algorithm_worker.product_adapters.hy2d_altimeter import build_plan, inspect_manifest


@pytest.mark.real_sample
def test_hy2d_is_along_track(hy2d_manifest):
    candidates = inspect_manifest(hy2d_manifest)
    assert len(candidates) == 10
    assert {item["dataShape"] for item in candidates} == {"ALONG_TRACK_OBSERVATION"}
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"
