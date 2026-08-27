from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

from rs_algorithm_worker.fy4b_agri_navigation import evaluate_coast_controls
from rs_algorithm_worker.product_adapters.fy4b_agri import ROLE_ORDER, inspect_manifest

pytestmark = pytest.mark.real_sample

def test_real_fy4b_package_matches_documented_contract(fy4b_manifest):
    candidate = inspect_manifest(fy4b_manifest)[0]
    assert [item["role"] for item in candidate["files"]] == list(ROLE_ORDER)
    assert candidate["metadataSnapshot"]["availableChannelsByResolution"]["L1_4000M"] == list(range(1, 16))

def test_real_fy4b_controls_meet_engineering_threshold(fy4b_manifest):
    control_file = os.getenv("MULTISOURCE_FY4B_COAST_CONTROL_FILE")
    if not control_file or not Path(control_file).is_file():
        pytest.skip("MULTISOURCE_FY4B_COAST_CONTROL_FILE is not configured")
    report = evaluate_coast_controls(control_file, lambda lon, lat: (0.0, 0.0))
    assert report["count"] >= 8
    assert report["maxPixels"] <= 2.0
    assert report["rmsePixels"] <= 1.0
