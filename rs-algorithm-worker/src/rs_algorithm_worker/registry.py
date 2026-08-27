"""Operation dispatch kept behind the validated Worker boundary."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from .product_adapters import fy3h_mersi, fy4b_agri, gf6_wfv_browse, hy1c_czi, hy1c_czi_l2a, hy2d_altimeter
from .hy2d_pipeline import execute_hy2d_stage
from .fy3h_mersi_pipeline import execute_fy3h_stage


LOGICAL_OUTPUT_URI = re.compile(r"rs://output/[A-Za-z0-9][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*/?")


def dispatch(request: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    operation = request["operation"]
    if operation == "inspect":
        adapter = _adapter_for_manifest(manifest)
        candidates = adapter.inspect_manifest(manifest)
        return {"rulePackageVersion": "rules-2026.08",
                "candidates": [_to_response_candidate(item, manifest) for item in candidates]}
    if operation == "build-plan":
        payload = _payload(request)
        selected = payload.get("candidate")
        if _is_l2a_candidate(selected):
            candidate = hy1c_czi_l2a.inspect_l2a_manifest(manifest)
            if candidate["packageId"] != selected.get("packageId"):
                raise ValueError("recognized HY-1C CZI L2A packageId is required")
            return hy1c_czi_l2a.build_l2a_plan(candidate, _parameter_snapshot(payload))
        adapter = _adapter_for_manifest(manifest)
        if adapter is hy2d_altimeter:
            candidate = _recognized_hy2d_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            return hy2d_altimeter.build_plan(candidate, _parameter_snapshot(payload))
        if adapter is fy3h_mersi:
            candidate = _recognized_fy3h_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            return fy3h_mersi.build_plan(candidate, _parameter_snapshot(payload))
        if adapter is gf6_wfv_browse:
            candidate = _recognized_gf_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            return adapter.build_plan(candidate, _parameter_snapshot(payload))
        return {"version": "plan-v1", "stages": [{
            "stageKey": "VALIDATE_SOURCE",
            "stageCode": "VALIDATE_SOURCE",
            "dependsOnStageKeys": [],
        }]}
    if operation == "execute-stage":
        payload = _payload(request)
        if payload.get("profileCode") == "FY-3H-MERSI":
            return execute_fy3h_stage(payload, manifest)
        if payload.get("profileCode") == "HY-2D-ALTIMETER":
            return execute_hy2d_stage(payload, manifest)
        adapter = _adapter_for_manifest(manifest)
        if adapter is hy2d_altimeter:
            return execute_hy2d_stage(payload, manifest)
        if adapter is gf6_wfv_browse:
            return _execute_gf_browse_stage(payload, manifest)
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


def _is_l2a_candidate(candidate: Any) -> bool:
    return isinstance(candidate, dict) and candidate.get("profileKey") == {
        "productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"
    }


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


def _payload(request: dict[str, Any]) -> dict[str, Any]:
    payload = request.get("payload")
    if not isinstance(payload, dict):
        raise ValueError("request payload is required")
    return payload


def _parameter_snapshot(payload: dict[str, Any]) -> dict[str, Any]:
    snapshot = payload.get("parameterSnapshot")
    if isinstance(snapshot, dict):
        return snapshot
    if not isinstance(snapshot, str):
        raise ValueError("UNSUPPORTED_BROWSE_OPERATION: invalid parameter snapshot")
    try:
        value = json.loads(snapshot)
    except json.JSONDecodeError as error:
        raise ValueError("UNSUPPORTED_BROWSE_OPERATION: invalid parameter snapshot") from error
    if not isinstance(value, dict):
        raise ValueError("UNSUPPORTED_BROWSE_OPERATION: invalid parameter snapshot")
    return value


def _recognized_gf_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized GF-6 packageId is required")
    candidates = gf6_wfv_browse.inspect_manifest(manifest)
    for candidate in candidates:
        if candidate["packageId"] == package_id:
            return candidate
    raise ValueError("recognized GF-6 packageId is required")


def _recognized_hy2d_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized HY-2D packageId is required")
    for item in hy2d_altimeter.inspect_manifest(manifest):
        if item["packageId"] == package_id:
            return item
    raise ValueError("recognized HY-2D packageId is required")


def _recognized_fy3h_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized FY-3H MERSI packageId is required")
    for item in fy3h_mersi.inspect_manifest(manifest):
        if item["packageId"] == package_id:
            return item
    raise ValueError("recognized FY-3H MERSI packageId is required")


def _execute_gf_browse_stage(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    package_id = payload.get("packageId")
    if isinstance(package_id, str) and package_id:
        candidate = _recognized_gf_candidate(manifest, package_id)
    else:
        candidates = gf6_wfv_browse.inspect_manifest(manifest)
        if len(candidates) != 1:
            raise ValueError("recognized GF-6 packageId is required")
        candidate = candidates[0]
    plan = gf6_wfv_browse.build_plan(candidate, _parameter_snapshot(payload))
    stage_key = payload.get("stageKey")
    stages = {stage["stageKey"] for stage in plan["stages"]}
    if stage_key not in stages:
        raise ValueError("unsupported GF-6 browse stage")
    result = {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": []}
    if stage_key != "WRITE_BROWSE_REPORT":
        return result

    output = manifest.get("output")
    output_dir = output.get("path") if isinstance(output, dict) else None
    output_uri = _logical_output_uri(payload.get("outputUri"))
    if not isinstance(output_dir, str):
        raise ValueError("GF-6 browse report output is required")
    report = gf6_wfv_browse.execute_visual_plan(candidate, output_dir)
    content = report.read_bytes()
    result["artifacts"] = [{
        "uri": output_uri.rstrip("/") + "/browse-report.json",
        "type": "BROWSE_REPORT_JSON",
        "sizeBytes": len(content),
        "checksum": "sha256:" + hashlib.sha256(content).hexdigest(),
    }]
    return result


def _logical_output_uri(value: Any) -> str:
    if not isinstance(value, str) or not LOGICAL_OUTPUT_URI.fullmatch(value):
        raise ValueError("GF-6 browse report requires a logical output URI")
    segments = value.removeprefix("rs://output/").rstrip("/").split("/")
    if any(segment in {".", ".."} for segment in segments):
        raise ValueError("GF-6 browse report requires a logical output URI")
    return value.rstrip("/")
