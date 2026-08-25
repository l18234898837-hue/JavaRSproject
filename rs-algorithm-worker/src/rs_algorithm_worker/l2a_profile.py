from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from pyproj import CRS

from .product_adapters.hy1c_czi_l2a import load_reviewed_mapping


@dataclass(frozen=True)
class L2AParameters:
    bands: tuple[str, ...]
    requested_crs: str
    resolved_crs: str
    resolution_meters: float
    science_resampling: str
    roi_wgs84: tuple[float, float, float, float] | None
    quality_policy: str
    algorithms: dict[str, dict[str, Any]]


def parse_l2a_parameters(
    payload: dict[str, Any], *, centre_longitude: float, centre_latitude: float
) -> L2AParameters:
    if not isinstance(payload, dict) or set(payload) - {
        "schemaVersion", "operation", "bands", "grid", "roiWgs84", "quality", "algorithms"
    }:
        raise ValueError("L2A parameters contain unsupported fields")
    if payload.get("schemaVersion") != "1" or payload.get("operation") != "STANDARDIZE_L2A":
        raise ValueError("L2A parameters require schemaVersion 1 and STANDARDIZE_L2A operation")

    mapping = load_reviewed_mapping()
    bands = _bands(payload.get("bands"), mapping.band_ids)
    grid = _mapping(payload.get("grid"), "grid")
    if set(grid) != {"targetCrs", "resolutionMeters", "scienceResampling"}:
        raise ValueError("L2A grid fields are invalid")
    requested_crs = grid.get("targetCrs")
    resolution = grid.get("resolutionMeters")
    resampling = grid.get("scienceResampling")
    if not isinstance(requested_crs, str) or not requested_crs:
        raise ValueError("L2A target CRS is required")
    if not isinstance(resolution, (int, float)) or isinstance(resolution, bool) or resolution <= 0:
        raise ValueError("L2A resolutionMeters must be positive")
    if resampling != "nearest":
        raise ValueError("L2A science resampling must be nearest")
    resolved_crs = _resolve_crs(requested_crs, centre_longitude, centre_latitude)

    quality = _mapping(payload.get("quality"), "quality")
    if set(quality) != {"policy"} or quality.get("policy") != "default":
        raise ValueError("L2A quality policy is unsupported")
    algorithms = _algorithms(payload.get("algorithms"), bands)
    return L2AParameters(
        bands=bands, requested_crs=requested_crs, resolved_crs=resolved_crs,
        resolution_meters=float(resolution), science_resampling=resampling,
        roi_wgs84=_roi(payload.get("roiWgs84")), quality_policy="default", algorithms=algorithms,
    )


def _bands(value: Any, allowed: tuple[str, ...]) -> tuple[str, ...]:
    if not isinstance(value, list) or not value or any(not isinstance(item, str) for item in value):
        raise ValueError("L2A bands are required")
    bands = tuple(value)
    if len(set(bands)) != len(bands) or any(item not in allowed for item in bands):
        raise ValueError("L2A bands must be unique reviewed CZI bands")
    return bands


def _resolve_crs(value: str, longitude: float, latitude: float) -> str:
    if not isinstance(longitude, (int, float)) or not -180 <= longitude <= 180:
        raise ValueError("L2A scene longitude is invalid")
    if not isinstance(latitude, (int, float)) or not -90 <= latitude <= 90:
        raise ValueError("L2A scene latitude is invalid")
    if value == "AUTO_UTM":
        zone = int((longitude + 180) // 6) + 1
        zone = min(60, max(1, zone))
        return f"EPSG:{32600 + zone if latitude >= 0 else 32700 + zone}"
    try:
        crs = CRS.from_user_input(value)
    except Exception as error:
        raise ValueError("L2A target CRS is invalid") from error
    if not crs.is_projected or not any(axis.unit_name.lower() in {"metre", "meter", "metres", "meters"}
                                       for axis in crs.axis_info):
        raise ValueError("L2A resolutionMeters requires a projected metre-based CRS")
    authority = crs.to_authority()
    return f"{authority[0]}:{authority[1]}" if authority else crs.to_string()


def _roi(value: Any) -> tuple[float, float, float, float] | None:
    if value is None:
        return None
    roi = _mapping(value, "roiWgs84")
    if set(roi) != {"minLon", "minLat", "maxLon", "maxLat"}:
        raise ValueError("L2A ROI fields are invalid")
    try:
        bounds = tuple(float(roi[key]) for key in ("minLon", "minLat", "maxLon", "maxLat"))
    except (TypeError, ValueError) as error:
        raise ValueError("L2A ROI values are invalid") from error
    min_lon, min_lat, max_lon, max_lat = bounds
    if not (-180 <= min_lon < max_lon <= 180 and -90 <= min_lat < max_lat <= 90):
        raise ValueError("L2A ROI bounds are invalid")
    return bounds


def _algorithms(value: Any, bands: tuple[str, ...]) -> dict[str, dict[str, Any]]:
    algorithms = _mapping(value, "algorithms")
    if set(algorithms) - {"features", "fusion", "saliency"}:
        raise ValueError("L2A algorithms contain unsupported fields")
    normalized: dict[str, dict[str, Any]] = {}
    for name, configuration in algorithms.items():
        configuration = _mapping(configuration, f"{name} algorithm")
        enabled = configuration.get("enabled", False)
        if not isinstance(enabled, bool):
            raise ValueError(f"L2A {name} enabled must be boolean")
        if name == "fusion" and enabled:
            selected = (configuration.get("red"), configuration.get("green"), configuration.get("blue"))
            if any(item not in bands for item in selected):
                raise ValueError("L2A fusion bands must be selected")
        if name == "saliency" and enabled and configuration.get("source") not in bands:
            raise ValueError("L2A saliency source must be selected")
        normalized[name] = dict(configuration)
    return normalized


def _mapping(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"L2A {name} must be an object")
    return value
