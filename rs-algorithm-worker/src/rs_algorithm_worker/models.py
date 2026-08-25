from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping

import numpy as np


@dataclass(frozen=True)
class BandMetadata:
    name: str
    wavelength_nm: float | None = None
    unit: str | None = None

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise ValueError("band name is required")


@dataclass(frozen=True)
class RasterData:
    values: np.ndarray
    crs: str | None
    transform: Any | None
    nodata: float | int | None
    band_metadata: tuple[BandMetadata, ...]

    def __post_init__(self) -> None:
        if self.values.ndim not in {2, 3}:
            raise ValueError("raster values must have two or three dimensions")
        expected_bands = 1 if self.values.ndim == 2 else self.values.shape[0]
        if len(self.band_metadata) != expected_bands:
            raise ValueError("band metadata count must match raster band count")
        if self.crs is not None and not self.crs.strip():
            raise ValueError("CRS must not be blank")


@dataclass(frozen=True)
class SwathData:
    values: np.ndarray
    longitude: np.ndarray
    latitude: np.ndarray
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.values.shape != self.longitude.shape or self.values.shape != self.latitude.shape:
            raise ValueError("swath values and coordinates must have matching shape")


@dataclass(frozen=True)
class GridData:
    raster: RasterData
    grid_spacing: tuple[float, float]

    def __post_init__(self) -> None:
        if any(value <= 0 for value in self.grid_spacing):
            raise ValueError("grid spacing must be positive")


@dataclass(frozen=True)
class BandSet:
    raster: RasterData

    def __post_init__(self) -> None:
        if self.raster.values.ndim != 3:
            raise ValueError("band set requires a multi-band raster")


@dataclass(frozen=True)
class ProductContext:
    profile: str
    data_shape: str
    adapter_version: str = "unspecified"
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.profile.strip() or not self.data_shape.strip() or not self.adapter_version.strip():
            raise ValueError("product context is incomplete")
