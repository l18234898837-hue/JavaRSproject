from __future__ import annotations

import xml.etree.ElementTree as ET

import h5py

from .base import build_plan, candidate, input_paths


def inspect_manifest(manifest):
    paths = input_paths(manifest)
    h5 = next((path for path in paths if path.suffix.lower() in {".h5", ".hdf5"}), None)
    xml = next((path for path in paths if path.suffix.lower() == ".xml"), None)
    browse = next((path for path in paths if path.suffix.lower() in {".jpg", ".jpeg"}), None)
    if not h5 or not xml or not browse:
        raise ValueError("HY-1C CZI evidence requires H5, XML, and browse files")
    try:
        with h5py.File(h5, "r") as handle:
            if not handle.keys():
                raise ValueError("HY-1C CZI evidence is incomplete")
        root = ET.parse(xml).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError("HY-1C CZI evidence is unreadable") from error
    metadata = {"xmlRoot": root.tag.rsplit("}", 1)[-1], "browseFile": browse.name}
    return [candidate(h5.stem, "HY-1C-CZI", "SWATH_RASTER", [h5, browse, xml], metadata)]
