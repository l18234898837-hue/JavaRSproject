from __future__ import annotations

import numpy as np
import pytest

from rs_algorithm_worker.algorithms.track_anomaly import detect_track_anomalies


def test_detect_track_anomalies_records_mad_and_adjacent_jump_reasons() -> None:
    result = detect_track_anomalies(
        np.array([0.0, 0.0, 0.0, 1.0, 10.0, 1.0, 0.0, 0.0, 0.0]),
        window_size=5,
        mad_threshold=3.5,
        jump_threshold_meters=5.0,
    )

    assert result.reason_codes[4] == ("MAD_OUTLIER", "ADJACENT_JUMP")
    assert result.mad_scores[4] == pytest.approx(9.0 / 1.4826)
    assert result.summary == {
        "validCount": 9,
        "madOutlierCount": 1,
        "jumpCount": 2,
        "anomalyCount": 2,
        "absoluteSshLimitMeters": 100.0,
        "absoluteSshLimitVersion": "hy2d-idr-2mc-engineering-baseline-v1",
        "absoluteSshLimitScientificStatus": "ENGINEERING_BASELINE_REQUIRES_OFFICIAL_REVIEW",
        "absoluteSshLimitRequiresOfficialReview": True,
    }


def test_detect_track_anomalies_uses_jump_only_when_local_mad_is_zero() -> None:
    result = detect_track_anomalies(
        np.array([1.0, 1.0, 4.0, 1.0, 1.0]),
        window_size=3,
        jump_threshold_meters=2.0,
    )

    assert np.isnan(result.mad_scores[2])
    assert result.reason_codes[2] == ("ADJACENT_JUMP",)
    assert result.reason_codes[3] == ("ADJACENT_JUMP",)


def test_detect_track_anomalies_skips_invalid_samples_and_breaks_jump_pairs() -> None:
    result = detect_track_anomalies(
        np.array([1.0, np.nan, 9.0, 1.0]), valid_mask=np.array([True, True, False, True]),
        window_size=3,
        jump_threshold_meters=2.0,
    )

    assert result.reason_codes == ((), (), (), ())
    assert result.summary["validCount"] == 2


def test_detect_track_anomalies_rejects_even_mad_windows() -> None:
    with pytest.raises(ValueError, match="positive odd integer"):
        detect_track_anomalies(np.array([1.0, 2.0, 3.0]), window_size=4)


def test_detect_track_anomalies_flags_absolute_ssh_when_mad_is_zero() -> None:
    result = detect_track_anomalies(
        np.array([0.0, 0.0, 150.0, 0.0, 0.0]),
        window_size=5,
        jump_threshold_meters=1_000.0,
        absolute_ssh_limit_meters=100.0,
    )

    assert np.isnan(result.mad_scores[2])
    assert result.reason_codes[2] == ("ABSOLUTE_SSH_LIMIT",)
    assert result.summary["absoluteSshLimitMeters"] == 100.0
    assert result.summary["anomalyCount"] == 1


def test_detect_track_anomalies_does_not_flag_absolute_ssh_at_limit() -> None:
    result = detect_track_anomalies(
        np.array([0.0, 0.0, -100.0, 0.0, 0.0]),
        window_size=5,
        jump_threshold_meters=1_000.0,
        absolute_ssh_limit_meters=100.0,
    )

    assert result.reason_codes[2] == ()


@pytest.mark.parametrize("kwargs", [
    {"window_size": 0},
    {"mad_threshold": 0.0},
    {"jump_threshold_meters": 0.0},
    {"absolute_ssh_limit_meters": 0.0},
    {"absolute_ssh_limit_meters": float("inf")},
    {"absolute_ssh_limit_meters": "100.0"},
])
def test_detect_track_anomalies_validates_positive_parameters(kwargs: dict[str, object]) -> None:
    with pytest.raises(ValueError):
        detect_track_anomalies(np.array([1.0, 2.0]), **kwargs)
