from __future__ import annotations

from collections.abc import Iterator

import h5py
import numpy as np


def iter_dataset_windows(dataset: h5py.Dataset, *, rows_per_window: int) -> Iterator[np.ndarray]:
    if dataset.ndim < 1:
        raise ValueError("windowed HDF reads require at least one dimension")
    if rows_per_window < 1:
        raise ValueError("rows per window must be positive")
    for start in range(0, dataset.shape[0], rows_per_window):
        yield dataset[start:start + rows_per_window]
