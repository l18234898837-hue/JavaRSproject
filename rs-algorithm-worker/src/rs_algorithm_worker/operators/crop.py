from __future__ import annotations

import numpy as np
from affine import Affine
from rasterio.windows import Window, WindowError, from_bounds

from rs_algorithm_worker.models import RasterData
from rs_algorithm_worker.operators.spatial import require_grid


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


def crop_grid(raster: RasterData, *, roi: tuple[float, float, float, float]) -> RasterData:
    _, transform = require_grid(raster)
    min_x, min_y, max_x, max_y = roi
    if not np.all(np.isfinite(roi)) or max_x <= min_x or max_y <= min_y:
        raise ValueError("ROI bounds are invalid")

    height, width = raster.values.shape[-2:]
    full_window = Window(0, 0, width, height)
    try:
        window = from_bounds(min_x, min_y, max_x, max_y, transform=transform)
        window = window.intersection(full_window).round_offsets().round_lengths()
    except WindowError as error:
        raise ValueError("ROI does not intersect raster bounds") from error

    if window.width <= 0 or window.height <= 0:
        raise ValueError("ROI does not intersect raster bounds")

    row_start = int(window.row_off)
    row_end = row_start + int(window.height)
    column_start = int(window.col_off)
    column_end = column_start + int(window.width)
    values = raster.values[row_start:row_end, column_start:column_end]
    if raster.values.ndim == 3:
        values = raster.values[:, row_start:row_end, column_start:column_end]

    return RasterData(
        values=values,
        crs=raster.crs,
        transform=transform * Affine.translation(column_start, row_start),
        nodata=raster.nodata,
        band_metadata=raster.band_metadata,
    )
