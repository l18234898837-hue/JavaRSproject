from __future__ import annotations

from dataclasses import dataclass
from numbers import Real

import numpy as np

from .track_features import _valid_runs, _validated_values, _validate_window_size


_ABSOLUTE_SSH_LIMIT_VERSION = "hy2d-idr-2mc-engineering-baseline-v1"
_ABSOLUTE_SSH_LIMIT_SCIENTIFIC_STATUS = "ENGINEERING_BASELINE_REQUIRES_OFFICIAL_REVIEW"


@dataclass(frozen=True)
class TrackAnomalyResult:
    reason_codes: tuple[tuple[str, ...], ...]
    mad_scores: np.ndarray
    summary: dict[str, int | float | str]


def detect_track_anomalies(
    ssh: np.ndarray,
    valid_mask: np.ndarray | None = None,
    *,
    window_size: int = 5,
    mad_threshold: float = 3.5,
    jump_threshold_meters: float = 1.0,
    absolute_ssh_limit_meters: float = 100.0,
) -> TrackAnomalyResult:
    """Flag local MAD outliers, SSH jumps, and engineering-limit SSH values.

    MAD scores use ``abs(value - median) / (1.4826 * MAD)``. A zero MAD has
    no defined standardized score, but the independent absolute SSH limit and
    adjacent-jump checks still apply.

    The absolute SSH limit is an engineering baseline, not an official HY-2D
    IDR/IGDR quality threshold. Replace or review it when the official product
    specification becomes available.
    """
    _validate_mad_window_size(window_size)
    _validate_positive("mad_threshold", mad_threshold)
    _validate_positive("jump_threshold_meters", jump_threshold_meters)
    _validate_positive("absolute_ssh_limit_meters", absolute_ssh_limit_meters)
    values, valid = _validated_values(ssh, valid_mask)
    reasons: list[list[str]] = [[] for _ in values]
    scores = np.full(values.shape, np.nan, dtype=float)
    half_window = window_size // 2

    for index in np.flatnonzero(valid):
        if abs(values[index]) > absolute_ssh_limit_meters:
            reasons[index].append("ABSOLUTE_SSH_LIMIT")

    for run_start, run_end in _valid_runs(valid):
        for index in range(run_start, run_end):
            local_start = max(run_start, index - half_window)
            local_end = min(run_end, index + half_window + 1)
            local_values = values[local_start:local_end]
            median = float(np.median(local_values))
            mad = float(np.median(np.abs(local_values - median)))
            if mad > 0.0:
                score = abs(values[index] - median) / (1.4826 * mad)
                scores[index] = score
                if score > mad_threshold:
                    reasons[index].append("MAD_OUTLIER")
        for index in range(run_start + 1, run_end):
            if abs(values[index] - values[index - 1]) > jump_threshold_meters:
                reasons[index].append("ADJACENT_JUMP")

    reason_codes = tuple(tuple(item) for item in reasons)
    mad_outlier_count = sum("MAD_OUTLIER" in item for item in reason_codes)
    jump_count = sum("ADJACENT_JUMP" in item for item in reason_codes)
    return TrackAnomalyResult(
        reason_codes=reason_codes,
        mad_scores=scores,
        summary={
            "validCount": int(valid.sum()),
            "madOutlierCount": mad_outlier_count,
            "jumpCount": jump_count,
            "anomalyCount": sum(bool(item) for item in reason_codes),
            "absoluteSshLimitMeters": float(absolute_ssh_limit_meters),
            "absoluteSshLimitVersion": _ABSOLUTE_SSH_LIMIT_VERSION,
            "absoluteSshLimitScientificStatus": _ABSOLUTE_SSH_LIMIT_SCIENTIFIC_STATUS,
            "absoluteSshLimitRequiresOfficialReview": True,
        },
    )


def _validate_mad_window_size(window_size: int) -> None:
    _validate_window_size(window_size)
    if window_size % 2 == 0:
        raise ValueError("window_size must be a positive odd integer")


def _validate_positive(name: str, value: float) -> None:
    if isinstance(value, bool) or not isinstance(value, Real) or not np.isfinite(value) or value <= 0.0:
        raise ValueError(f"{name} must be positive")
