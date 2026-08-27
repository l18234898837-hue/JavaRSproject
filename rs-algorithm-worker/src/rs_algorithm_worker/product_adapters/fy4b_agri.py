from __future__ import annotations

import re
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from .base import input_paths


PROFILE_KEY = {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}
ROLE_ORDER = ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M")
CHANNELS_BY_ROLE = {
    "L1_0500M": (2,),
    "L1_1000M": (1, 2, 3),
    "L1_2000M": tuple(range(1, 9)),
    "L1_4000M": tuple(range(1, 16)),
}
FULL_DISK_SHAPES = {
    "L1_0500M": (21984, 21984),
    "L1_1000M": (10992, 10992),
    "L1_2000M": (5496, 5496),
    "L1_4000M": (2748, 2748),
    "GEO_4000M": (2748, 2748),
}

_FILENAME = re.compile(
    r"^FY4B-_AGRI--_N_DISK_(?P<subsat>[^_]+)_L1-_(?P<kind>FDI|GEO)-_MULT_NOM_"
    r"(?P<start>\d{14})_(?P<end>\d{14})_(?P<resolution>0500M|500M|1000M|2000M|4000M)_"
    r"(?P<version>V[^.]+)\.HDF$",
    re.IGNORECASE,
)
_ROLE_BY_KIND_RESOLUTION = {
    ("FDI", "0500M"): "L1_0500M",
    ("FDI", "500M"): "L1_0500M",
    ("FDI", "1000M"): "L1_1000M",
    ("FDI", "2000M"): "L1_2000M",
    ("FDI", "4000M"): "L1_4000M",
    ("GEO", "4000M"): "GEO_4000M",
}
_GEO_FIELDS = (
    "LineNumber", "ColumnNumber", "NOMSatelliteZenith", "NOMSatelliteAzimuth",
    "NOMSunZenith", "NOMSunAzimuth",
)


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    return [inspect_package(input_paths(manifest))]


def inspect_package(paths: list[Path] | tuple[Path, ...]) -> dict[str, Any]:
    """Inspect only paths authorized by the caller's manifest."""
    if not isinstance(paths, (list, tuple)) or len(paths) != len(ROLE_ORDER):
        raise ValueError("FY-4B AGRI evidence requires a complete five-file package")
    if any(not isinstance(path, Path) or path.suffix.upper() != ".HDF" for path in paths):
        raise ValueError("FY-4B AGRI evidence requires HDF files")

    classified = [_classify_file(path) for path in paths]
    by_role: dict[str, dict[str, Any]] = {}
    for item in classified:
        role = item["role"]
        if role in by_role:
            raise ValueError("FY-4B AGRI package contains duplicate roles")
        by_role[role] = item
    if set(by_role) != set(ROLE_ORDER):
        raise ValueError("FY-4B AGRI evidence requires a complete five-file package")

    reference = by_role[ROLE_ORDER[0]]
    identity = (reference["subsat"], reference["start"], reference["end"], reference["version"])
    metadata_by_role: dict[str, dict[str, Any]] = {}
    for role in ROLE_ORDER:
        item = by_role[role]
        if (item["subsat"], item["start"], item["end"], item["version"]) != identity:
            raise ValueError("matching FY-4B AGRI five-file package metadata is required")
        try:
            with h5py.File(item["path"], "r") as handle:
                metadata_by_role[role] = (
                    _validate_geo(handle, role) if role == "GEO_4000M"
                    else _validate_l1(handle, role)
                )
        except OSError as error:
            raise ValueError("FY-4B AGRI evidence is unreadable") from error

    source_identity = metadata_by_role[ROLE_ORDER[0]]["identity"]
    source_time = (
        metadata_by_role[ROLE_ORDER[0]]["observingBeginning"],
        metadata_by_role[ROLE_ORDER[0]]["observingEnding"],
    )
    for role in ROLE_ORDER:
        item_metadata = metadata_by_role[role]
        if item_metadata["identity"] != source_identity or (
            item_metadata["observingBeginning"], item_metadata["observingEnding"]
        ) != source_time:
            raise ValueError("matching FY-4B AGRI five-file package metadata is required")

    return {
        "packageId": "_".join(identity),
        "profileKey": PROFILE_KEY.copy(),
        "dataShape": "GEOSTATIONARY_RASTER",
        "supportStatus": "SUPPORTED",
        "files": [
            {"role": role, "ordinal": index, "path": str(by_role[role]["path"])}
            for index, role in enumerate(ROLE_ORDER)
        ],
        "metadataSnapshot": {
            "subSatellite": reference["subsat"],
            "observingBeginning": source_time[0],
            "observingEnding": source_time[1],
            "version": reference["version"],
            "availableChannelsByResolution": {
                role: list(CHANNELS_BY_ROLE[role]) for role in CHANNELS_BY_ROLE
            },
            "sourceDataQuality": metadata_by_role["L1_0500M"].get("sourceDataQuality"),
        },
        "confidence": 0.95,
    }


