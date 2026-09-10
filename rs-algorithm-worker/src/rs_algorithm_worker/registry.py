"""Operation dispatch kept behind the validated Worker boundary."""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

from .fy3h_mersi_pipeline import execute_fy3h_product, execute_fy3h_stage
from .fy4b_agri_pipeline import execute_fy4b_product, execute_fy4b_stage
from .gf6_wfv_pipeline import execute_gf6_product, execute_gf6_stage
from .hy2d_pipeline import execute_hy2d_product, execute_hy2d_stage
from .l2a_pipeline import execute_l2a_product, execute_l2a_staged_stage
from .output_uri import logical_output_uri
from .product_adapters import base, fy3h_mersi, fy4b_agri, gf6_wfv_browse, gf6_wfv_l1a, hy1c_czi, hy1c_czi_l2a, hy2d_altimeter
from .product_adapters.base import logical_file_name


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
        requested_version = payload.get("requestedPlanVersion")
        if _is_l2a_candidate(selected):
            candidate = hy1c_czi_l2a.inspect_l2a_manifest(manifest)
            if candidate["packageId"] != selected.get("packageId"):
                raise ValueError("recognized HY-1C CZI L2A packageId is required")
            if requested_version == "plan-v3":
                return hy1c_czi_l2a.build_l2a_staged_plan(candidate, _parameter_snapshot(payload))
            return hy1c_czi_l2a.build_l2a_plan(candidate, _parameter_snapshot(payload))
        adapter = _adapter_for_manifest(manifest)
        if adapter is fy3h_mersi:
            candidate = _recognized_fy3h_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            builder = fy3h_mersi.build_staged_plan if requested_version == "plan-v3" else fy3h_mersi.build_integrated_plan
            return _with_requested_plan_version(builder(candidate, _parameter_snapshot(payload)), requested_version)
        if adapter is hy2d_altimeter:
            candidate = _recognized_hy2d_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            builder = hy2d_altimeter.build_staged_plan if requested_version == "plan-v3" else hy2d_altimeter.build_integrated_plan
            return _with_requested_plan_version(builder(candidate, _parameter_snapshot(payload)), requested_version)
        if adapter is fy4b_agri:
            candidate = _recognized_fy4b_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            builder = fy4b_agri.build_staged_plan if requested_version == "plan-v3" else fy4b_agri.build_integrated_plan
            return _with_requested_plan_version(builder(candidate, _parameter_snapshot(payload)), requested_version)
        if adapter is gf6_wfv_l1a:
            candidate = _recognized_gf6_l1a_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            if requested_version == "plan-v3":
                return gf6_wfv_l1a.build_staged_plan(candidate, _parameter_snapshot(payload))
            return gf6_wfv_l1a.build_integrated_plan(candidate, _parameter_snapshot(payload))
        if adapter is gf6_wfv_browse:
            candidate = _recognized_gf_candidate(manifest, payload.get("candidate", {}).get("packageId"))
            builder = adapter.build_staged_plan if requested_version == "plan-v3" else adapter.build_plan
            return _with_requested_plan_version(builder(candidate, _parameter_snapshot(payload)), requested_version)
        if adapter is base:
            candidate = base.candidate("GENERIC", "RASTER_GRID", "GENERIC", [Path(item["path"]) for item in manifest.get("inputs", [])], {})
            return _with_requested_plan_version(base.build_plan(candidate), requested_version)
        version = "plan-v3" if requested_version == "plan-v3" else "plan-v1"
        return {"version": version, "stages": [{
            "stageKey": "VALIDATE_SOURCE",
            "stageCode": "VALIDATE_SOURCE",
            "dependsOnStageKeys": [],
            "inputs": [{"key": "main", "source": "RAW_INPUT", "artifactType": "MAIN", "required": True}],
            "outputs": [{"artifactType": "VALIDATION_METADATA_JSON", "relativePath": "validation/source.json", "retention": "INTERMEDIATE"}] if version == "plan-v3" else [],
        }]}
    if operation == "execute-stage":
        payload = _payload(request)
        if payload.get("profileCode") == "HY-1C-CZI" and payload.get("planVersion") == "plan-v3":
            return execute_l2a_staged_stage(payload, manifest)
        if _is_l2a_execution(payload):
            return {
                "workerId": "worker-local",
                "status": "SUCCEEDED",
                "artifacts": execute_l2a_product(manifest, _parameter_snapshot(payload), logical_output_uri(payload.get("outputUri"))),
            }
        if payload.get("profileCode") == "FY-4B-AGRI":
            if payload.get("planVersion") == "plan-v3":
                return execute_fy4b_stage(payload, manifest)
            if payload.get("stageKey") != "STANDARDIZE_FY4B_AGRI":
                raise ValueError("unsupported FY-4B AGRI stage")
            return execute_fy4b_product(payload, manifest)
        if payload.get("profileCode") == "GF-6-WFV-L1A":
            if payload.get("planVersion") == "plan-v3":
                return execute_gf6_stage(payload, manifest)
            if payload.get("stageKey") != "STANDARDIZE_GF6_WFV_L1A":
                raise ValueError("unsupported GF6 L1A stage")
            return execute_gf6_product(payload, manifest)
        if payload.get("profileCode") == "FY-3H-MERSI":
            if payload.get("stageKey") == "STANDARDIZE_MERSI_L1":
                return execute_fy3h_product(payload, manifest)
            if payload.get("planVersion") != "plan-v3":
                raise ValueError("unsupported FY-3H MERSI stage")
            return execute_fy3h_stage(payload, manifest)
        if payload.get("profileCode") == "HY-2D-ALTIMETER":
            if payload.get("stageKey") == "STANDARDIZE_ALTIMETER_TRACK":
                return execute_hy2d_product(payload, manifest)
            if payload.get("planVersion") != "plan-v3":
                raise ValueError("unsupported HY-2D stage")
            return execute_hy2d_stage(payload, manifest)
        adapter = _adapter_for_manifest(manifest)
        if adapter is hy2d_altimeter:
            if payload.get("stageKey") == "STANDARDIZE_ALTIMETER_TRACK":
                return execute_hy2d_product(payload, manifest)
            if payload.get("planVersion") != "plan-v3":
                raise ValueError("unsupported HY-2D stage")
            return execute_hy2d_stage(payload, manifest)
        if adapter is gf6_wfv_browse:
            if payload.get("planVersion") == "plan-v3":
                package_id = payload.get("packageId")
                if package_id:
                    candidate = _recognized_gf_candidate(manifest, package_id)
                else:
                    candidates = gf6_wfv_browse.inspect_manifest(manifest)
                    if len(candidates) != 1:
                        raise ValueError("recognized GF-6 packageId is required")
                    candidate = candidates[0]
                return gf6_wfv_browse.execute_visual_stage(payload.get("stageKey"), candidate, manifest, (manifest.get("output") or {}).get("path"), payload.get("outputUri"))
            return _execute_gf_browse_stage(payload, manifest)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": []}
    raise ValueError(f"unsupported operation: {operation}")


