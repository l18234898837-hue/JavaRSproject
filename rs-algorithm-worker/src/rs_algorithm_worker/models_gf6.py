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
        if not 1 <= self.number <= 8 or not self.wavelength or not math.isfinite(self.gain) or not math.isfinite(self.offset):
            raise ValueError("GF6_EVIDENCE_INVALID: band calibration")


@dataclass(frozen=True)
class Gf6TileSpec:
    path: Path
    ordinal: int
    width: int
    height: int
    column_offset: int
    row_offset: int

    def __post_init__(self) -> None:
        if self.ordinal not in (1, 2, 3) or min(self.width, self.height) <= 0 or min(self.column_offset, self.row_offset) < 0:
            raise ValueError("GF6_EVIDENCE_INVALID: tile")


@dataclass(frozen=True)
class Gf6CalibrationSpec:
    bands: tuple[Gf6BandSpec, ...]
    unit: str = "W.m-2.sr-1.um-1"
    formula: str = "AGain*DN+AOffset"
    fill_value: int | None = None

    def __post_init__(self) -> None:
        if len(self.bands) != 8 or tuple(band.number for band in self.bands) != tuple(range(1, 9)) or not self.unit or not self.formula:
            raise ValueError("GF6_EVIDENCE_INVALID: calibration")


@dataclass(frozen=True)
class Gf6RpcSpec:
    line_offset: float; sample_offset: float; latitude_offset: float; longitude_offset: float; height_offset: float
    line_scale: float; sample_scale: float; latitude_scale: float; longitude_scale: float; height_scale: float
    line_num: tuple[float, ...]; line_den: tuple[float, ...]; sample_num: tuple[float, ...]; sample_den: tuple[float, ...]

    def __post_init__(self) -> None:
        coefficients = self.line_num + self.line_den + self.sample_num + self.sample_den
        scales = (self.line_scale, self.sample_scale, self.latitude_scale, self.longitude_scale, self.height_scale)
        if any(len(value) != 20 for value in (self.line_num, self.line_den, self.sample_num, self.sample_den)) or not all(math.isfinite(value) for value in coefficients + scales) or any(value == 0 for value in scales):
            raise ValueError("GF6_EVIDENCE_INVALID: RPC coefficients")


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
    rpc: Gf6RpcSpec
    metadata_snapshot: dict[str, Any]
    footprint_paths: tuple[Path, ...] = ()

    def __post_init__(self) -> None:
        if len(self.tiles) != 3 or len(self.bands) != 8 or len(self.footprint_paths) not in (0, 3):
            raise ValueError("GF6_EVIDENCE_INVALID: incomplete package")

    @property
    def width(self) -> int:
        return max(tile.column_offset + tile.width for tile in self.tiles)

    @property
    def height(self) -> int:
        return max(tile.row_offset + tile.height for tile in self.tiles)
