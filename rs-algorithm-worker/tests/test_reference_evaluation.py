import numpy as np
import pytest

from rs_algorithm_worker.reference_evaluation import (
    evaluate_binary_labels,
    evaluate_geolocation,
    evaluate_raster,
)


def test_evaluate_raster_returns_error_metrics_and_correlation():
    result = evaluate_raster(
        np.array([[1.0, 2.0], [3.0, np.nan]]),
        np.array([[0.0, 2.0], [5.0, 4.0]]),
    )
    assert result["sampleCount"] == 3
    assert result["mae"] == pytest.approx(1.0)
    assert result["rmse"] == pytest.approx(np.sqrt(5 / 3))
    assert result["bias"] == pytest.approx(-1 / 3)
    assert result["pearsonCorrelation"] is not None


def test_evaluate_geolocation_reports_zero_for_identical_coordinates():
    result = evaluate_geolocation(
        np.array([120.0, 121.0]),
        np.array([30.0, 31.0]),
        np.array([120.0, 121.0]),
        np.array([30.0, 31.0]),
    )
    assert result["sampleCount"] == 2
    assert result["maeMeters"] == pytest.approx(0.0)
    assert result["rmseMeters"] == pytest.approx(0.0)


def test_evaluate_binary_labels_returns_confusion_and_f1():
    result = evaluate_binary_labels(
        np.array([True, True, False, False]),
        np.array([True, False, True, False]),
    )
    assert result["truePositive"] == 1
    assert result["falsePositive"] == 1
    assert result["falseNegative"] == 1
    assert result["trueNegative"] == 1
    assert result["precision"] == pytest.approx(0.5)
    assert result["recall"] == pytest.approx(0.5)
    assert result["f1"] == pytest.approx(0.5)


def test_reference_evaluation_rejects_unaligned_or_empty_inputs():
    with pytest.raises(ValueError, match="matching"):
        evaluate_raster(np.ones(2), np.ones((2, 1)))
    with pytest.raises(ValueError, match="no comparable"):
        evaluate_geolocation(
            np.array([np.nan]), np.array([30.0]),
            np.array([120.0]), np.array([30.0]),
        )
