import pytest

from rs_algorithm_worker.product_adapters.hy1c_czi import build_plan, inspect_manifest


@pytest.mark.real_sample
def test_hy1c_emits_one_swath_candidate(hy1c_manifest):
    candidates = inspect_manifest(hy1c_manifest)
    assert len(candidates) == 1
    assert candidates[0]["dataShape"] == "SWATH_RASTER"
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"
