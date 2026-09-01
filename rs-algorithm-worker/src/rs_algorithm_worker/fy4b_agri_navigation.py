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


def build_nominal_grid(
    *,
    nom_center_lon: float,
    nom_sat_height: float,
    earth_equatorial_radius: float,
    inverse_flattening: float,
    sampling_angle_micro_rad: float,
    stepping_angle_micro_rad: float,
    width: int,
    height: int,
) -> NominalGrid:
    values = (
        nom_center_lon,
        nom_sat_height,
        earth_equatorial_radius,
        inverse_flattening,
        sampling_angle_micro_rad,
        stepping_angle_micro_rad,
    )
    if (
        any(not math.isfinite(value) for value in values)
        or nom_sat_height <= 0
        or earth_equatorial_radius <= 0
        or inverse_flattening <= 0
        or sampling_angle_micro_rad <= 0
        or stepping_angle_micro_rad <= 0
        or width < 1
        or height < 1
    ):
        raise ValueError("FY-4B nominal grid parameters are invalid")
    semi_minor_axis = earth_equatorial_radius * (1.0 - 1.0 / inverse_flattening)
    pixel_width = sampling_angle_micro_rad * 1e-6 * nom_sat_height
    pixel_height = stepping_angle_micro_rad * 1e-6 * nom_sat_height
    return NominalGrid(
        crs=(
            f"+proj=geos +lon_0={nom_center_lon} +h={nom_sat_height} "
            f"+a={earth_equatorial_radius} +b={semi_minor_axis} +sweep=x +units=m +no_defs"
        ),
        transform=Affine(pixel_width, 0.0, -0.5 * width * pixel_width, 0.0, -pixel_height, 0.5 * height * pixel_height),
        width=width,
        height=height,
        status="NOMINAL_UNVERIFIED",
        parameters={
            "nomCenterLon": nom_center_lon,
            "nomSatHeight": nom_sat_height,
            "earthEquatorialRadius": earth_equatorial_radius,
            "inverseFlattening": inverse_flattening,
            "samplingAngleMicroRad": sampling_angle_micro_rad,
            "steppingAngleMicroRad": stepping_angle_micro_rad,
        },
    )


def apparent_reflectance(
    rho: np.ndarray,
    *,
    earth_sun_distance_ratio: float,
    solar_zenith_degrees: np.ndarray,
) -> np.ndarray:
    reflectance = np.asarray(rho, dtype=np.float32)
    zenith = np.asarray(solar_zenith_degrees, dtype=np.float32)
    if reflectance.shape != zenith.shape or not math.isfinite(earth_sun_distance_ratio) or earth_sun_distance_ratio <= 0:
        raise ValueError("FY-4B apparent reflectance inputs are invalid")
    with np.errstate(divide="ignore", invalid="ignore"):
        corrected = reflectance * earth_sun_distance_ratio ** 2 / np.cos(np.deg2rad(zenith))
    return np.where((zenith >= 0) & (zenith < 90) & np.isfinite(corrected), corrected, np.nan).astype(np.float32)
