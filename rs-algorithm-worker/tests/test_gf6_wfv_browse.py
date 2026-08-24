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


def test_browse_plan_requires_the_exact_public_parameter_snapshot():
    candidate = {
        "packageId": "GF6-WFV-LEVEL1A-test",
        "profileKey": {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"},
        "supportStatus": "SUPPORTED",
        "dataShape": "RASTER_GRID",
        "files": [],
    }
    with pytest.raises(ValueError, match="UNSUPPORTED_BROWSE_OPERATION"):
        build_plan(candidate, {"schemaVersion": "1", "operation": "BROWSE_VISUAL_FEATURES"})
    assert build_plan(candidate, {"schemaVersion": "1", "operation": "DEFAULT_PROCESS"})["version"] == "plan-v1"


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
    content = report.read_bytes()
    assert content.endswith(b"\n")
    value = json.loads(content)
    assert value["packageId"] == candidate["packageId"]
    assert value["georeferencingMethod"] == "AFFINE_WORLD_FILE_APPROXIMATE"
    assert value["image"] == {
        "height": 2,
        "rgbStats": {
            "blue": {"max": 30, "mean": 30.0, "min": 30},
            "green": {"max": 20, "mean": 20.0, "min": 20},
            "red": {"max": 10, "mean": 10.0, "min": 10},
        },
        "width": 2,
    }


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


def test_inspect_pairs_role_inputs_when_storage_names_differ(tmp_path):
    image = tmp_path / "89c26829.jpg"
    Image.new("RGB", (2, 2), (10, 20, 30)).save(image)
    jgw = tmp_path / "86833e0b.jgw"
    jgw.write_text("1\n0\n0\n-1\n107.785024\n13.005316\n", encoding="utf-8")
    metadata = tmp_path / "83829c53.json"
    metadata.write_text(json.dumps({
        "productid": "1420733151",
        "satelliteid": "GF6",
        "sensorid": "WFV",
        "sublevel": "LEVEL1A",
        "metadataid": "GF6-WFV-LEVEL1A-1420733151",
        "geom": "POLYGON((107.785024 13.005316,108 13,108 12,107 12,107.785024 13.005316))",
        "starttime": "2026-08-09 10:24:57",
        "endtime": "2026-08-09 10:25:37",
        "cloudpercent": "27",
    }), encoding="utf-8")

    manifest = {"inputs": [
        {"path": str(image), "role": "BROWSE_IMAGE", "ordinal": 0},
        {"path": str(jgw), "role": "GEOREFERENCE", "ordinal": 1},
        {"path": str(metadata), "role": "METADATA", "ordinal": 2},
    ]}

    candidates = inspect_manifest(manifest)

    assert [item["packageId"] for item in candidates] == ["GF6-WFV-LEVEL1A-1420733151"]
