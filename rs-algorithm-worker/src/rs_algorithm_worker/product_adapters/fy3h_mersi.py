from __future__ import annotations

import re
from types import MappingProxyType
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from .base import build_plan, input_paths


PROFILE_KEY = {"productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"}
FILENAME = re.compile(
    r"^(?P<identity>FY3H_MERSI_.+)_(?P<kind>1000M|GEO1K)_(?P<version>[^_]+)\.HDF$",
    re.IGNORECASE,
)
REFLECTIVE_SOURCES = MappingProxyType({
    "Data/EV_250_Aggr.1KM_RefSB": (1, 2, 3, 4),
    "Data/EV_1KM_RefSB": tuple(range(5, 20)),
})
EMISSIVE_SOURCES = MappingProxyType({
    "Data/EV_1KM_Emissive": (20, 21, 22, 23),
    "Data/EV_250_Aggr.1KM_Emissive": (24, 25),
})
GEO_FIELDS = MappingProxyType({
    "latitude": "Geolocation/Latitude",
    "longitude": "Geolocation/Longitude",
    "solarZenith": "Geolocation/SolarZenith",
    "solarAzimuth": "Geolocation/SolarAzimuth",
    "sensorZenith": "Geolocation/SensorZenith",
    "sensorAzimuth": "Geolocation/SensorAzimuth",
    "altitude": "Geolocation/Altitude",
    "landSeaMask": "Geolocation/LandSeaMask",
    "landCover": "Geolocation/LandCover",
    "qaGeo": "Geolocation/QA_GEO",
    "dayNightFlag": "Timedata/DayNightFlag",
})
OBSERVATION_CHANNEL_SOURCES = MappingProxyType(
    dict(REFLECTIVE_SOURCES) | dict(EMISSIVE_SOURCES)
)
OBSERVATION_DATASETS = tuple(OBSERVATION_CHANNEL_SOURCES) + (
    "Calibration/VIS_Cal_Coeff",
    "Calibration/IR_Cal_Coeff",
    "QA/QA_Frame_Flag",
)
GEO_DATASETS = tuple(GEO_FIELDS.values())
GEO_NAVIGATION_DATASETS = tuple(GEO_FIELDS.values())[:9]
PAIR_ATTRIBUTES = (
    "Observing Beginning Date",
    "Observing Beginning Time",
    "Observing Ending Date",
    "Observing Ending Time",
    "Orbit Number",
    "Version Of Calibration Parameter",
    "Version Of Software",
)
CALIBRATION_ROOT_ATTRIBUTES = {
    "EarthSun Distance Ratio": 1,
    "Solar_Irradiance": 19,
    "Effect_Center_WaveLength": 25,
    "TBB_Trans_Coefficient": 12,
}


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    paths = input_paths(manifest)
    entries = manifest["inputs"]
    if len(paths) != 2 or any(path.suffix.upper() != ".HDF" for path in paths):
        raise ValueError("FY-3H MERSI evidence requires matching 1000M and GEO1K HDF files")

    classified = [_classify(path, entry) for path, entry in zip(paths, entries)]
    observations = [item for item in classified if item["kind"] == "1000M"]
    geos = [item for item in classified if item["kind"] == "GEO1K"]
    if len(observations) != 1 or len(geos) != 1:
        raise ValueError("matching 1000M and GEO1K files are required")

    observation, geo = observations[0], geos[0]
    if (observation["identity"], observation["version"]) != (geo["identity"], geo["version"]):
        raise ValueError("matching 1000M and GEO1K files are required")
    observation_metadata, observation_shape = _read_observation(observation["path"])
    geo_metadata, geo_shape = _read_geo(geo["path"])
    if observation_metadata != geo_metadata or observation_shape != geo_shape:
        raise ValueError("matching 1000M and GEO1K files are required")

    return [{
        "packageId": f"{observation['identity']}_{observation['version']}",
        "profileKey": PROFILE_KEY.copy(),
        "dataShape": "SWATH_RASTER",
        "supportStatus": "SUPPORTED",
        "files": [
            _file_entry("OBSERVATION", observation),
            _file_entry("GEO1K", geo),
        ],
        "metadataSnapshot": {
            "observingBeginning": f"{observation_metadata['Observing Beginning Date']}T"
                                 f"{observation_metadata['Observing Beginning Time']}",
            "observingEnding": f"{observation_metadata['Observing Ending Date']}T"
                              f"{observation_metadata['Observing Ending Time']}",
            "orbitNumber": observation_metadata["Orbit Number"],
            "version": observation["version"],
        },
        "confidence": 0.95,
    }]


