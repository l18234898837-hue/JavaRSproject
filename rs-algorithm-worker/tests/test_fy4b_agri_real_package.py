from __future__ import annotations

import pytest

from rs_algorithm_worker.product_adapters.fy4b_agri import ROLE_ORDER, inspect_manifest


@pytest.mark.real_sample
def test_real_fy4b_package_matches_five_file_contract_read_only(fy4b_manifest):
    candidate = inspect_manifest(fy4b_manifest)[0]

    assert [item["role"] for item in candidate["files"]] == list(ROLE_ORDER)
    assert candidate["metadataSnapshot"]["availableChannelsByResolution"]["L1_4000M"] == list(range(1, 16))
    assert candidate["metadataSnapshot"]["sourceDataQuality"] == 0
