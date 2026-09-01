from __future__ import annotations

import re
from datetime import datetime
from pathlib import Path
from typing import Any

import h5py
import numpy as np

from .base import build_plan as _legacy_build_plan
from .base import input_path_names
from ..fy4b_agri_profile import parse_fy4b_parameters, snapshot


PROFILE_KEY = {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}
ROLE_ORDER = ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M")
CHANNELS_BY_ROLE = {
    "L1_0500M": (2,), "L1_1000M": (1, 2, 3), "L1_2000M": tuple(range(1, 9)),
    "L1_4000M": tuple(range(1, 16)),
}
FULL_DISK_SHAPES = {
    "L1_0500M": (21984, 21984), "L1_1000M": (10992, 10992),
    "L1_2000M": (5496, 5496), "L1_4000M": (2748, 2748), "GEO_4000M": (2748, 2748),
}
_FILENAME = re.compile(
    r"^FY4B-_AGRI--_N_DISK_(?P<subsat>[^_]+)_L1-_(?P<kind>FDI|GEO)-_MULT_NOM_"
    r"(?P<start>\d{14})_(?P<end>\d{14})_(?P<resolution>0500M|500M|1000M|2000M|4000M)_"
    r"(?P<version>V[^.]+)\.HDF$", re.IGNORECASE,
)
_ROLE_BY_KIND_RESOLUTION = {
    ("FDI", "0500M"): "L1_0500M", ("FDI", "500M"): "L1_0500M",
    ("FDI", "1000M"): "L1_1000M", ("FDI", "2000M"): "L1_2000M",
    ("FDI", "4000M"): "L1_4000M", ("GEO", "4000M"): "GEO_4000M",
}
_GEO_FIELDS = ("LineNumber", "ColumnNumber", "NOMSatelliteZenith", "NOMSatelliteAzimuth",
               "NOMSunZenith", "NOMSunAzimuth")


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    path_names = input_path_names(manifest)
    paths = [path for path, _ in path_names]
    entries = manifest.get("inputs", [])
    if len(paths) < len(ROLE_ORDER):
        raise ValueError("FY-4B AGRI evidence requires a complete five-file package")
    classified = [_classify_file(path, original_name, entry)
                  for (path, original_name), entry in zip(path_names, entries)]
    by_role: dict[str, dict[str, Any]] = {}
    for item in classified:
        if item["role"] in by_role:
            raise ValueError("FY-4B AGRI package contains duplicate roles")
        by_role[item["role"]] = item
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
                metadata_by_role[role] = _validate_geo(handle) if role == "GEO_4000M" else _validate_l1(handle, role)
        except OSError as error:
            raise ValueError("FY-4B AGRI evidence is unreadable") from error
        if not _observation_is_within_nominal_interval(item, metadata_by_role[role]["observation"]):
            raise ValueError("FY-4B AGRI HDF observation is outside filename nominal interval")

    source = metadata_by_role[ROLE_ORDER[0]]
    for role in ROLE_ORDER:
        metadata = metadata_by_role[role]
        if metadata["identity"] != source["identity"] or metadata["observation"] != source["observation"]:
            raise ValueError("matching FY-4B AGRI five-file package metadata is required")
    return [{
        "packageId": "_".join(identity), "profileKey": PROFILE_KEY.copy(),
        "dataShape": "GEOSTATIONARY_RASTER", "supportStatus": "SUPPORTED",
        "files": [{"role": role, "ordinal": index, "path": str(by_role[role]["path"]),
                   "originalFileName": by_role[role]["originalFileName"], "required": True}
                  for index, role in enumerate(ROLE_ORDER)],
        "metadataSnapshot": {
            "subSatellite": reference["subsat"], "observingBeginning": source["observation"][0],
            "observingEnding": source["observation"][1], "version": reference["version"],
            "availableChannelsByResolution": {role: list(channels) for role, channels in CHANNELS_BY_ROLE.items()},
            "sourceDataQuality": source["sourceDataQuality"],
        }, "confidence": 0.95,
    }]


