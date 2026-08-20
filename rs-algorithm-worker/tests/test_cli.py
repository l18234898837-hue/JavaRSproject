import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from PIL import Image


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
    assert not response_file.with_suffix(".json.tmp").exists()
