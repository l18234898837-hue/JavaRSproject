from __future__ import annotations

import numpy as np


def extract_track_features(
    ssh: np.ndarray,
    valid_mask: np.ndarray | None = None,
    *,
    window_size: int,
) -> list[dict[str, float | int]]:
    """Compute statistics over fixed-count windows within contiguous valid runs.

    ``sshTrend`` is the least-squares slope per sample index.  ``validRatio``
    is the count of valid samples in a (possibly final partial) window divided
    by the requested window size.
    """
    values, valid = _validated_values(ssh, valid_mask)
    _validate_window_size(window_size)
    features: list[dict[str, float | int]] = []
    for run_start, run_end in _valid_runs(valid):
        for start in range(run_start, run_end, window_size):
            end = min(start + window_size, run_end)
            window = values[start:end]
            count = window.size
            indices = np.arange(count, dtype=float)
            trend = float(np.polyfit(indices, window, 1)[0]) if count > 1 else 0.0
            gradients = np.abs(np.diff(window))
            features.append({
                "startIndex": start,
                "endIndex": end - 1,
                "sshMean": float(np.mean(window)),
                "sshStd": float(np.std(window)),
                "sshTrend": trend,
                "sshGradientMean": float(np.mean(gradients)) if gradients.size else 0.0,
                "validRatio": float(count / window_size),
            })
    return features


def _validated_values(ssh: np.ndarray, valid_mask: np.ndarray | None) -> tuple[np.ndarray, np.ndarray]:
    values = np.asarray(ssh, dtype=float)
    if values.ndim != 1:
        raise ValueError("ssh must be a one-dimensional array")
    if valid_mask is None:
        supplied_valid = np.ones(values.shape, dtype=bool)
    else:
        supplied_valid = np.asarray(valid_mask, dtype=bool)
        if supplied_valid.ndim != 1 or supplied_valid.shape != values.shape:
            raise ValueError("valid_mask must be one-dimensional and match ssh")
    return values, supplied_valid & np.isfinite(values)


def _validate_window_size(window_size: int) -> None:
    if isinstance(window_size, bool) or not isinstance(window_size, (int, np.integer)) or window_size <= 0:
        raise ValueError("window_size must be a positive integer")


def _valid_runs(valid: np.ndarray) -> list[tuple[int, int]]:
    runs: list[tuple[int, int]] = []
    start: int | None = None
    for index, is_valid in enumerate(valid):
        if is_valid and start is None:
            start = index
        elif not is_valid and start is not None:
            runs.append((start, index))
            start = None
    if start is not None:
        runs.append((start, valid.size))
    return runs
