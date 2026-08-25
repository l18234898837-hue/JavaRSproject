from __future__ import annotations

from rasterio.enums import Resampling

from rs_algorithm_worker.models import RasterData
from rs_algorithm_worker.operators.reproject import _reproject_to_grid
from rs_algorithm_worker.operators.spatial import require_grid


def align_to_reference(
    raster: RasterData,
    reference: RasterData,
    *,
    resampling: Resampling = Resampling.nearest,
) -> RasterData:
    reference_crs, reference_transform = require_grid(reference)
    require_grid(raster)
    if not isinstance(resampling, Resampling):
        raise ValueError("resampling must be a rasterio Resampling value")
    height, width = reference.values.shape[-2:]
    return _reproject_to_grid(
        raster,
        destination_crs=reference_crs,
        destination_transform=reference_transform,
        destination_height=height,
        destination_width=width,
        resampling=resampling,
    )
