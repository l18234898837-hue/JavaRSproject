from __future__ import annotations

import json
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import rasterio
from rasterio.crs import CRS

from ..gf6_processing import GF6_MAX_WINDOW_SIZE
from ..models_gf6 import Gf6BandSpec, Gf6ProductEvidence, Gf6RpcSpec, Gf6TileSpec
from ..operators.gf6_rpc import validate_rpc_output_resolution
from .base import input_path_names, logical_file_name


PRODUCT_CODE = "GF-6-WFV-L1A"
PROFILE_VERSION = "v1"
PROFILE_KEY = {"productCode": PRODUCT_CODE, "profileVersion": PROFILE_VERSION}
_WAVELENGTHS = ("450-520nm", "520-590nm", "630-690nm", "770-890nm", "690-730nm", "730-770nm", "400-450nm", "590-630nm")
_RPC_FIELDS = {"lineOffset": "line_offset", "sampOffset": "sample_offset", "latOffset": "latitude_offset", "longOffset": "longitude_offset", "heightOffset": "height_offset", "lineScale": "line_scale", "sampScale": "sample_scale", "latScale": "latitude_scale", "longScale": "longitude_scale", "heightScale": "height_scale", "lineNumCoef": "line_num", "lineDenCoef": "line_den", "sampNumCoef": "sample_num", "sampDenCoef": "sample_den"}


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    path_names = input_path_names(manifest)
    names = {str(path): name for path, name in path_names}
    evidence = inspect_paths([path for path, _ in path_names], names)
    files = [{"role": "MAIN_TIFF_TILE", "ordinal": tile.ordinal - 1, "path": str(tile.path),
              "originalFileName": names[str(tile.path)], "required": True} for tile in evidence.tiles]
    files.extend({"role": role, "ordinal": 0, "path": str(path),
                  "originalFileName": names[str(path)], "required": True}
                 for role, path in (("METADATA_XML", evidence.xml_path), ("RPC_RPB", evidence.rpc_path),
                                    ("NAV", evidence.nav_path), ("TILE_INDEX_TIL", evidence.til_path)))
    files.extend({"role": role, "ordinal": 0, "path": str(path),
                  "originalFileName": names[str(path)], "required": False}
                 for role, path in zip(("FOOTPRINT_SHP", "FOOTPRINT_DBF", "FOOTPRINT_SHX"), evidence.footprint_paths))
    return [{"packageId": evidence.package_id, "profileKey": PROFILE_KEY.copy(), "dataShape": "RASTER_GRID", "supportStatus": "SUPPORTED", "files": files, "metadataSnapshot": evidence.metadata_snapshot, "confidence": 0.99}]


def inspect_evidence(manifest: dict[str, Any]) -> Gf6ProductEvidence:
    path_names = input_path_names(manifest)
    return inspect_paths([path for path, _ in path_names], {str(path): name for path, name in path_names})


