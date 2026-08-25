from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class GridSpec:
    min_lon: float
    min_lat: float
    max_lon: float
    max_lat: float
    resolution: float

    def __post_init__(self) -> None:
        if self.max_lon <= self.min_lon or self.max_lat <= self.min_lat or self.resolution <= 0:
            raise ValueError("grid bounds and resolution are invalid")


@dataclass(frozen=True)
class GriddedValues:
    values: np.ndarray
    bounds: tuple[float, float, float, float]
    resolution: float


def grid_nearest(*, values: np.ndarray, longitude: np.ndarray, latitude: np.ndarray, grid: GridSpec) -> GriddedValues:
    if values.shape != longitude.shape or values.shape != latitude.shape:
        raise ValueError("values and coordinates must have matching shape")
    width = int(np.ceil((grid.max_lon - grid.min_lon) / grid.resolution))
    height = int(np.ceil((grid.max_lat - grid.min_lat) / grid.resolution))
    output = np.full((height, width), np.nan, dtype=np.float32)
    distances = np.full((height, width), np.inf, dtype=np.float64)
    for value, lon, lat in zip(values.ravel(), longitude.ravel(), latitude.ravel()):
        if not (np.isfinite(value) and np.isfinite(lon) and np.isfinite(lat)):
            continue
        column = int(np.floor((lon - grid.min_lon) / grid.resolution))
        row = height - 1 - int(np.floor((lat - grid.min_lat) / grid.resolution))
        if not (0 <= column < width and 0 <= row < height):
            continue
        center_lon = grid.min_lon + (column + 0.5) * grid.resolution
        center_lat = grid.min_lat + (row + 0.5) * grid.resolution
        distance = (lon - center_lon) ** 2 + (lat - center_lat) ** 2
        if distance < distances[row, column]:
            output[row, column] = value
            distances[row, column] = distance
    return GriddedValues(output, (grid.min_lon, grid.min_lat, grid.max_lon, grid.max_lat), grid.resolution)
