from __future__ import annotations

from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import tempfile

import numpy as np
import rasterio

from rs_algorithm_worker.models import RasterData
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
            BLOCKSIZE=128,
            COMPRESS="DEFLATE",
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
