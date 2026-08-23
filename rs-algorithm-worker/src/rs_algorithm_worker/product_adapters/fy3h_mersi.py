from __future__ import annotations

from pathlib import Path

import h5py

from .base import build_plan, candidate, input_paths


def inspect_manifest(manifest):
    candidates = []
    for path in input_paths(manifest):
        if path.suffix.upper() != ".HDF":
            raise ValueError("FY-3H MERSI evidence requires HDF files")
        try:
            with h5py.File(path, "r") as handle:
                required = {"Data", "GEO", "Calibration"}
                if not required.issubset(handle.keys()):
                    raise ValueError("FY-3H MERSI evidence is incomplete")
                attrs = handle.attrs
                if _text(attrs.get("Satellite Name")) != "FY-3H" or _text(attrs.get("Sensor Identification Code")) != "MERSI":
                    raise ValueError("FY-3H MERSI evidence is incomplete")
                metadata = {
                    "observingBeginning": f"{_text(attrs.get('Observing Beginning Date'))}T{_text(attrs.get('Observing Beginning Time'))}",
                    "observingEnding": f"{_text(attrs.get('Observing Ending Date'))}T{_text(attrs.get('Observing Ending Time'))}",
                    "orbitNumber": _scalar(attrs.get("Orbit Number")),
                    "bandCount": len(attrs.get("WaveLength_Calculated", [])),
                }
        except OSError as error:
            raise ValueError("FY-3H MERSI evidence is unreadable") from error
        candidates.append(candidate(path.stem, "FY-3H-MERSI", "SWATH_RASTER", [path], metadata))
    return candidates


def _text(value):
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, list):
        value = value[0] if value else ""
    return str(value)


def _scalar(value):
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, list):
        return value[0] if value else None
    return value