def inspect_paths(paths: list[Path], original_file_names: dict[str, str] | None = None) -> Gf6ProductEvidence:
    names = {str(path): (original_file_names or {}).get(str(path), path.name) for path in paths}
    xml = _single_product_xml(paths, names)
    root = ET.parse(xml).getroot()
    value = lambda name: (root.findtext(name) or "").strip()
    if (value("SatelliteID"), value("SensorID"), value("ProductLevel")) != ("GF6", "WFV", "LEVEL1A"):
        raise ValueError("GF6_EVIDENCE_INVALID: XML product identity must be GF6/WFV/LEVEL1A")
    product_id = value("ProductID")
    if not product_id:
        raise ValueError("GF6_EVIDENCE_INVALID: XML product ID is missing")
    base = _shared_base(paths, names)
    tiffs = sorted((path for path in paths if _suffix(path, names) in {".tif", ".tiff"}),
                   key=lambda path: _tile_ordinal(_name(path, names)))
    if (len(tiffs) != 3 or [_tile_ordinal(_name(path, names)) for path in tiffs] != [1, 2, 3]
            or any(_tile_base(_name(path, names)) != base for path in tiffs)):
        raise ValueError("GF6_EVIDENCE_INVALID: TIFFs must be exactly ordered -1/-2/-3 tiles")
    rpbs = [path for path in paths if _suffix(path, names) == ".rpb" and _tile_ordinal(_name(path, names)) is None]
    if len(rpbs) != 1 or _plain_base(_name(rpbs[0], names)) != base:
        raise ValueError("GF6_EVIDENCE_INVALID: main basename RPC RPB is required")
    nav = _single_suffix(paths, ".nav", "NAV", names)
    til = _single_suffix(paths, ".til", "TIL", names)
    if (_plain_base(_name(nav, names)) != base or _plain_base(_name(til, names)) != base
            or _plain_base(_name(xml, names)) != base):
        raise ValueError("GF6_EVIDENCE_INVALID: raw input basenames must match")
    footprint_paths = _validate_footprint(paths, base, names)
    til_entries = _parse_til(til)
    if [Path(item[0]).name.lower() for item in til_entries] != [_name(path, names).lower() for path in tiffs]:
        raise ValueError("GF6_EVIDENCE_INVALID: TIL tile order does not match TIFF order")
    tiles = []
    for ordinal, (path, (_, col, row)) in enumerate(zip(tiffs, til_entries), 1):
        with rasterio.open(path) as source:
            if source.count != 8 or tuple(source.dtypes) != ("uint16",) * 8:
                raise ValueError("GF6_EVIDENCE_INVALID: each TIFF must contain eight UInt16 bands")
            tiles.append(Gf6TileSpec(path, ordinal, source.width, source.height, col, row))
    gains, offsets = _float_list(value("AGain"), "AGain"), _float_list(value("AOffset"), "AOffset")
    if len(gains) != 8 or len(offsets) != 8:
        raise ValueError("GF6_EVIDENCE_INVALID: XML must contain eight gains and offsets")
    bands = tuple(Gf6BandSpec(i, _WAVELENGTHS[i - 1], gains[i - 1], offsets[i - 1]) for i in range(1, 9))
    coverage = [max(tile.column_offset + tile.width for tile in tiles), max(tile.row_offset + tile.height for tile in tiles)]
    declared = [_integer(value("WidthInPixels"), "WidthInPixels"), _integer(value("HeightInPixels"), "HeightInPixels")]
    footprint = [[float(value(f"{corner}Longitude")), float(value(f"{corner}Latitude"))] for corner in ("TopLeft", "TopRight", "BottomRight", "BottomLeft")]
    footprint.append(footprint[0])
    metadata = {"satellite": "GF6", "sensor": "WFV", "productLevel": "LEVEL1A", "productId": product_id, "dimensions": coverage, "declaredDimensions": declared, "tileCoverageDimensions": coverage, "tileCoverageDelta": [coverage[0] - declared[0], coverage[1] - declared[1]], "bands": [{"number": band.number, "wavelength": band.wavelength} for band in bands], "footprint": footprint, "calibrationUnit": "W.m-2.sr-1.um-1", "navigationStatus": "NOMINAL_RPC_NO_DEM", "accuracyStatus": "UNVALIDATED"}
    return Gf6ProductEvidence(f"GF6-WFV-L1A-{product_id}", product_id, xml, rpbs[0], nav, til, tuple(tiles), bands, parse_rpc(rpbs[0]), metadata, footprint_paths)


def parse_rpc(path: Path) -> Gf6RpcSpec:
    text = path.read_text(encoding="utf-8", errors="replace")
    values: dict[str, Any] = {}
    for name, target in _RPC_FIELDS.items():
        match = re.search(rf"\b{re.escape(name)}\s*=\s*(?:\(([^)]*)\)|([^;]+));", text, flags=re.I | re.S)
        if not match:
            raise ValueError(f"GF6_EVIDENCE_INVALID: RPC field {name} is missing")
        numbers = tuple(float(token) for token in re.findall(r"[-+]?\d*\.?\d+(?:[Ee][-+]?\d+)?", (match.group(1) or match.group(2))))
        if not numbers or not all(math.isfinite(number) for number in numbers):
            raise ValueError(f"GF6_EVIDENCE_INVALID: RPC field {name} is invalid")
        values[target] = numbers if "Coef" in name else numbers[0]
    return Gf6RpcSpec(**values)


