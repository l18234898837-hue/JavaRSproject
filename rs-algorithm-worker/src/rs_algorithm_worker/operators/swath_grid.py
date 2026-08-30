from __future__ import annotations

from dataclasses import dataclass
import math

import numpy as np
from affine import Affine
from pyproj import CRS, Transformer
from scipy.spatial import cKDTree

from rs_algorithm_worker.models import BandMetadata, RasterData


QUALITY_NODATA = np.uint32(0xFFFFFFFF)
_MAX_GRID_CELLS = 20_000_000
_SPACING_SAMPLE_SIZE = 50_000


@dataclass(frozen=True)
class SwathGridMapping:
    source_shape: tuple[int, int]
    source_indices: np.ndarray
    crs: str
    transform: Affine
    maximum_distance: float

    def __post_init__(self) -> None:
        if self.source_indices.ndim != 2 or not np.issubdtype(self.source_indices.dtype, np.signedinteger):
            raise ValueError("FY-3H swath grid mapping requires a two-dimensional signed index array")


def prepare_swath_grid(
    longitude: np.ndarray,
    latitude: np.ndarray,
    *,
    target_crs: str,
    resolution: float,
    roi: tuple[float, float, float, float] | None = None,
    continuous_resampling: str = "nearest",
    navigation_valid: np.ndarray | None = None,
    maximum_distance: float | None = None,
) -> SwathGridMapping:
    """Build the reusable source-to-target mapping for one swath scene."""
    if continuous_resampling != "nearest":
        raise ValueError("FY-3H swath grid supports nearest resampling only")
    if not np.isfinite(resolution) or resolution <= 0:
        raise ValueError("FY-3H swath grid resolution must be positive")
    lon, lat = (np.asarray(item) for item in (longitude, latitude))
    if lon.ndim != 2 or lat.shape != lon.shape:
        raise ValueError("FY-3H swath navigation must contain matching two-dimensional arrays")
    crs = CRS.from_user_input(target_crs)
    if crs.is_geographic is False and resolution < 1:
        raise ValueError("projected FY-3H swath grids require metre resolution")
    if crs.is_geographic and resolution > 1:
        raise ValueError("geographic FY-3H swath grids require degree resolution")

    valid_navigation = (
        np.isfinite(lon) & np.isfinite(lat)
        & (lon >= -180.0) & (lon <= 180.0)
        & (lat >= -90.0) & (lat <= 90.0)
    )
    if navigation_valid is not None:
        declared_valid = np.asarray(navigation_valid, dtype=bool)
        if declared_valid.shape != lon.shape:
            raise ValueError("FY-3H swath navigation validity must match the coordinate arrays")
        valid_navigation &= declared_valid
    if not valid_navigation.any():
        raise ValueError("FY-3H swath grid requires valid navigation samples")

    source_longitude = lon[valid_navigation]
    if roi is None:
        source_longitude = _unwrap_longitude_span(source_longitude)
    transformer = Transformer.from_crs("EPSG:4326", crs, always_xy=True)
    if np.nanmax(source_longitude) > 180.0:
        transformer = Transformer.from_crs("EPSG:4326", crs, always_xy=True, force_over=True)
    x, y = transformer.transform(source_longitude, lat[valid_navigation])
    if not np.all(np.isfinite(x)) or not np.all(np.isfinite(y)):
        raise ValueError("FY-3H swath grid navigation cannot be projected")

    min_x, min_y, max_x, max_y = _bounds(np.asarray(x), np.asarray(y), resolution, roi, crs)
    width = int(round((max_x - min_x) / resolution))
    height = int(round((max_y - min_y) / resolution))
    if width < 1 or height < 1 or width * height > _MAX_GRID_CELLS:
        raise ValueError("FY-3H swath grid dimensions are invalid")
    transform = Affine(resolution, 0, min_x, 0, -resolution, max_y)

    navigation_points = np.column_stack((x, y))
    navigation_tree = cKDTree(navigation_points)
    distance_limit = _maximum_distance(navigation_tree, navigation_points, resolution, maximum_distance)
    source_flat_indices = np.flatnonzero(valid_navigation)
    target_source_indices = np.full((height, width), -1, dtype=np.int64)
    columns = min_x + (np.arange(width, dtype=np.float64) + 0.5) * resolution
    for first_row in range(0, height, 256):
        last_row = min(first_row + 256, height)
        rows = max_y - (np.arange(first_row, last_row, dtype=np.float64) + 0.5) * resolution
        target = np.column_stack((np.tile(columns, rows.size), np.repeat(rows, width)))
        distances, local_indices = navigation_tree.query(target, distance_upper_bound=distance_limit)
        covered = np.isfinite(distances) & (local_indices < source_flat_indices.size)
        mapped = np.full(local_indices.shape, -1, dtype=np.int64)
        mapped[covered] = source_flat_indices[local_indices[covered]]
        target_source_indices[first_row:last_row] = mapped.reshape(rows.size, width)
    return SwathGridMapping(lon.shape, target_source_indices, crs.to_string(), transform, distance_limit)


