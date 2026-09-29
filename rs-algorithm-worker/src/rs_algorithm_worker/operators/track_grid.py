from __future__ import annotations

from dataclasses import dataclass, field
import json
from pathlib import Path
from typing import Any, Mapping

import numpy as np
from netCDF4 import Dataset
import rasterio
from rasterio.transform import from_origin


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
        columns = (self.max_lon - self.min_lon) / self.resolution
        rows = (self.max_lat - self.min_lat) / self.resolution
        if not (np.isclose(columns, round(columns), rtol=0.0, atol=1e-9)
                and np.isclose(rows, round(rows), rtol=0.0, atol=1e-9)):
            raise ValueError("grid bounds must be divisible by resolution")


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
        "cogStatus": "provided: four-band statistical grid, not an image product",
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


@dataclass(frozen=True)
class TrackGridOutput:
    output_dir: Path
    paths: Mapping[str, Path]
    summary: Mapping[str, Any]


def write_track_grid_outputs(result: TrackGridResult, output_dir: Path) -> TrackGridOutput:
    """Write all declared optional-grid artifacts inside one caller-owned directory."""
    destination = Path(output_dir).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    paths = {
        "netcdf": _safe_child(destination, "track-grid.nc"),
        "cog": _safe_child(destination, "track-grid.tif"),
        "summary": _safe_child(destination, "summary.json"),
    }
    write_track_grid_netcdf(result, paths["netcdf"])
    write_track_grid_cog(result, paths["cog"])
    summary = {
        "method": result.method,
        "bounds": _bounds(result.grid),
        "resolution": result.grid.resolution,
        "gridShape": list(result.ssh_mean.shape),
        "observedBinCount": int((result.count > 0).sum()),
        "validObservationCount": int(result.count.sum()),
        "scientificBoundary": result.metadata["scientificBoundary"],
        "explainability": {
            "version": "hy2d-grid-explainability-v1",
            "algorithm": "HY-2D 沿轨 SSH 空间分箱",
            "purpose": "将离散沿轨观测按经纬度网格聚合，便于地图展示轨迹覆盖和局部统计。",
            "method": "仅统计落入网格的观测点，不执行空间插值。",
            "bandDefinitions": {
                "ssh_mean": "网格内通过质量筛选的 SSH 平均值。",
                "ssh_std": "网格内通过质量筛选的 SSH 总体标准差。",
                "count": "网格内通过质量筛选且 SSH 有限的观测点数量。",
                "valid_ratio": "有效观测点数量除以落入该网格的全部有限坐标观测点数量。",
            },
            "interpretation": "空网格表示没有轨迹观测覆盖，不表示 SSH 为零；count 较小时均值和标准差稳定性有限。",
            "limitations": [
                "结果是沿轨观测的空间分箱统计，不是连续海面场，也没有填补轨迹之间的空白。",
                "经度跨越日期变更线时可能使用连续展开坐标域，展示端应结合 bounds 解释。",
            ],
        },
    }
    paths["summary"].write_text(json.dumps(summary, indent=2, sort_keys=True, allow_nan=False), encoding="utf-8")
    return TrackGridOutput(destination, paths, summary)


def write_track_grid_netcdf(result: TrackGridResult, output_path: Path) -> Path:
    """Write a georeferenced statistical grid to NetCDF without interpolation."""
    path = Path(output_path).resolve()
    if not path.parent.is_dir():
        raise ValueError("output parent directory must exist")
    with Dataset(path, "w", format="NETCDF4") as dataset:
        dataset.createDimension("latitude", result.ssh_mean.shape[0])
        dataset.createDimension("longitude", result.ssh_mean.shape[1])
        latitude, longitude = _grid_centers(result.grid, result.ssh_mean.shape)
        dataset.createVariable("latitude", "f8", ("latitude",))[:] = latitude
        dataset.createVariable("longitude", "f8", ("longitude",))[:] = longitude
        for name, values, dtype in (
            ("ssh_mean", result.ssh_mean, "f8"),
            ("ssh_std", result.ssh_std, "f8"),
            ("count", result.count, "i4"),
            ("valid_ratio", result.valid_ratio, "f8"),
        ):
            variable = dataset.createVariable(name, dtype, ("latitude", "longitude"), fill_value=np.nan if dtype == "f8" else None)
            variable[:] = values
        dataset.setncattr("method", result.method)
        dataset.setncattr("crs", "EPSG:4326")
        dataset.setncattr("bounds", json.dumps(_bounds(result.grid)))
        dataset.setncattr("resolution", result.grid.resolution)
        dataset.setncattr("latitude_row_order", "north-to-south")
        for key, value in result.metadata.items():
            dataset.setncattr(key, str(value))
    return path


def write_track_grid_cog(result: TrackGridResult, output_path: Path) -> Path:
    """Write the four declared statistical bands as a georeferenced COG."""
    path = Path(output_path).resolve()
    if not path.parent.is_dir():
        raise ValueError("output parent directory must exist")
    height, width = result.ssh_mean.shape
    values = np.stack((
        result.ssh_mean,
        result.ssh_std,
        result.count.astype(np.float64),
        result.valid_ratio,
    ))
    transform = from_origin(result.grid.min_lon, result.grid.max_lat, result.grid.resolution, result.grid.resolution)
    with rasterio.open(
        path, "w", driver="COG", width=width, height=height, count=4, dtype="float64",
        crs="EPSG:4326", transform=transform, nodata=np.nan, BLOCKSIZE=128, COMPRESS="DEFLATE",
    ) as dataset:
        dataset.write(values)
        for index, name in enumerate(("ssh_mean", "ssh_std", "count", "valid_ratio"), start=1):
            dataset.set_band_description(index, name)
        dataset.update_tags(method=result.method, product_kind="statistical-grid-no-interpolation")
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


def _safe_child(output_dir: Path, filename: str) -> Path:
    path = (output_dir / filename).resolve()
    if path.parent != output_dir:
        raise ValueError("output path escapes the requested output directory")
    return path


def _bounds(grid: TrackGridSpec) -> dict[str, float]:
    return {"minLon": grid.min_lon, "minLat": grid.min_lat, "maxLon": grid.max_lon, "maxLat": grid.max_lat}


def _grid_centers(grid: TrackGridSpec, shape: tuple[int, int]) -> tuple[np.ndarray, np.ndarray]:
    height, width = shape
    longitude = grid.min_lon + (np.arange(width, dtype=np.float64) + 0.5) * grid.resolution
    latitude = grid.max_lat - (np.arange(height, dtype=np.float64) + 0.5) * grid.resolution
    return latitude, longitude
