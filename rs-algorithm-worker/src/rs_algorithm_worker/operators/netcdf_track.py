from __future__ import annotations

import re
import tarfile
import tempfile
from pathlib import Path, PurePosixPath
from typing import Any

import numpy as np
from netCDF4 import Dataset

from rs_algorithm_worker.models_track import TrackData


_CORE = ("lat", "lon", "alt", "range_ku")
_QUALITY = ("surface_type", "rain_flag", "ice_flag", "qual_alt_1hz_range_ku", "orb_state_flag")


def decode_values(raw: Any, scale_factor: Any = 1.0, add_offset: Any = 0.0,
                  fill_value: Any = None) -> np.ndarray:
    """Decode packed NetCDF values while mapping the declared fill value to NaN."""
    values = np.asarray(raw)
    result = values.astype(np.float64, copy=True)
    invalid = np.zeros(result.shape, dtype=bool)
    if np.ma.isMaskedArray(raw):
        invalid |= np.ma.getmaskarray(raw)
        result = np.asarray(np.ma.getdata(raw), dtype=np.float64)
    if fill_value is not None:
        try:
            invalid |= np.equal(values, fill_value)
        except (TypeError, ValueError):
            pass
    result = result * float(scale_factor if scale_factor is not None else 1.0)
    result += float(add_offset if add_offset is not None else 0.0)
    result[invalid] = np.nan
    return result


def _validate_member(name: str) -> None:
    if not name or name.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:", name):
        raise ValueError(f"unsafe archive member path: {name}")
    parts = PurePosixPath(name.replace("\\", "/")).parts
    if ".." in parts:
        raise ValueError(f"unsafe archive member path: {name}")


def _read_variable(variable) -> np.ndarray:
    variable.set_auto_maskandscale(False)
    fill = getattr(variable, "_FillValue", None)
    return decode_values(variable[:], getattr(variable, "scale_factor", 1.0),
                         getattr(variable, "add_offset", 0.0), fill)


def _decode_netcdf(path: Path, meta_bytes: bytes) -> TrackData:
    with Dataset(path, "r") as dataset:
        if dataset.file_format != "NETCDF3_CLASSIC":
            raise ValueError("HY-2D NetCDF must be NETCDF3_CLASSIC")
        variables = dataset.variables
        missing = [name for name in _CORE if name not in variables]
        if missing:
            raise ValueError(f"HY-2D NetCDF missing required variables: {', '.join(missing)}")
        core = {name: _read_variable(variables[name]).reshape(-1) for name in _CORE}
        length = core["lat"].size
        if any(values.size != length for values in core.values()):
            raise ValueError("HY-2D core 1 Hz variable lengths must match")
        fields: dict[str, np.ndarray] = {}
        for name, variable in variables.items():
            if name in {"lat", "lon"} or getattr(variable, "ndim", 0) != 1:
                continue
            values = _read_variable(variable).reshape(-1)
            if values.size == length:
                fields[name] = values
        time = fields.get("time")
        if time is None:
            time = np.arange(length, dtype=np.float64)
        quality = np.isfinite(core["lat"]) & np.isfinite(core["lon"])
        metadata = {
            "netcdf_format": dataset.file_format,
            "dimensions": {name: len(dim) for name, dim in dataset.dimensions.items()},
            "meta_xml_bytes": len(meta_bytes),
            "quality_fields": [name for name in _QUALITY if name in fields],
        }
        return TrackData(time=time, longitude=core["lon"], latitude=core["lat"],
                         fields={**fields, "alt": core["alt"], "range_ku": core["range_ku"]},
                         quality=quality, metadata=metadata)


def decode_tar_gz(package: str | Path) -> TrackData:
    """Safely decode one HY-2D IDR/IGDR tar.gz package."""
    package_path = Path(package)
    if not package_path.is_file() or not package_path.name.lower().endswith(".tar.gz"):
        raise ValueError("HY-2D input must be a .tar.gz package")
    try:
        with tarfile.open(package_path, "r:gz") as archive:
            members = archive.getmembers()
            for member in members:
                _validate_member(member.name)
                if not member.isfile():
                    raise ValueError("unsafe archive member: only regular files are supported")
            nc_members = [member for member in members if member.name.lower().endswith(".nc")]
            meta_members = [member for member in members if member.name.lower().endswith(".meta.xml")]
            if len(nc_members) != 1:
                raise ValueError("archive must contain exactly one .nc file")
            if not meta_members:
                raise ValueError("archive must contain a .meta.xml file")
            with tempfile.TemporaryDirectory(prefix="hy2d-track-") as temp:
                root = Path(temp)
                nc_path = root / Path(nc_members[0].name).name
                extracted = archive.extractfile(nc_members[0])
                if extracted is None:
                    raise ValueError("cannot read NetCDF member")
                nc_path.write_bytes(extracted.read())
                meta_file = archive.extractfile(meta_members[0])
                meta_bytes = meta_file.read() if meta_file is not None else b""
                return _decode_netcdf(nc_path, meta_bytes)
    except (OSError, tarfile.TarError) as error:
        raise ValueError("HY-2D tar.gz package is unreadable") from error
