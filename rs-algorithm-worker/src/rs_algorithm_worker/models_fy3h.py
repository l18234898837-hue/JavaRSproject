from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping

import numpy as np


MODEL_VERSION = "fy3h-mersi-model-v1"

QUALITY_MISSING = np.uint32(1 << 0)
QUALITY_SATURATED = np.uint32(1 << 1)
QUALITY_DEAD_DETECTOR = np.uint32(1 << 2)
QUALITY_OUT_OF_RANGE = np.uint32(1 << 3)
QUALITY_FRAME_CHANNEL_FAILURE = np.uint32(1 << 4)
QUALITY_FRAME_PREPROCESS_FAILURE = np.uint32(1 << 5)
QUALITY_CALIBRATION_FAILURE = np.uint32(1 << 6)
QUALITY_GEOLOCATION_FAILURE = np.uint32(1 << 7)
QUALITY_INVALID_NAVIGATION = np.uint32(1 << 8)
QUALITY_NON_DAYLIGHT = np.uint32(1 << 9)


@dataclass(frozen=True)
class QualityMask:
    valid: np.ndarray
    reason_bits: np.ndarray

    def __post_init__(self) -> None:
        if self.valid.dtype != np.dtype(bool) or self.reason_bits.dtype != np.dtype(np.uint32):
            raise ValueError("FY-3H MERSI quality mask requires bool and uint32 arrays")
        if self.valid.shape != self.reason_bits.shape:
            raise ValueError("FY-3H MERSI quality mask dimensions must match")


@dataclass(frozen=True)
class DecodedMersi:
    dn_by_channel: Mapping[int, np.ndarray]
    longitude: np.ndarray
    latitude: np.ndarray
    solar_zenith_degrees: np.ndarray
    sensor_zenith_degrees: np.ndarray
    frame_flags: np.ndarray
    geo_flags: np.ndarray
    scan_index: np.ndarray
    metadata: Mapping[str, Any] = field(default_factory=dict)
    calibration: Mapping[str, np.ndarray] = field(default_factory=dict)
    navigation: Mapping[str, np.ndarray] = field(default_factory=dict)

    def __post_init__(self) -> None:
        shape = self.latitude.shape
        required = (self.longitude, self.solar_zenith_degrees, self.sensor_zenith_degrees)
        if self.latitude.ndim != 2 or any(value.shape != shape for value in required):
            raise ValueError("decoded MERSI coordinates must be matching two-dimensional arrays")
        if not self.dn_by_channel or any(not 1 <= int(channel) <= 25 for channel in self.dn_by_channel):
            raise ValueError("decoded MERSI requires channels 1 through 25")
        if any(np.asarray(values).shape != shape for values in self.dn_by_channel.values()):
            raise ValueError("decoded MERSI channel dimensions must match coordinates")
        if self.scan_index.ndim != 1 or self.scan_index.size != shape[0]:
            raise ValueError("decoded MERSI scan index must have one entry per row")


@dataclass(frozen=True)
class CalibratedMersi:
    reflectance_by_channel: Mapping[int, np.ndarray]
    radiance_by_channel: Mapping[int, np.ndarray]
    brightness_temperature_by_channel: Mapping[int, np.ndarray]
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        groups = (self.reflectance_by_channel, self.radiance_by_channel, self.brightness_temperature_by_channel)
        arrays = [np.asarray(value) for group in groups for value in group.values()]
        if not arrays or any(value.ndim != 2 for value in arrays):
            raise ValueError("calibrated MERSI requires two-dimensional calibrated channels")
        shape = arrays[0].shape
        if any(value.shape != shape for value in arrays):
            raise ValueError("calibrated MERSI channel dimensions must match")


