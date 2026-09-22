import pytest

from rs_algorithm_worker.evaluation_report import (
    REPORT_VERSION,
    build_evaluation_report,
    serialize_evaluation_report,
)


EVALUATED_AT = __import__("datetime").datetime(
    2026, 9, 22, 12, 30, tzinfo=__import__("datetime").timezone.utc
)


def test_gf6_report_contains_observed_and_reference_metrics():
    report = build_evaluation_report(
        task_id="task-gf6-1",
        profile_code="GF-6-WFV-L1A",
        processor_version="gf6-worker-2.4.0",
        parameter_snapshot={"resampling": "bilinear", "tileSize": 512},
        inspection={"quality": {"boundsValid": True, "validPixelRatio": 0.92}},
        reference_payload={
            "schema": "gf6-control-points-v1",
            "referenceId": "survey-2026-01",
            "points": [
                {"id": "p1", "predictedLongitude": 120, "predictedLatitude": 30,
                 "referenceLongitude": 120.0001, "referenceLatitude": 30},
                {"id": "p2", "predictedLongitude": 121, "predictedLatitude": 31,
                 "referenceLongitude": 121, "referenceLatitude": 31.0001},
            ],
        },
        evaluated_at=EVALUATED_AT,
    )
    metrics = {item["key"]: item for item in report["metrics"]}
    assert report["reportVersion"] == REPORT_VERSION
    assert report["generatedAt"] == "2026-09-22T12:30:00Z"
    assert report["reference"]["referenceId"] == "survey-2026-01"
    assert report["evaluationStatus"] == "REFERENCE_EVALUATED"
    assert metrics["bounds_valid"]["value"] is True
    assert metrics["rpc_geolocation_error"]["status"] == "OBSERVED"
    assert metrics["rpc_geolocation_error"]["value"] > 0
    assert metrics["projection_error"]["status"] == "UNAVAILABLE"


def test_hy2d_report_maps_reference_precision_and_recall():
    report = build_evaluation_report(
        task_id="task-hy2d-1",
        profile_code="HY-2D-ALTIMETER",
        processor_version="hy2d-worker-1.8.0",
        inspection={"quality": {"validRatio": 0.8, "rowCount": 10}},
        reference_payload={
            "schema": "hy2d-labels-v1",
            "referenceId": "labels-2026-01",
            "labels": [
                {"id": "a", "predictedAnomaly": True, "referenceAnomaly": True},
                {"id": "b", "predictedAnomaly": True, "referenceAnomaly": False},
                {"id": "c", "predictedAnomaly": False, "referenceAnomaly": True},
            ],
        },
        evaluated_at=EVALUATED_AT,
    )
    metrics = {item["key"]: item for item in report["metrics"]}
    assert metrics["track_valid_ratio"]["value"] == 0.8
    assert metrics["anomaly_precision"]["value"] == pytest.approx(0.5)
    assert metrics["anomaly_recall"]["value"] == pytest.approx(0.5)
    assert report["referenceMetrics"]["sampleCount"] == 3


def test_missing_reference_is_explicit_and_does_not_fail_report():
    report = build_evaluation_report(
        task_id="task-gf6-2",
        profile_code="GF-6-WFV-L1A",
        processor_version="gf6-worker-2.4.0",
        inspection={"quality": {"boundsValid": True}},
        evaluated_at=EVALUATED_AT,
    )
    metrics = {item["key"]: item for item in report["metrics"]}
    assert report["evaluationStatus"] == "OBSERVED_OUTPUT_METRICS_ONLY"
    assert metrics["rpc_geolocation_error"]["status"] == "REQUIRES_REFERENCE"
    assert "控制点" in metrics["rpc_geolocation_error"]["reason"]
    assert metrics["mosaic_gap_ratio"]["status"] == "UNAVAILABLE"


def test_invalid_reference_and_parameters_are_rejected():
    with pytest.raises(ValueError, match="schema"):
        build_evaluation_report(
            task_id="task-1",
            profile_code="GF-6-WFV-L1A",
            processor_version="v1",
            reference_payload={"schema": "hy2d-labels-v1"},
        )
    with pytest.raises(ValueError, match="JSON-safe"):
        build_evaluation_report(
            task_id="task-1",
            profile_code="GF-6-WFV-L1A",
            processor_version="v1",
            parameter_snapshot={"invalid": float("nan")},
        )


def test_report_serialization_is_strict_json():
    report = build_evaluation_report(
        task_id="task-1",
        profile_code="HY-2D-ALTIMETER",
        processor_version="v1",
        evaluated_at=EVALUATED_AT,
    )
    encoded = serialize_evaluation_report(report)
    assert '"reportVersion": "algorithm-evaluation-report-v1"' in encoded
    assert "\\u" not in encoded
