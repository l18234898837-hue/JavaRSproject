from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from rs_algorithm_worker.models import BandMetadata, BandSet, RasterData
from rs_algorithm_worker.operators.spatial import require_grid


@dataclass(frozen=True)
class FusionParameters:
    red: str
    green: str
    blue: str
    low_percentile: float = 2.0
    high_percentile: float = 98.0


def fuse_bands(bands: BandSet, parameters: FusionParameters) -> RasterData:
    require_grid(bands.raster)
    if not 0 <= parameters.low_percentile < parameters.high_percentile <= 100:
        raise ValueError("fusion percentiles are invalid")
    by_name = {metadata.name: index for index, metadata in enumerate(bands.raster.band_metadata)}
    requested = (parameters.red, parameters.green, parameters.blue)
    missing = [name for name in requested if name not in by_name]
    if missing:
        raise ValueError(f"fusion bands are unavailable: {', '.join(missing)}")
    stacked = np.stack([_stretch(bands.raster.values[by_name[name]], parameters) for name in requested])
    return RasterData(
        values=stacked, crs=bands.raster.crs, transform=bands.raster.transform, nodata=0,
        band_metadata=(BandMetadata("red"), BandMetadata("green"), BandMetadata("blue")),
    )


def _stretch(values: np.ndarray, parameters: FusionParameters) -> np.ndarray:
    finite = values[np.isfinite(values)]
    if finite.size == 0:
        return np.zeros(values.shape, dtype=np.uint8)
    lower, upper = np.percentile(finite, [parameters.low_percentile, parameters.high_percentile])
    if upper <= lower:
        return np.zeros(values.shape, dtype=np.uint8)
    result = np.zeros(values.shape, dtype=np.uint8)
    valid = np.isfinite(values)
    result[valid] = np.clip((values[valid] - lower) * 255.0 / (upper - lower), 0, 255).astype(np.uint8)
    return result
