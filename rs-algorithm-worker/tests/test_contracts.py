import json
from datetime import datetime, timedelta, timezone

import pytest

from rs_algorithm_worker.contracts import ProtocolError, validate_invocation


UTC = timezone.utc


def request(inputs=None, request_id="req-01"):
    return {
        "schemaVersion": "1.0",
        "operation": "inspect",
        "requestId": request_id,
        "payload": {"inputs": inputs or [{"storageUriSnapshot": {"value": "rs://input/a.jpg"}}]},
    }


def manifest(uris=None, root=None, request_id="req-01", expires_at=None, inputs=None):
    root = root or "/tmp/worker/req-01/output"
    asset_dir = str(__import__("pathlib").Path(root).parent / "assets")
    return {
        "manifestVersion": "1.0",
        "requestId": request_id,
        "grantReference": "grant-01",
        "expiresAt": expires_at or (datetime.now(UTC) + timedelta(minutes=5)).isoformat(),
        "inputs": inputs or [
            {"storageUri": uri, "role": "MAIN", "ordinal": index,
             "path": f"{asset_dir}/{index}.jpg", "checksum": "sha256:test"}
            for index, uri in enumerate(uris or ["rs://input/a.jpg"])
        ],
        "output": {"path": root},
    }


def test_rejects_manifest_uri_not_declared_by_request(tmp_path):
    with pytest.raises(ProtocolError, match="does not match manifest"):
        validate_invocation(request(), manifest(["rs://input/b.jpg"], str(tmp_path)))


def test_rejects_expired_manifest(tmp_path):
    expired = (datetime.now(UTC) - timedelta(seconds=1)).isoformat()
    with pytest.raises(ProtocolError, match="expired"):
        validate_invocation(request(), manifest(root=str(tmp_path), expires_at=expired))


def test_rejects_duplicate_manifest_role_and_ordinal(tmp_path):
    duplicate = manifest(root=str(tmp_path), inputs=[
        {"storageUri": "rs://input/a.jpg", "role": "MAIN", "ordinal": 0,
         "path": str(tmp_path / "a.jpg"), "checksum": "sha256:a"},
        {"storageUri": "rs://input/b.jpg", "role": "MAIN", "ordinal": 0,
         "path": str(tmp_path / "b.jpg"), "checksum": "sha256:b"},
    ])
    with pytest.raises(ProtocolError, match="role and ordinal"):
        validate_invocation(request([
            {"storageUriSnapshot": {"value": "rs://input/a.jpg"}},
            {"storageUriSnapshot": {"value": "rs://input/b.jpg"}},
        ]), duplicate)


def test_rejects_request_id_mismatch_without_disclosing_local_path(tmp_path):
    with pytest.raises(ProtocolError, match="requestId") as error:
        validate_invocation(request(request_id="req-02"), manifest(root=str(tmp_path)))
    assert str(tmp_path) not in str(error.value)


def test_accepts_authorized_asset_root_outside_invocation_directory(tmp_path):
    invocation = tmp_path / "work" / "req-01"
    asset = tmp_path / "assets" / "a.jpg"
    manifest_value = manifest(root=str(invocation / "output"))
    manifest_value["inputs"][0]["path"] = str(asset)
    validate_invocation(request(), manifest_value, manifest_path=invocation / "asset-manifest.json")


def test_accepts_safe_optional_original_filename(tmp_path):
    manifest_value = manifest(root=str(tmp_path))
    manifest_value["inputs"][0]["originalFileName"] = "GF6_WFV_SAMPLE-1.tif"

    validate_invocation(request(), manifest_value)


@pytest.mark.parametrize("original_name", ["../escape.hdf", "directory\\escape.hdf", "bad\u0001.hdf", ""])
def test_rejects_unsafe_original_filename(tmp_path, original_name):
    manifest_value = manifest(root=str(tmp_path))
    manifest_value["inputs"][0]["originalFileName"] = original_name

    with pytest.raises(ProtocolError, match="originalFileName"):
        validate_invocation(request(), manifest_value)


def test_build_plan_reads_inputs_from_product_package(tmp_path):
    value = request()
    value["operation"] = "build-plan"
    value["payload"] = {
        "candidate": {
            "productPackage": {
                "files": [{"storageUriSnapshot": {"value": "rs://input/a.jpg"}}]
            }
        },
        "parameterSnapshot": "{}",
    }

    validate_invocation(value, manifest(root=str(tmp_path)))
