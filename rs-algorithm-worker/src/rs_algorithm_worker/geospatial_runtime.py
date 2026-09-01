from __future__ import annotations

import os
from importlib.util import find_spec
from pathlib import Path


def configure_geospatial_runtime() -> None:
    spec = find_spec("rasterio")
    if spec is None or not spec.submodule_search_locations:
        raise RuntimeError("rasterio runtime is unavailable")
    rasterio_root = Path(next(iter(spec.submodule_search_locations))).resolve()
    proj_data = rasterio_root / "proj_data"
    gdal_data = rasterio_root / "gdal_data"
    if not proj_data.is_dir() or not gdal_data.is_dir():
        raise RuntimeError("rasterio geospatial data directories are unavailable")
    os.environ["PROJ_DATA"] = str(proj_data)
    os.environ["PROJ_LIB"] = str(proj_data)
    os.environ["GDAL_DATA"] = str(gdal_data)
