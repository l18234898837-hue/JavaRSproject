from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

import numpy as np

from rs_algorithm_worker.models_track import TrackData


SSH_FORMULA_VERSION = "hy2d-idr-2mc-baseline-v1"
SSH_VARIABLES = (
    "alt",
    "range_ku",
    "model_dry_tropo_corr",
    "rad_wet_tropo_corr",
    "iono_corr_alt_ku",
    "sea_state_bias_ku",
    "inv_bar_corr",
    "hf_fluctuations_corr",
    "ocean_tide_sol1",
    "solid_earth_tide",
    "pole_tide",
    "load_tide_sol1",
)


@dataclass(frozen=True)
class SshResult:
    values: np.ndarray
    summary: Mapping[str, Any]


def _formula_values(track: TrackData, name: str) -> np.ndarray:
    values = track.fields.get(name)
    if values is None:
        return np.full(track.latitude.size, np.nan, dtype=np.float64)
    return np.asarray(values, dtype=np.float64)


def calculate_baseline_ssh(track: TrackData) -> SshResult:
    """Calculate the declared HY-2D baseline SSH, preserving missing terms as NaN."""
    terms = {name: _formula_values(track, name) for name in SSH_VARIABLES}
    range_corrections = sum(
        (terms[name] for name in SSH_VARIABLES[2:8]), np.zeros(track.latitude.size, dtype=np.float64)
    )
    tides = sum(
        (terms[name] for name in SSH_VARIABLES[8:]), np.zeros(track.latitude.size, dtype=np.float64)
    )
    values = terms["alt"] - (terms["range_ku"] + range_corrections) - tides
    valid = np.logical_and.reduce([np.isfinite(terms[name]) for name in SSH_VARIABLES])
    valid &= np.asarray(track.quality, dtype=bool)
    values = np.asarray(values, dtype=np.float64)
    values[~valid] = np.nan
    summary: dict[str, Any] = {
        "sshFormulaVersion": SSH_FORMULA_VERSION,
        "variables": list(SSH_VARIABLES),
        "missingRates": {
            name: float((~np.isfinite(terms[name])).mean()) if terms[name].size else 0.0
            for name in SSH_VARIABLES
        },
        "validCount": int(valid.sum()),
        "invalidCount": int((~valid).sum()),
    }
    return SshResult(values=values, summary=summary)
