from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping

import numpy as np
from netCDF4 import Dataset


_GRID_METHOD = "bin-statistics-no-interpolation"


@dataclass(frozen=True)
class TrackGridSpec:
    min_lon: float
    min_lat: float
    max_lon: float
    max_lat: float
    resolution: float

    def __post_init__(self) -> None:
        values = (self.min_lon, self.min_lat, self.max_lon, self.max_lat, self.resolution)
        if not all(np.isfinite(values)) or self.max_lon <= self.min_lon or self.max_lat <= self.min_lat:
            raise ValueError("grid bounds must be finite and increasing")
        if self.resolution <= 0:
            raise ValueError("grid resolution must be positive")


@dataclass(frozen=True)
class TrackGridResult:
    ssh_mean: np.ndarray
    ssh_std: np.ndarray
    count: np.ndarray
    valid_ratio: np.ndarray
    grid: TrackGridSpec
    method: str = _GRID_METHOD
    metadata: Mapping[str, Any] = field(default_factory=lambda: {
        "method": _GRID_METHOD,
        "scientificBoundary": "Track observations are aggregated by spatial bin; no interpolation is performed.",
        "cogStatus": "deferred: this is a statistical grid, not an image product",
    })


def bin_track_ssh(
    *,
    ssh: np.ndarray,
    longitude: np.ndarray,
    latitude: np.ndarray,
    quality_status: np.ndarray,
    grid: TrackGridSpec,
) -> TrackGridResult:
    """Aggregate valid finite SSH observations by bin without interpolation."""
    values, lon, lat, quality = _validated_inputs(ssh, longitude, latitude, quality_status)
    width = int(np.ceil((grid.max_lon - grid.min_lon) / grid.resolution))
    height = int(np.ceil((grid.max_lat - grid.min_lat) / grid.resolution))
    sums = np.zeros((height, width), dtype=np.float64)
    squared_sums = np.zeros((height, width), dtype=np.float64)
    valid_count = np.zeros((height, width), dtype=np.int32)
    observed_count = np.zeros((height, width), dtype=np.int32)

    for value, x, y, accepted in zip(values, lon, lat, quality):
        if not (np.isfinite(x) and np.isfinite(y)):
            continue
        column = int(np.floor((x - grid.min_lon) / grid.resolution))
        row = height - 1 - int(np.floor((y - grid.min_lat) / grid.resolution))
        if not (0 <= row < height and 0 <= column < width):
            continue
        observed_count[row, column] += 1
        if accepted and np.isfinite(value):
            valid_count[row, column] += 1
            sums[row, column] += value
            squared_sums[row, column] += value * value

    mean = np.full((height, width), np.nan, dtype=np.float64)
    std = np.full((height, width), np.nan, dtype=np.float64)
    has_valid = valid_count > 0
    mean[has_valid] = sums[has_valid] / valid_count[has_valid]
    variance = np.zeros((height, width), dtype=np.float64)
    variance[has_valid] = squared_sums[has_valid] / valid_count[has_valid] - mean[has_valid] ** 2
    std[has_valid] = np.sqrt(np.maximum(variance[has_valid], 0.0))
    valid_ratio = np.zeros((height, width), dtype=np.float64)
    has_observations = observed_count > 0
    valid_ratio[has_observations] = valid_count[has_observations] / observed_count[has_observations]
    return TrackGridResult(mean, std, valid_count, valid_ratio, grid)


def write_track_grid_netcdf(result: TrackGridResult, output_path: Path) -> Path:
    """Write the statistical grid to NetCDF. It deliberately does not create a COG."""
    path = Path(output_path).resolve()
    if not path.parent.is_dir():
        raise ValueError("output parent directory must exist")
    with Dataset(path, "w", format="NETCDF4") as dataset:
        dataset.createDimension("latitude", result.ssh_mean.shape[0])
        dataset.createDimension("longitude", result.ssh_mean.shape[1])
        for name, values, dtype in (
            ("ssh_mean", result.ssh_mean, "f8"),
            ("ssh_std", result.ssh_std, "f8"),
            ("count", result.count, "i4"),
            ("valid_ratio", result.valid_ratio, "f8"),
        ):
            variable = dataset.createVariable(name, dtype, ("latitude", "longitude"), fill_value=np.nan if dtype == "f8" else None)
            variable[:] = values
        dataset.setncattr("method", result.method)
        for key, value in result.metadata.items():
            dataset.setncattr(key, str(value))
    return path


def _validated_inputs(
    ssh: np.ndarray, longitude: np.ndarray, latitude: np.ndarray, quality_status: np.ndarray
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    values = np.asarray(ssh, dtype=np.float64)
    lon = np.asarray(longitude, dtype=np.float64)
    lat = np.asarray(latitude, dtype=np.float64)
    quality = np.asarray(quality_status, dtype=bool)
    if any(item.ndim != 1 for item in (values, lon, lat, quality)):
        raise ValueError("track grid inputs must be one-dimensional")
    if not (values.shape == lon.shape == lat.shape == quality.shape):
        raise ValueError("track grid inputs must have matching shapes")
    return values, lon, lat, quality
