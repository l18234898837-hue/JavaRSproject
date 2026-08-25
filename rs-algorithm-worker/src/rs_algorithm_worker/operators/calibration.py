from __future__ import annotations

import numpy as np


def calibrate_linear(values: np.ndarray, *, scale: float, offset: float, fill_value: float | int | None) -> np.ndarray:
    if not np.isfinite(scale) or not np.isfinite(offset):
        raise ValueError("calibration scale and offset must be finite")
    calibrated = values.astype(np.float32, copy=True) * np.float32(scale) + np.float32(offset)
    if fill_value is not None:
        calibrated[values == fill_value] = np.nan
    return calibrated
