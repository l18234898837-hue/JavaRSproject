from __future__ import annotations

import os
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

import numpy as np
import rasterio
from rasterio.io import DatasetWriter
from rasterio.shutil import copy as rasterio_copy


_COG_THREADS_ENV = "MULTISOURCE_COG_NUM_THREADS"
_OVERVIEW_MAX_BYTES_ENV = "MULTISOURCE_COG_OVERVIEW_MAX_BYTES"
_DEFAULT_COG_THREADS = 2
_MAX_COG_THREADS = 8
_DEFAULT_OVERVIEW_MAX_BYTES = 8 * 1024**3
_SIDECAR_SUFFIXES = (".msk", ".aux.xml", ".ovr")


@contextmanager
def open_windowed_cog(
    output_path: Path,
    profile: dict[str, Any],
    *,
    block_size: int = 256,
    overview_resampling: str = "AVERAGE",
    internal_mask: bool = False,
    overview_max_bytes: int | None = None,
    cog_threads: int | None = None,
) -> Iterator[DatasetWriter]:
    """Write windows to a disk-backed GeoTIFF and publish it as a COG.

    Rasterio's COG writer is a CreateCopy driver. Opening a large COG directly
    for writing can therefore allocate a full in-memory source dataset before
    the first window is written. A tiled GeoTIFF is a genuine random-write
    dataset, so it is safe for large windowed products and can be converted to
    a COG after it has been closed. COG overviews are omitted when the
    uncompressed source size exceeds ``overview_max_bytes`` (or
    ``MULTISOURCE_COG_OVERVIEW_MAX_BYTES``), avoiding an unbounded GDAL
    overview temporary file for very large products.
    """
    target = Path(output_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    _remove_stale_temporary_datasets(target)
    staging_profile = _staging_profile(profile, block_size)
    environment = {"GDAL_TIFF_INTERNAL_MASK": "YES"} if internal_mask else {}
    copy_options = _cog_copy_options(
        staging_profile,
        block_size=block_size,
        overview_resampling=overview_resampling,
        overview_max_bytes=overview_max_bytes,
        cog_threads=cog_threads,
    )
    staging_path = _temporary_tiff(target, "staging")
    final_path = _temporary_tiff(target, "cog")

    try:
        with rasterio.Env(**environment):
            with rasterio.open(staging_path, "w+", **staging_profile) as destination:
                yield destination
            rasterio_copy(
                staging_path,
                final_path,
                driver="COG",
                **copy_options,
            )
        os.replace(final_path, target)
        _remove_sidecars(target)
    finally:
        _remove_dataset(staging_path)
        _remove_dataset(final_path)


def _temporary_tiff(target: Path, role: str) -> Path:
    descriptor, name = tempfile.mkstemp(
        prefix=f".{target.stem}.{role}-",
        suffix=".tif",
        dir=target.parent,
    )
    os.close(descriptor)
    return Path(name)


def _remove_stale_temporary_datasets(target: Path) -> None:
    for role in ("staging", "cog"):
        for candidate in target.parent.glob(f".{target.stem}.{role}-*.tif"):
            _remove_dataset(candidate)
        for suffix in _SIDECAR_SUFFIXES:
            for candidate in target.parent.glob(f".{target.stem}.{role}-*.tif{suffix}"):
                candidate.unlink(missing_ok=True)
        for candidate in target.parent.glob(f".{target.stem}.{role}-*.tif.ovr.tmp*"):
            candidate.unlink(missing_ok=True)


def _staging_profile(profile: dict[str, Any], block_size: int) -> dict[str, Any]:
    result = dict(profile)
    for option in ("BLOCKSIZE", "COMPRESS", "BIGTIFF"):
        result.pop(option, None)
    result.update(
        driver="GTiff",
        tiled=True,
        blockxsize=block_size,
        blockysize=block_size,
        compress="DEFLATE",
        BIGTIFF="IF_SAFER",
    )
    return result


def _cog_copy_options(
    profile: dict[str, Any],
    *,
    block_size: int,
    overview_resampling: str,
    overview_max_bytes: int | None,
    cog_threads: int | None,
) -> dict[str, Any]:
    overview_limit = _configured_integer(
        overview_max_bytes,
        environment_name=_OVERVIEW_MAX_BYTES_ENV,
        default=_DEFAULT_OVERVIEW_MAX_BYTES,
        minimum=0,
    )
    requested_threads = _configured_integer(
        cog_threads,
        environment_name=_COG_THREADS_ENV,
        default=_DEFAULT_COG_THREADS,
        minimum=1,
    )
    options: dict[str, Any] = {
        "BLOCKSIZE": block_size,
        "COMPRESS": "DEFLATE",
        "BIGTIFF": "IF_SAFER",
        "RESAMPLING": overview_resampling,
        "NUM_THREADS": str(min(requested_threads, _MAX_COG_THREADS)),
    }
    if _estimated_uncompressed_bytes(profile) > overview_limit:
        options["OVERVIEWS"] = "NONE"
    return options


def _configured_integer(
    explicit_value: int | None,
    *,
    environment_name: str,
    default: int,
    minimum: int,
) -> int:
    value: Any = explicit_value
    if value is None:
        value = os.environ.get(environment_name, default)
    if isinstance(value, bool):
        raise ValueError(f"{environment_name} must be an integer >= {minimum}")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"{environment_name} must be an integer >= {minimum}") from error
    if parsed < minimum:
        raise ValueError(f"{environment_name} must be an integer >= {minimum}")
    return parsed


def _estimated_uncompressed_bytes(profile: dict[str, Any]) -> int:
    try:
        width = int(profile["width"])
        height = int(profile["height"])
        count = int(profile["count"])
        item_size = np.dtype(profile["dtype"]).itemsize
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("COG profile dimensions, band count, and dtype must be valid") from error
    if width <= 0 or height <= 0 or count <= 0:
        raise ValueError("COG profile dimensions and band count must be positive")
    return width * height * count * item_size


def _remove_dataset(path: Path) -> None:
    path.unlink(missing_ok=True)
    _remove_sidecars(path)


def _remove_sidecars(path: Path) -> None:
    for suffix in _SIDECAR_SUFFIXES:
        Path(f"{path}{suffix}").unlink(missing_ok=True)
    # GDAL can recursively create names such as
    # ``.tif.ovr.tmp_tmp_ovr.tif`` while building large COG pyramids.
    for candidate in path.parent.glob(f"{path.name}.ovr.tmp*"):
        candidate.unlink(missing_ok=True)
