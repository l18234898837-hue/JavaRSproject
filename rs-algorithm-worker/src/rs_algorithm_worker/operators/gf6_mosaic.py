from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Sequence

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


def iter_gf6_mosaic_windows(evidence: Gf6ProductEvidence, window_size: tuple[int, int] = (1024, 1024)) -> Iterator[MosaicWindow]:
    rows, cols = window_size
    if rows <= 0 or cols <= 0:
        raise ValueError("GF6 mosaic window size must be positive")
    for row in range(0, evidence.height, rows):
        for col in range(0, evidence.width, cols):
            yield MosaicWindow(row, col, min(rows, evidence.height - row), min(cols, evidence.width - col))


def read_gf6_window(evidence: Gf6ProductEvidence, window: MosaicWindow, bands: Sequence[int] = tuple(range(1, 9))) -> np.ndarray:
    if window.row_off < 0 or window.col_off < 0 or window.row_off + window.height > evidence.height or window.col_off + window.width > evidence.width:
        raise ValueError("GF6 mosaic window is outside the product")
    selected = tuple(bands)
    if not selected or any(band < 1 or band > 8 for band in selected):
        raise ValueError("GF6 band selection is invalid")
    result = np.zeros((len(selected), window.height, window.width), dtype=np.uint16)
    for tile in evidence.tiles:
        row_start = max(window.row_off, tile.row_offset)
        col_start = max(window.col_off, tile.column_offset)
        row_end = min(window.row_off + window.height, tile.row_offset + tile.height)
        col_end = min(window.col_off + window.width, tile.column_offset + tile.width)
        if row_end <= row_start or col_end <= col_start:
            continue
        src_window = Window(col_start - tile.column_offset, row_start - tile.row_offset, col_end - col_start, row_end - row_start)
        dst_rows = slice(row_start - window.row_off, row_end - window.row_off)
        dst_cols = slice(col_start - window.col_off, col_end - window.col_off)
        with rasterio.open(tile.path) as dataset:
            values = dataset.read(selected, window=src_window, out_dtype="uint16")
        result[:, dst_rows, dst_cols] = values
    return result