def build_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Retain the legacy public plan for the existing FY-4B smoke test."""
    del parameter_snapshot
    return _legacy_build_plan(product)


def build_integrated_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    _require_candidate(product)
    parameters = parse_fy4b_parameters(parameter_snapshot)
    outputs = [
        _output("FY4B_RED_500M_COG", "standard/0500m.tif"),
        _output("FY4B_RED_500M_QUALITY_COG", "standard/quality-0500m.tif"),
        _output("FY4B_VIS_NIR_1000M_COG", "standard/1000m.tif"),
        _output("FY4B_VIS_NIR_1000M_QUALITY_COG", "standard/quality-1000m.tif"),
        _output("FY4B_SPECTRAL_2000M_COG", "standard/2000m.tif"),
        _output("FY4B_SPECTRAL_2000M_QUALITY_COG", "standard/quality-2000m.tif"),
        _output("FY4B_MULTISPECTRAL_4000M_COG", "standard/4000m.tif"),
        _output("FY4B_MULTISPECTRAL_4000M_QUALITY_COG", "standard/quality-4000m.tif"),
        _output("FY4B_STANDARD_SUMMARY_JSON", "standard/summary.json"),
    ]
    if parameters["features"]["enabled"]:
        outputs.extend([_output("FY4B_FEATURE_COG", "features/fy4b-features.tif"),
                        _output("FY4B_FEATURE_SUMMARY_JSON", "features/summary.json")])
    if parameters["fusion"]["enabled"]:
        outputs.append(_output("FY4B_FALSE_COLOR_COG", "fusion/false-color.tif"))
    if parameters["saliency"]["enabled"]:
        outputs.extend([_output("FY4B_SALIENCY_SCORE_COG", "saliency/score.tif"),
                        _output("FY4B_SALIENCY_MASK_COG", "saliency/mask.tif"),
                        _output("FY4B_SALIENCY_SUMMARY_JSON", "saliency/summary.json")])
    return {"version": "plan-v2", "stages": [{
        "stageKey": "STANDARDIZE_FY4B_AGRI", "stageCode": "STANDARDIZE_FY4B_AGRI",
        "dependsOnStageKeys": [], "inputs": [_raw_input(role.lower(), role) for role in ROLE_ORDER],
        "outputs": outputs, "parameterSnapshot": snapshot(parameters),
    }]}


def _classify_file(path: Path, original_file_name: str, entry: Any) -> dict[str, Any]:
    if Path(original_file_name).suffix.upper() != ".HDF":
        raise ValueError("FY-4B AGRI evidence requires HDF files")
    match = _FILENAME.fullmatch(original_file_name)
    if match is None:
        raise ValueError("FY-4B AGRI evidence filename is invalid")
    kind, resolution = match["kind"].upper(), match["resolution"].upper()
    role = _ROLE_BY_KIND_RESOLUTION.get((kind, resolution))
    if role is None:
        raise ValueError("FY-4B AGRI evidence has an unsupported resolution or file role")
    declared_role = entry.get("role") if isinstance(entry, dict) else None
    if declared_role in ROLE_ORDER and declared_role != role:
        raise ValueError("FY-4B AGRI manifest role does not match the HDF product type")
    try:
        with h5py.File(path, "r") as handle:
            if _identity(handle) != ("FY-4B", "AGRI", "MULT", "DISK"):
                raise ValueError("FY-4B AGRI evidence has invalid source identity")
    except OSError as error:
        raise ValueError("FY-4B AGRI evidence is unreadable") from error
    return {"path": path, "originalFileName": original_file_name, "role": role,
            "subsat": match["subsat"], "start": match["start"],
            "end": match["end"], "version": match["version"]}


def _validate_l1(handle: h5py.File, role: str) -> dict[str, Any]:
    for channel in CHANNELS_BY_ROLE[role]:
        data_name, calibration_name = f"Data/NOMChannel{channel:02d}", f"Calibration/CALChannel{channel:02d}"
        if data_name not in handle or calibration_name not in handle:
            raise ValueError(f"FY-4B AGRI {role} is missing required dataset")
        if handle[data_name].ndim != 2 or handle[data_name].shape != FULL_DISK_SHAPES[role]:
            raise ValueError(f"FY-4B AGRI {role} has an invalid full-disk shape")
        if handle[calibration_name].size == 0:
            raise ValueError(f"FY-4B AGRI {role} has an invalid calibration dataset")
    if "NOMObs/NOMObsTime" not in handle:
        raise ValueError(f"FY-4B AGRI {role} is missing required dataset")
    return _root_metadata(handle)


def _validate_geo(handle: h5py.File) -> dict[str, Any]:
    shape = FULL_DISK_SHAPES["GEO_4000M"]
    for field in _GEO_FIELDS:
        path = f"Navigation/{field}"
        if path not in handle or handle[path].ndim != 2 or handle[path].shape != shape:
            raise ValueError("FY-4B AGRI GEO_4000M is missing required navigation dataset or shape")
    if "QA/NavQualityFlag" not in handle or handle["QA/NavQualityFlag"].shape != (15,):
        raise ValueError("FY-4B AGRI GEO_4000M is missing required navigation quality dataset")
    for field in ("LineNumber", "ColumnNumber"):
        dataset = handle[f"Navigation/{field}"]
        for start in range(0, shape[0], 256):
            values = dataset[start:start + 256, :]
            if np.any((values != -1) & ((values < 0) | (values > 2747))):
                raise ValueError("FY-4B AGRI GEO navigation values are out of range")
    return _root_metadata(handle)


def _root_metadata(handle: h5py.File) -> dict[str, Any]:
    beginning, ending = _iso_time(handle.attrs, "Beginning"), _iso_time(handle.attrs, "Ending")
    if beginning > ending:
        raise ValueError("FY-4B AGRI observation period has an end before beginning")
    return {"identity": _identity(handle), "observation": (beginning, ending),
            "sourceDataQuality": _scalar(handle.attrs.get("Data Quality"))}


def _identity(handle: h5py.File) -> tuple[str, str, str, str]:
    attrs = handle.attrs
    return (_text(attrs.get("Satellite Name")), _text(attrs.get("Sensor Name", attrs.get("Sensor Identification Code"))),
            _text(attrs.get("Dataset Name")), _text(attrs.get("OBIType")))


def _iso_time(attrs: h5py.AttributeManager, suffix: str) -> str:
    date, time = _text(attrs.get(f"Observing {suffix} Date")), _text(attrs.get(f"Observing {suffix} Time"))
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) or not re.fullmatch(r"\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?", time):
        raise ValueError("FY-4B AGRI evidence is missing observation time")
    try:
        datetime.fromisoformat(f"{date}T{time}")
    except ValueError as error:
        raise ValueError("FY-4B AGRI evidence has an invalid observation time") from error
    return f"{date}T{time}"


def _require_candidate(product: dict[str, Any]) -> None:
    files = product.get("files")
    if product.get("profileKey") != PROFILE_KEY or product.get("dataShape") != "GEOSTATIONARY_RASTER":
        raise ValueError("FY-4B AGRI candidate is required")
    if product.get("supportStatus") != "SUPPORTED" or not isinstance(files, list) or len(files) != len(ROLE_ORDER):
        raise ValueError("complete FY-4B AGRI candidate is required")
    if any(not isinstance(item, dict) for item in files):
        raise ValueError("complete FY-4B AGRI candidate is required")
    if [item.get("role") for item in files] != list(ROLE_ORDER):
        raise ValueError("complete FY-4B AGRI candidate is required")
    if any(item.get("ordinal") != index or isinstance(item.get("ordinal"), bool) or not isinstance(item.get("ordinal"), int)
           or not isinstance(item.get("path"), str) or not item["path"]
           for index, item in enumerate(files)):
        raise ValueError("complete FY-4B AGRI candidate is required")


def _raw_input(key: str, artifact_type: str) -> dict[str, Any]:
    return {"key": key, "source": "RAW_INPUT", "artifactType": artifact_type, "required": True}


def _output(artifact_type: str, relative_path: str) -> dict[str, Any]:
    return {"artifactType": artifact_type, "relativePath": relative_path, "retention": "FINAL"}


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


def _observation_is_within_nominal_interval(item: dict[str, Any], observation: tuple[str, str]) -> bool:
    nominal_start = datetime.strptime(item["start"], "%Y%m%d%H%M%S")
    nominal_end = datetime.strptime(item["end"], "%Y%m%d%H%M%S")
    actual_start, actual_end = (datetime.fromisoformat(value) for value in observation)
    return nominal_start <= actual_start <= actual_end <= nominal_end
