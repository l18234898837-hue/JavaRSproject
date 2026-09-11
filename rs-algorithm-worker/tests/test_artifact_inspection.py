import csv
import json

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

from rs_algorithm_worker.artifact_inspection import inspect_artifact
from rs_algorithm_worker.contracts import validate_invocation
from rs_algorithm_worker.registry import dispatch


def test_inspection_returns_bounded_json_table_summary(tmp_path):
    source = tmp_path / "summary.json"
    source.write_text(json.dumps([{"id": index, "name": f"row-{index}"} for index in range(120)]), encoding="utf-8")

    result = inspect_artifact(source, "JSON")

    assert result["format"] == "JSON"
    assert result["kind"] == "table"
    assert result["columns"] == ["id", "name"]
    assert result["rowCount"] == 120
    assert len(result["rows"]) == 100
    assert result["rows"][0] == {"id": 0, "name": "row-0"}


def test_inspection_returns_csv_schema_and_bounded_rows(tmp_path):
    source = tmp_path / "track.csv"
    with source.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["longitude", "latitude", "height"])
        writer.writeheader()
        writer.writerows(
            {"longitude": 120 + index / 10, "latitude": 30 + index / 10, "height": index}
            for index in range(103)
        )

    result = inspect_artifact(source, "CSV")

    assert result["format"] == "CSV"
    assert result["kind"] == "table"
    assert result["columns"] == ["longitude", "latitude", "height"]
    assert result["rowCount"] == 103
    assert len(result["rows"]) == 100


def test_inspection_returns_parquet_schema_statistics_and_bounded_rows(tmp_path):
    source = tmp_path / "track.parquet"
    pq.write_table(pa.table({"height": list(range(120)), "quality": [1] * 120}), source)

    result = inspect_artifact(source, "PARQUET")

    assert result["format"] == "PARQUET"
    assert result["kind"] == "dataset"
    assert result["columns"] == ["height", "quality"]
    assert result["rowCount"] == 120
    assert len(result["rows"]) == 100
    assert result["statistics"]["height"] == {"min": 0, "max": 119}


def test_inspection_returns_netcdf_dimensions_variables_and_time_range(tmp_path):
    netcdf4 = __import__("netCDF4")
    source = tmp_path / "track.nc"
    with netcdf4.Dataset(source, "w") as dataset:
        dataset.createDimension("time", 3)
        time = dataset.createVariable("time", "i8", ("time",))
        time.units = "seconds since 2026-01-01 00:00:00 UTC"
        time[:] = [0, 60, 120]
        height = dataset.createVariable("height", "f4", ("time",))
        height[:] = [1.5, 2.0, 3.5]

    result = inspect_artifact(source, "NETCDF")

    assert result["format"] == "NETCDF"
    assert result["kind"] == "dataset"
    assert result["dimensions"] == {"time": 3}
    assert result["variables"] == ["time", "height"]
    assert result["timeRange"] == {"start": "2026-01-01T00:00:00Z", "end": "2026-01-01T00:02:00Z"}
    assert result["statistics"]["height"] == {"min": 1.5, "max": 3.5}


def test_inspection_reports_npz_metadata_without_exposing_values(tmp_path):
    source = tmp_path / "decoded.npz"
    np.savez(source, band=np.zeros((4, 5), dtype=np.float32))

    result = inspect_artifact(source, "NPZ")

    assert result == {
        "format": "NPZ",
        "kind": "dataset",
        "arrays": [{"name": "band", "shape": [4, 5], "dtype": "float32"}],
    }


def test_inspect_artifact_operation_uses_only_the_authorized_manifest_input(tmp_path):
    source = tmp_path / "summary.json"
    source.write_text('[{"id": 1}]', encoding="utf-8")
    request = {
        "schemaVersion": "1.0",
        "operation": "inspect-artifact",
        "requestId": "inspect-artifact-01",
        "payload": {
            "format": "JSON",
            "inputs": [{"storageUriSnapshot": {"value": "rs://output/7/task-01/summary.json"}}],
        },
    }
    manifest = {
        "manifestVersion": "1.0",
        "requestId": "inspect-artifact-01",
        "grantReference": "grant-01",
        "expiresAt": "2099-01-01T00:00:00+00:00",
        "inputs": [{
            "storageUri": "rs://output/7/task-01/summary.json",
            "role": "ARTIFACT",
            "ordinal": 0,
            "path": str(source),
        }],
        "output": {"path": str(tmp_path / "output")},
    }

    validate_invocation(request, manifest)

    assert dispatch(request, manifest) == {
        "format": "JSON",
        "kind": "table",
        "columns": ["id"],
        "rowCount": 1,
        "rows": [{"id": 1}],
    }
