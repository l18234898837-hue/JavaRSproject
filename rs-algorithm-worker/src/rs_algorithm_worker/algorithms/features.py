from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from rs_algorithm_worker.models import RasterData


@dataclass(frozen=True)
class FeatureParameters:
    metrics: tuple[str, ...]


@dataclass(frozen=True)
class FeatureSet:
    statistics: dict[str, object]
    version: str = "statistics-v1"


def extract_features(raster: RasterData, parameters: FeatureParameters) -> FeatureSet:
    if "statistics" not in parameters.metrics:
        raise ValueError("statistics metric is required by the baseline extractor")
    values = raster.values if raster.values.ndim == 3 else raster.values[np.newaxis, ...]
    result: dict[str, object] = {"validPixelRatio": float(np.isfinite(values).mean())}
    for index, metadata in enumerate(raster.band_metadata):
        band = values[index]
        finite = band[np.isfinite(band)]
        if finite.size == 0:
            result[metadata.name] = {"min": None, "max": None, "mean": None, "std": None}
        else:
            result[metadata.name] = {
                "min": float(finite.min()), "max": float(finite.max()),
                "mean": float(finite.mean()), "std": float(finite.std()),
            }
    return FeatureSet(result)
