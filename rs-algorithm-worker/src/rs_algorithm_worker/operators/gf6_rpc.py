from __future__ import annotations

from dataclasses import dataclass
import math

import numpy as np
from affine import Affine
from pyproj import CRS, Transformer

from ..models_gf6 import Gf6ProductEvidence, Gf6RpcSpec


@dataclass(frozen=True)
class NominalRpcGrid:
    crs: str
    transform: Affine
    width: int
    height: int
    bounds: tuple[float, float, float, float]
    height_m: float
    navigation_status: str = "NOMINAL_RPC_NO_DEM"
    accuracy_status: str = "UNVALIDATED"


def rpc_image_to_ground(rpc: Gf6RpcSpec, sample: np.ndarray, line: np.ndarray, height_m: float) -> tuple[np.ndarray, np.ndarray]:
    s = (sample - rpc.sample_offset) / rpc.sample_scale
    l = (line - rpc.line_offset) / rpc.line_scale
    h = np.full_like(s, (float(height_m) - rpc.height_offset) / rpc.height_scale, dtype=float)
    terms = np.stack((np.ones_like(s), l, s, h, l*s, l*h, s*h, l*l, s*s, h*h,
                      l*s*h, l**3, l*s*s, l*h*h, l*l*s, s**3, s*h*h, l*l*h, s*s*h, h**3), axis=0)
    line_den = np.tensordot(np.asarray(rpc.line_den), terms, axes=(0, 0))
    sample_den = np.tensordot(np.asarray(rpc.sample_den), terms, axes=(0, 0))
    if np.any(np.isclose(line_den, 0)) or np.any(np.isclose(sample_den, 0)):
        raise ValueError("GF6 RPC denominator is singular")
    line_norm = np.tensordot(np.asarray(rpc.line_num), terms, axes=(0, 0)) / line_den
    sample_norm = np.tensordot(np.asarray(rpc.sample_num), terms, axes=(0, 0)) / sample_den
    return sample_norm * rpc.longitude_scale + rpc.longitude_offset, line_norm * rpc.latitude_scale + rpc.latitude_offset


def build_nominal_rpc_grid(evidence: Gf6ProductEvidence, target_crs: str = "EPSG:4326", resolution: float = 0.00015, height_m: float = 0.0) -> NominalRpcGrid:
    if not math.isfinite(resolution) or resolution <= 0 or not math.isfinite(height_m):
        raise ValueError("GF6 RPC grid parameters are invalid")
    try:
        target = CRS.from_user_input(target_crs)
    except Exception as error:
        raise ValueError("GF6 target CRS is invalid") from error
    samples = np.array([0, evidence.width - 1, evidence.width / 2], dtype=float)
    lines = np.array([0, evidence.height - 1, evidence.height / 2], dtype=float)
    sample_grid, line_grid = np.meshgrid(samples, lines)
    lon, lat = rpc_image_to_ground(evidence.rpc, sample_grid, line_grid, height_m)
    if not np.all(np.isfinite(lon)) or not np.all(np.isfinite(lat)):
        raise ValueError("GF6 RPC produced non-finite coordinates")
    transformer = Transformer.from_crs("EPSG:4326", target, always_xy=True)
    x, y = transformer.transform(lon, lat)
    min_x, max_x, min_y, max_y = float(np.min(x)), float(np.max(x)), float(np.min(y)), float(np.max(y))
    if not max_x > min_x or not max_y > min_y:
        raise ValueError("GF6 RPC grid extent is invalid")
    width = max(1, int(math.ceil((max_x - min_x) / resolution)))
    height = max(1, int(math.ceil((max_y - min_y) / resolution)))
    return NominalRpcGrid(target.to_string(), Affine(resolution, 0, min_x, 0, -resolution, max_y), width, height,
                          (min_x, min_y, max_x, max_y), float(height_m))
