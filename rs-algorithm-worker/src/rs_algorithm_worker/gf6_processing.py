from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np
import rasterio
from affine import Affine
from rasterio.enums import Resampling
from rasterio.transform import array_bounds
from rasterio.warp import calculate_default_transform, reproject
from rasterio.windows import from_bounds
from scipy.ndimage import uniform_filter


GF6_MAX_WINDOW_SIZE = 255


@dataclass(frozen=True)
class RpcResampleResult:
    values: np.ndarray
    crs: str
    transform: Affine
    navigation_status: str = "NOMINAL_RPC_NO_DEM"


def mosaic_gf6_tiles(tiles: Sequence[tuple[str | object, int]], *, height: int, width: int, bands: int) -> np.ndarray:
    if height <= 0 or width <= 0 or bands <= 0 or not tiles:
        raise ValueError("GF6 mosaic dimensions are invalid")
    output = np.zeros((bands, height, width), dtype=np.uint16)
    occupied = np.zeros((height, width), dtype=bool)
    for path, offset in tiles:
        if not isinstance(offset, int) or offset < 0:
            raise ValueError("GF6 tile offset is invalid")
        with rasterio.open(path) as source:
            if source.count != bands or source.height != height or tuple(source.dtypes) != ("uint16",) * bands or offset + source.width > width:
                raise ValueError("GF6 tiles do not share the expected grid")
            values = source.read()
        output[:, :, offset:offset + values.shape[-1]] = values
        occupied[:, offset:offset + values.shape[-1]] = True
    if not occupied.all():
        raise ValueError("GF6 mosaic contains uncovered pixels")
    return output


def calibrate_gf6(values: np.ndarray, *, gains: Sequence[float], offsets: Sequence[float], fill_value: int | None = None, nodata: float = -9999.0) -> np.ndarray:
    if values.ndim != 3 or len(gains) != values.shape[0] or len(offsets) != values.shape[0]:
        raise ValueError("GF6 calibration dimensions are invalid")
    output = values.astype(np.float32) * np.asarray(gains, dtype=np.float32)[:, None, None] + np.asarray(offsets, dtype=np.float32)[:, None, None]
    if fill_value is not None:
        output[values == fill_value] = np.float32(nodata)
    return output


def crop_gf6(values: np.ndarray, transform: Affine, roi: tuple[float, float, float, float]) -> tuple[np.ndarray, Affine]:
    if values.ndim not in (2, 3) or len(roi) != 4:
        raise ValueError("GF6 crop values must be raster-shaped")
    west, south, east, north = roi
    if not np.all(np.isfinite(roi)) or east <= west or north <= south:
        raise ValueError("GF6 ROI is invalid")
    try:
        window = from_bounds(west, south, east, north, transform=transform).intersection(rasterio.windows.Window(0, 0, values.shape[-1], values.shape[-2])).round_offsets().round_lengths()
    except Exception as error:
        raise ValueError("GF6 ROI does not intersect raster") from error
    if window.width <= 0 or window.height <= 0:
        raise ValueError("GF6 ROI does not intersect raster")
    row, col = int(window.row_off), int(window.col_off)
    sliced = values[row:row + int(window.height), col:col + int(window.width)] if values.ndim == 2 else values[:, row:row + int(window.height), col:col + int(window.width)]
    return sliced, transform * Affine.translation(col, row)


def rpc_resample_gf6(values: np.ndarray, *, source_crs: str, source_transform: Affine, target_crs: str, resolution: float, height_m: float = 0.0) -> RpcResampleResult:
    if values.ndim not in (2, 3) or not np.isfinite(resolution) or resolution <= 0 or height_m != 0.0:
        raise ValueError("GF6 RPC resampling parameters are invalid")
    source = values if values.ndim == 3 else values[None, ...]
    left, bottom, right, top = array_bounds(source.shape[-2], source.shape[-1], source_transform)
    transform, width, height = calculate_default_transform(source_crs, target_crs, source.shape[-1], source.shape[-2], left, bottom, right, top, resolution=resolution)
    output = np.full((source.shape[0], height, width), np.nan, dtype=np.float32)
    for index, band in enumerate(source):
        reproject(band.astype(np.float32), output[index], src_transform=source_transform, src_crs=source_crs, dst_transform=transform, dst_crs=target_crs, resampling=Resampling.bilinear)
    return RpcResampleResult(output, str(target_crs), transform)


def extract_gf6_features(values: np.ndarray, *, metrics: Sequence[str], window_size: int = 3) -> dict[str, np.ndarray]:
    if (
        type(window_size) is not int
        or window_size < 3
        or window_size > GF6_MAX_WINDOW_SIZE
        or window_size % 2 == 0
    ):
        raise ValueError(
            "GF6 feature window size must be an odd integer between 3 and 255"
        )
    result: dict[str, np.ndarray] = {}
    if "gradient" in metrics:
        result["gradient"] = _nan_aware_gradient(values)
    if "local_variance" in metrics:
        result["local_variance"] = _nan_aware_local_variance(values, window_size)
    if not result and "statistics" not in metrics:
        raise ValueError("GF6 feature metrics are empty")
    return result


