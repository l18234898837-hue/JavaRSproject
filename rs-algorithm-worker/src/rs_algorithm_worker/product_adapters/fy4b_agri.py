from __future__ import annotations

import h5py

from .base import build_plan, candidate, input_paths


def inspect_manifest(manifest):
    paths = input_paths(manifest)
    if len(paths) != 1 or paths[0].suffix.upper() != ".HDF":
        raise ValueError("FY-4B AGRI evidence requires one HDF file")
    path = paths[0]
    try:
        with h5py.File(path, "r") as handle:
            variables = set(handle.keys())
            attrs = handle.attrs
            product = _text(attrs.get("ProductName"))
            if not {"Data", "NOMObs"}.issubset(variables) or "AGRI" not in product:
                raise ValueError("FY-4B AGRI evidence is incomplete")
            metadata = {"productName": product, "groups": sorted(variables)}
    except OSError as error:
        raise ValueError("FY-4B AGRI evidence is unreadable") from error
    return [candidate(path.stem, "FY-4B-AGRI", "GEOSTATIONARY_RASTER", [path], metadata)]


def _text(value):
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, list):
        value = value[0] if value else ""
    return str(value)
