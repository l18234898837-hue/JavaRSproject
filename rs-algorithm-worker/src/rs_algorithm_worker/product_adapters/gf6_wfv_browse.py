from __future__ import annotations

import json
import math
import os
import tempfile
from pathlib import Path

from PIL import Image, ImageStat

from .base import input_paths


FORBIDDEN = {
    "RADIOMETRIC_CALIBRATION",
    "SPECTRAL_INDEX",
    "ATMOSPHERIC_CORRECTION",
    "RPC_ORTHORECTIFICATION",
    "ORTHORECTIFICATION",
    "TERRAIN_CORRECTION",
    "SCIENTIFIC_QUALITY_MASK",
    "QUALITY_MASK_INTERPRETATION",
}

PUBLIC_PARAMETERS = {"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}
ROLE_SUFFIXES = {
    "BROWSE_IMAGE": ".jpg",
    "GEOREFERENCE": ".jgw",
    "METADATA": ".json",
}
REQUIRED_SUFFIXES = set(ROLE_SUFFIXES.values())


def inspect_manifest(manifest):
    inputs = manifest.get("inputs")
    paths = input_paths(manifest)
    groups = _group_inputs(inputs)
    candidates = []
    for group_key, entries in sorted(groups.items(), key=lambda item: str(item[0])):
        files = _files_for_group(entries)
        if set(files) != REQUIRED_SUFFIXES:
            raise ValueError("GF-6 WFV browse package requires JPEG, JGW, and JSON")
        candidates.append(_inspect_package(str(group_key), files))
    if not candidates:
        raise ValueError("GF-6 WFV browse evidence is empty")
    return candidates


def _group_inputs(inputs):
    if not isinstance(inputs, list) or not inputs:
        raise ValueError("manifest inputs are required")
    if any("groupId" in item for item in inputs if isinstance(item, dict)):
        if not all(isinstance(item.get("groupId"), str) and item["groupId"] for item in inputs):
            raise ValueError("GF-6 product groupId must be present for every input")
        return _group_by(inputs, lambda item: item["groupId"])
    roles = [item.get("role") for item in inputs if isinstance(item, dict)]
    if all(role in ROLE_SUFFIXES for role in roles):
        if len(roles) == len(ROLE_SUFFIXES) and set(roles) == set(ROLE_SUFFIXES):
            return _group_by(inputs, lambda item: 0)
        return _group_by(inputs, lambda item: item.get("ordinal", 0))
    return _group_by(inputs, lambda item: Path(item["path"]).stem)


def _group_by(inputs, key_fn):
    groups = {}
    for item in inputs:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            raise ValueError("manifest input path is required")
        groups.setdefault(key_fn(item), []).append(item)
    return groups


def _files_for_group(entries):
    use_roles = any(item.get("role") in ROLE_SUFFIXES for item in entries)
    files = {}
    for item in entries:
        path = Path(item["path"])
        suffix = path.suffix.lower()
        if use_roles:
            role = item.get("role")
            expected_suffix = ROLE_SUFFIXES.get(role)
            if expected_suffix is None:
                raise ValueError("GF-6 browse package contains an unsupported input role")
            if suffix != expected_suffix:
                raise ValueError(f"GF-6 {role} file extension is invalid")
            suffix = expected_suffix
        if suffix in files:
            raise ValueError("GF-6 browse package contains duplicate input roles")
        files[suffix] = path
    return files


def build_plan(product: dict, parameters: dict | None = None) -> dict:
    if parameters != PUBLIC_PARAMETERS:
        operation = parameters.get("operation") if isinstance(parameters, dict) else None
        raise ValueError(f"UNSUPPORTED_BROWSE_OPERATION: {operation or 'invalid parameter snapshot'}")
    if product.get("profileKey") != {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"}:
        raise ValueError("GF-6 browse profile is required")
    return {"version": "plan-v1", "stages": [
        {"stageKey": "VALIDATE_BROWSE", "stageCode": "VALIDATE_BROWSE", "dependsOnStageKeys": []},
        {"stageKey": "GEOREFERENCE_BROWSE", "stageCode": "GEOREFERENCE_BROWSE", "dependsOnStageKeys": ["VALIDATE_BROWSE"]},
        {"stageKey": "EXTRACT_VISUAL_FEATURES", "stageCode": "EXTRACT_VISUAL_FEATURES", "dependsOnStageKeys": ["GEOREFERENCE_BROWSE"]},
        {"stageKey": "WRITE_BROWSE_REPORT", "stageCode": "WRITE_BROWSE_REPORT", "dependsOnStageKeys": ["EXTRACT_VISUAL_FEATURES"]},
    ]}


def execute_visual_plan(product: dict, output_dir: str | os.PathLike[str]) -> Path:
    files = [Path(item["path"]) for item in product.get("files", []) if item.get("role") == "BROWSE_IMAGE"]
    if len(files) != 1:
        raise ValueError("GF-6 browse image is required")
    with Image.open(files[0]) as image:
        width, height = image.size
        if width <= 0 or height <= 0 or image.mode != "RGB":
            raise ValueError("GF-6 browse image must be a positive RGB JPEG")
        rgb_stats = _rgb_stats(image)
    output = Path(output_dir).resolve()
    output.mkdir(parents=True, exist_ok=True)
    report = output / "browse-report.json"
    metadata = product.get("metadataSnapshot", {})
    value = {
        "georeferencingMethod": "AFFINE_WORLD_FILE_APPROXIMATE",
        "image": {"width": width, "height": height, "rgbStats": rgb_stats},
        "packageId": product["packageId"],
        "productMetadata": {
            "cloudPercent": metadata.get("cloudPercent"),
            "productLevel": metadata.get("productLevel"),
        },
        "profile": "GF-6-WFV-BROWSE/v1",
    }
    fd, temporary_name = tempfile.mkstemp(prefix=".browse-report.", suffix=".tmp", dir=output)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as temporary:
            json.dump(value, temporary, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
            temporary.write("\n")
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_name, report)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
    return report


def _rgb_stats(image: Image.Image) -> dict[str, dict[str, int | float]]:
    statistics = ImageStat.Stat(image)
    pixels = image.width * image.height
    return {
        name: {
            "min": int(statistics.extrema[index][0]),
            "max": int(statistics.extrema[index][1]),
            "mean": statistics.sum[index] / pixels,
        }
        for index, name in enumerate(("red", "green", "blue"))
    }


def _inspect_package(stem: str, files: dict[str, Path]) -> dict:
    try:
        with Image.open(files[".jpg"]) as image:
            if image.mode != "RGB" or image.width <= 0 or image.height <= 0:
                raise ValueError("GF-6 browse image must be a positive RGB JPEG")
            dimensions = [image.width, image.height]
        coefficients = [float(line.strip()) for line in files[".jgw"].read_text().splitlines() if line.strip()]
        if len(coefficients) != 6 or not all(math.isfinite(value) for value in coefficients):
            raise ValueError("GF-6 world file coefficients are invalid")
        a, d, b, e, c, f = coefficients
        if abs(a * e - b * d) < 1e-15:
            raise ValueError("GF-6 world file transform is singular")
        metadata = json.loads(files[".json"].read_text(encoding="utf-8"))
        if not isinstance(metadata, dict):
            raise ValueError("GF-6 metadata must be an object")
        if metadata.get("satelliteid") != "GF6" or metadata.get("sensorid") != "WFV" or metadata.get("sublevel") != "LEVEL1A":
            raise ValueError("GF-6 metadata identity is invalid")
        product_id = str(metadata.get("productid", ""))
        if not product_id or metadata.get("metadataid") != f"GF6-WFV-LEVEL1A-{product_id}":
            raise ValueError("GF-6 metadata identity is invalid")
        footprint = _parse_polygon(metadata.get("geom"))
        if not _close((c, f), footprint[0], 1e-5):
            raise ValueError("GF-6 world file origin does not match footprint")
        start = metadata.get("starttime")
        end = metadata.get("endtime")
        cloud = float(metadata.get("cloudpercent"))
        if not start or not end or end < start or not 0 <= cloud <= 100:
            raise ValueError("GF-6 metadata quality fields are invalid")
    except (OSError, ValueError, json.JSONDecodeError) as error:
        if isinstance(error, ValueError) and str(error).startswith("GF-6"):
            raise
        raise ValueError("GF-6 metadata evidence is invalid") from error
    package_id = f"GF6-WFV-LEVEL1A-{product_id}"
    return {"packageId": package_id,
            "profileKey": {"productCode": "GF-6-WFV-BROWSE", "profileVersion": "v1"},
            "dataShape": "RASTER_GRID", "supportStatus": "SUPPORTED",
            "files": [{"role": role, "ordinal": index, "path": str(files[suffix])}
                      for index, (role, suffix) in enumerate((("BROWSE_IMAGE", ".jpg"), ("GEOREFERENCE", ".jgw"), ("METADATA", ".json")))],
            "metadataSnapshot": {"georeferencingMethod": "AFFINE_WORLD_FILE_APPROXIMATE",
                                 "dimensions": dimensions, "footprint": footprint,
                                 "cloudPercent": cloud, "productLevel": "LEVEL1A"},
            "confidence": 0.99}


def _parse_polygon(value):
    if not isinstance(value, str) or not value.startswith("POLYGON((") or not value.endswith("))"):
        raise ValueError("GF-6 metadata footprint is invalid")
    points = []
    for pair in value[9:-2].split(","):
        values = pair.strip().split()
        if len(values) != 2:
            raise ValueError("GF-6 metadata footprint is invalid")
        points.append([float(values[0]), float(values[1])])
    if len(points) < 4 or points[0] != points[-1]:
        raise ValueError("GF-6 metadata footprint is invalid")
    return points


def _close(left, right, tolerance):
    return abs(left[0] - right[0]) <= tolerance and abs(left[1] - right[1]) <= tolerance
