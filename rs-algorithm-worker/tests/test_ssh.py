from __future__ import annotations

import numpy as np

from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.operators.ssh import SSH_VARIABLES, calculate_baseline_ssh


def _track(hf_correction: np.ndarray) -> TrackData:
    size = hf_correction.size
    fields = {name: np.ones(size, dtype=np.float64) for name in SSH_VARIABLES}
    fields["alt"] = np.full(size, 20.0)
    fields["range_ku"] = np.full(size, 10.0)
    fields["hf_fluctuations_corr"] = hf_correction
    return TrackData(
        time=np.arange(size, dtype=np.float64),
        longitude=np.linspace(120.0, 121.0, size),
        latitude=np.linspace(20.0, 21.0, size),
        fields=fields,
        quality=np.ones(size, dtype=bool),
    )


def test_all_missing_hf_correction_uses_audited_zero_fallback() -> None:
    result = calculate_baseline_ssh(_track(np.full(3, np.nan)))

    assert np.isfinite(result.values).all()
    assert result.summary["validCount"] == 3
    assert result.summary["missingRates"]["hf_fluctuations_corr"] == 1.0
    assert result.summary["allMissingTermFallbacks"] == {
        "hf_fluctuations_corr": "ZERO_WHEN_ALL_VALUES_ARE_MISSING"
    }


def test_partly_missing_hf_correction_is_not_silently_filled() -> None:
    result = calculate_baseline_ssh(_track(np.array([0.0, np.nan, 0.0])))

    assert np.isfinite(result.values[[0, 2]]).all()
    assert np.isnan(result.values[1])
    assert result.summary["allMissingTermFallbacks"] == {}
