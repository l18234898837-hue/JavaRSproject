from __future__ import annotations

import numpy as np
from rasterio.crs import CRS
from rasterio.enums import Resampling
from rasterio.transform import array_bounds
from rasterio.warp import calculate_default_transform, reproject

from rs_algorithm_worker.models import RasterData
from rs_algorithm_worker.operators.spatial import as_band_first, require_grid, restore_band_dimensions


def reproject_raster(
    raster: RasterData,
    *,
    destination_crs: str,
    resolution: float | None = None,
    resampling: Resampling = Resampling.nearest,
) -> RasterData:
    source_crs, source_transform = require_grid(raster)
    try:
        target_crs = CRS.from_user_input(destination_crs)
    except Exception as error:
        raise ValueError("destination CRS is invalid") from error
    if resolution is not None and (not np.isfinite(resolution) or resolution <= 0):
        raise ValueError("resolution must be positive")
    if not isinstance(resampling, Resampling):
        raise ValueError("resampling must be a rasterio Resampling value")

    height, width = raster.values.shape[-2:]
    left, bottom, right, top = array_bounds(height, width, source_transform)
    target_transform, target_width, target_height = calculate_default_transform(
        source_crs,
        target_crs,
        width,
        height,
        left,
        bottom,
        right,
        top,
        resolution=resolution,
    )
    return _reproject_to_grid(
        raster,
        destination_crs=target_crs,
        destination_transform=target_transform,
        destination_height=target_height,
        destination_width=target_width,
        resampling=resampling,
    )


def _reproject_to_grid(
    raster: RasterData,
    *,
    destination_crs: CRS,
    destination_transform,
    destination_height: int,
    destination_width: int,
    resampling: Resampling,
) -> RasterData:
    source_crs, source_transform = require_grid(raster)
    source_values, single_band = as_band_first(raster)
    output_nodata = np.nan if raster.nodata is None else raster.nodata
    output_values = np.full(
        (source_values.shape[0], destination_height, destination_width),
        output_nodata,
        dtype=np.float32,
    )
    for index, values in enumerate(source_values):
        reproject(
            source=values,
            destination=output_values[index],
            src_transform=source_transform,
            src_crs=source_crs,
            src_nodata=raster.nodata,
            dst_transform=destination_transform,
            dst_crs=destination_crs,
            dst_nodata=output_nodata,
            resampling=resampling,
        )
    return RasterData(
        values=restore_band_dimensions(output_values, single_band),
        crs=destination_crs.to_string(),
        transform=destination_transform,
        nodata=raster.nodata,
        band_metadata=raster.band_metadata,
    )
