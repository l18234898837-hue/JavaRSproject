from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping

import numpy as np
from scipy.ndimage import uniform_filter

from rs_algorithm_worker.models import BandMetadata, RasterData


@dataclass(frozen=True)
class FeatureParameters:
    metrics: tuple[str, ...]
    window_size: int = 3


@dataclass(frozen=True)
class FeatureSet:
    statistics: dict[str, object]
    rasters: Mapping[str, RasterData] = field(default_factory=dict)
    version: str = "statistics-v1"


def extract_features(raster: RasterData, parameters: FeatureParameters) -> FeatureSet:
    supported_metrics = {"statistics", "gradient", "local_variance"}
    unknown_metrics = set(parameters.metrics).difference(supported_metrics)
    if not parameters.metrics or unknown_metrics:
        raise ValueError("unsupported feature metric")
    if "local_variance" in parameters.metrics and (parameters.window_size < 3 or parameters.window_size % 2 == 0):
        raise ValueError("local variance window size must be an odd integer of at least three")
    values = raster.values if raster.values.ndim == 3 else raster.values[np.newaxis, ...]
    result: dict[str, object] = {"validPixelRatio": float(np.isfinite(values).mean())}
    feature_rasters: dict[str, RasterData] = {}
    for index, metadata in enumerate(raster.band_metadata):
        band = values[index]
        finite = band[np.isfinite(band)]
        if "statistics" in parameters.metrics and finite.size == 0:
            result[metadata.name] = {"min": None, "max": None, "mean": None, "std": None}
        elif "statistics" in parameters.metrics:
            result[metadata.name] = {
                "min": float(finite.min()), "max": float(finite.max()),
                "mean": float(finite.mean()), "std": float(finite.std()),
            }
        if "gradient" in parameters.metrics:
            gradient = _gradient(band)
            feature_rasters[f"gradient:{metadata.name}"] = _feature_raster(
                gradient, raster, f"gradient:{metadata.name}"
            )
        if "local_variance" in parameters.metrics:
            variance = _local_variance(band, parameters.window_size)
            feature_rasters[f"local_variance:{metadata.name}"] = _feature_raster(
                variance, raster, f"local_variance:{metadata.name}"
            )
    return FeatureSet(result, feature_rasters)


def _gradient(values: np.ndarray) -> np.ndarray:
    return _gradient_with_fill(values, None)


def _gradient_with_fill(values: np.ndarray, fill_value: float | None) -> np.ndarray:
    valid = np.isfinite(values)
    if not valid.any():
        return np.full(values.shape, np.nan, dtype=np.float32)
    replacement = float(np.nanmean(values)) if fill_value is None else float(fill_value)
    filled = np.where(valid, values, replacement)
    vertical, horizontal = np.gradient(filled)
    gradient = np.hypot(vertical, horizontal).astype(np.float32)
    gradient[~valid] = np.nan
    return gradient


def _local_variance(values: np.ndarray, window_size: int) -> np.ndarray:
    valid = np.isfinite(values)
    counts = uniform_filter(valid.astype(np.float32), size=window_size, mode="nearest")
    filled = np.where(valid, values, 0.0).astype(np.float32)
    mean = uniform_filter(filled, size=window_size, mode="nearest") / np.where(counts == 0, 1.0, counts)
    mean_square = uniform_filter(filled * filled, size=window_size, mode="nearest") / np.where(counts == 0, 1.0, counts)
    variance = np.maximum(mean_square - mean * mean, 0.0).astype(np.float32)
    variance[(counts == 0) | ~valid] = np.nan
    return variance


def feature_tile(
    values: np.ndarray,
    metrics: tuple[str, ...],
    window_size: int,
    core: tuple[int, int, int, int],
    *,
    gradient_fill: float | None = None,
) -> dict[str, np.ndarray]:
    """Compute feature metrics for a halo-inclusive tile and return its core."""
    top, bottom, left, right = core
    if not (0 <= top <= bottom <= values.shape[0] and 0 <= left <= right <= values.shape[1]):
        raise ValueError("feature tile core is outside the tile")
    if "local_variance" in metrics and (window_size < 3 or window_size % 2 == 0):
        raise ValueError("local variance window size must be an odd integer of at least three")
    result: dict[str, np.ndarray] = {}
    core_slice = np.s_[top:bottom, left:right]
    if "gradient" in metrics:
        result["gradient"] = _gradient_with_fill(values, gradient_fill)[core_slice]
    if "local_variance" in metrics:
        result["local_variance"] = _local_variance(values, window_size)[core_slice]
    return result


def _feature_raster(values: np.ndarray, source: RasterData, name: str) -> RasterData:
    return RasterData(
        values=values,
        crs=source.crs,
        transform=source.transform,
        nodata=np.nan,
        band_metadata=(BandMetadata(name),),
    )
