import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from PIL import Image
import pytest

from rs_algorithm_worker import cli


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


def test_cli_preserves_actionable_validation_message(tmp_path, monkeypatch):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "build-plan",
        "requestId": "req-invalid-01",
        "payload": {},
    }), encoding="utf-8")
    manifest_file.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(cli, "validate_invocation", lambda *_args, **_kwargs: None)

    def fail_with_detail(_request, _manifest):
        raise ValueError("L2A ROI bounds are invalid")

    monkeypatch.setattr(cli, "dispatch", fail_with_detail)

    assert cli.main([
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ]) == 2

    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["error"]["message"] == "L2A ROI bounds are invalid"


def test_cli_returns_a_structured_memory_limit_error(tmp_path, monkeypatch):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "execute-stage",
        "requestId": "req-memory-limit",
        "payload": {},
    }), encoding="utf-8")
    manifest_file.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(cli, "validate_invocation", lambda *_args, **_kwargs: None)
    monkeypatch.setattr(cli, "dispatch", lambda *_args, **_kwargs: (_ for _ in ()).throw(MemoryError()))

    assert cli.main([
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ]) == 2

    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["error"] == {
        "code": "MEMORY_LIMIT_EXCEEDED",
        "message": "影像处理内存不足，请缩小 ROI、减少波段或使用分块处理配置",
        "retryable": False,
    }


def test_cli_preserves_the_authoritative_gf6_output_limit_error_code(tmp_path, monkeypatch):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "execute-stage",
        "requestId": "req-gf6-limit",
        "payload": {},
    }), encoding="utf-8")
    manifest_file.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(cli, "validate_invocation", lambda *_args, **_kwargs: None)

    def fail_with_limit(_request, _manifest):
        raise ValueError(
            "GF6_OUTPUT_LIMIT_EXCEEDED: grid pixels 31339110702159 exceed 2000000000"
        )

    monkeypatch.setattr(cli, "dispatch", fail_with_limit)

    assert cli.main([
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ]) == 2

    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["error"] == {
        "code": "GF6_OUTPUT_LIMIT_EXCEEDED",
        "message": "GF6_OUTPUT_LIMIT_EXCEEDED: grid pixels 31339110702159 exceed 2000000000",
        "retryable": False,
    }


@pytest.mark.parametrize(("message", "actionable"), [
    ("GF6_PARAMETER_INVALID: roi at {path}\nTraceback (most recent call last):\ninternal detail", "GF6_PARAMETER_INVALID: roi"),
    ("GF6_PARAMETER_INVALID: roi at {path} Traceback (most recent call last): internal detail", "GF6_PARAMETER_INVALID: roi"),
    ("FY-4B AGRI evidence requires a complete five-file package at {path}\nTraceback (most recent call last):\ninternal detail",
     "FY-4B AGRI evidence requires a complete five-file package"),
])
def test_cli_sanitizes_actionable_validation_messages_without_losing_the_error_detail(tmp_path, monkeypatch, message, actionable):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "execute-stage",
        "requestId": "req-invalid-02",
        "payload": {},
    }), encoding="utf-8")
    manifest_file.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(cli, "validate_invocation", lambda *_args, **_kwargs: None)

    def fail_with_private_detail(_request, _manifest):
        raise ValueError(message.format(path=tmp_path / "private-input"))

    monkeypatch.setattr(cli, "dispatch", fail_with_private_detail)

    assert cli.main([
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ]) == 2

    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["error"]["code"] == "INVALID_REQUEST"
    assert response["error"]["retryable"] is False
    assert actionable in response["error"]["message"]
    assert str(tmp_path) not in response["error"]["message"]
    assert "Traceback" not in response["error"]["message"]


@pytest.mark.parametrize("private_path", [
    r"\\server\private\scene.tif",
    r"\\?\C:\private data\scene.tif",
    r"C:\private data\scene.tif",
])
def test_cli_sanitizes_unc_and_space_containing_windows_paths_without_losing_actionable_text(tmp_path, monkeypatch, private_path):
    request_file = tmp_path / "request.json"
    manifest_file = tmp_path / "asset-manifest.json"
    response_file = tmp_path / "response.json"
    request_file.write_text(json.dumps({
        "schemaVersion": "1.0",
        "operation": "execute-stage",
        "requestId": "req-invalid-03",
        "payload": {},
    }), encoding="utf-8")
    manifest_file.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(cli, "validate_invocation", lambda *_args, **_kwargs: None)

    def fail_with_private_detail(_request, _manifest):
        raise ValueError(f"GF6_PARAMETER_INVALID: roi for {private_path}")

    monkeypatch.setattr(cli, "dispatch", fail_with_private_detail)

    assert cli.main([
        "--request", str(request_file),
        "--asset-manifest", str(manifest_file),
        "--response", str(response_file),
    ]) == 2

    response = json.loads(response_file.read_text(encoding="utf-8"))
    assert response["error"] == {
        "code": "INVALID_REQUEST",
        "message": "GF6_PARAMETER_INVALID: roi for <local path>",
        "retryable": False,
    }
