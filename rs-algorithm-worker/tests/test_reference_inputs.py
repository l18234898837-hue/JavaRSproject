import pytest

from rs_algorithm_worker.reference_inputs import (
    evaluate_gf6_control_points,
    evaluate_hy2d_labels,
    validate_gf6_control_points,
    validate_hy2d_labels,
)


def test_gf6_control_point_contract_produces_geolocation_metrics():
    result = evaluate_gf6_control_points({
        "schema": "gf6-control-points-v1",
        "referenceId": "survey-2026-01",
        "points": [
            {"id": "p1", "predictedLongitude": 120.0, "predictedLatitude": 30.0,
             "referenceLongitude": 120.0001, "referenceLatitude": 30.0},
            {"id": "p2", "predictedLongitude": 121.0, "predictedLatitude": 31.0,
             "referenceLongitude": 121.0, "referenceLatitude": 31.0001},
        ],
    })
    assert result["referenceSchema"] == "gf6-control-points-v1"
    assert result["metrics"]["sampleCount"] == 2
    assert result["metrics"]["maeMeters"] > 0


def test_hy2d_labels_contract_produces_classification_metrics():
    result = evaluate_hy2d_labels({
        "schema": "hy2d-labels-v1",
        "referenceId": "labels-2026-01",
        "labels": [
            {"id": "a", "predictedAnomaly": True, "referenceAnomaly": True},
            {"id": "b", "predictedAnomaly": True, "referenceAnomaly": False},
            {"id": "c", "predictedAnomaly": False, "referenceAnomaly": True},
        ],
    })
    assert result["metrics"]["precision"] == pytest.approx(0.5)
    assert result["metrics"]["recall"] == pytest.approx(0.5)
    assert result["metrics"]["f1"] == pytest.approx(0.5)


def test_reference_contracts_reject_duplicates_and_invalid_coordinates():
    point = {"id": "p1", "predictedLongitude": 0, "predictedLatitude": 0,
             "referenceLongitude": 0, "referenceLatitude": 0}
    with pytest.raises(ValueError, match="unique"):
        validate_gf6_control_points({
            "schema": "gf6-control-points-v1", "referenceId": "survey",
            "points": [point, point],
        })
    with pytest.raises(ValueError, match="invalid"):
        validate_gf6_control_points({
            "schema": "gf6-control-points-v1", "referenceId": "survey",
            "points": [{**point, "predictedLatitude": 91}],
        })


def test_hy2d_reference_requires_strict_booleans():
    with pytest.raises(ValueError, match="boolean"):
        validate_hy2d_labels({
            "schema": "hy2d-labels-v1", "referenceId": "labels",
            "labels": [{"id": "a", "predictedAnomaly": 1, "referenceAnomaly": False}],
        })
