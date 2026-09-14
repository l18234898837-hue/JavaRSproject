"""Bounded metadata and sample inspection for authorized task artifacts."""

from __future__ import annotations

import csv
import json
from collections.abc import Iterable
from datetime import datetime
from pathlib import Path
from typing import Any

import netCDF4
import numpy as np
import pyarrow.parquet as pq
from PIL import Image


MAX_SAMPLE_ROWS = 100
MAX_JSON_BYTES = 10 * 1024 * 1024


def inspect_artifact(path: Path, artifact_format: str) -> dict[str, Any]:
    """Return safe, bounded inspection data without exposing the source path."""
    source = Path(path)
    format_name = artifact_format.upper()
    if format_name == "JSON":
        return _inspect_json(source)
    if format_name == "GEOJSON":
        return _inspect_geojson(source)
    if format_name == "CSV":
        return _inspect_csv(source)
    if format_name == "PARQUET":
        return _inspect_parquet(source)
    if format_name == "NETCDF":
        return _inspect_netcdf(source)
    if format_name == "NPZ":
        return _inspect_npz(source)
    if format_name in {"PNG", "JPEG", "JPG"}:
        return _inspect_image(source, format_name)
    raise ValueError("unsupported artifact inspection format")


def _inspect_json(source: Path) -> dict[str, Any]:
    _require_small_json(source)
    value = json.loads(source.read_text(encoding="utf-8"))
    if isinstance(value, list) and all(isinstance(item, dict) for item in value):
        return _table_result("JSON", value, len(value))
    if isinstance(value, dict):
        return {
            "format": "JSON",
            "kind": "object",
            "fields": sorted(value.keys()),
            "summary": _json_safe(value),
        }
    return {"format": "JSON", "kind": "value", "summary": _json_safe(value)}


def _inspect_geojson(source: Path) -> dict[str, Any]:
    _require_small_json(source)
    value = json.loads(source.read_text(encoding="utf-8"))
    features = value.get("features") if isinstance(value, dict) else None
    if not isinstance(features, list):
        raise ValueError("invalid GeoJSON feature collection")
    properties = [feature.get("properties") or {} for feature in features if isinstance(feature, dict)]
    result = _table_result("GEOJSON", properties, len(features))
    result["geometryTypes"] = sorted({
        geometry.get("type")
        for feature in features
        if isinstance(feature, dict)
        for geometry in [feature.get("geometry")]
        if isinstance(geometry, dict) and isinstance(geometry.get("type"), str)
    })
    invalid = 0
    coordinate_range_valid = True
    for feature in features:
        geometry = feature.get("geometry") if isinstance(feature, dict) else None
        valid, in_range = _validate_geometry(geometry)
        if not valid or not in_range:
            invalid += 1
        coordinate_range_valid = coordinate_range_valid and in_range
    result["quality"].update({
        "geometryCount": len(features),
        "invalidGeometryCount": invalid,
        "coordinateRangeValid": coordinate_range_valid,
    })
    return result


def _validate_geometry(geometry: Any) -> tuple[bool, bool]:
    if not isinstance(geometry, dict) or not isinstance(geometry.get("type"), str):
        return False, False
    if geometry["type"] == "GeometryCollection":
        geometries = geometry.get("geometries")
        if not isinstance(geometries, list):
            return False, False
        statuses = [_validate_geometry(item) for item in geometries]
        return (
            bool(statuses) and all(item[0] for item in statuses),
            all(item[1] for item in statuses),
        )
    coordinates = geometry.get("coordinates")
    pairs: list[tuple[Any, Any]] = []

    def collect(value: Any) -> bool:
        if (
            isinstance(value, (list, tuple))
            and len(value) >= 2
            and all(
                isinstance(item, (int, float)) and not isinstance(item, bool)
                for item in value[:2]
            )
        ):
            pairs.append((value[0], value[1]))
            return True
        if isinstance(value, (list, tuple)) and value:
            return all(collect(item) for item in value)
        return False

    structural = collect(coordinates)
    in_range = structural and all(
        np.isfinite(x) and np.isfinite(y) and -180 <= x <= 180 and -90 <= y <= 90
        for x, y in pairs
    )
    return structural, bool(in_range)


def _inspect_csv(source: Path) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    row_count = 0
    with source.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        columns = reader.fieldnames or []
        for row in reader:
            row_count += 1
            if len(rows) < MAX_SAMPLE_ROWS:
                rows.append({key: value for key, value in row.items()})
    return {
        "format": "CSV",
        "kind": "table",
        "columns": columns,
        "rowCount": row_count,
        "rows": rows,
        "quality": _table_quality(rows, columns, row_count),
    }


def _inspect_parquet(source: Path) -> dict[str, Any]:
    parquet = pq.ParquetFile(source)
    columns = parquet.schema_arrow.names
    rows: list[dict[str, Any]] = []
    for batch in parquet.iter_batches(batch_size=MAX_SAMPLE_ROWS):
        rows.extend(batch.to_pylist())
        break
    result = {
        "format": "PARQUET",
        "kind": "dataset",
        "columns": columns,
        "rowCount": parquet.metadata.num_rows,
        "rows": _json_safe(rows),
        "statistics": _parquet_statistics(parquet),
    }
    result["quality"] = _table_quality(rows, columns, parquet.metadata.num_rows)
    return result


