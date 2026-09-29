from __future__ import annotations

import numpy as np
from netCDF4 import Dataset
import pytest
import rasterio

from rs_algorithm_worker.operators.track_grid import TrackGridSpec, bin_track_ssh, write_track_grid_outputs


def test_track_grid_spec_rejects_bounds_not_divisible_by_resolution() -> None:
    with pytest.raises(ValueError, match="divisible"):
        TrackGridSpec(min_lon=0.0, min_lat=0.0, max_lon=2.2, max_lat=1.0, resolution=1.0)


def test_bin_track_ssh_aggregates_valid_observations_without_interpolation() -> None:
    result = bin_track_ssh(
        ssh=np.array([1.0, 3.0, 100.0, np.nan]),
        longitude=np.array([0.1, 0.2, 1.1, 0.3]),
        latitude=np.array([0.1, 0.2, 0.1, 0.3]),
        quality_status=np.array([True, True, False, True]),
        grid=TrackGridSpec(min_lon=0.0, min_lat=0.0, max_lon=2.0, max_lat=1.0, resolution=1.0),
    )

    assert result.method == "bin-statistics-no-interpolation"
    assert result.count.tolist() == [[2, 0]]
    assert result.ssh_mean[0, 0] == 2.0
    assert result.ssh_std[0, 0] == 1.0
    assert result.valid_ratio[0, 0] == 2 / 3
    assert np.isnan(result.ssh_mean[0, 1])
    assert result.count[0, 1] == 0
    assert result.valid_ratio[0, 1] == 0.0


def test_write_track_grid_outputs_are_georeferenced_statistical_artifacts(tmp_path) -> None:
    result = bin_track_ssh(
        ssh=np.array([1.0]), longitude=np.array([0.1]), latitude=np.array([0.1]),
        quality_status=np.array([True]),
        grid=TrackGridSpec(min_lon=0.0, min_lat=0.0, max_lon=1.0, max_lat=1.0, resolution=1.0),
    )
    outputs = write_track_grid_outputs(result, tmp_path / "grid")

    with Dataset(outputs.paths["netcdf"]) as dataset:
        assert set(dataset.variables) == {"latitude", "longitude", "ssh_mean", "ssh_std", "count", "valid_ratio"}
        assert dataset.getncattr("method") == "bin-statistics-no-interpolation"
        assert dataset.getncattr("crs") == "EPSG:4326"
        assert dataset.variables["longitude"][:].tolist() == [0.5]
        assert dataset.variables["latitude"][:].tolist() == [0.5]
    with rasterio.open(outputs.paths["cog"]) as dataset:
        assert dataset.count == 4
        assert dataset.descriptions == ("ssh_mean", "ssh_std", "count", "valid_ratio")
        assert dataset.tags()["method"] == "bin-statistics-no-interpolation"
    summary = __import__("json").loads(outputs.paths["summary"].read_text(encoding="utf-8"))
    assert summary["method"] == "bin-statistics-no-interpolation"
    assert summary["explainability"]["algorithm"] == "HY-2D 沿轨 SSH 空间分箱"
    assert "不执行空间插值" in summary["explainability"]["method"]
