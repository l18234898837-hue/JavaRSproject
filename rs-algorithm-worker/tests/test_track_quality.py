from __future__ import annotations

import numpy as np

from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.operators.track_quality import QualityPolicy, apply_quality_policy


def _track(**overrides: np.ndarray) -> TrackData:
    fields = {
        "alt": np.array([100.0, 100.0, 100.0, 100.0]),
        "range_ku": np.array([10.0, 10.0, 10.0, 10.0]),
        "surface_type": np.array([0.0, 1.0, 0.0, 0.0]),
        "rain_flag": np.array([0.0, 0.0, 1.0, 0.0]),
        "ice_flag": np.zeros(4),
        "qual_alt_1hz_range_ku": np.zeros(4),
        "orb_state_flag": np.full(4, 3.0),
    }
    fields.update(overrides)
    return TrackData(
        time=np.arange(4, dtype=float), longitude=np.array([120.0, 121.0, 122.0, 123.0]),
        latitude=np.array([20.0, 21.0, 22.0, 23.0]), fields=fields,
        quality=np.ones(4, dtype=bool),
    )


def test_baseline_policy_marks_only_explicit_quality_failures_invalid() -> None:
    track = _track(range_ku=np.array([10.0, 10.0, 10.0, np.nan]))

    result = apply_quality_policy(track, QualityPolicy.baseline())

    assert result.policy.version == "hy2d-idr-2mc-baseline-v1"
    assert result.status.tolist() == [True, False, False, False]
    assert result.reason_codes == ((), ("NON_OCEAN_SURFACE",), ("RAIN_FLAG",), ("NONFINITE_RANGE_KU",))
    assert result.track.quality.tolist() == result.status.tolist()
    assert result.summary["invalidCount"] == 3


def test_quality_policy_records_each_applicable_reason() -> None:
    track = _track(
        ice_flag=np.array([1.0, 0.0, 0.0, 0.0]),
        qual_alt_1hz_range_ku=np.array([1.0, 0.0, 0.0, 0.0]),
        orb_state_flag=np.array([8.0, 3.0, 3.0, 3.0]),
    )

    result = apply_quality_policy(track)

    assert result.status.tolist() == [False, False, False, True]
    assert result.reason_codes[0] == ("ICE_FLAG", "RANGE_QUALITY_FLAG", "ORBIT_STATE_FLAG")


def test_orbit_state_uses_embedded_good_and_bad_meanings_without_rejecting_unknown_states() -> None:
    result = apply_quality_policy(_track(
        surface_type=np.zeros(4), rain_flag=np.zeros(4),
        orb_state_flag=np.array([3.0, 8.0, 4.0, 99.0]),
    ))

    assert result.status.tolist() == [True, False, True, True]
    assert result.reason_codes == ((), ("ORBIT_STATE_FLAG",), ("UNRESOLVED_ORBIT_STATE_FLAG",),
                                   ("UNKNOWN_ORBIT_STATE_FLAG",))


def test_unknown_binary_quality_codes_are_recorded_without_rejection() -> None:
    result = apply_quality_policy(_track(
        surface_type=np.zeros(4), rain_flag=np.array([2.0, 0.0, 0.0, 0.0]),
        ice_flag=np.array([0.0, 2.0, 0.0, 0.0]),
        qual_alt_1hz_range_ku=np.array([0.0, 0.0, 2.0, 0.0]),
    ))

    assert result.status.tolist() == [True, True, True, True]
    assert result.reason_codes == (("UNKNOWN_RAIN_FLAG",), ("UNKNOWN_ICE_FLAG",),
                                   ("UNKNOWN_RANGE_QUALITY_FLAG",), ())


def test_unknown_surface_code_is_recorded_but_not_rejected() -> None:
    result = apply_quality_policy(_track(surface_type=np.array([9.0, 0.0, 0.0, 0.0])))

    assert result.status[0]
    assert result.reason_codes[0] == ("UNKNOWN_SURFACE_TYPE",)
    assert result.summary["unknownSurfaceTypeCount"] == 1