def _classify_file(path: Path) -> dict[str, Any]:
    match = _FILENAME.fullmatch(path.name)
    if match is None:
        raise ValueError("FY-4B AGRI evidence filename is invalid")
    kind = match["kind"].upper()
    resolution = match["resolution"].upper()
    role = _ROLE_BY_KIND_RESOLUTION.get((kind, resolution))
    if role is None:
        raise ValueError("FY-4B AGRI evidence has an unsupported resolution or file role")
    try:
        with h5py.File(path, "r") as handle:
            attrs = handle.attrs
            identity = (
                _text(attrs.get("Satellite Name")),
                _text(attrs.get("Sensor Name", attrs.get("Sensor Identification Code"))),
                _text(attrs.get("Dataset Name")),
                _text(attrs.get("OBIType")),
            )
            if identity != ("FY-4B", "AGRI", "MULT", "DISK"):
                raise ValueError("FY-4B AGRI evidence has invalid source identity")
    except OSError as error:
        raise ValueError("FY-4B AGRI evidence is unreadable") from error
    return {
        "path": path, "role": role, "kind": kind, "resolution": resolution,
        "subsat": match["subsat"], "start": match["start"], "end": match["end"],
        "version": match["version"],
    }


def _validate_l1(handle: h5py.File, role: str) -> dict[str, Any]:
    shape = FULL_DISK_SHAPES[role]
    for channel in CHANNELS_BY_ROLE[role]:
        data_name = f"Data/NOMChannel{channel:02d}"
        calibration_name = f"Calibration/CALChannel{channel:02d}"
        if data_name not in handle or calibration_name not in handle:
            raise ValueError(f"FY-4B AGRI {role} is missing required dataset")
        dataset = handle[data_name]
        if dataset.ndim != 2 or dataset.shape != shape:
            raise ValueError(f"FY-4B AGRI {role} has an invalid full-disk shape")
        if handle[calibration_name].size == 0:
            raise ValueError(f"FY-4B AGRI {role} has an invalid calibration dataset")
    if "NOMObs/NOMObsTime" not in handle:
        raise ValueError(f"FY-4B AGRI {role} is missing required dataset")
    return _root_metadata(handle)


def _validate_geo(handle: h5py.File, role: str = "GEO_4000M") -> dict[str, Any]:
    shape = FULL_DISK_SHAPES[role]
    for name in _GEO_FIELDS:
        path = f"Navigation/{name}"
        if path not in handle or handle[path].ndim != 2 or handle[path].shape != shape:
            raise ValueError("FY-4B AGRI GEO_4000M is missing required navigation dataset or shape")
    if "QA/NavQualityFlag" not in handle or handle["QA/NavQualityFlag"].shape != (15,):
        raise ValueError("FY-4B AGRI GEO_4000M is missing required navigation quality dataset")
    # Operational files use -1 for fill and valid values in the 0..2747 range.
    # Read bounded row windows so validation never materializes a full GEO grid.
    for name in ("LineNumber", "ColumnNumber"):
        dataset = handle[f"Navigation/{name}"]
        for row_start in range(0, shape[0], 256):
            values = dataset[row_start:row_start + 256, :]
            if np.any((values != -1) & ((values < 0) | (values > 2747))):
                raise ValueError("FY-4B AGRI GEO navigation values are out of range")
    return _root_metadata(handle)


def _root_metadata(handle: h5py.File) -> dict[str, Any]:
    attrs = handle.attrs
    return {
        "identity": (
            _text(attrs.get("Satellite Name")),
            _text(attrs.get("Sensor Name", attrs.get("Sensor Identification Code"))),
            _text(attrs.get("Dataset Name")),
            _text(attrs.get("OBIType")),
        ),
        "observingBeginning": _iso_time(attrs, "Beginning"),
        "observingEnding": _iso_time(attrs, "Ending"),
        "sourceDataQuality": _scalar(attrs.get("Data Quality")),
    }


def _iso_time(attrs: h5py.AttributeManager, suffix: str) -> str:
    date = _text(attrs.get(f"Observing {suffix} Date"))
    time = _text(attrs.get(f"Observing {suffix} Time"))
    if not date or not time:
        raise ValueError("FY-4B AGRI evidence is missing observation time")
    return f"{date}T{time}"


def _scalar(value: Any) -> Any:
    if isinstance(value, np.ndarray):
        value = value.tolist()
    if isinstance(value, list):
        return value[0] if len(value) == 1 else value
    return value


def _text(value: Any) -> str:
    value = _scalar(value)
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace").rstrip("\x00")
    return str(value) if value is not None else ""


def build_plan(product: dict[str, Any]) -> dict[str, Any]:
    if product.get("profileKey") != PROFILE_KEY or product.get("dataShape") != "GEOSTATIONARY_RASTER":
        raise ValueError("FY-4B AGRI candidate is required")
    if product.get("supportStatus") != "SUPPORTED" or [item.get("role") for item in product.get("files", [])] != list(ROLE_ORDER):
        raise ValueError("complete FY-4B AGRI candidate is required")
    return {"version": "plan-v1", "stages": [
        {"stageKey": "VALIDATE_SOURCE", "stageCode": "VALIDATE_SOURCE", "dependsOnStageKeys": []},
        {"stageKey": "EXTRACT_FEATURES", "stageCode": "EXTRACT_FEATURES", "dependsOnStageKeys": ["VALIDATE_SOURCE"]},
    ]}
