from __future__ import annotations

from dataclasses import dataclass
import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
from typing import Iterable

import numpy as np

# Rasterio loads PROJ when it is imported.  The backend process may inherit
# PROJ_LIB from PostgreSQL/PostGIS, whose proj.db is not compatible with the
# GDAL bundled by Rasterio.  Prefer the matching data files shipped with the
# installed Rasterio package before that import happens.
_rasterio_spec = importlib.util.find_spec("rasterio")
if _rasterio_spec is not None and _rasterio_spec.origin is not None:
    _rasterio_proj_data = Path(_rasterio_spec.origin).resolve().parent / "proj_data"
    if _rasterio_proj_data.is_dir():
        os.environ["PROJ_DATA"] = str(_rasterio_proj_data)
        os.environ["PROJ_LIB"] = str(_rasterio_proj_data)

import rasterio
from rasterio.shutil import copy as copy_raster
from rasterio.windows import Window

from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.operators.spatial import as_band_first, require_grid


@dataclass(frozen=True)
class COGWriteResult:
    path: Path
    sha256: str
    byte_size: int
    valid_pixel_count: int


def write_cog(raster: RasterData, output_path: Path, *, compression: str = "deflate") -> COGWriteResult:
    crs, transform = require_grid(raster)
    output_path = Path(output_path)
    if not output_path.parent.is_dir():
        raise ValueError("output parent directory must exist")
    if compression.lower() != "deflate":
        raise ValueError("only deflate compression is supported")

    values, _ = as_band_first(raster)
    height, width = values.shape[-2:]
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output_path.stem}.",
        suffix=output_path.suffix or ".tif",
        dir=output_path.parent,
    )
    os.close(descriptor)
    temporary_path = Path(temporary_name)
    try:
        with rasterio.open(
            temporary_path,
            "w",
            driver="COG",
            width=width,
            height=height,
            count=values.shape[0],
            dtype=values.dtype,
            crs=crs,
            transform=transform,
            nodata=raster.nodata,
            BLOCKSIZE=512,
            COMPRESS="DEFLATE",
            NUM_THREADS="ALL_CPUS",
            BIGTIFF="IF_SAFER",
        ) as dataset:
            dataset.write(values)
            for index, metadata in enumerate(raster.band_metadata, start=1):
                dataset.set_band_description(index, metadata.name)
                if metadata.unit is not None:
                    dataset.update_tags(index, unit=metadata.unit)
                if metadata.wavelength_nm is not None:
                    dataset.update_tags(index, wavelength_nm=str(metadata.wavelength_nm))

        _validate_cog(
            temporary_path,
            raster=raster,
            crs=crs,
            transform=transform,
            width=width,
            height=height,
        )
        os.replace(temporary_path, output_path)
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise

    return COGWriteResult(
        path=output_path,
        sha256=_sha256(output_path),
        byte_size=output_path.stat().st_size,
        valid_pixel_count=_valid_pixel_count(values, raster.nodata),
    )


def write_cog_blocks(
    *,
    output_path: Path,
    width: int,
    height: int,
    count: int,
    dtype: str,
    crs,
    transform,
    nodata: float | int | None,
    band_metadata: tuple[BandMetadata, ...],
    blocks: Iterable[tuple[Window, np.ndarray]],
    compression: str = "deflate",
) -> COGWriteResult:
    """Write band-first blocks without materializing the complete raster."""
    output_path = Path(output_path)
    if not output_path.parent.is_dir():
        raise ValueError("output parent directory must exist")
    if compression.lower() != "deflate":
        raise ValueError("only deflate compression is supported")
    if count != len(band_metadata):
        raise ValueError("band metadata count does not match output band count")

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output_path.stem}.", suffix=".source.tif", dir=output_path.parent,
    )
    os.close(descriptor)
    source_path = Path(temporary_name)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{output_path.stem}.", suffix=output_path.suffix or ".tif", dir=output_path.parent,
    )
    os.close(descriptor)
    temporary_path = Path(temporary_name)
    valid_pixel_count = 0
    try:
        with rasterio.open(
            source_path,
            "w",
            driver="GTiff",
            width=width,
            height=height,
            count=count,
            dtype=dtype,
            crs=crs,
            transform=transform,
            nodata=nodata,
            tiled=True,
            blockxsize=512,
            blockysize=512,
            compress="deflate",
            BIGTIFF="IF_SAFER",
        ) as dataset:
            for index, metadata in enumerate(band_metadata, start=1):
                dataset.set_band_description(index, metadata.name)
                if metadata.unit is not None:
                    dataset.update_tags(index, unit=metadata.unit)
                if metadata.wavelength_nm is not None:
                    dataset.update_tags(index, wavelength_nm=str(metadata.wavelength_nm))
            for window, values in blocks:
                values = np.asarray(values)
                if values.shape != (count, int(window.height), int(window.width)):
                    raise ValueError("streamed block dimensions do not match output window")
                dataset.write(values, window=window)
                valid_pixel_count += _valid_pixel_count(values, nodata)

        copy_raster(
            source_path,
            temporary_path,
            driver="COG",
            BLOCKSIZE=512,
            COMPRESS="DEFLATE",
            NUM_THREADS="ALL_CPUS",
            BIGTIFF="IF_SAFER",
        )
        _validate_block_cog(
            temporary_path, crs=crs, transform=transform, width=width, height=height,
            band_metadata=band_metadata,
        )
        os.replace(temporary_path, output_path)
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise
    finally:
        source_path.unlink(missing_ok=True)

    return COGWriteResult(
        path=output_path,
        sha256=_sha256(output_path),
        byte_size=output_path.stat().st_size,
        valid_pixel_count=valid_pixel_count,
    )


def _validate_cog(
    path: Path,
    *,
    raster: RasterData,
    crs,
    transform,
    width: int,
    height: int,
) -> None:
    with rasterio.open(path) as dataset:
        if dataset.crs != crs or dataset.transform != transform:
            raise ValueError("written COG grid does not match source raster")
        if dataset.width != width or dataset.height != height:
            raise ValueError("written COG dimensions do not match source raster")
        if dataset.count != len(raster.band_metadata) or not _has_tiled_blocks(dataset.block_shapes):
            raise ValueError("written raster is not a tiled COG")
        if dataset.descriptions != tuple(item.name for item in raster.band_metadata):
            raise ValueError("written COG band descriptions do not match source raster")


def _validate_block_cog(
    path: Path, *, crs, transform, width: int, height: int, band_metadata: tuple[BandMetadata, ...],
) -> None:
    with rasterio.open(path) as dataset:
        if dataset.crs != crs or dataset.transform != transform:
            raise ValueError("written COG grid does not match source raster")
        if dataset.width != width or dataset.height != height:
            raise ValueError("written COG dimensions do not match source raster")
        if dataset.count != len(band_metadata) or not _has_tiled_blocks(dataset.block_shapes):
            raise ValueError("written raster is not a tiled COG")
        if dataset.descriptions != tuple(item.name for item in band_metadata):
            raise ValueError("written COG band descriptions do not match source raster")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _has_tiled_blocks(block_shapes: tuple[tuple[int, int], ...] | list[tuple[int, int]]) -> bool:
    return all(rows > 1 and columns > 1 for rows, columns in block_shapes)


def _valid_pixel_count(values: np.ndarray, nodata: float | int | None) -> int:
    valid = np.isfinite(values)
    if nodata is not None and np.isfinite(nodata):
        valid &= values != nodata
    return int(valid.sum())
