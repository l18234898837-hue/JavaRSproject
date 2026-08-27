from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

import numpy as np
import rasterio
from affine import Affine
from rasterio.enums import Resampling
from rasterio.transform import array_bounds, from_bounds
from rasterio.warp import calculate_default_transform, reproject
from rasterio.windows import from_bounds as window_from_bounds


@dataclass(frozen=True)
class RpcResampleResult:
    values: np.ndarray
    crs: str
    transform: Affine
    navigation_status: str = "NOMINAL_RPC_NO_DEM"


def mosaic_gf6_tiles(tiles: Sequence[tuple[str, int]], *, height: int, width: int, bands: int) -> np.ndarray:
    if height <= 0 or width <= 0 or bands <= 0 or not tiles:
        raise ValueError("GF6 mosaic dimensions are invalid")
    output = np.zeros((bands, height, width), dtype=np.uint16)
    occupied = np.zeros((height, width), dtype=bool)
    for raw_path, column_offset in tiles:
        if column_offset < 0:
            raise ValueError("GF6 tile offset is invalid")
        with rasterio.open(raw_path) as dataset:
            if dataset.count != bands or dataset.height != height or dataset.dtypes != ("uint16",) * bands:
                raise ValueError("GF6 tiles do not share the expected grid")
            right = column_offset + dataset.width
            if right > width:
                raise ValueError("GF6 tile exceeds mosaic bounds")
            values = dataset.read()
        target = occupied[:, column_offset:right]
        write_mask = ~target
        destination = output[:, :, column_offset:right]
        destination[:, write_mask] = values[:, write_mask]
        occupied[:, column_offset:right] |= True
    if not occupied.all():
        raise ValueError("GF6 mosaic contains uncovered pixels")
    return output


def calibrate_gf6(values: np.ndarray, *, gains: Sequence[float], offsets: Sequence[float], fill_value: int | None = None) -> np.ndarray:
    if values.ndim != 3 or len(gains) != values.shape[0] or len(offsets) != values.shape[0]:
        raise ValueError("GF6 calibration dimensions are invalid")
    if not all(np.isfinite(gain) and np.isfinite(offset) for gain, offset in zip(gains, offsets)):
        raise ValueError("GF6 calibration coefficients are invalid")
    result = values.astype(np.float32) * np.asarray(gains, dtype=np.float32)[:, None, None] + np.asarray(offsets, dtype=np.float32)[:, None, None]
    if fill_value is not None:
        result[values == fill_value] = np.nan
    return result


def crop_gf6(values: np.ndarray, transform: Affine, roi: tuple[float, float, float, float]) -> tuple[np.ndarray, Affine]:
    if values.ndim not in (2, 3):
        raise ValueError("GF6 crop values must be raster-shaped")
    min_x, min_y, max_x, max_y = roi
    if not np.all(np.isfinite(roi)) or max_x <= min_x or max_y <= min_y:
        raise ValueError("GF6 ROI is invalid")
    height, width = values.shape[-2:]
    try:
        window = window_from_bounds(min_x, min_y, max_x, max_y, transform=transform).intersection(rasterio.windows.Window(0, 0, width, height)).round_offsets().round_lengths()
    except Exception as error:
        raise ValueError("GF6 ROI does not intersect raster") from error
    if window.width <= 0 or window.height <= 0:
        raise ValueError("GF6 ROI does not intersect raster")
    row, col = int(window.row_off), int(window.col_off)
    r_end, c_end = row + int(window.height), col + int(window.width)
    cropped = values[row:r_end, col:c_end] if values.ndim == 2 else values[:, row:r_end, col:c_end]
    return cropped, transform * Affine.translation(col, row)


