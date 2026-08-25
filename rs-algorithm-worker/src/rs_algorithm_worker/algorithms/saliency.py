from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from rs_algorithm_worker.models import BandMetadata, RasterData


@dataclass(frozen=True)
class SaliencyParameters:
    threshold: float


@dataclass(frozen=True)
class SaliencyResult:
    score: RasterData
    mask: np.ndarray
    version: str = "contrast-v1"


def detect_saliency(raster: RasterData, parameters: SaliencyParameters) -> SaliencyResult:
    if not 0.0 <= parameters.threshold <= 1.0:
        raise ValueError("saliency threshold must be between zero and one")
    values = raster.values if raster.values.ndim == 2 else np.nanmean(raster.values, axis=0)
    finite = values[np.isfinite(values)]
    if finite.size == 0 or finite.max() == finite.min():
        score_values = np.zeros(values.shape, dtype=np.float32)
    else:
        mean = finite.mean()
        score_values = np.abs(values - mean).astype(np.float32)
        score_values = np.nan_to_num(score_values / np.nanmax(score_values), nan=0.0)
    score = RasterData(score_values, raster.crs, raster.transform, 0.0, (BandMetadata("saliency_score"),))
    return SaliencyResult(score=score, mask=score_values >= parameters.threshold)