def write_decoded_mersi(path: Path, value: DecodedMersi) -> Path:
    arrays: dict[str, np.ndarray] = {
        "longitude": value.longitude,
        "latitude": value.latitude,
        "solar_zenith_degrees": value.solar_zenith_degrees,
        "sensor_zenith_degrees": value.sensor_zenith_degrees,
        "frame_flags": value.frame_flags,
        "geo_flags": value.geo_flags,
        "scan_index": value.scan_index,
    }
    arrays.update({f"dn__{channel}": values for channel, values in value.dn_by_channel.items()})
    arrays.update({f"calibration__{name}": values for name, values in value.calibration.items()})
    arrays.update({f"navigation__{name}": values for name, values in value.navigation.items()})
    return _write(path, arrays, "decoded", value.metadata)


def read_decoded_mersi(path: Path) -> DecodedMersi:
    arrays, metadata = _read(path, "decoded")
    required = {"longitude", "latitude", "solar_zenith_degrees", "sensor_zenith_degrees", "frame_flags", "geo_flags", "scan_index"}
    if not required <= arrays.keys():
        raise ValueError("decoded MERSI is missing required coordinate or metadata arrays")
    channels = {int(name.removeprefix("dn__")): values for name, values in arrays.items() if name.startswith("dn__")}
    if not channels:
        raise ValueError("decoded MERSI is missing channel arrays")
    return DecodedMersi(channels, arrays["longitude"], arrays["latitude"], arrays["solar_zenith_degrees"],
                        arrays["sensor_zenith_degrees"], arrays["frame_flags"], arrays["geo_flags"], arrays["scan_index"],
                        metadata, {name.removeprefix("calibration__"): values for name, values in arrays.items() if name.startswith("calibration__")},
                        {name.removeprefix("navigation__"): values for name, values in arrays.items() if name.startswith("navigation__")})


def write_calibrated_mersi(path: Path, value: CalibratedMersi) -> Path:
    arrays: dict[str, np.ndarray] = {}
    arrays.update({f"reflectance__{channel}": values for channel, values in value.reflectance_by_channel.items()})
    arrays.update({f"radiance__{channel}": values for channel, values in value.radiance_by_channel.items()})
    arrays.update({f"brightness_temperature__{channel}": values for channel, values in value.brightness_temperature_by_channel.items()})
    return _write(path, arrays, "calibrated", value.metadata)


def read_calibrated_mersi(path: Path) -> CalibratedMersi:
    arrays, metadata = _read(path, "calibrated")
    groups = tuple({int(name.removeprefix(prefix)): values for name, values in arrays.items() if name.startswith(prefix)}
                   for prefix in ("reflectance__", "radiance__", "brightness_temperature__"))
    return CalibratedMersi(*groups, metadata)


def _write(path: Path, arrays: Mapping[str, np.ndarray], model: str, metadata: Mapping[str, Any]) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    document = {"modelVersion": MODEL_VERSION, "model": model, "metadata": _json_value(metadata)}
    # These archives only connect stages inside one local execution. Deflate
    # dominated real 25-band runtimes and the integrated stage removes them
    # after final artifacts are written, so compression has no storage value.
    np.savez(path, **arrays, metadata_json=np.asarray(json.dumps(document, ensure_ascii=True, sort_keys=True)))
    path.with_suffix(".json").write_text(json.dumps(document, ensure_ascii=True, sort_keys=True, allow_nan=False), encoding="utf-8")
    return path


def _read(path: Path, model: str) -> tuple[dict[str, np.ndarray], dict[str, Any]]:
    metadata_path = path.with_suffix(".json")
    try:
        with np.load(path, allow_pickle=False) as data:
            embedded = str(data["metadata_json"]) if "metadata_json" in data else None
            arrays = {name: data[name] for name in data.files if name != "metadata_json"}
        document = json.loads(embedded) if embedded is not None else json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        raise ValueError("FY-3H MERSI metadata is unreadable") from error
    if not isinstance(document, dict) or document.get("modelVersion") != MODEL_VERSION or document.get("model") != model:
        raise ValueError("FY-3H MERSI metadata version is unsupported")
    metadata = document.get("metadata")
    if not isinstance(metadata, dict):
        raise ValueError("FY-3H MERSI metadata is invalid")
    return arrays, metadata


def _json_value(value: Any) -> Any:
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, Mapping):
        return {str(key): _json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_value(item) for item in value]
    return value
