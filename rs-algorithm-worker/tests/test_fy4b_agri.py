import pytest

from rs_algorithm_worker.product_adapters.fy4b_agri import build_plan, inspect_manifest


@pytest.mark.real_sample
def test_fy4b_emits_geostationary_candidate(fy4b_manifest):
    candidates = inspect_manifest(fy4b_manifest)
    assert len(candidates) == 1
    assert candidates[0]["dataShape"] == "GEOSTATIONARY_RASTER"
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"
