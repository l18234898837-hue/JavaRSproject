from __future__ import annotations

import numpy as np


def apply_quality_mask(values: np.ndarray, quality_flags: np.ndarray, *, bad_bits: int) -> tuple[np.ndarray, np.ndarray]:
    if values.shape != quality_flags.shape:
        raise ValueError("values and quality flags must have matching shape")
    if bad_bits < 0:
        raise ValueError("bad bits must be non-negative")
    valid = (quality_flags.astype(np.uint64) & np.uint64(bad_bits)) == 0
    masked = values.astype(np.float32, copy=True)
    masked[~valid] = np.nan
    return masked, valid
