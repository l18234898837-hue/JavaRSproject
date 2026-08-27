from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import rasterio

from ..models_gf6 import Gf6BandSpec, Gf6CalibrationSpec, Gf6ProductEvidence, Gf6RpcSpec, Gf6TileSpec
from .base import input_paths


PRODUCT_CODE = "GF-6-WFV-L1A"
PROFILE_VERSION = "v1"
_RPC_KEYS = {
    "lineOffset": "line_offset", "sampOffset": "sample_offset", "latOffset": "latitude_offset",
    "longOffset": "longitude_offset", "heightOffset": "height_offset", "lineScale": "line_scale",
    "sampScale": "sample_scale", "latScale": "latitude_scale", "longScale": "longitude_scale",
    "heightScale": "height_scale", "lineNumCoef": "line_num", "lineDenCoef": "line_den",
    "sampNumCoef": "sample_num", "sampDenCoef": "sample_den",
}
_WAVELENGTHS = ("450-520nm", "520-590nm", "630-690nm", "770-890nm", "690-730nm", "730-770nm", "400-450nm", "590-630nm")


def inspect_manifest(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    paths = input_paths(manifest)
    evidence = inspect_paths(paths)
    files = [
        {"role": "MAIN_TIFF_TILE", "ordinal": tile.ordinal - 1, "path": str(tile.path)}
        for tile in evidence.tiles
    ] + [
        {"role": "METADATA_XML", "ordinal": 0, "path": str(evidence.xml_path)},
        {"role": "RPC_RPB", "ordinal": 0, "path": str(evidence.rpc_path)},
        {"role": "NAV", "ordinal": 0, "path": str(evidence.nav_path)},
        {"role": "TILE_INDEX_TIL", "ordinal": 0, "path": str(evidence.til_path)},
    ]
    return [{
        "packageId": evidence.package_id,
        "profileKey": {"productCode": PRODUCT_CODE, "profileVersion": PROFILE_VERSION},
        "dataShape": "RASTER_GRID",
        "supportStatus": "SUPPORTED",
        "files": files,
        "metadataSnapshot": evidence.metadata_snapshot,
        "confidence": 0.99,
    }]


def inspect_paths(paths: list[Path]) -> Gf6ProductEvidence:
    files = {path.name.lower(): path for path in paths}
    tiffs = sorted((path for path in paths if path.suffix.lower() in {".tif", ".tiff"}), key=lambda p: _tile_number(p.name))
    xml = _product_xml(paths)
    til = _single(paths, ".til", required_name="tile index")
    nav = _single(paths, ".nav", required_name="navigation file")
    rpb_matches = [path for path in paths if path.suffix.lower() == ".rpb" and not re.search(r"-\d+\.rpb$", path.name, re.I)]
    if len(rpb_matches) != 1:
        rpb_matches = [path for path in paths if path.suffix.lower() == ".rpb"]
    if not rpb_matches:
        raise ValueError("GF6_EVIDENCE_INVALID: RPC file is required")
    rpb = rpb_matches[0]
    if len(tiffs) != 3:
        raise ValueError("GF6_EVIDENCE_INVALID: exactly three TIFF tiles are required")
    root = ET.parse(xml).getroot()
    text = lambda name: (root.findtext(name) or "").strip()
    if (text("SatelliteID"), text("SensorID"), text("ProductLevel")) != ("GF6", "WFV", "LEVEL1A"):
        raise ValueError("GF6_EVIDENCE_INVALID: XML product identity is invalid")
    product_id = text("ProductID")
    if not product_id:
        raise ValueError("GF6_EVIDENCE_INVALID: XML product ID is missing")
    package_id = f"GF6-WFV-L1A-{product_id}"
    gains = _csv_floats(text("AGain"), 8, "AGain")
    offsets = _csv_floats(text("AOffset"), 8, "AOffset")
    bands = tuple(Gf6BandSpec(index, _WAVELENGTHS[index - 1], gains[index - 1], offsets[index - 1]) for index in range(1, 9))
    footprint = tuple((float(text(f"{name}Longitude")), float(text(f"{name}Latitude"))) for name in ("TopLeft", "TopRight", "BottomRight", "BottomLeft"))
    footprint = footprint + (footprint[0],)
    tiles_meta = _parse_til(til)
    tiles: list[Gf6TileSpec] = []
    for ordinal, (path, metadata) in enumerate(zip(tiffs, tiles_meta), start=1):
        with rasterio.open(path) as dataset:
            if dataset.count != 8 or dataset.dtypes != ("uint16",) * 8:
                raise ValueError("GF6_EVIDENCE_INVALID: each TIFF must contain eight UInt16 bands")
            width, height = dataset.width, dataset.height
        expected_name, column_offset, row_offset = metadata
        if Path(expected_name).name.lower() != path.name.lower():
            raise ValueError("GF6_EVIDENCE_INVALID: TIL tile order does not match manifest")
        tiles.append(Gf6TileSpec(path, ordinal, width, height, column_offset, row_offset))
    rpc = parse_rpc(rpb)
    metadata_snapshot = {
        "satellite": "GF6", "sensor": "WFV", "productLevel": "LEVEL1A", "productId": product_id,
        "sceneId": text("SceneID"), "orbitId": text("OrbitID"), "imageGsd": float(text("ImageGSD") or 0),
        "dimensions": [int(text("WidthInPixels") or max(tile.column_offset + tile.width for tile in tiles)), int(text("HeightInPixels") or max(tile.row_offset + tile.height for tile in tiles))],
        "tileCoverageDimensions": [max(tile.column_offset + tile.width for tile in tiles), max(tile.row_offset + tile.height for tile in tiles)],
        "declaredDimensions": [int(text("WidthInPixels") or 0), int(text("HeightInPixels") or 0)],
        "bands": [{"number": band.number, "wavelength": band.wavelength} for band in bands],
        "footprint": [list(point) for point in footprint], "calibrationUnit": "W.m-2.sr-1.um-1",
        "navigationStatus": "NOMINAL_RPC_NO_DEM", "accuracyStatus": "UNVALIDATED",
    }
    return Gf6ProductEvidence(package_id, product_id, xml, rpb, nav, til, tuple(tiles), bands,
                               Gf6CalibrationSpec(bands), rpc, footprint, metadata_snapshot)


def parse_rpc(path: Path) -> Gf6RpcSpec:
    value: dict[str, Any] = {}
    text = path.read_text(encoding="utf-8", errors="replace")
    for key, target in _RPC_KEYS.items():
        match = re.search(rf"\b{re.escape(key)}\s*=\s*(?:\(([^)]*)\)|([^;]+));", text, re.I | re.S)
        if not match:
            raise ValueError(f"GF6_EVIDENCE_INVALID: RPC field {key} is missing")
        raw = (match.group(1) or match.group(2) or "").replace(",", " ")
        numbers = tuple(float(token) for token in re.findall(r"[-+]?\d*\.?\d+(?:[Ee][-+]?\d+)?", raw))
        value[target] = numbers if "Coef" in key else (numbers[0] if numbers else math.nan)
    return Gf6RpcSpec(**value)


def build_plan(product: dict[str, Any], parameters: dict[str, Any] | None = None) -> dict[str, Any]:
    if product.get("profileKey") != {"productCode": PRODUCT_CODE, "profileVersion": PROFILE_VERSION}:
        raise ValueError("GF6 L1A profile is required")
    normalized = parse_parameters(parameters or {})
    stages = [
        ("INSPECT_GF6_WFV_L1A", []), ("MOSAIC_GF6_TILES", ["INSPECT_GF6_WFV_L1A"]),
        ("CALIBRATE_GF6_WFV", ["MOSAIC_GF6_TILES"]), ("BUILD_GF6_RPC_GRID", ["CALIBRATE_GF6_WFV"]),
        ("ALIGN_GF6_BANDS", ["BUILD_GF6_RPC_GRID"]), ("CROP_GF6_RASTER", ["ALIGN_GF6_BANDS"]),
        ("WRITE_GF6_STANDARD_COG", ["CROP_GF6_RASTER"]),
    ]
    if normalized["features"]["enabled"]:
        stages.append(("EXTRACT_GF6_FEATURES", ["WRITE_GF6_STANDARD_COG"]))
    if normalized["fusion"]["enabled"]:
        stages.append(("FUSE_GF6_BANDS", ["WRITE_GF6_STANDARD_COG"]))
    if normalized["saliency"]["enabled"]:
        stages.append(("DETECT_GF6_SALIENCY", ["WRITE_GF6_STANDARD_COG"]))
    return {"version": "plan-v2", "parameters": normalized,
            "stages": [{"stageKey": key, "stageCode": key, "dependsOnStageKeys": deps} for key, deps in stages]}


def parse_parameters(raw: dict[str, Any]) -> dict[str, Any]:
    allowed = {"schemaVersion", "targetCrs", "resolution", "rpcHeightMode", "rpcHeight", "roi", "bands", "features", "fusion", "saliency"}
    if set(raw) - allowed or raw.get("schemaVersion", "gf6-wfv-l1a-v1") != "gf6-wfv-l1a-v1":
        raise ValueError("GF6_PARAMETER_INVALID")
    bands = raw.get("bands", list(range(1, 9)))
    if not isinstance(bands, list) or not bands or len(set(bands)) != len(bands) or any(not isinstance(item, int) or not 1 <= item <= 8 for item in bands):
        raise ValueError("GF6_PARAMETER_INVALID: bands")
    resolution = raw.get("resolution", 0.00015)
    if not isinstance(resolution, (int, float)) or not math.isfinite(resolution) or resolution <= 0:
        raise ValueError("GF6_PARAMETER_INVALID: resolution")
    if raw.get("rpcHeightMode", "CONSTANT") != "CONSTANT":
        raise ValueError("GF6_PARAMETER_INVALID: rpcHeightMode")
    height = raw.get("rpcHeight", 0.0)
    if not isinstance(height, (int, float)) or not math.isfinite(height):
        raise ValueError("GF6_PARAMETER_INVALID: rpcHeight")
    roi = raw.get("roi")
    if roi is not None and (not isinstance(roi, list) or len(roi) != 4 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in roi) or roi[2] <= roi[0] or roi[3] <= roi[1]):
        raise ValueError("GF6_PARAMETER_INVALID: roi")
    features = raw.get("features", {"enabled": False, "metrics": []})
    fusion = raw.get("fusion", {"enabled": False, "red": 3, "green": 2, "blue": 1})
    saliency = raw.get("saliency", {"enabled": False, "source": "B3", "windowSize": 9, "threshold": 0.95})
    if not isinstance(features, dict) or not isinstance(fusion, dict) or not isinstance(saliency, dict):
        raise ValueError("GF6_PARAMETER_INVALID: optional settings")
    if fusion.get("enabled") and any(fusion.get(key) not in bands for key in ("red", "green", "blue")):
        raise ValueError("GF6_PARAMETER_INVALID: fusion bands must be selected")
    if saliency.get("enabled") and (not isinstance(saliency.get("source"), str) or saliency["source"] not in {f"B{band}" for band in bands}):
        raise ValueError("GF6_PARAMETER_INVALID: saliency source must be selected")
    return {"schemaVersion": "gf6-wfv-l1a-v1", "targetCrs": raw.get("targetCrs", "EPSG:4326"),
            "resolution": float(resolution), "rpcHeightMode": "CONSTANT", "rpcHeight": float(height),
            "roi": roi, "bands": bands, "features": features, "fusion": fusion, "saliency": saliency}


