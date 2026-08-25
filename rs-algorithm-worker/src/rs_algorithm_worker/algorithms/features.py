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
    valid = np.isfinite(values)
    if not valid.any():
        return np.full(values.shape, np.nan, dtype=np.float32)
    filled = np.where(valid, values, float(np.nanmean(values)))
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


def _feature_raster(values: np.ndarray, source: RasterData, name: str) -> RasterData:
    return RasterData(
        values=values,
        crs=source.crs,
        transform=source.transform,
        nodata=np.nan,
        band_metadata=(BandMetadata(name),),
    )
