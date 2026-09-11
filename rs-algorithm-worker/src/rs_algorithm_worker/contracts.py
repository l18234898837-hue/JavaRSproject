"""Strict boundary validation for the Java Worker envelope and local asset manifest."""

from __future__ import annotations

import math
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA_VERSION = "1.0"
MANIFEST_VERSION = "1.0"


class ProtocolError(ValueError):
    """Non-retryable protocol input error safe to expose to the caller."""


def validate_invocation(
    request: dict[str, Any],
    manifest: dict[str, Any],
    *,
    manifest_path: str | os.PathLike[str] | None = None,
    now: datetime | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    _require_object(request, "request")
    _require_object(manifest, "manifest")
    if request.get("schemaVersion") != SCHEMA_VERSION:
        raise ProtocolError("unsupported request schema")
    if manifest.get("manifestVersion") != MANIFEST_VERSION:
        raise ProtocolError("unsupported manifest version")
    request_id = request.get("requestId")
    if not isinstance(request_id, str) or not request_id:
        raise ProtocolError("requestId is required")
    if manifest.get("requestId") != request_id:
        raise ProtocolError("requestId does not match manifest")

    expires_at = _parse_time(manifest.get("expiresAt"), "manifest expiration")
    current = now or datetime.now(timezone.utc)
    if expires_at <= current:
        raise ProtocolError("manifest is expired")

    manifest_inputs = manifest.get("inputs")
    if not isinstance(manifest_inputs, list) or not manifest_inputs:
        raise ProtocolError("manifest inputs are required")
    manifest_uris: list[str] = []
    coordinates: set[tuple[str, int]] = set()
    for item in manifest_inputs:
        _require_object(item, "manifest input")
        uri = item.get("storageUri")
        role = item.get("role")
        ordinal = item.get("ordinal")
        path = item.get("path")
        original_file_name = item.get("originalFileName")
        if not isinstance(uri, str) or not uri:
            raise ProtocolError("manifest storageUri is required")
        if not isinstance(role, str) or not role:
            raise ProtocolError("manifest role is required")
        if not isinstance(ordinal, int) or isinstance(ordinal, bool) or ordinal < 0:
            raise ProtocolError("manifest ordinal is invalid")
        if not isinstance(path, str) or not Path(path).is_absolute():
            raise ProtocolError("manifest input path must be absolute")
        if "originalFileName" in item and not _safe_original_file_name(original_file_name):
            raise ProtocolError("manifest originalFileName is invalid")
        if uri in manifest_uris:
            raise ProtocolError("duplicate manifest storageUri")
        coordinate = (role, ordinal)
        if coordinate in coordinates:
            raise ProtocolError("duplicate manifest role and ordinal")
        manifest_uris.append(uri)
        coordinates.add(coordinate)

    request_uris = _request_uris(request)
    if set(request_uris) != set(manifest_uris) or len(request_uris) != len(manifest_uris):
        raise ProtocolError("request input set does not match manifest")

    output = manifest.get("output")
    _require_object(output, "manifest output")
    output_path = output.get("path")
    if not isinstance(output_path, str) or not Path(output_path).is_absolute():
        raise ProtocolError("manifest output path must be absolute")
    if manifest_path is not None:
        invocation_dir = Path(manifest_path).resolve().parent
        if not _within(Path(output_path), invocation_dir):
            raise ProtocolError("manifest output is outside invocation directory")
    return request, manifest


def _request_uris(request: dict[str, Any]) -> list[str]:
    payload = request.get("payload")
    _require_object(payload, "request payload")
    operation = request.get("operation")
    if operation in {"inspect", "inspect-artifact"}:
        inputs = payload.get("inputs")
    elif operation == "execute-stage":
        inputs = payload.get("inputs")
    elif operation == "build-plan":
        candidate = payload.get("candidate")
        _require_object(candidate, "candidate")
        inputs = candidate.get("files")
        if not isinstance(inputs, list):
            product_package = candidate.get("productPackage")
            if isinstance(product_package, dict):
                inputs = product_package.get("files")
    else:
        raise ProtocolError("unsupported operation")
    if not isinstance(inputs, list) or not inputs:
        raise ProtocolError("request inputs are required")
    result: list[str] = []
    for item in inputs:
        _require_object(item, "request input")
        snapshot = item.get("storageUriSnapshot")
        if isinstance(snapshot, dict):
            uri = snapshot.get("value")
        else:
            uri = item.get("storageUri")
        if not isinstance(uri, str) or not uri:
            raise ProtocolError("request input storageUri is required")
        result.append(uri)
    return result


def _parse_time(value: Any, label: str) -> datetime:
    if not isinstance(value, str):
        raise ProtocolError(f"{label} is invalid")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ProtocolError(f"{label} is invalid") from error
    if parsed.tzinfo is None:
        raise ProtocolError(f"{label} must include timezone")
    return parsed.astimezone(timezone.utc)


def _within(path: Path, parent: Path) -> bool:
    try:
        path.resolve().relative_to(parent.resolve())
        return True
    except ValueError:
        return False


def _safe_original_file_name(value: Any) -> bool:
    return (
        isinstance(value, str)
        and bool(value.strip())
        and len(value) <= 255
        and value not in {".", ".."}
        and "/" not in value
        and "\\" not in value
        and not any(ord(character) < 32 or 127 <= ord(character) <= 159 for character in value)
    )


def _require_object(value: Any, label: str) -> None:
    if not isinstance(value, dict):
        raise ProtocolError(f"{label} must be an object")
