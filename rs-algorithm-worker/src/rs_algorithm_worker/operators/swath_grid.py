from __future__ import annotations

import math

import numpy as np
from affine import Affine
from pyproj import CRS, Transformer
from scipy.spatial import cKDTree

from rs_algorithm_worker.models import BandMetadata, RasterData


QUALITY_NODATA = np.uint32(0xFFFFFFFF)


def grid_swath(values: np.ndarray, quality_bits: np.ndarray, longitude: np.ndarray, latitude: np.ndarray, *,
               target_crs: str, resolution: float, roi: tuple[float, float, float, float] | None = None,
               continuous_resampling: str = "nearest") -> tuple[RasterData, RasterData]:
    if continuous_resampling != "nearest":
        raise ValueError("FY-3H swath grid supports nearest resampling only")
    if not np.isfinite(resolution) or resolution <= 0:
        raise ValueError("FY-3H swath grid resolution must be positive")
    value, quality, lon, lat = (np.asarray(item) for item in (values, quality_bits, longitude, latitude))
    if value.ndim != 2 or any(item.shape != value.shape for item in (quality, lon, lat)):
        raise ValueError("FY-3H swath grid inputs must be matching two-dimensional arrays")
    crs = CRS.from_user_input(target_crs)
    if crs.is_geographic is False and resolution < 1:
        raise ValueError("projected FY-3H swath grids require metre resolution")
    if crs.is_geographic and resolution > 1:
        raise ValueError("geographic FY-3H swath grids require degree resolution")
    valid_navigation = np.isfinite(lon) & np.isfinite(lat)
    valid = np.isfinite(value) & valid_navigation & (quality == 0)
    if not valid_navigation.any():
        raise ValueError("FY-3H swath grid requires valid navigation and science samples")
    transformer = Transformer.from_crs("EPSG:4326", crs, always_xy=True)
    source = valid if valid.any() else valid_navigation
    x, y = transformer.transform(lon[source], lat[source])
    if not np.all(np.isfinite(x)) or not np.all(np.isfinite(y)):
        raise ValueError("FY-3H swath grid navigation cannot be projected")
    bounds = _bounds(np.asarray(x), np.asarray(y), resolution, roi, crs)
    min_x, min_y, max_x, max_y = bounds
    width = int(round((max_x - min_x) / resolution))
    height = int(round((max_y - min_y) / resolution))
    if width < 1 or height < 1 or width * height > 20_000_000:
        raise ValueError("FY-3H swath grid dimensions are invalid")
    transform = Affine(resolution, 0, min_x, 0, -resolution, max_y)
    cols, rows = np.meshgrid(np.arange(width), np.arange(height))
    target = np.column_stack((min_x + (cols.ravel() + 0.5) * resolution, max_y - (rows.ravel() + 0.5) * resolution))
    _, index = cKDTree(np.column_stack((x, y))).query(target)
    if valid.any():
        source_values, source_quality = value[valid], quality[valid].astype(np.uint32)
        science = source_values[index].reshape(height, width).astype(np.float32)
        mask = source_quality[index].reshape(height, width).astype(np.uint32)
    else:
        science = np.full((height, width), np.nan, dtype=np.float32)
        mask = np.full((height, width), QUALITY_NODATA, dtype=np.uint32)
    return (RasterData(science, crs.to_string(), transform, np.nan, (BandMetadata("science"),)),
            RasterData(mask, crs.to_string(), transform, QUALITY_NODATA, (BandMetadata("quality_mask", unit="bitmask"),)))


def _bounds(x: np.ndarray, y: np.ndarray, resolution: float, roi, crs: CRS) -> tuple[float, float, float, float]:
    if roi is not None:
        if len(roi) != 4:
            raise ValueError("FY-3H swath grid ROI must contain four coordinates")
        if crs.is_geographic:
            min_x, min_y, max_x, max_y = roi
        else:
            transformer = Transformer.from_crs("EPSG:4326", crs, always_xy=True)
            min_x, min_y = transformer.transform(roi[0], roi[1])
            max_x, max_y = transformer.transform(roi[2], roi[3])
    else:
        min_x, min_y, max_x, max_y = float(x.min()), float(y.min()), float(x.max()), float(y.max())
    min_x = math.floor(min_x / resolution) * resolution
    min_y = math.floor(min_y / resolution) * resolution
    max_x = (math.floor(max_x / resolution) + 1) * resolution
    max_y = (math.floor(max_y / resolution) + 1) * resolution
    return min_x, min_y, max_x, max_y
