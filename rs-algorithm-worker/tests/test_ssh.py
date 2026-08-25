from __future__ import annotations

import numpy as np

from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.operators.ssh import calculate_baseline_ssh


def _track(**overrides: np.ndarray) -> TrackData:
    fields = {
        "alt": np.array([100.0, 100.0]), "range_ku": np.array([10.0, 10.0]),
        "model_dry_tropo_corr": np.array([1.0, 1.0]), "rad_wet_tropo_corr": np.array([2.0, 2.0]),
        "iono_corr_alt_ku": np.array([3.0, 3.0]), "sea_state_bias_ku": np.array([4.0, 4.0]),
        "inv_bar_corr": np.array([5.0, 5.0]), "hf_fluctuations_corr": np.array([6.0, 6.0]),
        "ocean_tide_sol1": np.array([7.0, 7.0]), "solid_earth_tide": np.array([8.0, 8.0]),
        "pole_tide": np.array([9.0, 9.0]), "load_tide_sol1": np.array([10.0, 10.0]),
    }
    fields.update(overrides)
    return TrackData(
        time=np.arange(2, dtype=float), longitude=np.array([120.0, 121.0]),
        latitude=np.array([20.0, 21.0]), fields=fields, quality=np.array([True, True]),
    )


def test_calculate_baseline_ssh_uses_all_documented_terms() -> None:
    result = calculate_baseline_ssh(_track())

    assert result.values.tolist() == [35.0, 35.0]
    assert result.summary["sshFormulaVersion"] == "hy2d-idr-2mc-baseline-v1"
    assert result.summary["variables"] == [
        "alt", "range_ku", "model_dry_tropo_corr", "rad_wet_tropo_corr", "iono_corr_alt_ku",
        "sea_state_bias_ku", "inv_bar_corr", "hf_fluctuations_corr", "ocean_tide_sol1",
        "solid_earth_tide", "pole_tide", "load_tide_sol1",
    ]


def test_missing_or_nonfinite_formula_term_produces_nan_and_summary_rate() -> None:
    result = calculate_baseline_ssh(_track(rad_wet_tropo_corr=np.array([np.nan, 2.0])))

    assert np.isnan(result.values[0])
    assert result.values[1] == 35.0
    assert result.summary["missingRates"]["rad_wet_tropo_corr"] == 0.5
