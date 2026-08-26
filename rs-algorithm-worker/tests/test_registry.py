import json
from copy import deepcopy
from pathlib import Path

import pytest

from rs_algorithm_worker.product_adapters.gf6_wfv_browse import inspect_manifest
from rs_algorithm_worker.registry import dispatch
from fy3h_fixture import make_fy3h_pair


def _fy3h_manifest(*paths):
    return {"inputs": [
        {"path": str(path), "role": "MAIN", "ordinal": ordinal, "storageUri": f"file:///{path.name}"}
        for ordinal, path in enumerate(paths)
    ]}


def test_registry_builds_fy3h_plan_v2_for_recognized_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    manifest = _fy3h_manifest(observation, geo)
    candidate = dispatch({"operation": "inspect"}, manifest)["candidates"][0]

    response = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate,
        "parameterSnapshot": {"targetCrs": "EPSG:4326", "resolutionDegrees": 0.01},
    }}, manifest)

    assert response["version"] == "plan-v2"
    assert response["stages"][0]["stageKey"] == "VALIDATE_FY3H_PAIR"


@pytest.mark.real_sample
def test_registry_returns_logical_uris_without_local_paths(gf_manifest):
    request = {
        "operation": "inspect",
        "requestId": "req-01",
        "payload": {"inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}}
                                 for item in gf_manifest["inputs"]]},
    }
    response = dispatch(request, gf_manifest)
    assert len(response["candidates"]) == 2
    encoded = json.dumps(response)
    assert "asset-manifest" not in encoded
    assert not any(str(Path(item["path"])) in encoded for item in gf_manifest["inputs"])


@pytest.mark.real_sample
def test_registry_builds_the_gf_browse_dag_for_exact_public_parameters(gf_manifest):
    candidate = inspect_manifest(gf_manifest)[0]

    response = dispatch({
        "operation": "build-plan",
        "payload": {
            "candidate": candidate,
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
        },
    }, gf_manifest)

    assert response == {"version": "plan-v1", "stages": [
        {"stageKey": "VALIDATE_BROWSE", "stageCode": "VALIDATE_BROWSE", "dependsOnStageKeys": []},
        {"stageKey": "GEOREFERENCE_BROWSE", "stageCode": "GEOREFERENCE_BROWSE",
         "dependsOnStageKeys": ["VALIDATE_BROWSE"]},
        {"stageKey": "EXTRACT_VISUAL_FEATURES", "stageCode": "EXTRACT_VISUAL_FEATURES",
         "dependsOnStageKeys": ["GEOREFERENCE_BROWSE"]},
        {"stageKey": "WRITE_BROWSE_REPORT", "stageCode": "WRITE_BROWSE_REPORT",
         "dependsOnStageKeys": ["EXTRACT_VISUAL_FEATURES"]},
    ]}


@pytest.mark.real_sample
def test_registry_rejects_any_non_default_gf_browse_operation(gf_manifest):
    candidate = inspect_manifest(gf_manifest)[0]

    with pytest.raises(ValueError, match="UNSUPPORTED_BROWSE_OPERATION"):
        dispatch({
            "operation": "build-plan",
            "payload": {
                "candidate": candidate,
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "BROWSE_VISUAL_FEATURES"}),
            },
        }, gf_manifest)


@pytest.mark.real_sample
def test_registry_writes_report_only_for_the_selected_recognized_gf_package(gf_manifest, tmp_path):
    candidate = inspect_manifest(gf_manifest)[1]
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}
    output_uri = "rs://output/task-101"

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "packageId": candidate["packageId"],
            "stageKey": "WRITE_BROWSE_REPORT",
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
            "outputUri": output_uri,
        },
    }, manifest)

    assert response["workerId"] == "worker-local"
    assert response["status"] == "SUCCEEDED"
    assert len(response["artifacts"]) == 1
    artifact = response["artifacts"][0]
    assert artifact["uri"] == f"{output_uri}/browse-report.json"
    assert artifact["type"] == "BROWSE_REPORT_JSON"
    report = tmp_path / "output" / "browse-report.json"
    content = report.read_bytes()
    assert artifact["sizeBytes"] == len(content)
    assert artifact["checksum"].startswith("sha256:")
    value = json.loads(content)
    assert value["packageId"] == candidate["packageId"]
    assert value["georeferencingMethod"] == "AFFINE_WORLD_FILE_APPROXIMATE"
    assert {"width", "height", "rgbStats"}.issubset(value["image"])
    assert str(Path(gf_manifest["inputs"][0]["path"]).parent) not in content.decode("utf-8")


@pytest.mark.real_sample
@pytest.mark.parametrize("output_uri", [
    r"C:\\worker\\output",
    "/var/worker/output",
    "",
    "file:///var/worker/output",
    "https://example.test/output",
    "rs://output/../escape",
])
def test_registry_rejects_non_logical_output_uris(gf_manifest, tmp_path, output_uri):
    candidate = inspect_manifest(gf_manifest)[0]
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}

    with pytest.raises(ValueError, match="logical output URI"):
        dispatch({
            "operation": "execute-stage",
            "payload": {
                "packageId": candidate["packageId"],
                "stageKey": "WRITE_BROWSE_REPORT",
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
                "outputUri": output_uri,
            },
        }, manifest)


@pytest.mark.real_sample
def test_registry_does_not_infer_another_gf_package_for_stage_execution(gf_manifest, tmp_path):
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}

    with pytest.raises(ValueError, match="recognized GF-6 package"):
        dispatch({
            "operation": "execute-stage",
            "payload": {
                "packageId": "GF6-WFV-LEVEL1A-not-present",
                "stageKey": "VALIDATE_BROWSE",
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
                "outputUri": "rs://output/task-101",
            },
        }, manifest)


@pytest.mark.real_sample
def test_registry_infers_the_only_gf_package_for_stage_execution(gf_manifest, tmp_path):
    candidate = inspect_manifest(gf_manifest)[0]
    selected_paths = {str(item["path"]) for item in candidate["files"]}
    manifest = deepcopy(gf_manifest)
    manifest["inputs"] = [item for item in manifest["inputs"] if item["path"] in selected_paths]
    manifest["output"] = {"path": str(tmp_path / "output")}

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "stageKey": "VALIDATE_BROWSE",
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
            "outputUri": "rs://output/task-101",
        },
    }, manifest)

    assert response["status"] == "SUCCEEDED"