def _single(paths: list[Path], suffix: str, *, required_name: str) -> Path:
    matches = [path for path in paths if path.suffix.lower() == suffix and not path.name.lower().endswith(".aux.xml")]
    if len(matches) != 1:
        raise ValueError(f"GF6_EVIDENCE_INVALID: exactly one {required_name} is required")
    return matches[0]


def _product_xml(paths: list[Path]) -> Path:
    matches = []
    for path in paths:
        if path.suffix.lower() != ".xml" or path.name.lower().endswith(".aux.xml"):
            continue
        try:
            if ET.parse(path).getroot().tag.rsplit("}", 1)[-1] == "ProductMetaData":
                matches.append(path)
        except (OSError, ET.ParseError):
            continue
    if len(matches) != 1:
        raise ValueError("GF6_EVIDENCE_INVALID: exactly one product XML is required")
    return matches[0]


def _csv_floats(value: str, count: int, name: str) -> tuple[float, ...]:
    numbers = tuple(float(item.strip()) for item in value.split(",") if item.strip())
    if len(numbers) != count or not all(math.isfinite(item) for item in numbers):
        raise ValueError(f"GF6_EVIDENCE_INVALID: {name} must contain {count} finite values")
    return numbers


def _tile_number(name: str) -> int:
    match = re.search(r"-(\d+)\.tiff?$", name, re.I)
    return int(match.group(1)) if match else 999


def _parse_til(path: Path) -> list[tuple[str, int, int]]:
    text = path.read_text(encoding="utf-8", errors="replace")
    result = []
    for match in re.finditer(r"filename\s*=\s*\"([^\"]+)\"(?P<body>.*?)(?=BEGIN_GROUP|$)", text, re.I | re.S):
        body = match.group("body")
        col = re.search(r"ULColOffset\s*=\s*(\d+)", body, re.I)
        row = re.search(r"ULRowOffset\s*=\s*(\d+)", body, re.I)
        if not col or not row:
            raise ValueError("GF6_EVIDENCE_INVALID: TIL offsets are missing")
        result.append((match.group(1), int(col.group(1)), int(row.group(1))))
    if len(result) != 3:
        raise ValueError("GF6_EVIDENCE_INVALID: TIL must describe three tiles")
    return result
