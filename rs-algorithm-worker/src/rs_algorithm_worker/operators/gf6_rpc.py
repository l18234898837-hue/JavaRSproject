from __future__ import annotations

from dataclasses import dataclass
import math

from affine import Affine
import numpy as np
from pyproj import CRS, Transformer
from rasterio.rpc import RPC
from rasterio.transform import RPCTransformer
from rasterio.windows import Window, from_bounds

from ..models_gf6 import Gf6ProductEvidence


MIN_GEOGRAPHIC_RESOLUTION_DEGREES = 0.00001
MIN_PROJECTED_RESOLUTION_METRES = 1.0


@dataclass(frozen=True)
class NominalRpcGrid:
    crs: str
    transform: Affine
    width: int
    height: int
    bounds: tuple[float, float, float, float]
    height_m: float = 0.0
    navigation_status: str = "NOMINAL_RPC_NO_DEM"
    accuracy_status: str = "UNVALIDATED"


def window_transform(transform: Affine, window: Window) -> Affine:
    return transform * Affine.translation(window.col_off, window.row_off)


def as_rasterio_rpc(rpc) -> RPC:
    return RPC(height_off=rpc.height_offset, height_scale=rpc.height_scale, lat_off=rpc.latitude_offset,
               lat_scale=rpc.latitude_scale, line_den_coeff=rpc.line_den, line_num_coeff=rpc.line_num,
               line_off=rpc.line_offset, line_scale=rpc.line_scale, long_off=rpc.longitude_offset,
               long_scale=rpc.longitude_scale, samp_den_coeff=rpc.sample_den, samp_num_coeff=rpc.sample_num,
               samp_off=rpc.sample_offset, samp_scale=rpc.sample_scale)


def validate_rpc_output_resolution(target_crs: str, resolution: float) -> CRS:
    if isinstance(resolution, bool) or not isinstance(resolution, (int, float)) or not math.isfinite(resolution) or resolution <= 0:
        raise ValueError("GF6_PARAMETER_INVALID: resolution")
    try:
        crs = CRS.from_user_input(target_crs)
    except Exception as error:
        raise ValueError("GF6_PARAMETER_INVALID: targetCrs") from error
    axes = crs.axis_info[:2]
    if crs.is_geographic and len(axes) == 2 and all("degree" in (axis.unit_name or "").lower() for axis in axes):
        if resolution < MIN_GEOGRAPHIC_RESOLUTION_DEGREES:
            raise ValueError(
                "GF6_OUTPUT_LIMIT_EXCEEDED: resolution below 0.00001 degree for geographic CRS"
            )
        return crs
    if crs.is_projected and len(axes) == 2 and all(
        math.isclose(float(axis.unit_conversion_factor or 0.0), 1.0, rel_tol=0.0, abs_tol=1e-12)
        for axis in axes
    ):
        if resolution < MIN_PROJECTED_RESOLUTION_METRES:
            raise ValueError(
                "GF6_OUTPUT_LIMIT_EXCEEDED: resolution below 1 metre for projected CRS"
            )
        return crs
    raise ValueError(
        "GF6_OUTPUT_LIMIT_EXCEEDED: target CRS axes must use degrees or metres"
    )


def crop_nominal_rpc_grid(grid: NominalRpcGrid, roi_wgs84: tuple[float, float, float, float]) -> NominalRpcGrid:
    if len(roi_wgs84) != 4 or not all(math.isfinite(value) for value in roi_wgs84):
        raise ValueError("GF6 ROI is invalid")
    west, south, east, north = roi_wgs84
    if east <= west or north <= south:
        raise ValueError("GF6 ROI is invalid")
    transform = Transformer.from_crs("EPSG:4326", grid.crs, always_xy=True)
    xs, ys = transform.transform([west, west, east, east], [south, north, south, north])
    try:
        window = from_bounds(min(xs), min(ys), max(xs), max(ys), transform=grid.transform).intersection(Window(0, 0, grid.width, grid.height)).round_offsets().round_lengths()
    except Exception as error:
        raise ValueError("ROI_DOES_NOT_INTERSECT") from error
    if window.width <= 0 or window.height <= 0:
        raise ValueError("ROI_DOES_NOT_INTERSECT")
    target = window_transform(grid.transform, window)
    width, height = int(window.width), int(window.height)
    return NominalRpcGrid(grid.crs, target, width, height, (target.c, target.f + target.e * height, target.c + target.a * width, target.f), grid.height_m)


def build_nominal_rpc_grid(evidence: Gf6ProductEvidence, target_crs: str = "EPSG:4326", resolution: float = 0.00015, height_m: float = 0.0) -> NominalRpcGrid:
    if height_m != 0.0:
        raise ValueError("GF6 RPC grid parameters are invalid")
    crs = validate_rpc_output_resolution(target_crs, resolution)
    samples = np.asarray([0, evidence.width - 1, evidence.width / 2, 0, evidence.width - 1], dtype=float)
    lines = np.asarray([0, evidence.height - 1, evidence.height / 2, evidence.height / 2, evidence.height / 2], dtype=float)
    try:
        with RPCTransformer(as_rasterio_rpc(evidence.rpc)) as transformer:
            longitudes, latitudes = transformer.xy(lines, samples, zs=np.zeros(samples.shape, dtype=float))
    except Exception as error:
        raise ValueError("GF6_EVIDENCE_INVALID: RPC grid") from error
    if not np.all(np.isfinite(longitudes)) or not np.all(np.isfinite(latitudes)):
        raise ValueError("GF6_EVIDENCE_INVALID: RPC grid")
    project = Transformer.from_crs("EPSG:4326", crs, always_xy=True)
    xs, ys = project.transform(longitudes, latitudes)
    left, right, bottom, top = min(xs), max(xs), min(ys), max(ys)
    return NominalRpcGrid(crs.to_string(), Affine(resolution, 0, left, 0, -resolution, top), max(1, math.ceil((right-left)/resolution)), max(1, math.ceil((top-bottom)/resolution)), (left, bottom, right, top))