def parse_parameters(raw: dict[str, Any] | None) -> dict[str, Any]:
    raw = {} if raw is None else raw
    if not isinstance(raw, dict):
        raise ValueError("GF6_PARAMETER_INVALID: parameters")
    allowed = {"schemaVersion", "targetCrs", "resolution", "rpcHeightMode", "rpcHeight", "roi", "bands", "features", "fusion", "saliency"}
    unknown = set(raw) - allowed
    if unknown:
        raise ValueError("GF6_PARAMETER_INVALID: unknown fields " + ", ".join(sorted(unknown)))
    if raw.get("schemaVersion", "gf6-wfv-l1a-v1") != "gf6-wfv-l1a-v1":
        raise ValueError("GF6_PARAMETER_INVALID: schemaVersion")
    target_crs = _canonical_crs(raw.get("targetCrs", "EPSG:4326"))
    resolution = _positive(raw.get("resolution", 0.00015), "resolution")
    validate_rpc_output_resolution(target_crs, resolution)
    if raw.get("rpcHeightMode", "CONSTANT") != "CONSTANT":
        raise ValueError("GF6_PARAMETER_INVALID: rpcHeightMode")
    if not _is_number(raw.get("rpcHeight", 0.0)) or raw.get("rpcHeight", 0.0) != 0:
        raise ValueError("GF6_PARAMETER_INVALID: rpcHeight")
    roi = raw.get("roi")
    if roi is not None and (not isinstance(roi, list) or len(roi) != 4 or not all(_is_number(v) for v in roi) or roi[2] <= roi[0] or roi[3] <= roi[1]):
        raise ValueError("GF6_PARAMETER_INVALID: roi")
    bands = raw.get("bands", list(range(1, 9)))
    if not isinstance(bands, list) or not bands or len(set(bands)) != len(bands) or any(type(item) is not int or item not in range(1, 9) for item in bands):
        raise ValueError("GF6_PARAMETER_INVALID: bands")
    features = _features(raw.get("features", {"enabled": False, "metrics": ["statistics"]}))
    fusion = _fusion(raw.get("fusion", {"enabled": False, "red": 3, "green": 2, "blue": 1, "percentiles": [2.0, 98.0]}), bands)
    saliency = _saliency(raw.get("saliency", {"enabled": False, "source": "B3", "windowSize": 9, "threshold": 0.95}), bands)
    return {"schemaVersion": "gf6-wfv-l1a-v1", "targetCrs": target_crs, "resolution": resolution, "rpcHeightMode": "CONSTANT", "rpcHeight": 0.0, "roi": roi, "bands": bands, "features": features, "fusion": fusion, "saliency": saliency}


def build_integrated_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    if product.get("profileKey") != PROFILE_KEY or product.get("dataShape") != "RASTER_GRID":
        raise ValueError("GF6 L1A candidate is required")
    parameters = parse_parameters(parameter_snapshot)
    files = product.get("files")
    if not isinstance(files, list) or any(not isinstance(item, dict) or not isinstance(item.get("path"), str) or not item["path"] or type(item.get("ordinal")) is not int for item in files) or len({item["path"] for item in files}) != len(files):
        raise ValueError("GF6 L1A candidate has invalid input entries")
    roles = [item.get("role") for item in files]
    expected = ["MAIN_TIFF_TILE"] * 3 + ["METADATA_XML", "RPC_RPB", "NAV", "TILE_INDEX_TIL"]
    optional_roles = ["FOOTPRINT_SHP", "FOOTPRINT_DBF", "FOOTPRINT_SHX"]
    if roles not in (expected, expected + optional_roles) or [item["ordinal"] for item in files[:3]] != [0, 1, 2] or any(item["ordinal"] != 0 for item in files[3:]):
        raise ValueError("GF6 L1A candidate requires all raw inputs")
    file_names = [logical_file_name(Path(item["path"]), item) for item in files]
    tile_base = _tile_base(file_names[0])
    if (any(Path(file_names[index]).suffix.lower() not in {".tif", ".tiff"}
            or _tile_ordinal(file_names[index]) != index + 1 or _tile_base(file_names[index]) != tile_base
            for index in range(3))
            or any(_plain_base(file_names[index]) != tile_base for index in range(3, 7))):
        raise ValueError("GF6 L1A candidate input paths do not match the shared basename")
    if len(files) == 10 and any(_plain_base(file_names[index]) != tile_base
                                or Path(file_names[index]).suffix.lower() != suffix
                                for index, suffix in zip(range(7, 10), (".shp", ".dbf", ".shx"))):
        raise ValueError("GF6 L1A candidate footprint paths are invalid")
    inputs = [{"key": f"raw-{index}", "source": "RAW_INPUT", "artifactType": role, "required": True} for index, role in enumerate(expected)]
    if len(files) == 10:
        inputs.extend({"key": f"footprint-{index}", "source": "RAW_INPUT", "artifactType": role, "required": False} for index, role in enumerate(optional_roles))
    outputs = [_output("GF6_STANDARD_COG", "standard/gf6-standard.tif"), _output("GF6_PROCESSING_SUMMARY_JSON", "standard/summary.json")]
    if parameters["features"]["enabled"]: outputs.extend([_output("GF6_FEATURES_COG", "features/gf6-features.tif"), _output("GF6_FEATURES_SUMMARY_JSON", "features/summary.json")])
    if parameters["fusion"]["enabled"]: outputs.extend([_output("GF6_RGB_FUSION_COG", "fusion/gf6-rgb.tif"), _output("GF6_FUSION_SUMMARY_JSON", "fusion/summary.json")])
    if parameters["saliency"]["enabled"]: outputs.extend([_output("GF6_SALIENCY_SCORE_COG", "saliency/score.tif"), _output("GF6_SALIENCY_MASK_COG", "saliency/mask.tif"), _output("GF6_SALIENCY_SUMMARY_JSON", "saliency/summary.json")])
    return {"version": "plan-v2", "stages": [{"stageKey": "STANDARDIZE_GF6_WFV_L1A", "stageCode": "STANDARDIZE_GF6_WFV_L1A", "dependsOnStageKeys": [], "inputs": inputs, "outputs": outputs, "parameterSnapshot": json.dumps(parameters, sort_keys=True, separators=(",", ":"))}]}


