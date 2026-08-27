import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from PIL import Image

from fy3h_fixture import make_fy3h_pair
from fy4b_fixture import make_fy4b_package


def test_cli_writes_response_atomically(tmp_path):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "execute-stage",
        "requestId": "req-01",
        "payload": {"inputs": [{"storageUriSnapshot": {"value": "rs://input/a.jpg"}}]},
    }), encoding="utf-8")
    manifest_file.write_text(json.dumps({
        "manifestVersion": "1.0",
        "requestId": "req-01",
        "grantReference": "grant-01",
        "expiresAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(),
        "inputs": [{"storageUri": "rs://input/a.jpg", "role": "MAIN", "ordinal": 0,
                     "path": str(tmp_path / "a.jpg"), "checksum": "sha256:test"}],
        "output": {"path": str(tmp_path / "output")},
    }), encoding="utf-8")
    Image.new("RGB", (2, 2), (10, 20, 30)).save(tmp_path / "a.jpg")

    result = subprocess.run([
        sys.executable, "-m", "rs_algorithm_worker.cli",
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ], capture_output=True, text=True)

    assert result.returncode == 0, result.stderr
    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["schemaVersion"] == "1.0"
    assert response["requestId"] == "req-01"
    assert response["payload"] == {
        "workerId": "worker-local",
        "status": "SUCCEEDED",
        "artifacts": [],
    }
    assert not response_file.with_suffix(".json.tmp").exists()


def test_cli_runs_fy3h_inspect_build_plan_and_declared_validation_stage(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    inputs = [
        {"storageUri": "rs://input/observation", "role": "OBSERVATION", "ordinal": 0, "path": str(observation), "checksum": "sha256:observation"},
        {"storageUri": "rs://input/geo", "role": "GEO1K", "ordinal": 1, "path": str(geo), "checksum": "sha256:geo"},
    ]
    inspect = _invoke_cli(tmp_path, "inspect", {"inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}} for item in inputs]}, inputs)
    candidate = inspect["payload"]["candidates"][0]
    assert candidate["profileKey"]["productCode"] == "FY-3H-MERSI"

    plan = _invoke_cli(tmp_path, "build-plan", {"candidate": candidate, "parameterSnapshot": {"targetCrs": "EPSG:4326", "resolutionDegrees": 0.01}}, inputs)
    assert plan["payload"]["version"] == "plan-v2"

    validated = _invoke_cli(tmp_path, "execute-stage", {
        "profileCode": "FY-3H-MERSI", "stageKey": "VALIDATE_FY3H_PAIR", "outputUri": "rs://output/fy3h-validation",
        "parameterSnapshot": {}, "inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}} for item in inputs],
    }, inputs)
    artifact = validated["payload"]["artifacts"][0]
    assert artifact["type"] == "FY3H_INPUT_VALIDATION_JSON"
    assert artifact["uri"] == "rs://output/fy3h-validation/validation/pair.json"
    assert str(tmp_path) not in json.dumps(validated)


def test_cli_runs_fy4b_inspect_build_plan_and_declared_validation_stage(tmp_path):
    package = make_fy4b_package(tmp_path)
    inputs = [
        {"storageUri": f"rs://input/fy4b/{index}", "role": item["role"], "ordinal": item["ordinal"],
         "path": item["path"], "checksum": f"sha256:{index}"}
        for index, item in enumerate(package["inputs"])
    ]
    inspect = _invoke_cli(tmp_path, "inspect", {"inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}} for item in inputs]}, inputs)
    candidate = inspect["payload"]["candidates"][0]
    assert candidate["profileKey"]["productCode"] == "FY-4B-AGRI"
    plan = _invoke_cli(tmp_path, "build-plan", {"candidate": candidate, "parameterSnapshot": {"schemaVersion": "1", "targetCrs": "EPSG:4326"}}, inputs)
    assert plan["payload"]["stages"][0]["stageKey"] == "VALIDATE_FY4B_PACKAGE"
    response = _invoke_cli(tmp_path, "execute-stage", {
        "profileCode": "FY-4B-AGRI", "stageKey": "VALIDATE_FY4B_PACKAGE", "outputUri": "rs://output/fy4b-validation",
        "parameterSnapshot": {"schemaVersion": "1", "targetCrs": "EPSG:4326"},
        "inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}} for item in inputs],
    }, inputs)
    artifact = response["payload"]["artifacts"][0]
    assert artifact["type"] == "FY4B_INPUT_VALIDATION_JSON"
    assert artifact["uri"] == "rs://output/fy4b-validation/validation/package.json"
    assert str(tmp_path) not in json.dumps(response)


def _invoke_cli(tmp_path, operation, payload, inputs):
    request_file = tmp_path / f"{operation}-request.json"
    manifest_file = tmp_path / f"{operation}-manifest.json"
    response_file = tmp_path / f"{operation}-response.json"
    request_file.write_text(json.dumps({"schemaVersion": "1.0", "operation": operation, "requestId": "fy3h-request", "payload": payload}), encoding="utf-8")
    manifest_file.write_text(json.dumps({"manifestVersion": "1.0", "requestId": "fy3h-request", "grantReference": "grant-fy3h", "expiresAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(), "inputs": inputs, "output": {"path": str(tmp_path / "worker-output")}}), encoding="utf-8")
    result = subprocess.run([sys.executable, "-m", "rs_algorithm_worker.cli", "--request", str(request_file), "--asset-manifest", str(manifest_file), "--response", str(response_file)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    return json.loads(response_file.read_text(encoding="utf-8"))