def _classify(path: Path, entry: dict[str, Any]) -> dict[str, Any]:
    match = FILENAME.fullmatch(path.name)
    if match is None:
        raise ValueError("FY-3H MERSI evidence requires matching 1000M and GEO1K filenames")
    ordinal = entry.get("ordinal")
    if not isinstance(ordinal, int):
        raise ValueError("FY-3H MERSI evidence requires manifest ordinals")
    return {"path": path, "ordinal": ordinal, **match.groupdict()}


def _read_observation(path: Path) -> tuple[dict[str, Any], tuple[int, int]]:
    try:
        with h5py.File(path, "r") as handle:
            metadata = _root_metadata(handle)
            _validate_calibration_root_attributes(handle)
            _require_datasets(handle, OBSERVATION_DATASETS)
            shapes = [_spatial_shape(handle, dataset) for dataset in OBSERVATION_CHANNEL_SOURCES]
            _validate_observation_layout(handle, shapes[0])
    except OSError as error:
        raise ValueError("FY-3H MERSI evidence is unreadable") from error
    if len(set(shapes)) != 1:
        raise ValueError("FY-3H MERSI evidence has inconsistent observation dimensions")
    return metadata, shapes[0]


def _read_geo(path: Path) -> tuple[dict[str, Any], tuple[int, int]]:
    try:
        with h5py.File(path, "r") as handle:
            metadata = _root_metadata(handle)
            _require_datasets(handle, GEO_DATASETS)
            shapes = [_spatial_shape(handle, dataset) for dataset in GEO_NAVIGATION_DATASETS]
    except OSError as error:
        raise ValueError("FY-3H MERSI evidence is unreadable") from error
    if len(set(shapes)) != 1:
        raise ValueError("matching 1000M and GEO1K files are required")
    return metadata, shapes[0]


def _root_metadata(handle: h5py.File) -> dict[str, Any]:
    attrs = handle.attrs
    if _text(attrs.get("Satellite Name")) != "FY-3H" or _text(attrs.get("Sensor Identification Code")) != "MERSI":
        raise ValueError("FY-3H MERSI evidence is incomplete")
    metadata = {name: _scalar(attrs.get(name)) for name in PAIR_ATTRIBUTES}
    if any(value in {None, ""} for value in metadata.values()):
        raise ValueError("FY-3H MERSI evidence is incomplete")
    return metadata


def _require_datasets(handle: h5py.File, paths: tuple[str, ...]) -> None:
    if any(path not in handle for path in paths):
        raise ValueError("FY-3H MERSI evidence is incomplete")


def _validate_calibration_root_attributes(handle: h5py.File) -> None:
    for name, expected_length in CALIBRATION_ROOT_ATTRIBUTES.items():
        try:
            values = np.asarray(handle.attrs[name], dtype=float)
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError("FY-3H MERSI evidence has invalid calibration attributes") from error
        if values.shape != (expected_length,) or not np.all(np.isfinite(values)):
            raise ValueError("FY-3H MERSI evidence has invalid calibration attributes")


def _validate_observation_layout(handle: h5py.File, spatial_shape: tuple[int, int]) -> None:
    for path, channels in OBSERVATION_CHANNEL_SOURCES.items():
        if _dataset_shape(handle, path)[:1] != (len(channels),):
            raise ValueError("FY-3H MERSI evidence has invalid observation layout")
    rows = spatial_shape[0]
    if rows % 10:
        raise ValueError("FY-3H MERSI evidence has invalid observation layout")
    frames = rows // 10
    if _dataset_shape(handle, "Calibration/VIS_Cal_Coeff") != (19, 3):
        raise ValueError("FY-3H MERSI evidence has invalid observation layout")
    if _dataset_shape(handle, "Calibration/IR_Cal_Coeff") != (6, 4, frames):
        raise ValueError("FY-3H MERSI evidence has invalid observation layout")
    if _dataset_shape(handle, "QA/QA_Frame_Flag") != (frames,):
        raise ValueError("FY-3H MERSI evidence has invalid observation layout")


def _spatial_shape(handle: h5py.File, path: str) -> tuple[int, int]:
    shape = _dataset_shape(handle, path)
    if len(shape) < 2:
        raise ValueError("FY-3H MERSI evidence has invalid raster dimensions")
    return tuple(shape[-2:])


def _dataset_shape(handle: h5py.File, path: str) -> tuple[int, ...]:
    dataset = handle[path]
    if not isinstance(dataset, h5py.Dataset):
        raise ValueError("FY-3H MERSI evidence has invalid raster dimensions")
    return dataset.shape


def _file_entry(role: str, item: dict[str, Any]) -> dict[str, Any]:
    return {"role": role, "ordinal": item["ordinal"], "path": str(item["path"])}


def _text(value: Any) -> str:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, list):
        value = value[0] if value else ""
    return str(value)


def _scalar(value: Any) -> Any:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, list):
        return value[0] if value else None
    return value
