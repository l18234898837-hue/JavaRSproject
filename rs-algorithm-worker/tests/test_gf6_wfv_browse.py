import json

import pytest
from PIL import Image

from rs_algorithm_worker.product_adapters.gf6_wfv_browse import build_plan, execute_visual_plan, inspect_manifest


@pytest.mark.real_sample
def test_gf_directory_yields_two_browse_candidates(gf_manifest):
    candidates = inspect_manifest(gf_manifest)
    assert [item["packageId"] for item in candidates] == [
        "GF6-WFV-LEVEL1A-1420733151", "GF6-WFV-LEVEL1A-1420734648"
    ]
    assert {item["dataShape"] for item in candidates} == {"RASTER_GRID"}
    assert candidates[0]["metadataSnapshot"]["georeferencingMethod"] == "AFFINE_WORLD_FILE_APPROXIMATE"


def test_scientific_operation_is_rejected(tmp_path):
    candidate = {
        "packageId": "GF6-WFV-LEVEL1A-test",
        "profileKey": {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"},
        "supportStatus": "SUPPORTED",
        "dataShape": "RASTER_GRID",
        "files": [],
    }
    with pytest.raises(ValueError, match="UNSUPPORTED_BROWSE_OPERATION"):
        build_plan(candidate, {"operation": "RADIOMETRIC_CALIBRATION"})


def test_browse_plan_writes_deterministic_report(tmp_path):
    image = tmp_path / "browse.jpg"
    Image.new("RGB", (2, 2), (10, 20, 30)).save(image)
    candidate = {
        "packageId": "GF6-WFV-LEVEL1A-test",
        "profileKey": {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"},
        "supportStatus": "SUPPORTED",
        "dataShape": "RASTER_GRID",
        "files": [{"role": "BROWSE_IMAGE", "path": str(image)}],
    }
    report = execute_visual_plan(candidate, tmp_path / "output")
    assert json.loads(report.read_text(encoding="utf-8"))["packageId"] == candidate["packageId"]


def _minimal_group(tmp_path):
    image = tmp_path / "GF6-WFV-LEVEL1A-1.jpg"
    Image.new("RGB", (2, 2), (10, 20, 30)).save(image)
    jgw = image.with_suffix(".jgw")
    jgw.write_text("1\n0\n0\n-1\n0\n0\n", encoding="utf-8")
    metadata = image.with_suffix(".json")
    metadata.write_text(json.dumps({"satelliteid": "GF6"}), encoding="utf-8")
    return image, jgw, metadata


def test_rejects_missing_jgw(tmp_path):
    image, jgw, metadata = _minimal_group(tmp_path)
    jgw.unlink()
    with pytest.raises(ValueError, match="JPEG, JGW, and JSON"):
        inspect_manifest({"inputs": [{"path": str(path), "role": "MAIN", "ordinal": index}
                                      for index, path in enumerate((image, metadata))]})


def test_rejects_non_finite_world_file(tmp_path):
    image, jgw, metadata = _minimal_group(tmp_path)
    jgw.write_text("nan\n0\n0\n-1\n0\n0\n", encoding="utf-8")
    with pytest.raises(ValueError, match="world file coefficients"):
        inspect_manifest({"inputs": [{"path": str(path), "role": "MAIN", "ordinal": index}
                                      for index, path in enumerate((image, jgw, metadata))]})


def test_rejects_invalid_metadata_json(tmp_path):
    image, jgw, metadata = _minimal_group(tmp_path)
    metadata.write_text("not-json", encoding="utf-8")
    with pytest.raises(ValueError, match="metadata evidence"):
        inspect_manifest({"inputs": [{"path": str(path), "role": "MAIN", "ordinal": index}
                                      for index, path in enumerate((image, jgw, metadata))]})
