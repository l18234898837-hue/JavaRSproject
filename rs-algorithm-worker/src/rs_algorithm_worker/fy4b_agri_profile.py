from __future__ import annotations

import json
import math
from typing import Any

DEFAULT_PARAMETERS = {
    "schemaVersion": "1",
    "targetCrs": "EPSG:4326",
    "roi": None,
    "features": {"enabled": False, "metrics": ["statistics"], "windowSize": 3},
    "fusion": {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]},
    "saliency": {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3},
}
_TOP = {"schemaVersion", "targetCrs", "roi", "features", "fusion", "saliency"}
_METRICS = {"statistics", "gradient", "local_variance", "toa_nir_red_nd", "btd_c13_c14"}
_SOURCES = {"C02_REFLECTANCE_500M", "C03_REFLECTANCE_1000M", "C13_BRIGHTNESS_TEMPERATURE_4000M", "toa_nir_red_nd", "btd_c13_c14"}

def parse_fy4b_parameters(raw: dict[str, Any] | None) -> dict[str, Any]:
    if raw is None: raw = {}
    if not isinstance(raw, dict) or set(raw) - _TOP:
        raise ValueError("FY-4B parameter object is invalid")
    schema = raw.get("schemaVersion", "1")
    if schema != "1": raise ValueError("FY-4B parameter schemaVersion is invalid")
    crs = raw.get("targetCrs", "EPSG:4326")
    if crs not in {"EPSG:4326", "EPSG:4490"}: raise ValueError("FY-4B parameter targetCrs is invalid")
    roi = raw.get("roi")
    if roi is not None:
        if not isinstance(roi, (list, tuple)) or len(roi) != 4 or any(isinstance(v, bool) or not isinstance(v, (int,float)) or not math.isfinite(v) for v in roi):
            raise ValueError("FY-4B parameter roi is invalid")
        roi = [float(v) for v in roi]
        if not (-180 <= roi[0] < roi[2] <= 180 and -90 <= roi[1] < roi[3] <= 90): raise ValueError("FY-4B parameter roi is invalid")
    features = _features(raw.get("features", {}))
    fusion = _fusion(raw.get("fusion", {}))
    saliency = _saliency(raw.get("saliency", {}), features)
    return {"schemaVersion": "1", "targetCrs": crs, "roi": roi, "features": features, "fusion": fusion, "saliency": saliency}

def _odd(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 3 or value % 2 == 0: raise ValueError(f"FY-4B parameter {label} windowSize is invalid")
    return value

def _features(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "metrics", "windowSize"}: raise ValueError("FY-4B parameter features is invalid")
    enabled = value.get("enabled", False)
    metrics = value.get("metrics", ["statistics"])
    if not isinstance(enabled, bool) or not isinstance(metrics, list) or len(set(metrics)) != len(metrics) or any(m not in _METRICS for m in metrics): raise ValueError("FY-4B parameter features is invalid")
    if enabled and not metrics: raise ValueError("FY-4B parameter features requires metrics")
    result = {"enabled": enabled, "metrics": list(metrics)}
    if "local_variance" in metrics: result["windowSize"] = _odd(value.get("windowSize", 3), "features")
    return result

def _fusion(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "mode", "percentiles"}: raise ValueError("FY-4B parameter fusion is invalid")
    enabled = value.get("enabled", False)
    if not isinstance(enabled, bool): raise ValueError("FY-4B parameter fusion is invalid")
    if not enabled: return {"enabled": False, "mode": "NIR_RED_BLUE", "percentiles": [2.0, 98.0]}
    if value.get("mode", "NIR_RED_BLUE") != "NIR_RED_BLUE": raise ValueError("FY-4B parameter fusion mode is invalid")
    p = value.get("percentiles", [2.0, 98.0])
    if not isinstance(p, (list, tuple)) or len(p) != 2 or any(isinstance(v, bool) or not isinstance(v, (int,float)) or not math.isfinite(v) for v in p) or not 0 <= p[0] < p[1] <= 100: raise ValueError("FY-4B parameter percentiles are invalid")
    return {"enabled": True, "mode": "NIR_RED_BLUE", "percentiles": [float(p[0]), float(p[1])]}

def _saliency(value: Any, features: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) - {"enabled", "source", "threshold", "windowSize"}: raise ValueError("FY-4B parameter saliency is invalid")
    enabled = value.get("enabled", False)
    if not isinstance(enabled, bool): raise ValueError("FY-4B parameter saliency is invalid")
    if not enabled: return {"enabled": False, "source": "C02_REFLECTANCE_500M", "threshold": 0.5, "windowSize": 3}
    source = value.get("source", "C02_REFLECTANCE_500M")
    feature_sources = {f"{m}:C02_REFLECTANCE_500M" for m in features.get("metrics", []) if m in {"gradient", "local_variance"}}
    feature_sources |= {m for m in features.get("metrics", []) if m in {"toa_nir_red_nd", "btd_c13_c14"}}
    if source not in _SOURCES and source not in feature_sources: raise ValueError("FY-4B parameter saliency source is invalid")
    threshold = value.get("threshold", 0.5)
    if isinstance(threshold, bool) or not isinstance(threshold, (int,float)) or not math.isfinite(threshold) or not 0 <= threshold <= 1: raise ValueError("FY-4B parameter saliency threshold is invalid")
    return {"enabled": True, "source": source, "threshold": float(threshold), "windowSize": _odd(value.get("windowSize", 3), "saliency")}

def snapshot(parameters: dict[str, Any]) -> str:
    return json.dumps(parameters, sort_keys=True, separators=(",", ":"))

def raw_input(key: str, artifact_type: str) -> dict[str, Any]: return {"key": key, "source": "RAW_INPUT", "artifactType": artifact_type, "required": True}
def artifact_input(key: str, artifact_type: str) -> dict[str, Any]: return {"key": key, "source": "UPSTREAM_ARTIFACT", "artifactType": artifact_type, "required": True}
def output(artifact_type: str, relative_path: str, retention: str) -> dict[str, Any]: return {"artifactType": artifact_type, "relativePath": relative_path, "retention": retention}
def stage(key: str, deps: list[str], inputs: list[dict[str, Any]], outputs: list[dict[str, Any]], params: str) -> dict[str, Any]: return {"stageKey": key, "stageCode": key, "dependsOnStageKeys": deps, "inputs": inputs, "outputs": outputs, "parameterSnapshot": params}
