"""Read-only, versioned algorithm evaluation report generation.

This module only assembles already validated observations and reference
evaluation results.  It does not read product files, upload reference data, or
change task execution.
"""

from __future__ import annotations

from datetime import datetime, timezone
import json
from typing import Any, Mapping

from .algorithm_evaluation import metric_catalog, observe_artifact_metrics
from .reference_inputs import evaluate_gf6_control_points, evaluate_hy2d_labels


REPORT_VERSION = "algorithm-evaluation-report-v1"
_OBSERVED_REASON = "已从产物检查结果中观测到"
_GF6_REFERENCE_REASON = "需要 GF-6 控制点参考数据后才能评价"
_HY2D_REFERENCE_REASON = "需要 HY-2D 人工标注数据后才能评价"
_UNSUPPORTED_REFERENCE_REASON = "当前版本尚未接入该指标的参考评价计算"


def build_evaluation_report(
    *,
    task_id: str,
    profile_code: str,
    processor_version: str,
    parameter_snapshot: Mapping[str, Any] | None = None,
    inspection: Mapping[str, Any] | None = None,
    reference_payload: Mapping[str, Any] | None = None,
    evaluated_at: datetime | None = None,
) -> dict[str, Any]:
    """Build a JSON-safe evaluation report without touching task state.

    ``inspection`` is the existing bounded artifact inspection output.
    ``reference_payload`` must match the profile's versioned reference input
    contract when supplied.  A missing reference payload is represented
    explicitly in metric statuses rather than treated as a failed evaluation.
    """
    task_id = _required_text(task_id, "task_id")
    processor_version = _required_text(processor_version, "processor_version")
    metric_catalog(profile_code)  # fail early for unsupported profiles
    inspection = {} if inspection is None else _mapping(inspection, "inspection")
    parameters = {} if parameter_snapshot is None else _json_mapping(
        parameter_snapshot, "parameter_snapshot"
    )
    timestamp = _timestamp(evaluated_at)

    observed = observe_artifact_metrics(profile_code, inspection)["metrics"]
    observed_by_key = {item["key"]: item for item in observed}
    reference_info: dict[str, Any] | None = None
    reference_metrics: dict[str, Any] = {}
    if reference_payload is not None:
        reference_result = _evaluate_reference(profile_code, reference_payload)
        reference_metrics = dict(reference_result["metrics"])
        reference_info = {
            "schema": reference_result["referenceSchema"],
            "referenceId": reference_result["referenceId"],
            "sampleCount": reference_metrics.get("sampleCount"),
        }

    metrics: list[dict[str, Any]] = []
    for definition in metric_catalog(profile_code):
        observed_item = observed_by_key[definition.key]
        if observed_item["status"] == "OBSERVED":
            status = "OBSERVED"
            value = observed_item["value"]
            reason = _OBSERVED_REASON
        elif definition.key in _reference_metric_values(profile_code, reference_metrics):
            status = "OBSERVED"
            value = _reference_metric_values(profile_code, reference_metrics)[definition.key]
            reason = "已根据参考数据计算"
        elif definition.reference_required:
            status = "REQUIRES_REFERENCE" if reference_payload is None else "UNAVAILABLE"
            value = None
            reason = (
                _reference_reason(profile_code)
                if reference_payload is None
                else _UNSUPPORTED_REFERENCE_REASON
            )
        else:
            status = "UNAVAILABLE"
            value = None
            reason = "当前版本尚未接入该指标的自动观测"
        metrics.append({
            "key": definition.key,
            "label": definition.label,
            "category": definition.category,
            "direction": definition.direction,
            "unit": definition.unit,
            "status": status,
            "value": value,
            "source": definition.source,
            "referenceRequired": definition.reference_required,
            "reason": reason,
        })

    has_reference_values = any(item["status"] == "OBSERVED" and item["source"].startswith("reference.")
                               for item in metrics)
    return {
        "reportVersion": REPORT_VERSION,
        "generatedAt": timestamp,
        "taskId": task_id,
        "profileCode": profile_code,
        "processorVersion": processor_version,
        "parameterSnapshot": parameters,
        "reference": reference_info,
        "referenceMetrics": reference_metrics or None,
        "evaluationStatus": (
            "REFERENCE_EVALUATED" if has_reference_values
            else "OBSERVED_OUTPUT_METRICS_ONLY"
        ),
        "metrics": metrics,
        "limitations": [
            "该报告只评价已观测的产物质量指标和已提供的参考数据指标。",
            "没有参考产品、控制点或人工标注时，不输出科学精度结论。",
        ],
    }


def generate_evaluation_report(**kwargs: Any) -> dict[str, Any]:
    """Alias with an action-oriented name for callers and future adapters."""
    return build_evaluation_report(**kwargs)


def serialize_evaluation_report(report: Mapping[str, Any]) -> str:
    """Serialize a generated report with strict JSON number handling."""
    _mapping(report, "report")
    try:
        return json.dumps(report, ensure_ascii=False, allow_nan=False, sort_keys=True)
    except (TypeError, ValueError) as error:
        raise ValueError("evaluation report is not JSON serializable") from error


def _evaluate_reference(profile_code: str, payload: Mapping[str, Any]) -> dict[str, Any]:
    payload = _mapping(payload, "reference_payload")
    if profile_code == "GF-6-WFV-L1A":
        if payload.get("schema") != "gf6-control-points-v1":
            raise ValueError("GF-6 reference payload schema does not match profile")
        return evaluate_gf6_control_points(payload)
    if profile_code == "HY-2D-ALTIMETER":
        if payload.get("schema") != "hy2d-labels-v1":
            raise ValueError("HY-2D reference payload schema does not match profile")
        return evaluate_hy2d_labels(payload)
    raise ValueError(
        f"reference evaluation is not supported for profile: {profile_code}"
    )


def _reference_metric_values(
    profile_code: str, reference_metrics: Mapping[str, Any]
) -> dict[str, Any]:
    if not reference_metrics:
        return {}
    if profile_code == "GF-6-WFV-L1A":
        value = reference_metrics.get("maeMeters")
        return {"rpc_geolocation_error": value} if value is not None else {}
    if profile_code == "HY-2D-ALTIMETER":
        values: dict[str, Any] = {}
        if reference_metrics.get("precision") is not None:
            values["anomaly_precision"] = reference_metrics["precision"]
        if reference_metrics.get("recall") is not None:
            values["anomaly_recall"] = reference_metrics["recall"]
        return values
    return {}


def _reference_reason(profile_code: str) -> str:
    if profile_code == "GF-6-WFV-L1A":
        return _GF6_REFERENCE_REASON
    if profile_code == "HY-2D-ALTIMETER":
        return _HY2D_REFERENCE_REASON
    return "需要受支持的参考数据后才能评价"


def _required_text(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} is required")
    return value.strip()


def _mapping(value: Any, field: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{field} must be an object")
    return value


def _json_mapping(value: Mapping[str, Any], field: str) -> dict[str, Any]:
    _mapping(value, field)
    try:
        encoded = json.dumps(value, ensure_ascii=False, allow_nan=False)
        decoded = json.loads(encoded)
    except (TypeError, ValueError) as error:
        raise ValueError(f"{field} must contain JSON-safe values") from error
    if not isinstance(decoded, dict):
        raise ValueError(f"{field} must be an object")
    return decoded


def _timestamp(value: datetime | None) -> str:
    current = datetime.now(timezone.utc) if value is None else value
    if current.tzinfo is None:
        raise ValueError("evaluated_at must include a timezone")
    return current.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
