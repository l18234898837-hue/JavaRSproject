from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import math


@dataclass(frozen=True)
class Gf6BandSpec:
    number: int
    wavelength: str
    gain: float
    offset: float

    def __post_init__(self) -> None:
        if not 1 <= self.number <= 8 or not self.wavelength.strip():
            raise ValueError("GF6 band evidence is invalid")
        if not math.isfinite(self.gain) or not math.isfinite(self.offset):
            raise ValueError("GF6 calibration evidence is invalid")


@dataclass(frozen=True)
class Gf6TileSpec:
    path: Path
    ordinal: int
    width: int
    height: int
    column_offset: int
    row_offset: int

    def __post_init__(self) -> None:
        if self.ordinal < 1 or min(self.width, self.height) <= 0 or min(self.column_offset, self.row_offset) < 0:
            raise ValueError("GF6 tile evidence is invalid")


@dataclass(frozen=True)
class Gf6CalibrationSpec:
    bands: tuple[Gf6BandSpec, ...]
    unit: str = "W.m-2.sr-1.um-1"
    formula: str = "AGain*DN+AOffset"
    fill_value: int | None = None

    def __post_init__(self) -> None:
        if len(self.bands) != 8 or tuple(item.number for item in self.bands) != tuple(range(1, 9)):
            raise ValueError("GF6 calibration must contain eight ordered bands")
        if not self.unit.strip() or not self.formula.strip():
            raise ValueError("GF6 calibration metadata is incomplete")


@dataclass(frozen=True)
class Gf6RpcSpec:
    line_offset: float
    sample_offset: float
    latitude_offset: float
    longitude_offset: float
    height_offset: float
    line_scale: float
    sample_scale: float
    latitude_scale: float
    longitude_scale: float
    height_scale: float
    line_num: tuple[float, ...]
    line_den: tuple[float, ...]
    sample_num: tuple[float, ...]
    sample_den: tuple[float, ...]

    def __post_init__(self) -> None:
        values = (self.line_offset, self.sample_offset, self.latitude_offset, self.longitude_offset,
                  self.height_offset, self.line_scale, self.sample_scale, self.latitude_scale,
                  self.longitude_scale, self.height_scale, *self.line_num, *self.line_den,
                  *self.sample_num, *self.sample_den)
        if len(self.line_num) != 20 or len(self.line_den) != 20 or len(self.sample_num) != 20 or len(self.sample_den) != 20:
            raise ValueError("GF6 RPC coefficients must contain twenty terms")
        if not all(math.isfinite(value) for value in values) or any(abs(value) == 0 for value in (
                self.line_scale, self.sample_scale, self.latitude_scale, self.longitude_scale, self.height_scale)):
            raise ValueError("GF6 RPC metadata is invalid")


@dataclass(frozen=True)
class Gf6ProductEvidence:
    package_id: str
    product_id: str
    xml_path: Path
    rpc_path: Path
    nav_path: Path
    til_path: Path
    tiles: tuple[Gf6TileSpec, ...]
    bands: tuple[Gf6BandSpec, ...]
    calibration: Gf6CalibrationSpec
    rpc: Gf6RpcSpec
    footprint: tuple[tuple[float, float], ...]
    metadata_snapshot: dict[str, Any]

    def __post_init__(self) -> None:
        if not self.package_id.strip() or not self.product_id.strip() or len(self.tiles) != 3:
            raise ValueError("GF6 product evidence is incomplete")
        if len(self.footprint) < 4 or self.footprint[0] != self.footprint[-1]:
            raise ValueError("GF6 footprint evidence is invalid")

    @property
    def width(self) -> int:
        declared = self.metadata_snapshot.get("declaredDimensions")
        return int(declared[0]) if isinstance(declared, (list, tuple)) and len(declared) == 2 else max(tile.column_offset + tile.width for tile in self.tiles)

    @property
    def height(self) -> int:
        declared = self.metadata_snapshot.get("declaredDimensions")
        return int(declared[1]) if isinstance(declared, (list, tuple)) and len(declared) == 2 else max(tile.row_offset + tile.height for tile in self.tiles)
