"""Versioned, read-only reference-data contracts for evaluation."""

from __future__ import annotations

from typing import Any, Mapping

import numpy as np

from .reference_evaluation import evaluate_binary_labels, evaluate_geolocation


GF6_REFERENCE_SCHEMA = "gf6-control-points-v1"
HY2D_REFERENCE_SCHEMA = "hy2d-labels-v1"
MAX_REFERENCE_ROWS = 100_000


def evaluate_gf6_control_points(payload: Mapping[str, Any]) -> dict[str, Any]:
    points = validate_gf6_control_points(payload)
    result = evaluate_geolocation(
        np.array([point["predictedLongitude"] for point in points], dtype=float),
        np.array([point["predictedLatitude"] for point in points], dtype=float),
        np.array([point["referenceLongitude"] for point in points], dtype=float),
        np.array([point["referenceLatitude"] for point in points], dtype=float),
    )
    return {"referenceSchema": GF6_REFERENCE_SCHEMA, "profileCode": "GF-6-WFV-L1A",
            "referenceId": payload["referenceId"], "metrics": result}


def evaluate_hy2d_labels(payload: Mapping[str, Any]) -> dict[str, Any]:
    labels = validate_hy2d_labels(payload)
    result = evaluate_binary_labels(
        np.array([item["predictedAnomaly"] for item in labels], dtype=bool),
        np.array([item["referenceAnomaly"] for item in labels], dtype=bool),
    )
    return {"referenceSchema": HY2D_REFERENCE_SCHEMA, "profileCode": "HY-2D-ALTIMETER",
            "referenceId": payload["referenceId"], "metrics": result}


def validate_gf6_control_points(payload: Mapping[str, Any]) -> list[dict[str, Any]]:
    _require_mapping(payload, "GF-6 reference")
    if payload.get("schema") != GF6_REFERENCE_SCHEMA:
        raise ValueError("GF-6 reference schema is unsupported")
    _reference_id(payload, "GF-6")
    points = payload.get("points")
    if not isinstance(points, list) or not points or len(points) > MAX_REFERENCE_ROWS:
        raise ValueError("GF-6 reference points are invalid")
    normalized: list[dict[str, Any]] = []
    identifiers: set[str] = set()
    for point in points:
        _require_mapping(point, "GF-6 control point")
        identifier = _identifier(point.get("id"), "GF-6 control point id")
        if identifier in identifiers:
            raise ValueError("GF-6 control point ids must be unique")
        identifiers.add(identifier)
        normalized.append({
            "id": identifier,
            "predictedLongitude": _coordinate(point.get("predictedLongitude"), -180, 180, "predictedLongitude"),
            "predictedLatitude": _coordinate(point.get("predictedLatitude"), -90, 90, "predictedLatitude"),
            "referenceLongitude": _coordinate(point.get("referenceLongitude"), -180, 180, "referenceLongitude"),
            "referenceLatitude": _coordinate(point.get("referenceLatitude"), -90, 90, "referenceLatitude"),
        })
    return normalized


def validate_hy2d_labels(payload: Mapping[str, Any]) -> list[dict[str, Any]]:
    _require_mapping(payload, "HY-2D reference")
    if payload.get("schema") != HY2D_REFERENCE_SCHEMA:
        raise ValueError("HY-2D reference schema is unsupported")
    _reference_id(payload, "HY-2D")
    labels = payload.get("labels")
    if not isinstance(labels, list) or not labels or len(labels) > MAX_REFERENCE_ROWS:
        raise ValueError("HY-2D labels are invalid")
    normalized: list[dict[str, Any]] = []
    identifiers: set[str] = set()
    for label in labels:
        _require_mapping(label, "HY-2D label")
        identifier = _identifier(label.get("id"), "HY-2D label id")
        if identifier in identifiers:
            raise ValueError("HY-2D label ids must be unique")
        identifiers.add(identifier)
        normalized.append({
            "id": identifier,
            "predictedAnomaly": _boolean(label.get("predictedAnomaly"), "predictedAnomaly"),
            "referenceAnomaly": _boolean(label.get("referenceAnomaly"), "referenceAnomaly"),
        })
    return normalized


def _require_mapping(value: Any, label: str) -> None:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")


def _reference_id(payload: Mapping[str, Any], profile: str) -> str:
    value = payload.get("referenceId")
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{profile} referenceId is required")
    return value.strip()


def _identifier(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > 128:
        raise ValueError(f"{label} is invalid")
    return value.strip()


def _coordinate(value: Any, minimum: float, maximum: float, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"GF-6 {label} is invalid")
    numeric = float(value)
    if not np.isfinite(numeric) or numeric < minimum or numeric > maximum:
        raise ValueError(f"GF-6 {label} is invalid")
    return numeric


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"HY-2D {label} must be boolean")
    return value