def _output(artifact_type: str, relative_path: str) -> dict[str, Any]: return {"artifactType": artifact_type, "relativePath": relative_path, "retention": "FINAL"}
def _is_number(value: Any) -> bool: return type(value) in (int, float) and math.isfinite(value)
def _canonical_crs(value: Any) -> str:
    if not isinstance(value, str) or not value.strip(): raise ValueError("GF6_PARAMETER_INVALID: targetCrs")
    try: return CRS.from_user_input(value).to_string()
    except Exception as error: raise ValueError("GF6_PARAMETER_INVALID: targetCrs") from error
def _positive(value: Any, name: str) -> float:
    if not _is_number(value) or value <= 0: raise ValueError(f"GF6_PARAMETER_INVALID: {name}")
    return float(value)
def _features(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) not in ({"enabled", "metrics"}, {"enabled", "metrics", "windowSize"}) or type(value.get("enabled")) is not bool or not isinstance(value.get("metrics"), list) or not value["metrics"] or len(set(value["metrics"])) != len(value["metrics"]) or any(item not in {"statistics", "gradient", "local_variance"} for item in value["metrics"]): raise ValueError("GF6_PARAMETER_INVALID: features")
    if "windowSize" in value and (
        "local_variance" not in value["metrics"]
        or type(value["windowSize"]) is not int
        or value["windowSize"] < 3
        or value["windowSize"] > GF6_MAX_WINDOW_SIZE
        or value["windowSize"] % 2 == 0
    ):
        raise ValueError("GF6_PARAMETER_INVALID: features.windowSize")
    return {"enabled": value["enabled"], "metrics": value["metrics"], **({"windowSize": value.get("windowSize", 3)} if "local_variance" in value["metrics"] else {})}
