from __future__ import annotations

import numpy as np
from netCDF4 import Dataset

from rs_algorithm_worker.operators.track_grid import TrackGridSpec, bin_track_ssh, write_track_grid_netcdf


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


def test_write_track_grid_netcdf_declares_statistical_no_interpolation_method(tmp_path) -> None:
    result = bin_track_ssh(
        ssh=np.array([1.0]), longitude=np.array([0.1]), latitude=np.array([0.1]),
        quality_status=np.array([True]),
        grid=TrackGridSpec(min_lon=0.0, min_lat=0.0, max_lon=1.0, max_lat=1.0, resolution=1.0),
    )
    output = write_track_grid_netcdf(result, tmp_path / "track-grid.nc")

    with Dataset(output) as dataset:
        assert set(dataset.variables) == {"ssh_mean", "ssh_std", "count", "valid_ratio"}
        assert dataset.getncattr("method") == "bin-statistics-no-interpolation"