def grid_swath_with_mapping(
    values: np.ndarray,
    quality_bits: np.ndarray,
    mapping: SwathGridMapping,
) -> tuple[RasterData, RasterData]:
    """Apply one prepared mapping so science and quality use the same source pixel."""
    value, quality = (np.asarray(item) for item in (values, quality_bits))
    if value.shape != mapping.source_shape or quality.shape != mapping.source_shape:
        raise ValueError("FY-3H swath grid inputs must match the prepared navigation shape")
    science = np.full(mapping.source_indices.shape, np.nan, dtype=np.float32)
    mask = np.full(mapping.source_indices.shape, QUALITY_NODATA, dtype=np.uint32)
    covered = mapping.source_indices >= 0
    if covered.any():
        source_indices = mapping.source_indices[covered]
        source_values = value.reshape(-1)[source_indices]
        source_quality = quality.reshape(-1)[source_indices].astype(np.uint32, copy=False)
        mask[covered] = source_quality
        accepted = np.isfinite(source_values) & (source_quality == 0)
        target_values = np.full(source_values.shape, np.nan, dtype=np.float32)
        target_values[accepted] = source_values[accepted].astype(np.float32)
        science[covered] = target_values
    return (
        RasterData(science, mapping.crs, mapping.transform, np.nan, (BandMetadata("science"),)),
        RasterData(mask, mapping.crs, mapping.transform, QUALITY_NODATA,
                   (BandMetadata("quality_mask", unit="bitmask"),)),
    )


def grid_swath(
    values: np.ndarray,
    quality_bits: np.ndarray,
    longitude: np.ndarray,
    latitude: np.ndarray,
    *,
    target_crs: str,
    resolution: float,
    roi: tuple[float, float, float, float] | None = None,
    continuous_resampling: str = "nearest",
) -> tuple[RasterData, RasterData]:
    """Compatibility wrapper for callers that grid one channel at a time."""
    value, quality, lon, lat = (np.asarray(item) for item in (values, quality_bits, longitude, latitude))
    if value.ndim != 2 or any(item.shape != value.shape for item in (quality, lon, lat)):
        raise ValueError("FY-3H swath grid inputs must be matching two-dimensional arrays")
    mapping = prepare_swath_grid(
        lon,
        lat,
        target_crs=target_crs,
        resolution=resolution,
        roi=roi,
        continuous_resampling=continuous_resampling,
    )
    return grid_swath_with_mapping(value, quality, mapping)


def _maximum_distance(
    tree: cKDTree,
    points: np.ndarray,
    resolution: float,
    requested: float | None,
) -> float:
    if requested is not None:
        if not np.isfinite(requested) or requested <= 0:
            raise ValueError("FY-3H swath grid maximum distance must be positive")
        return float(requested)
    base = math.sqrt(2.0) * resolution
    if points.shape[0] < 2:
        return base
    if points.shape[0] > _SPACING_SAMPLE_SIZE:
        sample_indices = np.linspace(0, points.shape[0] - 1, _SPACING_SAMPLE_SIZE, dtype=np.int64)
        sample = points[sample_indices]
    else:
        sample = points
    distances, _ = tree.query(sample, k=2)
    nearest = distances[:, 1]
    nearest = nearest[np.isfinite(nearest) & (nearest > 0)]
    source_spacing = float(np.percentile(nearest, 95)) if nearest.size else 0.0
    return max(base, source_spacing * 2.5)


def _unwrap_longitude_span(longitude: np.ndarray) -> np.ndarray:
    values = np.asarray(longitude, dtype=np.float64)
    if values.size < 2:
        return values
    normalized = (values + 180.0) % 360.0 - 180.0
    ordered = np.sort(normalized)
    gaps = np.diff(np.concatenate((ordered, ordered[:1] + 360.0)))
    cut = int(np.argmax(gaps))
    start = ordered[(cut + 1) % ordered.size]
    return (normalized - start) % 360.0 + start


def _bounds(x: np.ndarray, y: np.ndarray, resolution: float, roi, crs: CRS) -> tuple[float, float, float, float]:
    if roi is not None:
        if len(roi) != 4:
            raise ValueError("FY-3H swath grid ROI must contain four coordinates")
        if crs.is_geographic:
            min_x, min_y, max_x, max_y = roi
        else:
            transformer = Transformer.from_crs("EPSG:4326", crs, always_xy=True)
            lon = np.asarray([roi[0], roi[0], roi[2], roi[2]], dtype=np.float64)
            lat = np.asarray([roi[1], roi[3], roi[1], roi[3]], dtype=np.float64)
            projected_x, projected_y = transformer.transform(lon, lat)
            min_x, max_x = float(np.min(projected_x)), float(np.max(projected_x))
            min_y, max_y = float(np.min(projected_y)), float(np.max(projected_y))
    else:
        min_x, min_y, max_x, max_y = float(x.min()), float(y.min()), float(x.max()), float(y.max())
    min_x = math.floor(min_x / resolution) * resolution
    min_y = math.floor(min_y / resolution) * resolution
    max_x = (math.floor(max_x / resolution) + 1) * resolution
    max_y = (math.floor(max_y / resolution) + 1) * resolution
    return min_x, min_y, max_x, max_y