def _fusion(value: Any, bands: list[int]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != {"enabled", "red", "green", "blue", "percentiles"} or type(value["enabled"]) is not bool or any(type(value[key]) is not int or value[key] not in bands for key in ("red", "green", "blue")) or not isinstance(value["percentiles"], list) or len(value["percentiles"]) != 2 or not all(_is_number(item) for item in value["percentiles"]) or not 0 <= value["percentiles"][0] < value["percentiles"][1] <= 100: raise ValueError("GF6_PARAMETER_INVALID: fusion")
    return value
def _saliency(value: Any, bands: list[int]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != {"enabled", "source", "windowSize", "threshold"} or type(value["enabled"]) is not bool or not isinstance(value["source"], str) or value["source"] not in {f"B{band}" for band in bands} or not _is_number(value["threshold"]) or not 0 <= value["threshold"] <= 1: raise ValueError("GF6_PARAMETER_INVALID: saliency")
    if (
        type(value["windowSize"]) is not int
        or value["windowSize"] < 3
        or value["windowSize"] > GF6_MAX_WINDOW_SIZE
        or value["windowSize"] % 2 == 0
    ):
        raise ValueError("GF6_PARAMETER_INVALID: saliency.windowSize")
    return value
def _single_suffix(paths: list[Path], suffix: str, name: str, names: dict[str, str]) -> Path:
    matches = [path for path in paths if _suffix(path, names) == suffix]
    if len(matches) != 1: raise ValueError(f"GF6_EVIDENCE_INVALID: exactly one {name} is required")
    return matches[0]
def _single_product_xml(paths: list[Path], names: dict[str, str]) -> Path:
    matches = []
    for path in paths:
        if _suffix(path, names) != ".xml" or _name(path, names).lower().endswith(".aux.xml"):
            continue
        try:
            if ET.parse(path).getroot().tag.rsplit("}", 1)[-1] == "ProductMetaData": matches.append(path)
        except (OSError, ET.ParseError):
            continue
    if len(matches) != 1: raise ValueError("GF6_EVIDENCE_INVALID: exactly one product XML is required")
    return matches[0]
def _tile_ordinal(name: str) -> int | None:
    match = re.search(r"-(\d+)\.[^.]+$", name, re.I); return int(match.group(1)) if match else None
def _tile_base(name: str) -> str: return re.sub(r"-\d+\.tiff?$", "", name, flags=re.I).lower()
def _plain_base(name: str) -> str: return Path(name).stem.lower()
def _name(path: Path, names: dict[str, str]) -> str: return names.get(str(path), path.name)
def _suffix(path: Path, names: dict[str, str]) -> str: return Path(_name(path, names)).suffix.lower()
def _shared_base(paths: list[Path], names: dict[str, str]) -> str:
    bases = {_tile_base(_name(path, names)) for path in paths if _suffix(path, names) in {".tif", ".tiff"}}
    if len(bases) != 1: raise ValueError("GF6_EVIDENCE_INVALID: TIFF basenames must match")
    return next(iter(bases))
def _float_list(text: str, name: str) -> tuple[float, ...]:
    try: values = tuple(float(item.strip()) for item in text.split(",") if item.strip())
    except ValueError as error: raise ValueError(f"GF6_EVIDENCE_INVALID: {name}") from error
    if not all(math.isfinite(value) for value in values): raise ValueError(f"GF6_EVIDENCE_INVALID: {name}")
    return values
def _integer(text: str, name: str) -> int:
    try: value = int(text)
    except ValueError as error: raise ValueError(f"GF6_EVIDENCE_INVALID: {name}") from error
    if value <= 0: raise ValueError(f"GF6_EVIDENCE_INVALID: {name}")
    return value
def _parse_til(path: Path) -> list[tuple[str, int, int]]:
    entries = []
    for match in re.finditer(r"filename\s*=\s*\"([^\"]+)\"(?P<body>.*?)(?=BEGIN_GROUP|$)", path.read_text(encoding="utf-8", errors="replace"), re.I | re.S):
        col, row = re.search(r"ULColOffset\s*=\s*(\d+)", match.group("body"), re.I), re.search(r"ULRowOffset\s*=\s*(\d+)", match.group("body"), re.I)
        if not col or not row: raise ValueError("GF6_EVIDENCE_INVALID: TIL offsets are missing")
        entries.append((match.group(1), int(col.group(1)), int(row.group(1))))
    if len(entries) != 3: raise ValueError("GF6_EVIDENCE_INVALID: TIL must describe three tiles")
    return entries
def _validate_footprint(paths: list[Path], base: str, names: dict[str, str]) -> tuple[Path, ...]:
    by_suffix = {_suffix(path, names): path for path in paths if _suffix(path, names) in {".shp", ".dbf", ".shx"}}
    suffixes = set(by_suffix)
    if suffixes and suffixes != {".shp", ".dbf", ".shx"}: raise ValueError("GF6_EVIDENCE_INVALID: footprint SHP/DBF/SHX are all-or-none")
    if suffixes and any(_plain_base(_name(path, names)) != base for path in by_suffix.values()): raise ValueError("GF6_EVIDENCE_INVALID: footprint basenames must match")
    return tuple(by_suffix[suffix] for suffix in (".shp", ".dbf", ".shx") if suffix in by_suffix)
