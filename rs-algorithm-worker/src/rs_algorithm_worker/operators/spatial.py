from __future__ import annotations

from affine import Affine
from rasterio.crs import CRS

from rs_algorithm_worker.models import RasterData


def require_grid(raster: RasterData) -> tuple[CRS, Affine]:
    if raster.crs is None or raster.transform is None:
        raise ValueError("raster CRS and transform are required")
    if not isinstance(raster.transform, Affine):
        raise ValueError("raster transform must be an Affine")
    try:
        crs = CRS.from_user_input(raster.crs)
    except Exception as error:
        raise ValueError("raster CRS is invalid") from error
    return crs, raster.transform


def as_band_first(raster: RasterData):
    if raster.values.ndim == 2:
        return raster.values[None, ...], True
    return raster.values, False


def restore_band_dimensions(values, single_band: bool):
    return values[0] if single_band else values
