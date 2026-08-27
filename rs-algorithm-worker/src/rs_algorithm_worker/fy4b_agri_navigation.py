from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np
from affine import Affine

@dataclass(frozen=True)
class NominalGrid:
    crs: str
    transform: Affine
    width: int
    height: int
    status: str
    parameters: dict[str, float]

def build_nominal_grid(*, nom_center_lon: float, nom_sat_height: float, earth_equatorial_radius: float, inverse_flattening: float, sampling_angle_micro_rad: float, stepping_angle_micro_rad: float, width: int, height: int) -> NominalGrid:
    values = (nom_center_lon, nom_sat_height, earth_equatorial_radius, inverse_flattening, sampling_angle_micro_rad, stepping_angle_micro_rad)
    if any(not math.isfinite(v) for v in values) or nom_sat_height <= 0 or earth_equatorial_radius <= 0 or inverse_flattening <= 0 or sampling_angle_micro_rad <= 0 or stepping_angle_micro_rad <= 0 or width < 1 or height < 1:
        raise ValueError("FY-4B nominal grid parameters are invalid")
    b = earth_equatorial_radius * (1.0 - 1.0 / inverse_flattening)
    sx = sampling_angle_micro_rad * 1e-6 * nom_sat_height
    sy = stepping_angle_micro_rad * 1e-6 * nom_sat_height
    transform = Affine(sx, 0.0, -0.5 * width * sx, 0.0, -sy, 0.5 * height * sy)
    crs = f"+proj=geos +lon_0={nom_center_lon} +h={nom_sat_height} +a={earth_equatorial_radius} +b={b} +sweep=x +units=m +no_defs"
    return NominalGrid(crs, transform, width, height, "NOMINAL_UNVERIFIED", {"nomCenterLon": nom_center_lon, "nomSatHeight": nom_sat_height, "earthEquatorialRadius": earth_equatorial_radius, "inverseFlattening": inverse_flattening, "samplingAngleMicroRad": sampling_angle_micro_rad, "steppingAngleMicroRad": stepping_angle_micro_rad})

def apparent_reflectance(rho: np.ndarray, *, earth_sun_distance_ratio: float, solar_zenith_degrees: np.ndarray) -> np.ndarray:
    rho = np.asarray(rho, dtype=np.float32); angle = np.asarray(solar_zenith_degrees, dtype=np.float32)
    if rho.shape != angle.shape or not math.isfinite(earth_sun_distance_ratio) or earth_sun_distance_ratio <= 0: raise ValueError("FY-4B apparent reflectance inputs are invalid")
    with np.errstate(divide="ignore", invalid="ignore"):
        result = rho * earth_sun_distance_ratio ** 2 / np.cos(np.deg2rad(angle))
    return np.where((angle >= 0) & (angle < 90) & np.isfinite(result), result, np.nan).astype(np.float32)