def rpc_resample_gf6(values: np.ndarray, *, source_crs: str, source_transform: Affine, target_crs: str, resolution: float, height_m: float = 0.0) -> RpcResampleResult:
    if resolution <= 0 or not np.isfinite(resolution) or not np.isfinite(height_m):
        raise ValueError("GF6 RPC resampling parameters are invalid")
    src_height, src_width = values.shape[-2:]
    left, bottom, right, top = array_bounds(src_height, src_width, source_transform)
    target_transform, target_width, target_height = calculate_default_transform(source_crs, target_crs, src_width, src_height, left, bottom, right, top, resolution=resolution)
    source = values if values.ndim == 3 else values[None, ...]
    result = np.full((source.shape[0], target_height, target_width), np.nan, dtype=np.float32)
    for index, band in enumerate(source):
        reproject(band.astype(np.float32), result[index], src_transform=source_transform, src_crs=source_crs, dst_transform=target_transform, dst_crs=target_crs, resampling=Resampling.nearest)
    return RpcResampleResult(result, str(target_crs), target_transform)


def extract_gf6_features(values: np.ndarray, *, metrics: Sequence[str]) -> dict[str, np.ndarray | dict[str, np.ndarray]]:
    if values.ndim != 3:
        raise ValueError("GF6 features require band-first values")
    result: dict[str, np.ndarray | dict[str, np.ndarray]] = {}
    finite = np.isfinite(values)
    if "statistics" in metrics:
        result["statistics"] = {name: np.nanmean(values, axis=(1, 2)) if name == "mean" else np.nanstd(values, axis=(1, 2)) if name == "std" else np.nanmin(values, axis=(1, 2)) if name == "min" else np.nanmax(values, axis=(1, 2)) for name in ("min", "max", "mean", "std")}
    if "gradient" in metrics:
        gy, gx = np.gradient(np.nan_to_num(values, nan=0.0), axis=(-2, -1))
        result["gradient"] = np.hypot(gx, gy).astype(np.float32)
    if "local_variance" in metrics:
        padded = np.pad(np.nan_to_num(values, nan=0.0), ((0, 0), (1, 1), (1, 1)), mode="reflect")
        windows = np.lib.stride_tricks.sliding_window_view(padded, (3, 3), axis=(-2, -1))
        result["local_variance"] = windows.var(axis=(-2, -1)).astype(np.float32)
    if not result:
        raise ValueError("GF6 feature metrics are empty")
    return result


def fuse_gf6_bands(values: np.ndarray, *, rgb: tuple[int, int, int], percentiles: tuple[float, float] = (2.0, 98.0)) -> np.ndarray:
    if values.ndim != 3 or any(index < 0 or index >= values.shape[0] for index in rgb):
        raise ValueError("GF6 fusion band selection is invalid")
    low_q, high_q = percentiles
    if not 0 <= low_q < high_q <= 100:
        raise ValueError("GF6 fusion percentiles are invalid")
    output = np.zeros((3, values.shape[1], values.shape[2]), dtype=np.uint8)
    for target, source_index in enumerate(rgb):
        band = values[source_index].astype(np.float32)
        valid = np.isfinite(band)
        if valid.any():
            low, high = np.percentile(band[valid], [low_q, high_q])
            output[target] = np.clip((band - low) * 255 / max(high - low, 1e-6), 0, 255).astype(np.uint8)
    return output


def detect_gf6_saliency(values: np.ndarray, *, window_size: int = 3, threshold: float = 0.95) -> tuple[np.ndarray, np.ndarray]:
    if values.ndim != 2 or window_size < 3 or window_size % 2 == 0 or not 0 <= threshold <= 1:
        raise ValueError("GF6 saliency parameters are invalid")
    radius = window_size // 2
    padded = np.pad(np.nan_to_num(values, nan=0.0), radius, mode="reflect")
    windows = np.lib.stride_tricks.sliding_window_view(padded, (window_size, window_size))
    center = np.nan_to_num(values, nan=0.0)
    local_mean = windows.mean(axis=(-2, -1))
    local_std = windows.std(axis=(-2, -1))
    score = np.clip(np.abs(center - local_mean) / np.maximum(local_std, 1e-6), 0, 1).astype(np.float32)
    return score, score >= threshold