def _with_requested_plan_version(plan: dict[str, Any], requested_version: Any) -> dict[str, Any]:
    if requested_version is None:
        return plan
    if requested_version == "plan-v2":
        return plan
    if requested_version != "plan-v3":
        raise ValueError("unsupported requested plan version")
    return {**plan, "version": "plan-v3"}


def _adapter_for_manifest(manifest):
    roles = {item.get("role") for item in manifest.get("inputs", [])}
    if _has_gf6_l1a_evidence(manifest, roles):
        return gf6_wfv_l1a
    if {"BROWSE_IMAGE", "GEOREFERENCE", "METADATA"}.issubset(roles):
        return gf6_wfv_browse
    paths = [_manifest_input_name(item).lower() for item in manifest.get("inputs", [])]
    if any(path.endswith((".tar.gz", ".tgz", ".gz")) for path in paths):
        return hy2d_altimeter
    if any(path.endswith(".h5") for path in paths) and any(path.endswith(".xml") for path in paths):
        if hy1c_czi_l2a.has_l2a_product_evidence(manifest):
            return hy1c_czi_l2a
        return hy1c_czi
    if _has_fy4b_agri_evidence(manifest, roles):
        return fy4b_agri
    if any(Path(_manifest_input_name(item)).suffix.lower() == ".hdf" for item in manifest.get("inputs", [])):
        return fy3h_mersi
    return base


def _has_gf6_l1a_evidence(manifest: dict[str, Any], roles: set[Any]) -> bool:
    required_roles = {"METADATA_XML", "RPC_RPB", "NAV", "TILE_INDEX_TIL"}
    if required_roles.issubset(roles) and sum(item.get("role") == "MAIN_TIFF_TILE" for item in manifest.get("inputs", [])) == 3:
        return True
    suffixes = [Path(_manifest_input_name(item)).suffix.lower() for item in manifest.get("inputs", [])]
    return suffixes.count(".tif") + suffixes.count(".tiff") == 3 and {".xml", ".rpb", ".nav", ".til"}.issubset(suffixes)


