from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import uniform_filter

from rs_algorithm_worker.models import BandMetadata, RasterData


@dataclass(frozen=True)
class SaliencyParameters:
    threshold: float
    window_size: int = 3


@dataclass(frozen=True)
class SaliencyResult:
    score: RasterData
    mask: np.ndarray
    version: str = "contrast-v1"


def detect_saliency(raster: RasterData, parameters: SaliencyParameters) -> SaliencyResult:
    if not 0.0 <= parameters.threshold <= 1.0:
        raise ValueError("saliency threshold must be between zero and one")
    if parameters.window_size < 3 or parameters.window_size % 2 == 0:
        raise ValueError("saliency window size must be an odd integer of at least three")
    values = raster.values if raster.values.ndim == 2 else np.nanmean(raster.values, axis=0)
    valid = np.isfinite(values)
    finite = values[valid]
    if finite.size == 0:
        score_values = np.full(values.shape, np.nan, dtype=np.float32)
    elif finite.max() == finite.min():
        score_values = np.full(values.shape, np.nan, dtype=np.float32)
        score_values[valid] = 0.0
    else:
        counts = uniform_filter(valid.astype(np.float32), size=parameters.window_size, mode="nearest")
        local_mean = uniform_filter(np.where(valid, values, 0.0), size=parameters.window_size, mode="nearest")
        local_mean /= np.where(counts == 0, 1.0, counts)
        score_values = np.abs(values - local_mean).astype(np.float32)
        score_values[~valid] = np.nan
        maximum = np.nanmax(score_values)
        if maximum > 0:
            score_values /= maximum
    score = RasterData(score_values, raster.crs, raster.transform, np.nan, (BandMetadata("saliency_score"),))
    return SaliencyResult(score=score, mask=np.isfinite(score_values) & (score_values >= parameters.threshold))
