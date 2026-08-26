from __future__ import annotations

import math
from typing import Any

from pyproj import CRS
from rasterio.crs import CRS as RasterioCRS


ALLOWED_RESAMPLING = {"nearest"}
_TOP_LEVEL_FIELDS = {
    "targetCrs", "resolutionDegrees", "resolutionMeters", "roi",
    "continuousResampling", "bands", "features", "fusion", "saliency",
}
_FEATURE_METRICS = {"statistics", "gradient", "local_variance"}


def parse_fy3h_parameters(raw: dict[str, Any]) -> dict[str, Any]:
    """Validate and normalize the public FY-3H MERSI processing parameters."""
    if not isinstance(raw, dict) or set(raw) - _TOP_LEVEL_FIELDS:
        raise ValueError("unsupported FY-3H parameter")

    target_crs, is_wgs84 = _target_crs(raw.get("targetCrs", "EPSG:4326"))
    parameters: dict[str, Any] = {"targetCrs": target_crs}
    has_degrees = "resolutionDegrees" in raw
    has_meters = "resolutionMeters" in raw
    if has_degrees and has_meters:
        raise ValueError("FY-3H resolutionDegrees and resolutionMeters are mutually exclusive")
    if is_wgs84:
        if has_meters:
            raise ValueError("EPSG:4326 requires resolutionDegrees")
        parameters["resolutionDegrees"] = _positive(raw.get("resolutionDegrees", 0.01), "resolutionDegrees")
    else:
        if has_degrees:
            raise ValueError("projected FY-3H CRS requires resolutionMeters")
        parameters["resolutionMeters"] = _positive(raw.get("resolutionMeters", 1000.0), "resolutionMeters")

    parameters["continuousResampling"] = raw.get("continuousResampling", "nearest")
    if parameters["continuousResampling"] not in ALLOWED_RESAMPLING:
        raise ValueError("FY-3H continuousResampling must be nearest")
    parameters["bands"] = _bands(raw.get("bands", list(range(1, 26))))
    if "roi" in raw:
        parameters["roi"] = _roi(raw["roi"])

    features = _features(raw.get("features", {"enabled": False}))
    fusion = _fusion(raw.get("fusion", {"enabled": False}), parameters["bands"])
    saliency = _saliency(raw.get("saliency", {"enabled": False}), parameters["bands"], features)
    parameters["features"] = features
    parameters["fusion"] = fusion
    parameters["saliency"] = saliency
    return parameters


