from __future__ import annotations

import json
import math
from typing import Any


_TOP_LEVEL_FIELDS = {"schemaVersion", "targetCrs", "roi", "features", "fusion", "saliency"}
_FEATURE_FIELDS = {"enabled", "metrics", "windowSize"}
_FUSION_FIELDS = {"enabled", "mode", "percentiles"}
_SALIENCY_FIELDS = {"enabled", "source", "threshold", "windowSize"}
_METRICS = {"statistics", "gradient", "local_variance", "toa_nir_red_nd", "btd_c13_c14"}
_PHYSICAL_SOURCES = {"C02_REFLECTANCE_500M", "C03_REFLECTANCE_1000M", "C13_BRIGHTNESS_TEMPERATURE_4000M"}


def parse_fy4b_parameters(raw: dict[str, Any] | None) -> dict[str, Any]:
    """Validate the FY-4B Worker snapshot and fill its stable defaults."""
    if raw is None:
        raw = {}
    if not isinstance(raw, dict) or set(raw).difference(_TOP_LEVEL_FIELDS):
        raise ValueError("FY-4B parameter object is invalid")
    schema_version = raw.get("schemaVersion", "1")
    if schema_version != "1":
        raise ValueError("FY-4B parameter schemaVersion is invalid")
    target_crs = raw.get("targetCrs", "EPSG:4326")
    if not isinstance(target_crs, str) or target_crs not in {"EPSG:4326", "EPSG:4490"}:
        raise ValueError("FY-4B parameter targetCrs is invalid")
    features = _features(raw.get("features", {}))
    return {
        "schemaVersion": "1", "targetCrs": target_crs, "roi": _roi(raw.get("roi")),
        "features": features, "fusion": _fusion(raw.get("fusion", {})),
        "saliency": _saliency(raw.get("saliency", {}), features),
    }


def snapshot(parameters: dict[str, Any]) -> str:
    return json.dumps(parameters, sort_keys=True, separators=(",", ":"))


def _roi(value: Any) -> list[float] | None:
    if value is None:
        return None
    if not isinstance(value, (list, tuple)) or len(value) != 4 or any(not _finite_number(item) for item in value):
        raise ValueError("FY-4B parameter roi is invalid")
    roi = [float(item) for item in value]
    if not (-180 <= roi[0] < roi[2] <= 180 and -90 <= roi[1] < roi[3] <= 90):
        raise ValueError("FY-4B parameter roi is invalid")
    return roi


def _features(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value).difference(_FEATURE_FIELDS):
        raise ValueError("FY-4B parameter features is invalid")
    enabled, metrics = value.get("enabled", False), value.get("metrics", ["statistics"])
    if (not isinstance(enabled, bool) or not isinstance(metrics, list)
            or any(not isinstance(metric, str) or metric not in _METRICS for metric in metrics)
            or len(metrics) != len(set(metrics)) or enabled and not metrics):
        raise ValueError("FY-4B parameter features is invalid")
    result = {"enabled": enabled, "metrics": list(metrics)}
    if "local_variance" in metrics:
        result["windowSize"] = _odd_window(value.get("windowSize", 3), "features")
    elif "windowSize" in value:
        raise ValueError("FY-4B parameter features windowSize is invalid")
    return result


def _fusion(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value).difference(_FUSION_FIELDS):
        raise ValueError("FY-4B parameter fusion is invalid")
    enabled, mode, percentiles = value.get("enabled", False), value.get("mode", "NIR_RED_BLUE"), value.get("percentiles", [2.0, 98.0])
    if not isinstance(enabled, bool):
        raise ValueError("FY-4B parameter fusion is invalid")
    if not enabled:
        return {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]}
    if mode != "NIR_RED_BLUE":
        raise ValueError("FY-4B parameter fusion mode is invalid")
    if (not isinstance(percentiles, (list, tuple)) or len(percentiles) != 2
            or any(not _finite_number(item) for item in percentiles)
            or not 0 <= percentiles[0] < percentiles[1] <= 100):
        raise ValueError("FY-4B parameter percentiles are invalid")
    return {"enabled": True, "mode": "NIR_RED_BLUE", "percentiles": [float(percentiles[0]), float(percentiles[1])]}


def _saliency(value: Any, features: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value).difference(_SALIENCY_FIELDS):
        raise ValueError("FY-4B parameter saliency is invalid")
    enabled, source, threshold = value.get("enabled", False), value.get("source", "C02_REFLECTANCE_500M"), value.get("threshold", 0.5)
    if not isinstance(enabled, bool) or not isinstance(source, str):
        raise ValueError("FY-4B parameter saliency is invalid")
    if not enabled:
        return {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3}
    feature_sources = {
        f"{metric}:C02_REFLECTANCE_500M" for metric in features["metrics"]
        if metric in {"gradient", "local_variance"}
    }
    feature_sources.update(metric for metric in features["metrics"] if metric in {"toa_nir_red_nd", "btd_c13_c14"})
    if source not in _PHYSICAL_SOURCES and (not features["enabled"] or source not in feature_sources):
        raise ValueError("FY-4B parameter saliency source is invalid")
    if not _finite_number(threshold) or not 0 <= threshold <= 1:
        raise ValueError("FY-4B parameter saliency threshold is invalid")
    return {"enabled": True, "source": source, "threshold": float(threshold),
            "windowSize": _odd_window(value.get("windowSize", 3), "saliency")}


def _odd_window(value: Any, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 3 or value % 2 == 0:
        raise ValueError(f"FY-4B parameter {name} windowSize is invalid")
    return value


def _finite_number(value: Any) -> bool:
    return not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value)