def _inspect_netcdf(source: Path) -> dict[str, Any]:
    with netCDF4.Dataset(source, "r") as dataset:
        dimensions = {name: len(dimension) for name, dimension in dataset.dimensions.items()}
        variables = list(dataset.variables)
        result: dict[str, Any] = {
            "format": "NETCDF",
            "kind": "dataset",
            "dimensions": dimensions,
            "variables": variables,
            "statistics": {
                name: _sample_statistics(variable)
                for name, variable in dataset.variables.items()
                if np.issubdtype(variable.dtype, np.number) and name.lower() not in {"time", "latitude", "longitude", "lat", "lon"}
            },
        }
        time_range = _netcdf_time_range(dataset)
        if time_range is not None:
            result["timeRange"] = time_range
        observation_count = next(
            (
                len(dataset.dimensions[name])
                for name in ("observation", "observations", "time")
                if name in dataset.dimensions
            ),
            0,
        )
        result["quality"] = {
            "dimensionCount": len(dimensions),
            "variableCount": len(variables),
            "observationCount": observation_count,
            "timeRangeValid": (
                time_range is not None and time_range["start"] <= time_range["end"]
            ),
            "statistics": result["statistics"],
        }
        return result


def _inspect_npz(source: Path) -> dict[str, Any]:
    with np.load(source, allow_pickle=False) as archive:
        arrays = [
            {"name": name, "shape": list(archive[name].shape), "dtype": str(archive[name].dtype)}
            for name in archive.files
        ]
    return {"format": "NPZ", "kind": "dataset", "arrays": arrays}


def _inspect_image(source: Path, artifact_format: str) -> dict[str, Any]:
    with Image.open(source) as image:
        image.load()
        array = np.asarray(image)
        channels = 1 if array.ndim == 2 else array.shape[-1]
        if array.ndim == 2:
            valid = np.ones(array.shape, dtype=bool)
            values = array
        elif channels == 4:
            valid = array[..., 3] > 0
            values = array[..., :3]
        else:
            valid = np.ones(array.shape[:2], dtype=bool)
            values = array[..., :3]
        numeric = values.astype(np.float64, copy=False)
        finite = np.isfinite(numeric)
        valid_values = numeric[valid[..., None] & finite] if numeric.ndim == 3 else numeric[valid & finite]
        statistics = {}
        if valid_values.size:
            statistics = {"min": _number(valid_values.min()), "max": _number(valid_values.max())}
        quality = {
            "width": image.width,
            "height": image.height,
            "bandCount": channels,
            "validPixelRatio": float(valid.mean()) if valid.size else 0.0,
            "hasNanOrInf": bool(np.any(~finite)),
            "statistics": statistics,
        }
        return {"format": artifact_format, "kind": "image", "mode": image.mode,
                "quality": quality}


def _table_result(artifact_format: str, records: Iterable[dict[str, Any]], row_count: int) -> dict[str, Any]:
    sample = list(records)[:MAX_SAMPLE_ROWS]
    columns = list(dict.fromkeys(key for row in sample for key in row))
    return {
        "format": artifact_format,
        "kind": "table",
        "columns": columns,
        "rowCount": row_count,
        "rows": _json_safe(sample),
        "quality": _table_quality(sample, columns, row_count),
    }


def _table_quality(sample: list[dict[str, Any]], columns: list[str], row_count: int) -> dict[str, Any]:
    total = len(sample) * len(columns)
    empty = sum(1 for row in sample for column in columns if row.get(column) in (None, ""))
    return {
        "rowCount": row_count,
        "columnCount": len(columns),
        "emptyValueRatio": empty / total if total else 0.0,
    }


def _parquet_statistics(parquet: pq.ParquetFile) -> dict[str, dict[str, Any]]:
    statistics: dict[str, dict[str, Any]] = {}
    metadata = parquet.metadata
    for column_index, column_name in enumerate(parquet.schema_arrow.names):
        values = [
            metadata.row_group(row_group).column(column_index).statistics
            for row_group in range(metadata.num_row_groups)
        ]
        values = [value for value in values if value is not None and value.has_min_max]
        if values:
            statistics[column_name] = {
                "min": _json_safe(min(value.min for value in values)),
                "max": _json_safe(max(value.max for value in values)),
            }
    return statistics


def _sample_statistics(variable: netCDF4.Variable) -> dict[str, float] | None:
    if variable.size == 0:
        return None
    values = np.asanyarray(variable[_sample_slices(variable.shape)]).compressed()
    if not values.size:
        return None
    return {"min": _number(values.min()), "max": _number(values.max())}


def _sample_slices(shape: tuple[int, ...]) -> tuple[slice, ...]:
    if not shape:
        return ()
    limit = max(1, int(MAX_SAMPLE_ROWS ** (1 / len(shape))))
    return tuple(slice(0, min(dimension, limit)) for dimension in shape)


def _netcdf_time_range(dataset: netCDF4.Dataset) -> dict[str, str] | None:
    variable = next((value for name, value in dataset.variables.items() if name.lower() == "time"), None)
    if variable is None or not getattr(variable, "units", None) or variable.size == 0:
        return None
    values = np.asanyarray(variable[_sample_slices(variable.shape)]).compressed()
    if not values.size:
        return None
    dates = netCDF4.num2date([values.min(), values.max()], variable.units, getattr(variable, "calendar", "standard"))
    return {"start": _iso_time(dates[0]), "end": _iso_time(dates[1])}


def _iso_time(value: Any) -> str:
    if isinstance(value, datetime):
        return value.replace(tzinfo=value.tzinfo or None).isoformat().replace("+00:00", "Z")
    return f"{value.isoformat()}Z"


def _json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if isinstance(value, np.generic):
        return value.item()
    if isinstance(value, (datetime,)):
        return _iso_time(value)
    if isinstance(value, bytes):
        return value.hex()
    return value


def _number(value: Any) -> float | int:
    numeric = _json_safe(value)
    return int(numeric) if isinstance(numeric, int) else float(numeric)


def _require_small_json(source: Path) -> None:
    if source.stat().st_size > MAX_JSON_BYTES:
        raise ValueError("JSON artifact exceeds inspection size limit")
