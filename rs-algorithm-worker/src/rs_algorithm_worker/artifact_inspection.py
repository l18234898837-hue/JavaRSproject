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
    return result


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
    return {"format": "CSV", "kind": "table", "columns": columns, "rowCount": row_count, "rows": rows}


def _inspect_parquet(source: Path) -> dict[str, Any]:
    parquet = pq.ParquetFile(source)
    columns = parquet.schema_arrow.names
    rows: list[dict[str, Any]] = []
    for batch in parquet.iter_batches(batch_size=MAX_SAMPLE_ROWS):
        rows.extend(batch.to_pylist())
        break
    return {
        "format": "PARQUET",
        "kind": "dataset",
        "columns": columns,
        "rowCount": parquet.metadata.num_rows,
        "rows": _json_safe(rows),
        "statistics": _parquet_statistics(parquet),
    }


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
        return result


def _inspect_npz(source: Path) -> dict[str, Any]:
    with np.load(source, allow_pickle=False) as archive:
        arrays = [
            {"name": name, "shape": list(archive[name].shape), "dtype": str(archive[name].dtype)}
            for name in archive.files
        ]
    return {"format": "NPZ", "kind": "dataset", "arrays": arrays}


def _table_result(artifact_format: str, records: Iterable[dict[str, Any]], row_count: int) -> dict[str, Any]:
    sample = list(records)[:MAX_SAMPLE_ROWS]
    columns = list(dict.fromkeys(key for row in sample for key in row))
    return {
        "format": artifact_format,
        "kind": "table",
        "columns": columns,
        "rowCount": row_count,
        "rows": _json_safe(sample),
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