def _has_fy4b_agri_evidence(manifest: dict[str, Any], roles: set[Any]) -> bool:
    required_roles = {"L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M"}
    if required_roles.issubset(roles):
        return True
    names = [_manifest_input_name(item).upper() for item in manifest.get("inputs", [])]
    role_patterns = {
        "L1_0500M": r"^FY4B-_AGRI--_N_DISK_.+_L1-_FDI-_MULT_NOM_\d{14}_\d{14}_(?:0500M|500M)_V[^.]+\.HDF$",
        "L1_1000M": r"^FY4B-_AGRI--_N_DISK_.+_L1-_FDI-_MULT_NOM_\d{14}_\d{14}_1000M_V[^.]+\.HDF$",
        "L1_2000M": r"^FY4B-_AGRI--_N_DISK_.+_L1-_FDI-_MULT_NOM_\d{14}_\d{14}_2000M_V[^.]+\.HDF$",
        "L1_4000M": r"^FY4B-_AGRI--_N_DISK_.+_L1-_FDI-_MULT_NOM_\d{14}_\d{14}_4000M_V[^.]+\.HDF$",
        "GEO_4000M": r"^FY4B-_AGRI--_N_DISK_.+_L1-_GEO-_MULT_NOM_\d{14}_\d{14}_4000M_V[^.]+\.HDF$",
    }
    return all(sum(re.fullmatch(pattern, name) is not None for name in names) == 1 for pattern in role_patterns.values())


def _is_l2a_candidate(candidate: Any) -> bool:
    return isinstance(candidate, dict) and candidate.get("profileKey") == {
        "productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"
    }


def _is_l2a_execution(payload: dict[str, Any]) -> bool:
    return (
        payload.get("profileCode") == "HY-1C-CZI"
        and payload.get("profileVersion") == "L2A-v1"
        and payload.get("stageKey") == "STANDARDIZE_L2A"
    )


def _to_response_candidate(item, manifest):
    by_path = {str(entry["path"]): entry for entry in manifest["inputs"]}
    files = []
    for file in item["files"]:
        entry = by_path.get(str(file["path"]))
        if entry is None:
            raise ValueError("adapter file is not declared by manifest")
        files.append({
            "role": file["role"], "ordinal": file["ordinal"],
            "reference": {"sourceType": "LOCAL_FILE", "sourceId": file.get("originalFileName", Path(file["path"]).name)},
            "storageUriSnapshot": {"value": entry["storageUri"]},
            "checksum": entry.get("checksum", "sha256:manifest-authorized"),
            "required": file.get("required", True),
        })
    return {key: value for key, value in item.items() if key != "files"} | {"files": files}


def _manifest_input_name(item: Any) -> str:
    if not isinstance(item, dict):
        return ""
    path = item.get("path")
    if not isinstance(path, str):
        return ""
    return logical_file_name(Path(path), item)


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


def _recognized_fy3h_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized FY-3H MERSI packageId is required")
    for candidate in fy3h_mersi.inspect_manifest(manifest):
        if candidate["packageId"] == package_id:
            return candidate
    raise ValueError("recognized FY-3H MERSI packageId is required")


def _recognized_hy2d_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized HY-2D packageId is required")
    for candidate in hy2d_altimeter.inspect_manifest(manifest):
        if candidate["packageId"] == package_id:
            return candidate
    raise ValueError("recognized HY-2D packageId is required")


def _recognized_fy4b_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized FY-4B AGRI packageId is required")
    for candidate in fy4b_agri.inspect_manifest(manifest):
        if candidate["packageId"] == package_id:
            return candidate
    raise ValueError("recognized FY-4B AGRI packageId is required")


def _recognized_gf6_l1a_candidate(manifest: dict[str, Any], package_id: Any) -> dict[str, Any]:
    if not isinstance(package_id, str) or not package_id:
        raise ValueError("recognized GF6 L1A packageId is required")
    for candidate in gf6_wfv_l1a.inspect_manifest(manifest):
        if candidate["packageId"] == package_id:
            return candidate
    raise ValueError("recognized GF6 L1A packageId is required")


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
    output_uri = logical_output_uri(payload.get("outputUri"))
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