def _nan_aware_gradient(values: np.ndarray) -> np.ndarray:
    source = np.asarray(values, dtype=np.float64)
    valid = np.isfinite(source)
    vertical = _finite_difference(source, valid, axis=-2)
    horizontal = _finite_difference(source, valid, axis=-1)
    gradient = np.hypot(horizontal, vertical).astype(np.float32)
    gradient[~valid] = np.nan
    return gradient


def _finite_difference(values: np.ndarray, valid: np.ndarray, *, axis: int) -> np.ndarray:
    before = np.roll(values, 1, axis=axis)
    after = np.roll(values, -1, axis=axis)
    before_valid = np.roll(valid, 1, axis=axis)
    after_valid = np.roll(valid, -1, axis=axis)
    first = [slice(None)] * values.ndim
    first[axis] = 0
    before_valid[tuple(first)] = False
    last = [slice(None)] * values.ndim
    last[axis] = -1
    after_valid[tuple(last)] = False

    derivative = np.zeros(values.shape, dtype=np.float64)
    central = valid & before_valid & after_valid
    derivative[central] = (after[central] - before[central]) / 2.0
    forward = valid & ~before_valid & after_valid
    derivative[forward] = after[forward] - values[forward]
    backward = valid & before_valid & ~after_valid
    derivative[backward] = values[backward] - before[backward]
    return derivative


def _nan_aware_local_variance(values: np.ndarray, window_size: int) -> np.ndarray:
    source = np.asarray(values, dtype=np.float64)
    valid = np.isfinite(source)
    filter_size = (1,) * (source.ndim - 2) + (window_size, window_size)
    valid_fraction = uniform_filter(
        valid.astype(np.float64), size=filter_size, mode="reflect"
    )
    filled = np.where(valid, source, 0.0)
    local_sum = uniform_filter(filled, size=filter_size, mode="reflect")
    local_square_sum = uniform_filter(
        filled * filled, size=filter_size, mode="reflect"
    )
    denominator = np.where(valid_fraction > 0.0, valid_fraction, 1.0)
    mean = local_sum / denominator
    variance = np.maximum(local_square_sum / denominator - mean * mean, 0.0)
    variance[(valid_fraction <= 0.0) | ~valid] = np.nan
    return variance.astype(np.float32)


def fuse_gf6_bands(values: np.ndarray, *, rgb: tuple[int, int, int], percentiles: tuple[float, float] = (2.0, 98.0)) -> np.ndarray:
    if values.ndim != 3 or any(index < 0 or index >= values.shape[0] for index in rgb) or not 0 <= percentiles[0] < percentiles[1] <= 100:
        raise ValueError("GF6 fusion parameters are invalid")
    output = np.zeros((3, values.shape[-2], values.shape[-1]), dtype=np.uint8)
    for index, band_index in enumerate(rgb):
        values_for_band = values[band_index]; valid = np.isfinite(values_for_band)
        if valid.any():
            lower, upper = np.percentile(values_for_band[valid], percentiles)
            output[index, valid] = np.clip((values_for_band[valid] - lower) * 255.0 / max(upper - lower, 1e-6), 0, 255)
    return output


def detect_gf6_saliency(values: np.ndarray, *, window_size: int = 9, threshold: float = .95) -> tuple[np.ndarray, np.ndarray]:
    if values.ndim != 2 or not 0 <= threshold <= 1:
        raise ValueError("GF6 saliency parameters are invalid")
    if (
        type(window_size) is not int
        or window_size < 3
        or window_size > GF6_MAX_WINDOW_SIZE
        or window_size % 2 == 0
    ):
        raise ValueError(
            "GF6 saliency window size must be an odd integer between 3 and 255"
        )
    source = np.asarray(values, dtype=np.float64)
    valid = np.isfinite(source)
    valid_fraction = uniform_filter(valid.astype(np.float64), size=window_size, mode="reflect")
    filled = np.where(valid, source, 0.0)
    denominator = np.where(valid_fraction > 0.0, valid_fraction, 1.0)
    local_mean = uniform_filter(filled, size=window_size, mode="reflect") / denominator
    local_mean_square = uniform_filter(
        filled * filled, size=window_size, mode="reflect"
    ) / denominator
    local_std = np.sqrt(np.maximum(local_mean_square - local_mean * local_mean, 0.0))
    deviation = np.abs(source - local_mean)
    roundoff = (
        np.finfo(np.float64).eps
        * np.maximum(np.maximum(np.abs(source), np.abs(local_mean)), 1.0)
        * 8.0
    )
    deviation = np.where(deviation <= roundoff, 0.0, deviation)
    score = np.clip(
        deviation / np.maximum(local_std, 1e-6), 0, 1
    ).astype(np.float32)
    score[~valid] = np.nan
    return score, np.isfinite(score) & (score >= threshold)
