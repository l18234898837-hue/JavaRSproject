from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

import h5py
import numpy as np

from .models import BandMetadata, RasterData
from .operators.quality_mask import apply_quality_mask
from .product_adapters.hy1c_czi_l2a import ReviewedL2AMapping, load_reviewed_mapping


@dataclass(frozen=True)
class L2AStageResult:
    raster: RasterData
    valid_mask: np.ndarray | None = None


def execute_l2a_stage(stage_code: str, inputs: Sequence[Any], parameters: dict[str, Any], output_dir: Path) -> L2AStageResult:
    del parameters, output_dir
    if stage_code == "DECODE_SCALE_AND_NODATA":
        h5_path = _h5_input(inputs)
        return L2AStageResult(_decode(h5_path, load_reviewed_mapping()))
    if stage_code == "APPLY_L2_FLAGS":
        decoded = next((item for item in inputs if isinstance(item, L2AStageResult)), None)
        if decoded is None:
            raise ValueError("APPLY_L2_FLAGS requires decoded L2A raster input")
        h5_path = _h5_input(inputs)
        return _apply_flags(decoded.raster, h5_path, load_reviewed_mapping())
    raise ValueError(f"unsupported HY-1C CZI L2A stage: {stage_code}")


def _decode(path: Path, mapping: ReviewedL2AMapping) -> RasterData:
    with h5py.File(path, "r") as handle:
        values = []
        metadata = []
        for band in mapping.bands:
            dataset = handle[band.path]
            raw = dataset[...].astype(np.float32)
            scale = float(dataset.attrs[band.scale_attribute])
            offset = float(dataset.attrs[band.offset_attribute])
            decoded = raw * scale + offset
            fill = float(dataset.attrs[band.fill_value_attribute])
            minimum = float(dataset.attrs[band.valid_min_attribute])
            maximum = float(dataset.attrs[band.valid_max_attribute])
            invalid = ~np.isfinite(decoded) | (decoded < minimum) | (decoded > maximum)
            if np.isfinite(fill):
                invalid |= raw == fill
            decoded[invalid] = np.nan
            values.append(decoded)
            metadata.append(BandMetadata(band.band_id, band.wavelength_nm, "1/sr"))
    return RasterData(np.stack(values), None, None, np.nan, tuple(metadata))


def _apply_flags(raster: RasterData, path: Path, mapping: ReviewedL2AMapping) -> L2AStageResult:
    with h5py.File(path, "r") as handle:
        flags = handle[mapping.quality_flags_path][...]
    bad_bits = sum(1 << bit for name, bit in mapping.flag_bits if name in mapping.default_bad_flag_names)
    masked_bands = []
    valid_mask = None
    for band in raster.values:
        masked, valid = apply_quality_mask(band, flags, bad_bits=bad_bits)
        masked_bands.append(masked)
        valid_mask = valid if valid_mask is None else valid_mask & valid
    return L2AStageResult(
        RasterData(np.stack(masked_bands), raster.crs, raster.transform, raster.nodata, raster.band_metadata),
        valid_mask,
    )


def _h5_input(inputs: Sequence[Any]) -> Path:
    paths = [Path(item) for item in inputs if isinstance(item, (str, Path)) and Path(item).suffix.lower() in {".h5", ".hdf5"}]
    if len(paths) != 1 or not paths[0].is_file():
        raise ValueError("L2A stage requires one declared HDF5 input")
    return paths[0]
