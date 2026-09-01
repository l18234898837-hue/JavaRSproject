from __future__ import annotations

from pathlib import Path
from typing import Any


def input_paths(manifest: dict[str, Any]) -> list[Path]:
    inputs = manifest.get("inputs")
    if not isinstance(inputs, list) or not inputs:
        raise ValueError("manifest inputs are required")
    paths = []
    for item in inputs:
        path = item.get("path") if isinstance(item, dict) else None
        if not isinstance(path, str):
            raise ValueError("manifest input path is required")
        value = Path(path)
        if not value.is_file():
            raise ValueError("manifest input file does not exist")
        paths.append(value)
    return paths


def input_path_names(manifest: dict[str, Any]) -> list[tuple[Path, str]]:
    inputs = manifest.get("inputs")
    paths = input_paths(manifest)
    return [(path, logical_file_name(path, item)) for path, item in zip(paths, inputs)]


def logical_file_name(path: Path, item: dict[str, Any] | None = None) -> str:
    name = item.get("originalFileName") if isinstance(item, dict) and "originalFileName" in item else path.name
    if (
        not isinstance(name, str)
        or not name.strip()
        or len(name) > 255
        or name in {".", ".."}
        or "/" in name
        or "\\" in name
        or any(ord(character) < 32 or 127 <= ord(character) <= 159 for character in name)
    ):
        raise ValueError("manifest originalFileName is invalid")
    return name


def candidate(package_id: str, product_code: str, shape: str, files: list[Path], metadata: dict[str, Any]) -> dict[str, Any]:
    return {
        "packageId": package_id,
        "profileKey": {"productCode": product_code, "profileVersion": "v1"},
        "dataShape": shape,
        "supportStatus": "SUPPORTED",
        "files": [{"role": "MAIN", "ordinal": index, "path": str(path)} for index, path in enumerate(files)],
        "metadataSnapshot": metadata,
        "confidence": 0.95,
    }


def build_plan(product: dict[str, Any]) -> dict[str, Any]:
    if product.get("supportStatus") != "SUPPORTED":
        raise ValueError("product is not supported")
    if not product.get("files"):
        raise ValueError("supported product has no files")
    return {"version": "plan-v1", "stages": [
        {"stageKey": "VALIDATE_SOURCE", "stageCode": "VALIDATE_SOURCE", "dependsOnStageKeys": []},
        {"stageKey": "EXTRACT_FEATURES", "stageCode": "EXTRACT_FEATURES", "dependsOnStageKeys": ["VALIDATE_SOURCE"]},
    ]}
