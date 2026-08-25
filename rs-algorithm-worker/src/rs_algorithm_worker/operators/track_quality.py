from __future__ import annotations

from dataclasses import dataclass, replace
from typing import Any, Mapping

import numpy as np

from rs_algorithm_worker.models_track import TrackData


@dataclass(frozen=True)
class QualityPolicy:
    """Versioned, conservative HY-2D IDR 2MC quality policy."""

    version: str
    ocean_surface_types: frozenset[float]
    non_ocean_surface_types: frozenset[float]

    @classmethod
    def baseline(cls) -> "QualityPolicy":
        # The package metadata identifies 0 as ocean and 1/2 as enclosed sea/land.
        return cls(
            version="hy2d-idr-2mc-baseline-v1",
            ocean_surface_types=frozenset({0.0}),
            non_ocean_surface_types=frozenset({1.0, 2.0}),
        )


@dataclass(frozen=True)
class TrackQualityResult:
    track: TrackData
    status: np.ndarray
    reason_codes: tuple[tuple[str, ...], ...]
    summary: Mapping[str, Any]
    policy: QualityPolicy


def _field(track: TrackData, name: str) -> np.ndarray | None:
    values = track.fields.get(name)
    return None if values is None else np.asarray(values, dtype=np.float64)


def apply_quality_policy(track: TrackData, policy: QualityPolicy | None = None) -> TrackQualityResult:
    """Apply only the documented baseline exclusions to a decoded track."""
    policy = policy or QualityPolicy.baseline()
    length = track.latitude.size
    status = np.ones(length, dtype=bool)
    reasons: list[list[str]] = [[] for _ in range(length)]

    def exclude(mask: np.ndarray, code: str) -> None:
        for index in np.flatnonzero(mask):
            status[index] = False
            reasons[index].append(code)

    exclude(~np.isfinite(track.longitude), "NONFINITE_LONGITUDE")
    exclude(~np.isfinite(track.latitude), "NONFINITE_LATITUDE")
    for field_name, reason in (("alt", "NONFINITE_ALT"), ("range_ku", "NONFINITE_RANGE_KU")):
        values = _field(track, field_name)
        if values is None:
            exclude(np.ones(length, dtype=bool), reason)
        else:
            exclude(~np.isfinite(values), reason)

    unavailable_fields: list[str] = []
    surface_type = _field(track, "surface_type")
    unknown_surface = np.zeros(length, dtype=bool)
    if surface_type is None:
        unavailable_fields.append("surface_type")
    else:
        explicit_non_ocean = np.isin(surface_type, tuple(policy.non_ocean_surface_types))
        exclude(explicit_non_ocean, "NON_OCEAN_SURFACE")
        known_surface = np.isin(
            surface_type, tuple(policy.ocean_surface_types | policy.non_ocean_surface_types)
        )
        unknown_surface = np.isfinite(surface_type) & ~known_surface
        for index in np.flatnonzero(unknown_surface):
            reasons[index].append("UNKNOWN_SURFACE_TYPE")

    for field_name, code in (
        ("rain_flag", "RAIN_FLAG"),
        ("ice_flag", "ICE_FLAG"),
        ("qual_alt_1hz_range_ku", "RANGE_QUALITY_FLAG"),
    ):
        values = _field(track, field_name)
        if values is None:
            unavailable_fields.append(field_name)
            continue
        exclude(values == 1.0, code)
        unknown = np.isfinite(values) & ~np.isin(values, (0.0, 1.0))
        for index in np.flatnonzero(unknown):
            reasons[index].append("UNKNOWN_" + code)

    orbit_state = _field(track, "orb_state_flag")
    if orbit_state is None:
        unavailable_fields.append("orb_state_flag")
    else:
        # The product metadata defines 3 as good and 8 as bad. Values 4-7
        # require the official threshold before they can be rejected.
        exclude(orbit_state == 8.0, "ORBIT_STATE_FLAG")
        unresolved_orbit = np.isin(orbit_state, (4.0, 5.0, 6.0, 7.0))
        for index in np.flatnonzero(unresolved_orbit):
            reasons[index].append("UNRESOLVED_ORBIT_STATE_FLAG")
        unknown_orbit = np.isfinite(orbit_state) & ~np.isin(
            orbit_state, (3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        )
        for index in np.flatnonzero(unknown_orbit):
            reasons[index].append("UNKNOWN_ORBIT_STATE_FLAG")

    updated_track = replace(track, quality=status.copy())
    reason_codes = tuple(tuple(item) for item in reasons)
    summary: dict[str, Any] = {
        "qualityPolicyVersion": policy.version,
        "totalCount": int(length),
        "validCount": int(status.sum()),
        "invalidCount": int((~status).sum()),
        "validRatio": float(status.mean()) if length else 0.0,
        "unavailableQualityFields": unavailable_fields,
        "unknownSurfaceTypeCount": int(unknown_surface.sum()),
        "reasonCounts": {
            code: sum(code in item for item in reason_codes)
            for code in sorted({code for item in reason_codes for code in item})
        },
    }
    return TrackQualityResult(updated_track, status, reason_codes, summary, policy)
