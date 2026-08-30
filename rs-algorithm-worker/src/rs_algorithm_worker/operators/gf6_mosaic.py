from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass

import numpy as np
import rasterio
from rasterio.windows import Window

from ..models_gf6 import Gf6ProductEvidence


@dataclass(frozen=True)
class MosaicWindow:
    row_off: int
    col_off: int
    height: int
    width: int


def iter_gf6_mosaic_windows(evidence: Gf6ProductEvidence, window_size: tuple[int, int] = (256, 256)) -> Iterator[MosaicWindow]:
    rows, cols = window_size
    if rows <= 0 or cols <= 0:
        raise ValueError("GF6 mosaic window size must be positive")
    for row in range(0, evidence.height, rows):
        for col in range(0, evidence.width, cols):
            yield MosaicWindow(row, col, min(rows, evidence.height - row), min(cols, evidence.width - col))


def read_gf6_window(evidence: Gf6ProductEvidence, window: MosaicWindow, bands: Sequence[int] = tuple(range(1, 9))) -> np.ndarray:
    if not bands or any(band not in range(1, 9) for band in bands):
        raise ValueError("GF6 band selection is invalid")
    output = np.zeros((len(bands), window.height, window.width), dtype=np.uint16)
    for tile in evidence.tiles:
        top, left = max(window.row_off, tile.row_offset), max(window.col_off, tile.column_offset)
        bottom, right = min(window.row_off + window.height, tile.row_offset + tile.height), min(window.col_off + window.width, tile.column_offset + tile.width)
        if bottom <= top or right <= left:
            continue
        with rasterio.open(tile.path) as source:
            values = source.read(bands, window=Window(left - tile.column_offset, top - tile.row_offset, right - left, bottom - top))
        output[:, top - window.row_off:bottom - window.row_off, left - window.col_off:right - window.col_off] = values
    return output