def require_odd_window(value: int, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 3 or value % 2 == 0:
        raise ValueError(f"{label} window size must be an odd integer of at least three")
    return value


def _target_crs(value: Any) -> tuple[str, bool]:
    if not isinstance(value, str) or not value.strip():
        raise ValueError("FY-3H targetCrs is required")
    try:
        pyproj_crs = CRS.from_user_input(value)
        rasterio_crs = RasterioCRS.from_user_input(value)
    except Exception as error:
        raise ValueError("FY-3H targetCrs is invalid") from error
    if pyproj_crs != CRS.from_epsg(4326) and not pyproj_crs.is_projected:
        raise ValueError("FY-3H targetCrs must be EPSG:4326 or a projected CRS")
    authority = pyproj_crs.to_authority()
    normalized = f"{authority[0]}:{authority[1]}" if authority else rasterio_crs.to_string()
    return normalized, pyproj_crs == CRS.from_epsg(4326)


def _positive(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
        raise ValueError(f"FY-3H {label} must be a finite positive number")
    return float(value)


def _bands(value: Any) -> list[int]:
    if not isinstance(value, list) or not value:
        raise ValueError("FY-3H bands are required")
    if any(isinstance(band, bool) or not isinstance(band, int) or not 1 <= band <= 25 for band in value):
        raise ValueError("FY-3H bands must be integers from 1 through 25")
    if len(set(value)) != len(value):
        raise ValueError("FY-3H bands must be unique")
    return list(value)


def _roi(value: Any) -> list[float]:
    if not isinstance(value, (list, tuple)) or len(value) != 4:
        raise ValueError("FY-3H roi must be a WGS84 four-value sequence")
    if any(isinstance(item, bool) or not isinstance(item, (int, float)) or not math.isfinite(item) for item in value):
        raise ValueError("FY-3H roi values must be finite numbers")
    min_lon, min_lat, max_lon, max_lat = (float(item) for item in value)
    if not (-180 <= min_lon < max_lon <= 180 and -90 <= min_lat < max_lat <= 90):
        raise ValueError("FY-3H roi bounds are invalid")
    return [min_lon, min_lat, max_lon, max_lat]


def _features(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "metrics", "windowSize"}:
        raise ValueError("FY-3H features are invalid")
    enabled = value.get("enabled", False)
    if not isinstance(enabled, bool):
        raise ValueError("FY-3H features enabled must be boolean")
    metrics = value.get("metrics", [])
    if not isinstance(metrics, list) or any(metric not in _FEATURE_METRICS for metric in metrics) or len(set(metrics)) != len(metrics):
        raise ValueError("FY-3H features metrics are invalid")
    if enabled and not metrics:
        raise ValueError("FY-3H enabled features require metrics")
    result: dict[str, Any] = {"enabled": enabled, "metrics": list(metrics)}
    if "local_variance" in metrics:
        result["windowSize"] = require_odd_window(value.get("windowSize", 3), "FY-3H local_variance")
    elif "windowSize" in value:
        raise ValueError("FY-3H feature windowSize is only valid for local_variance")
    return result


def _fusion(value: Any, bands: list[int]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "red", "green", "blue", "percentiles"}:
        raise ValueError("FY-3H fusion is invalid")
    enabled = value.get("enabled", False)
    if not isinstance(enabled, bool):
        raise ValueError("FY-3H fusion enabled must be boolean")
    result: dict[str, Any] = {"enabled": enabled}
    if not enabled:
        if set(value) != {"enabled"}:
            raise ValueError("disabled FY-3H fusion must not declare settings")
        return result
    rgb = {key: value.get(key) for key in ("red", "green", "blue")}
    if any(component not in bands for component in rgb.values()):
        raise ValueError("FY-3H fusion RGB bands must be selected")
    percentiles = value.get("percentiles", [2.0, 98.0])
    if not isinstance(percentiles, (list, tuple)) or len(percentiles) != 2:
        raise ValueError("FY-3H fusion percentiles are invalid")
    low, high = percentiles
    if any(isinstance(item, bool) or not isinstance(item, (int, float)) or not math.isfinite(item) for item in (low, high)) or not 0 <= low < high <= 100:
        raise ValueError("FY-3H fusion percentiles are invalid")
    return {"enabled": True, **rgb, "percentiles": [float(low), float(high)]}


def _saliency(value: Any, bands: list[int], features: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "source", "windowSize", "threshold"}:
        raise ValueError("FY-3H saliency is invalid")
    enabled = value.get("enabled", False)
    if not isinstance(enabled, bool):
        raise ValueError("FY-3H saliency enabled must be boolean")
    if not enabled:
        if set(value) != {"enabled"}:
            raise ValueError("disabled FY-3H saliency must not declare settings")
        return {"enabled": False}
    source = value.get("source")
    if source not in _selected_variables(bands) and source not in _feature_sources(features, bands):
        raise ValueError("FY-3H saliency source must be a selected variable or feature")
    window_size = require_odd_window(value.get("windowSize", 3), "FY-3H saliency")
    threshold = value.get("threshold", 0.5)
    if isinstance(threshold, bool) or not isinstance(threshold, (int, float)) or not math.isfinite(threshold) or not 0 <= threshold <= 1:
        raise ValueError("FY-3H saliency threshold must be between zero and one")
    return {"enabled": True, "source": source, "windowSize": window_size, "threshold": float(threshold)}


def _selected_variables(bands: list[int]) -> set[str]:
    return {
        f"CH{band:02d}_{'REFLECTANCE' if band <= 19 else 'BRIGHTNESS_TEMPERATURE'}"
        for band in bands
    }


def _feature_sources(features: dict[str, Any], bands: list[int]) -> set[str]:
    if not features["enabled"]:
        return set()
    variables = _selected_variables(bands)
    return {
        f"{metric}:{variable}"
        for metric in features["metrics"] if metric in {"gradient", "local_variance"} for variable in variables
    }
