from __future__ import annotations

import numpy as np


def crop_raster(
    values: np.ndarray,
    *,
    bounds: tuple[float, float, float, float],
    roi: tuple[float, float, float, float],
) -> tuple[np.ndarray, tuple[float, float, float, float]]:
    min_x, min_y, max_x, max_y = bounds
    roi_min_x, roi_min_y, roi_max_x, roi_max_y = roi
    if roi_max_x <= roi_min_x or roi_max_y <= roi_min_y:
        raise ValueError("ROI bounds are invalid")
    intersection = (max(min_x, roi_min_x), max(min_y, roi_min_y), min(max_x, roi_max_x), min(max_y, roi_max_y))
    if intersection[2] <= intersection[0] or intersection[3] <= intersection[1]:
        raise ValueError("ROI does not intersect raster bounds")
    return values, intersection
