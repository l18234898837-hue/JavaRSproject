"""Numerical evaluation against trusted reference arrays.

The functions in this module are deliberately pure and bounded: callers must
provide already aligned arrays, and no product files or task state are read.
This keeps reference evaluation separate from production processing.
"""

from __future__ import annotations

from typing import Any

import numpy as np


EARTH_RADIUS_METERS = 6_371_008.8
EVALUATION_VERSION = "reference-evaluation-v1"


def evaluate_raster(
    predicted: np.ndarray,
    reference: np.ndarray,
    *,
    valid_mask: np.ndarray | None = None,
) -> dict[str, Any]:
    """Return finite-pixel regression metrics for aligned raster values."""
    prediction, truth = _paired_values(predicted, reference, valid_mask)
    error = prediction - truth
    absolute = np.abs(error)
    result: dict[str, Any] = {
        "evaluationVersion": EVALUATION_VERSION,
        "sampleCount": int(error.size),
        "mae": float(absolute.mean()),
        "rmse": float(np.sqrt(np.mean(error * error))),
        "bias": float(error.mean()),
        "maxAbsoluteError": float(absolute.max()),
    }
    if error.size > 1 and np.std(prediction) > 0 and np.std(truth) > 0:
        result["pearsonCorrelation"] = float(np.corrcoef(prediction, truth)[0, 1])
    else:
        result["pearsonCorrelation"] = None
    return result


def evaluate_geolocation(
    predicted_longitude: np.ndarray,
    predicted_latitude: np.ndarray,
    reference_longitude: np.ndarray,
    reference_latitude: np.ndarray,
) -> dict[str, Any]:
    """Return paired geolocation error in meters using the haversine distance."""
    predicted_lon, predicted_lat, reference_lon, reference_lat = _paired_coordinates(
        predicted_longitude, predicted_latitude, reference_longitude, reference_latitude
    )
    lat1 = np.radians(predicted_lat)
    lat2 = np.radians(reference_lat)
    delta_lat = lat2 - lat1
    delta_lon = np.radians(reference_lon - predicted_lon)
    haversine = np.sin(delta_lat / 2.0) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin(delta_lon / 2.0) ** 2
    distances = 2.0 * EARTH_RADIUS_METERS * np.arcsin(np.sqrt(np.clip(haversine, 0.0, 1.0)))
    lon_error = reference_lon - predicted_lon
    lat_error = reference_lat - predicted_lat
    return {
        "evaluationVersion": EVALUATION_VERSION,
        "sampleCount": int(distances.size),
        "maeMeters": float(np.mean(distances)),
        "rmseMeters": float(np.sqrt(np.mean(distances * distances))),
        "maxMeters": float(np.max(distances)),
        "biasLongitudeDegrees": float(np.mean(lon_error)),
        "biasLatitudeDegrees": float(np.mean(lat_error)),
    }


def evaluate_binary_labels(
    predicted: np.ndarray,
    reference: np.ndarray,
) -> dict[str, Any]:
    """Return confusion-matrix metrics for anomaly/quality labels."""
    prediction, truth = _paired_labels(predicted, reference)
    true_positive = int(np.sum(prediction & truth))
    false_positive = int(np.sum(prediction & ~truth))
    false_negative = int(np.sum(~prediction & truth))
    true_negative = int(np.sum(~prediction & ~truth))
    precision = _ratio(true_positive, true_positive + false_positive)
    recall = _ratio(true_positive, true_positive + false_negative)
    f1 = (
        None if precision is None or recall is None or precision + recall == 0
        else 2.0 * precision * recall / (precision + recall)
    )
    return {
        "evaluationVersion": EVALUATION_VERSION,
        "sampleCount": int(prediction.size),
        "truePositive": true_positive,
        "falsePositive": false_positive,
        "falseNegative": false_negative,
        "trueNegative": true_negative,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def _paired_values(
    predicted: np.ndarray,
    reference: np.ndarray,
    valid_mask: np.ndarray | None,
) -> tuple[np.ndarray, np.ndarray]:
    left = np.asarray(predicted, dtype=float)
    right = np.asarray(reference, dtype=float)
    if left.shape != right.shape or left.size == 0:
        raise ValueError("reference arrays must have matching non-empty shapes")
    mask = np.isfinite(left) & np.isfinite(right)
    if valid_mask is not None:
        supplied = np.asarray(valid_mask, dtype=bool)
        if supplied.shape != left.shape:
            raise ValueError("reference valid mask shape does not match values")
        mask &= supplied
    if not np.any(mask):
        raise ValueError("reference evaluation has no comparable finite values")
    return left[mask].reshape(-1), right[mask].reshape(-1)


def _paired_coordinates(*arrays: np.ndarray) -> tuple[np.ndarray, ...]:
    converted = tuple(np.asarray(value, dtype=float) for value in arrays)
    if any(value.shape != converted[0].shape for value in converted) or converted[0].size == 0:
        raise ValueError("reference coordinate arrays must have matching non-empty shapes")
    mask = np.ones(converted[0].shape, dtype=bool)
    for value in converted:
        mask &= np.isfinite(value)
    mask &= (converted[0] >= -180) & (converted[0] <= 180)
    mask &= (converted[2] >= -180) & (converted[2] <= 180)
    mask &= (converted[1] >= -90) & (converted[1] <= 90)
    mask &= (converted[3] >= -90) & (converted[3] <= 90)
    if not np.any(mask):
        raise ValueError("reference evaluation has no comparable coordinates")
    return tuple(value[mask].reshape(-1) for value in converted)


def _paired_labels(predicted: np.ndarray, reference: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    left = np.asarray(predicted, dtype=bool)
    right = np.asarray(reference, dtype=bool)
    if left.shape != right.shape or left.size == 0:
        raise ValueError("reference label arrays must have matching non-empty shapes")
    return left.reshape(-1), right.reshape(-1)


def _ratio(numerator: int, denominator: int) -> float | None:
    return None if denominator == 0 else float(numerator / denominator)
