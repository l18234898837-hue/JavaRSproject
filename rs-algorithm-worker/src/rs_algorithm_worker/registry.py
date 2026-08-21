"""Operation dispatch kept behind the validated Worker boundary."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from .product_adapters import fy3h_mersi, fy4b_agri, gf6_wfv_browse, hy1c_czi, hy2d_altimeter


def dispatch(request: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    operation = request["operation"]
    if operation == "inspect":
        adapter = _adapter_for_manifest(manifest)
        candidates = adapter.inspect_manifest(manifest)
        return {"rulePackageVersion": "rules-2026.08",
                "candidates": [_to_response_candidate(item, manifest) for item in candidates]}
    if operation == "build-plan":
        return {"version": "plan-v1", "stages": [{
            "stageKey": "VALIDATE_SOURCE",
            "stageCode": "VALIDATE_SOURCE",
            "dependsOnStageKeys": [],
        }]}
    if operation == "execute-stage":
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": []}
    raise ValueError(f"unsupported operation: {operation}")


def _adapter_for_manifest(manifest):
    roles = {item.get("role") for item in manifest.get("inputs", [])}
    if {"BROWSE_IMAGE", "GEOREFERENCE", "METADATA"}.issubset(roles):
        return gf6_wfv_browse
    paths = [str(item.get("path", "")).lower() for item in manifest.get("inputs", [])]
    if any(path.endswith(".tar.gz") for path in paths):
        return hy2d_altimeter
    if any(path.endswith(".h5") for path in paths) and any(path.endswith(".xml") for path in paths):
        return hy1c_czi
    if any("fy4b" in path or "agri" in path for path in paths):
        return fy4b_agri
    return fy3h_mersi


def _to_response_candidate(item, manifest):
    by_path = {str(entry["path"]): entry for entry in manifest["inputs"]}
    files = []
    for file in item["files"]:
        entry = by_path.get(str(file["path"]))
        if entry is None:
            raise ValueError("adapter file is not declared by manifest")
        files.append({
            "role": file["role"], "ordinal": file["ordinal"],
            "reference": {"sourceType": "LOCAL_FILE", "sourceId": Path(file["path"]).name},
            "storageUriSnapshot": {"value": entry["storageUri"]},
            "checksum": entry.get("checksum", "sha256:manifest-authorized"),
            "required": True,
        })
    return {key: value for key, value in item.items() if key != "files"} | {"files": files}
