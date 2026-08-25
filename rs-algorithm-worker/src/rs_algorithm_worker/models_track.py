from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping

import numpy as np


@dataclass(frozen=True)
class TrackData:
    """One-dimensional along-track observations and decoded variables."""

    time: np.ndarray
    longitude: np.ndarray
    latitude: np.ndarray
    fields: Mapping[str, np.ndarray]
    quality: np.ndarray
    metadata: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        size = self.latitude.size
        if self.latitude.ndim != 1 or self.longitude.ndim != 1 or self.time.ndim != 1:
            raise ValueError("track coordinates and time must be one-dimensional")
        if self.longitude.size != size or self.time.size != size or self.quality.size != size:
            raise ValueError("track coordinates, quality and time must have matching length")
        for name, values in self.fields.items():
            if np.asarray(values).ndim != 1 or np.asarray(values).size != size:
                raise ValueError(f"track field {name!r} must match the 1 Hz track length")
