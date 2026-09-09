from __future__ import annotations

import hashlib
import json
import math
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator

import h5py
import numpy as np
import rasterio
from affine import Affine
from rasterio.crs import CRS
from rasterio.enums import Resampling
from rasterio.shutil import copy as rio_copy
from rasterio.transform import array_bounds
from rasterio.warp import calculate_default_transform, reproject, transform_bounds
from rasterio.windows import Window, from_bounds as window_from_bounds, transform as window_transform

from .algorithms.features import FeatureParameters, extract_features
from .algorithms.fusion import FusionParameters, fuse_bands
from .algorithms.saliency import SaliencyParameters, detect_saliency
from .fy4b_agri_navigation import apparent_reflectance, build_nominal_grid
from .models import BandMetadata, BandSet, RasterData
from .operators.align import align_to_reference
from .operators.cog import write_cog


FY4B_QUALITY_FILL = 1
FY4B_QUALITY_OUT_OF_RANGE = 2
FY4B_QUALITY_NAVIGATION_FAILURE = 4
FY4B_QUALITY_CALIBRATION_DEGRADED = 8
FY4B_QUALITY_FILE_DEGRADED = 16
FY4B_QUALITY_OUTSIDE_DISK = 32
FY4B_QUALITY_NIGHT = 64
_MAX_MATERIALIZED_HDF_CHUNK_BYTES = 1024 * 1024 * 1024
_REPROJECT_THREADS = 2
_SOURCE_ROI_PADDING_PIXELS = 4
_FULL_GRID_TAG_NAMESPACE = "FY4B"


@dataclass(frozen=True)
class ReflectiveCalibration:
    reflectance: np.ndarray
    radiance: np.ndarray


@dataclass(frozen=True)
class EmissiveCalibration:
    brightness_temperature: np.ndarray
    radiance: np.ndarray


@dataclass(frozen=True)
class QualityMask:
    reason_bits: np.ndarray


@dataclass(frozen=True)
class _MaterializedDataset:
    values: np.ndarray
    attrs: dict[str, Any]
    source_shape: tuple[int, ...] | None = None
    row_offset: int = 0
    column_offset: int = 0

    @property
    def shape(self) -> tuple[int, ...]:
        return self.source_shape or self.values.shape

    def __getitem__(self, key: Any) -> np.ndarray:
        if not isinstance(key, tuple) or len(key) < 2:
            return self.values[key]
        rows, columns, *remaining = key
        translated = (
            _translate_materialized_slice(rows, self.row_offset, self.values.shape[0]),
            _translate_materialized_slice(columns, self.column_offset, self.values.shape[1]),
            *remaining,
        )
        return self.values[translated]


def calibrate_reflective(
    values: np.ndarray,
    *,
    lookup: np.ndarray,
    fill_value: float,
    scale: float = 1.0,
    offset: float = 0.0,
    esun: float = 1.0,
) -> ReflectiveCalibration:
    raw, table = np.asarray(values), np.asarray(lookup, dtype=np.float32)
    valid = _valid_indices(raw, fill_value, table.size)
    reflectance = np.full(raw.shape, np.nan, dtype=np.float32)
    radiance = np.full(raw.shape, np.nan, dtype=np.float32)
    reflectance[valid] = table[raw[valid]] * float(scale) + float(offset)
    radiance[valid] = reflectance[valid] * float(esun) / np.pi
    return ReflectiveCalibration(reflectance, radiance)


def calibrate_emissive(
    values: np.ndarray,
    *,
    lookup: np.ndarray,
    fill_value: float,
    scale: float = 1.0,
    offset: float = 0.0,
) -> EmissiveCalibration:
    raw, table = np.asarray(values), np.asarray(lookup, dtype=np.float32)
    valid = _valid_indices(raw, fill_value, table.size)
    brightness = np.full(raw.shape, np.nan, dtype=np.float32)
    radiance = np.full(raw.shape, np.nan, dtype=np.float32)
    brightness[valid] = table[raw[valid]]
    radiance[valid] = raw[valid].astype(np.float32) * float(scale) + float(offset)
    return EmissiveCalibration(brightness, radiance)


def build_quality_mask(
    values: np.ndarray,
    fill_value: float,
    valid_range: tuple[float, float],
    *,
    navigation_ok: bool = True,
    calibration_ok: bool = True,
    file_quality_ok: bool = True,
    outside_disk: np.ndarray | None = None,
) -> QualityMask:
    raw = np.asarray(values)
    bits = np.zeros(raw.shape, dtype=np.uint32)
    bits[raw == fill_value] |= FY4B_QUALITY_FILL
    bits[(raw < valid_range[0]) | (raw > valid_range[1])] |= FY4B_QUALITY_OUT_OF_RANGE
    if not navigation_ok:
        bits |= FY4B_QUALITY_NAVIGATION_FAILURE
    if not calibration_ok:
        bits |= FY4B_QUALITY_CALIBRATION_DEGRADED
    if not file_quality_ok:
        bits |= FY4B_QUALITY_FILE_DEGRADED
    if outside_disk is not None:
        outside = np.asarray(outside_disk, dtype=bool)
        if outside.shape != raw.shape:
            raise ValueError("FY-4B outside-disk mask shape is invalid")
        bits[outside] |= FY4B_QUALITY_OUTSIDE_DISK
    return QualityMask(bits)


def iter_dataset_windows(dataset: np.ndarray, rows_per_window: int) -> Iterator[np.ndarray]:
    if rows_per_window < 1:
        raise ValueError("rows_per_window must be positive")
    for start in range(0, dataset.shape[0], rows_per_window):
        yield dataset[start:start + rows_per_window, ...]


def iter_dataset_tiles(
    dataset: Any,
    rows_per_window: int,
    columns_per_window: int,
    *,
    window: Window | None = None,
) -> Iterator[tuple[Window, np.ndarray]]:
    if rows_per_window < 1 or columns_per_window < 1:
        raise ValueError("FY-4B tile dimensions must be positive")
    height, width = dataset.shape[:2]
    region = _bounded_integer_window(window, width=width, height=height)
    row_start, column_start = int(region.row_off), int(region.col_off)
    row_stop, column_stop = row_start + int(region.height), column_start + int(region.width)
    for row in range(row_start, row_stop, rows_per_window):
        for column in range(column_start, column_stop, columns_per_window):
            tile = Window(
                column,
                row,
                min(columns_per_window, column_stop - column),
                min(rows_per_window, row_stop - row),
            )
            yield tile, np.asarray(
                dataset[row:row + int(tile.height), column:column + int(tile.width)],
            )


def _tile_read_source(dataset: Any, window: Window | None = None) -> Any:
    chunks = getattr(dataset, "chunks", None)
    compression = getattr(dataset, "compression", None)
    shape = tuple(int(value) for value in dataset.shape)
    if compression is None or chunks is None or tuple(chunks) != shape:
        return dataset
    uncompressed_bytes = math.prod(shape) * np.dtype(dataset.dtype).itemsize
    if uncompressed_bytes > _MAX_MATERIALIZED_HDF_CHUNK_BYTES:
        raise ValueError("FY-4B compressed HDF chunk exceeds the bounded in-memory read limit")
    selected = _bounded_integer_window(window, width=shape[1], height=shape[0])
    row, column = int(selected.row_off), int(selected.col_off)
    bottom, right = row + int(selected.height), column + int(selected.width)
    return _MaterializedDataset(
        np.asarray(dataset[row:bottom, column:right]),
        {name: dataset.attrs[name] for name in dataset.attrs},
        source_shape=shape,
        row_offset=row,
        column_offset=column,
    )


def _translate_materialized_slice(value: Any, offset: int, length: int) -> Any:
    if not isinstance(value, slice):
        translated = int(value) - offset
        if translated < 0 or translated >= length:
            raise IndexError("FY-4B materialized HDF read is outside its source window")
        return translated
    start = offset if value.start is None else int(value.start)
    stop = offset + length if value.stop is None else int(value.stop)
    if start < offset or stop > offset + length:
        raise IndexError("FY-4B materialized HDF read is outside its source window")
    return slice(start - offset, stop - offset, value.step)


def _bounded_integer_window(window: Window | None, *, width: int, height: int) -> Window:
    if window is None:
        return Window(0, 0, width, height)
    column = max(0, int(math.floor(window.col_off)))
    row = max(0, int(math.floor(window.row_off)))
    right = min(width, int(math.ceil(window.col_off + window.width)))
    bottom = min(height, int(math.ceil(window.row_off + window.height)))
    if right <= column or bottom <= row:
        raise ValueError("FY-4B source window does not intersect raster")
    return Window(column, row, right - column, bottom - row)


def build_quality_mask_from_geo(
    values: np.ndarray,
    *,
    fill_value: float,
    valid_range: tuple[float, float],
    l1_navigation_ok: bool,
    geo_navigation_ok: bool,
    geo_line: np.ndarray,
    geo_column: np.ndarray,
    geo_outside_disk: np.ndarray | None = None,
    calibration_ok: bool = True,
    file_quality_ok: bool = True,
) -> QualityMask:
    line, column = np.asarray(geo_line), np.asarray(geo_column)
    if line.shape != np.asarray(values).shape or column.shape != np.asarray(values).shape:
        raise ValueError("FY-4B GEO navigation window shape is invalid")
    outside_disk = (line < 0) | (column < 0) | (line >= 2748) | (column >= 2748)
    if geo_outside_disk is not None:
        satellite_outside = np.asarray(geo_outside_disk, dtype=bool)
        if satellite_outside.shape != outside_disk.shape:
            raise ValueError("FY-4B GEO disk mask window shape is invalid")
        outside_disk |= satellite_outside
    quality = build_quality_mask(
        values, fill_value, valid_range, navigation_ok=l1_navigation_ok and geo_navigation_ok,
        calibration_ok=calibration_ok, file_quality_ok=file_quality_ok, outside_disk=outside_disk,
    )
    quality.reason_bits[outside_disk] |= FY4B_QUALITY_NAVIGATION_FAILURE | FY4B_QUALITY_OUTSIDE_DISK
    return quality


def _valid_indices(raw: np.ndarray, fill_value: float, table_size: int) -> np.ndarray:
    valid = (raw != fill_value) & (raw >= 0)
    if np.any(valid & (raw >= table_size)):
        raise ValueError("FY-4B calibration table does not cover valid DN")
    return valid


def execute_fy4b_product(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    if payload.get("stageKey") != "STANDARDIZE_FY4B_AGRI":
        raise ValueError("unsupported FY-4B AGRI stage")
    output_uri = payload.get("outputUri")
    if not isinstance(output_uri, str) or not output_uri.startswith("rs://output/"):
        raise ValueError("FY-4B logical output URI is required")
    output = manifest.get("output")
    if not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("FY-4B temporary output directory is required")
    directory = Path(output["path"]).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    parameters = _parameters(payload)
    raw_paths = _raw_paths(manifest)
    _require_paths(raw_paths, ("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M"), "FY-4B execution requires all raw roles")
    try:
        _validate_fy4b_package(manifest, directory)
        navigation = _build_fy4b_nominal_grid(raw_paths["GEO_4000M"], directory)
        native = _calibrate_and_georeference_fy4b(raw_paths, navigation, parameters, directory)
        gridded = _reproject_and_crop_fy4b(native, parameters, directory)
        final_artifacts = _write_fy4b_standard_cog(gridded, directory)
        standard = {artifact_type: path for artifact_type, path in final_artifacts}
        if parameters["features"]["enabled"]:
            feature_artifacts = _write_fy4b_features(standard, directory, parameters)
            final_artifacts.extend(feature_artifacts)
            standard.update(feature_artifacts)
        if parameters["fusion"]["enabled"]:
            fusion_artifacts = _write_fy4b_fusion(standard, directory, parameters)
            final_artifacts.extend(fusion_artifacts)
            standard.update(fusion_artifacts)
        if parameters["saliency"]["enabled"]:
            saliency_artifacts = _write_fy4b_saliency(standard, directory, parameters)
            final_artifacts.extend(saliency_artifacts)
            standard.update(saliency_artifacts)
        return {
            "workerId": "worker-local",
            "status": "SUCCEEDED",
            "artifacts": [_artifact(output_uri, directory, artifact_type, path) for artifact_type, path in final_artifacts],
            "metadata": {"navigationStatus": "NOMINAL_UNVERIFIED"},
        }
    finally:
        _remove_intermediates(directory)


def execute_fy4b_stage(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    """Execute one FY-4B AGRI plan-v3 stage, preserving intermediate artifacts."""
    stage = payload.get("stageKey")
    output_uri = payload.get("outputUri")
    output = manifest.get("output")
    if not isinstance(stage, str) or not isinstance(output_uri, str) or not output_uri.startswith("rs://output/"):
        raise ValueError("FY-4B AGRI stage and logical output URI are required")
    if not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("FY-4B temporary output directory is required")
    directory = Path(output["path"]).resolve(); directory.mkdir(parents=True, exist_ok=True)
    parameters = _parameters(payload)
    raw_paths = _raw_paths(manifest)
    if stage == "VALIDATE_FY4B_PACKAGE":
        _require_paths(raw_paths, ROLE_ORDER, "FY-4B execution requires all raw roles")
        return _stage_response(output_uri, directory, _validate_fy4b_package(manifest, directory))
    if stage == "BUILD_FY4B_NOMINAL_GRID":
        geo = raw_paths.get("GEO_4000M")
        if geo is None:
            raise ValueError("FY-4B nominal grid requires GEO_4000M input")
        return _stage_response(output_uri, directory, _build_fy4b_nominal_grid(geo, directory))
    if stage == "CALIBRATE_AND_GEOREFERENCE_FY4B":
        _require_paths(raw_paths, ROLE_ORDER, "FY-4B calibration requires all raw roles")
        grid_path = raw_paths.get("FY4B_NOMINAL_GRID_JSON") or directory / "navigation" / "nominal-grid.json"
        if not Path(grid_path).is_file():
            raise ValueError("FY-4B calibration requires nominal grid")
        return _stage_response(output_uri, directory, _calibrate_and_georeference_fy4b(raw_paths, [("FY4B_NOMINAL_GRID_JSON", Path(grid_path))], parameters, directory))
    if stage == "REPROJECT_AND_CROP_FY4B":
        native = []
        for resolution in ("0500M", "1000M", "2000M", "4000M"):
            for suffix in ("RASTER", "QUALITY"):
                key = f"FY4B_NATIVE_{resolution}_{suffix}"
                path = raw_paths.get(key) or directory / "native" / (f"{resolution.lower()}.tif" if suffix == "RASTER" else f"quality-{resolution.lower()}.tif")
                if not Path(path).is_file():
                    raise ValueError(f"FY-4B reprojection requires {key}")
                native.append((key, Path(path)))
        return _stage_response(output_uri, directory, _reproject_and_crop_fy4b(native, parameters, directory))
    if stage == "WRITE_FY4B_STANDARD_COG":
        gridded = []
        for resolution in ("0500M", "1000M", "2000M", "4000M"):
            for suffix in ("RASTER", "QUALITY"):
                key = f"FY4B_GRIDDED_{resolution}_{suffix}"
                path = raw_paths.get(key) or directory / "grid" / (f"{resolution.lower()}.tif" if suffix == "RASTER" else f"quality-{resolution.lower()}.tif")
                if not Path(path).is_file():
                    raise ValueError(f"FY-4B standard COG requires {key}")
                gridded.append((key, Path(path)))
        return _stage_response(output_uri, directory, _write_fy4b_standard_cog(gridded, directory))
    if stage == "EXTRACT_FY4B_FEATURES":
        standard = {"FY4B_VIS_NIR_1000M_COG": raw_paths.get("FY4B_VIS_NIR_1000M_COG") or directory / "standard" / "1000m.tif",
                    "FY4B_RED_500M_COG": raw_paths.get("FY4B_RED_500M_COG") or directory / "standard" / "0500m.tif"}
        return _stage_response(output_uri, directory, _write_fy4b_features({k: Path(v) for k, v in standard.items()}, directory, parameters))
    if stage == "FUSE_FY4B_BANDS":
        standard = {k: Path(raw_paths.get(k) or directory / "standard" / name) for k, name in (("FY4B_VIS_NIR_1000M_COG", "1000m.tif"), ("FY4B_RED_500M_COG", "0500m.tif"), ("FY4B_MULTISPECTRAL_4000M_COG", "4000m.tif"))}
        return _stage_response(output_uri, directory, _write_fy4b_fusion(standard, directory, parameters))
    if stage == "DETECT_FY4B_SALIENCY":
        source = raw_paths.get("FY4B_FEATURE_COG") if parameters["saliency"]["source"].startswith(("gradient:", "local_variance:")) else raw_paths.get("FY4B_VIS_NIR_1000M_COG")
        source = Path(source or directory / ("features/fy4b-features.tif" if parameters["saliency"]["source"].startswith(("gradient:", "local_variance:")) else "standard/1000m.tif"))
        return _stage_response(output_uri, directory, _write_fy4b_saliency({"FY4B_FEATURE_COG": source, "FY4B_VIS_NIR_1000M_COG": source}, directory, parameters))
    raise ValueError("unsupported FY-4B AGRI stage")


def _stage_response(output_uri: str, directory: Path, artifacts: list[tuple[str, Path]]) -> dict[str, Any]:
    return {"workerId": "worker-local", "status": "SUCCEEDED",
            "artifacts": [_artifact(output_uri, directory, artifact_type, path) for artifact_type, path in artifacts]}


def _validate_fy4b_package(manifest: dict[str, Any], directory: Path) -> list[tuple[str, Path]]:
    from .product_adapters.fy4b_agri import inspect_manifest

    candidates = inspect_manifest(manifest)
    if len(candidates) != 1:
        raise ValueError("FY-4B execution requires exactly one validated package")
    path = directory / "validation" / "package.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(candidates[0]["metadataSnapshot"], sort_keys=True), encoding="utf-8")
    return [("FY4B_INPUT_VALIDATION_JSON", path)]


def _build_fy4b_nominal_grid(geo_path: Path, directory: Path) -> list[tuple[str, Path]]:
    try:
        with h5py.File(geo_path, "r") as handle:
            attributes = handle.attrs
            navigation = handle["Navigation/LineNumber"]
            grid = build_nominal_grid(
                nom_center_lon=_scalar_float(attributes["NOMCenterLon"]),
                nom_sat_height=_scalar_float(attributes["NOMSatHeight"]),
                earth_equatorial_radius=_scalar_float(attributes["dEA"]),
                inverse_flattening=_scalar_float(attributes["dObRecFlat"]),
                sampling_angle_micro_rad=_scalar_float(attributes["dSamplingAngle"]),
                stepping_angle_micro_rad=_scalar_float(attributes["dSteppingAngle"]),
                width=navigation.shape[1],
                height=navigation.shape[0],
            )
    except (OSError, KeyError, TypeError, ValueError, IndexError) as error:
        raise ValueError("FY-4B nominal grid requires valid GEO_4000M navigation") from error
    path = directory / "navigation" / "nominal-grid.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({
        "crs": grid.crs, "transform": list(grid.transform), "width": grid.width, "height": grid.height,
        "status": grid.status, "parameters": grid.parameters,
    }, sort_keys=True), encoding="utf-8")
    return [("FY4B_NOMINAL_GRID_JSON", path)]


def _calibrate_and_georeference_fy4b(
    raw_paths: dict[str, Path], navigation: list[tuple[str, Path]], parameters: dict[str, Any], directory: Path,
) -> list[tuple[str, Path]]:
    navigation_paths = dict(navigation)
    grid_path = navigation_paths.get("FY4B_NOMINAL_GRID_JSON")
    if grid_path is None:
        raise ValueError("FY-4B calibration requires nominal grid")
    fallback_crs, fallback_transform = _read_nominal_grid(grid_path)
    artifacts: list[tuple[str, Path]] = []
    native = directory / "native"
    native.mkdir(parents=True, exist_ok=True)
    for role, resolution in (("L1_0500M", "0500M"), ("L1_1000M", "1000M"), ("L1_2000M", "2000M"), ("L1_4000M", "4000M")):
        science_path = native / f"{resolution.lower()}.tif"
        quality_path = native / f"quality-{resolution.lower()}.tif"
        _write_native_l1_windows(raw_paths[role], raw_paths["GEO_4000M"], role, science_path, quality_path,
                                 fallback=(fallback_crs, fallback_transform), roi=parameters.get("roi"))
        artifacts.extend([
            (f"FY4B_NATIVE_{resolution}_RASTER", science_path),
            (f"FY4B_NATIVE_{resolution}_QUALITY", quality_path),
        ])
    summary = directory / "calibration" / "summary.json"
    summary.parent.mkdir(parents=True, exist_ok=True)
    summary.write_text(json.dumps({"formulaVersion": "fy4b-agri-l1-v1"}), encoding="utf-8")
    artifacts.append(("FY4B_CALIBRATION_SUMMARY_JSON", summary))
    return artifacts


def _reproject_and_crop_fy4b(
    native: list[tuple[str, Path]], parameters: dict[str, Any], directory: Path,
) -> list[tuple[str, Path]]:
    paths = dict(native)
    grid = directory / "grid"
    grid.mkdir(parents=True, exist_ok=True)
    artifacts: list[tuple[str, Path]] = []
    for resolution in ("0500M", "1000M", "2000M", "4000M"):
        source = paths.get(f"FY4B_NATIVE_{resolution}_RASTER")
        quality_source = paths.get(f"FY4B_NATIVE_{resolution}_QUALITY")
        if source is None or quality_source is None:
            raise ValueError("FY-4B reprojection requires all native science and quality rasters")
        science_path, quality_path = grid / f"{resolution.lower()}.tif", grid / f"quality-{resolution.lower()}.tif"
        _reproject_raster_windows(source, science_path, parameters, Resampling.bilinear)
        _reproject_raster_windows(quality_source, quality_path, parameters, Resampling.nearest)
        artifacts.extend([
            (f"FY4B_GRIDDED_{resolution}_RASTER", science_path),
            (f"FY4B_GRIDDED_{resolution}_QUALITY", quality_path),
        ])
    return artifacts


def _write_fy4b_standard_cog(gridded: list[tuple[str, Path]], directory: Path) -> list[tuple[str, Path]]:
    paths = dict(gridded)
    labels = {
        "0500M": "RED_500M", "1000M": "VIS_NIR_1000M", "2000M": "SPECTRAL_2000M", "4000M": "MULTISPECTRAL_4000M",
    }
    standard = directory / "standard"
    standard.mkdir(parents=True, exist_ok=True)
    artifacts: list[tuple[str, Path]] = []
    summaries: dict[str, dict[str, Any]] = {}
    for resolution, label in labels.items():
        source, quality_source = paths.get(f"FY4B_GRIDDED_{resolution}_RASTER"), paths.get(f"FY4B_GRIDDED_{resolution}_QUALITY")
        if source is None or quality_source is None:
            raise ValueError("FY-4B standard COG requires all gridded science and quality rasters")
        science_path, quality_path = standard / f"{resolution.lower()}.tif", standard / f"quality-{resolution.lower()}.tif"
        _copy_to_cog(source, science_path)
        _copy_to_cog(quality_source, quality_path)
        artifacts.extend([(f"FY4B_{label}_COG", science_path), (f"FY4B_{label}_QUALITY_COG", quality_path)])
        summaries[label] = _raster_summary(science_path)
        summaries[f"{label}_QUALITY"] = _raster_summary(quality_path)
    summary = standard / "summary.json"
    summary.write_text(json.dumps({
        "profileKey": {"productCode": "FY-4B-AGRI", "profileVersion": "v1"},
        "formulaVersion": "fy4b-agri-l1-v1", "qualityRuleVersion": "fy4b-agri-l1-quality-v1",
        "navigationStatus": "NOMINAL_UNVERIFIED", "artifacts": summaries,
    }, sort_keys=True), encoding="utf-8")
    artifacts.append(("FY4B_STANDARD_SUMMARY_JSON", summary))
    return artifacts


def _write_fy4b_features(standard: dict[str, Path], directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    source = standard.get("FY4B_VIS_NIR_1000M_COG")
    if source is None:
        raise ValueError("FY-4B features requires standard COG")
    raster = _load_raster(source)
    metrics = tuple(metric for metric in parameters["features"]["metrics"] if metric in {"statistics", "gradient", "local_variance"})
    feature_set = extract_features(raster, FeatureParameters(metrics or ("statistics",), parameters["features"].get("windowSize", 3)))
    names, values = list(feature_set.rasters), [item.values for item in feature_set.rasters.values()]
    red = None
    red_band = None
    red_feature_metrics = tuple(metric for metric in parameters["features"]["metrics"] if metric in {"gradient", "local_variance"})
    if red_feature_metrics or "toa_nir_red_nd" in parameters["features"]["metrics"]:
        red = _load_raster(_required_artifact(standard, "FY4B_RED_500M_COG", "FY-4B features requires 500M red source"))
        red_band = _named_band(red, "C02_")
        if red_band is None:
            raise ValueError("FY-4B features requires C02")
    if red_feature_metrics and red is not None and red_band is not None:
        red_features = extract_features(
            RasterData(red_band, red.crs, red.transform, red.nodata, (BandMetadata("C02_REFLECTANCE_500M"),)),
            FeatureParameters(red_feature_metrics, parameters["features"].get("windowSize", 3)),
        )
        for name, red_feature in red_features.rasters.items():
            aligned = align_to_reference(red_feature, raster, resampling=Resampling.bilinear)
            names.append(name)
            values.append(aligned.values)
    if "toa_nir_red_nd" in parameters["features"]["metrics"]:
        nir_band = _named_band(raster, "C03_")
        if red_band is None or nir_band is None:
            raise ValueError("FY-4B features requires C02 and C03")
        red_aligned = align_to_reference(RasterData(red_band, red.crs, red.transform, red.nodata, (BandMetadata("C02"),)), raster,
                                         resampling=Resampling.bilinear).values
        denominator = nir_band + red_aligned
        values.append(np.where(np.isfinite(denominator) & (np.abs(denominator) > 1e-6), (nir_band - red_aligned) / denominator, np.nan).astype(np.float32))
        names.append("toa_nir_red_nd")
    if "btd_c13_c14" in parameters["features"]["metrics"]:
        thermal = _load_raster(_required_artifact(
            standard, "FY4B_MULTISPECTRAL_4000M_COG", "FY-4B features requires 4000M thermal source",
        ))
        c13, c14 = _named_band(thermal, "C13_"), _named_band(thermal, "C14_")
        if c13 is None or c14 is None:
            raise ValueError("FY-4B features requires C13 and C14")
        c13_aligned = align_to_reference(
            RasterData(c13, thermal.crs, thermal.transform, thermal.nodata, (BandMetadata("C13"),)), raster,
            resampling=Resampling.bilinear,
        ).values
        c14_aligned = align_to_reference(
            RasterData(c14, thermal.crs, thermal.transform, thermal.nodata, (BandMetadata("C14"),)), raster,
            resampling=Resampling.bilinear,
        ).values
        values.append(np.where(np.isfinite(c13_aligned) & np.isfinite(c14_aligned), c13_aligned - c14_aligned, np.nan).astype(np.float32))
        names.append("btd_c13_c14")
    if not values:
        source_values = raster.values if raster.values.ndim == 3 else raster.values[np.newaxis, ...]
        values = [source_values[index] for index in range(source_values.shape[0])]
        names = [f"statistics:{metadata.name}" for metadata in raster.band_metadata]
    feature_dir = directory / "features"
    feature_dir.mkdir(parents=True, exist_ok=True)
    artifacts: list[tuple[str, Path]] = []
    path = feature_dir / "fy4b-features.tif"
    _write(np.stack(values), path, raster.crs, raster.transform, tuple(names))
    artifacts.append(("FY4B_FEATURE_COG", path))
    summary = feature_dir / "summary.json"
    summary.write_text(json.dumps({"classification": "NONE", **feature_set.statistics}, allow_nan=False, sort_keys=True), encoding="utf-8")
    artifacts.append(("FY4B_FEATURE_SUMMARY_JSON", summary))
    return artifacts


def _write_fy4b_fusion(standard: dict[str, Path], directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    source = _required_artifact(standard, "FY4B_VIS_NIR_1000M_COG", "FY-4B fusion requires 1000M standard COG")
    raster = _load_raster(source)
    result = fuse_bands(BandSet(raster), FusionParameters("C03_REFLECTANCE", "C02_REFLECTANCE", "C01_REFLECTANCE", *parameters["fusion"]["percentiles"]))
    path = directory / "fusion" / "false-color.tif"
    path.parent.mkdir(parents=True, exist_ok=True)
    _write(result.values, path, result.crs, result.transform,
           ("NIR_RED_BLUE_FALSE_COLOR_R", "NIR_RED_BLUE_FALSE_COLOR_G", "NIR_RED_BLUE_FALSE_COLOR_B"), 0)
    return [("FY4B_FALSE_COLOR_COG", path)]


def _write_fy4b_saliency(standard: dict[str, Path], directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    source_name = parameters["saliency"]["source"]
    source = _saliency_source(standard, source_name)
    raster = _load_raster(source)
    if source_name in {"toa_nir_red_nd", "btd_c13_c14"} or ":" in source_name:
        index = next((index for index, metadata in enumerate(raster.band_metadata) if metadata.name == source_name), None)
        if index is None:
            raise ValueError("FY-4B saliency requires declared feature source")
        values = raster.values if raster.values.ndim == 2 else raster.values[index]
        raster = RasterData(values, raster.crs, raster.transform, raster.nodata, (BandMetadata(source_name),))
    else:
        band_name = source_name.rsplit("_", 1)[0]
        index = next((index for index, metadata in enumerate(raster.band_metadata) if metadata.name == band_name), None)
        if index is None:
            raise ValueError("FY-4B saliency requires declared physical source")
        values = raster.values if raster.values.ndim == 2 else raster.values[index]
        raster = RasterData(values, raster.crs, raster.transform, raster.nodata, (BandMetadata(source_name),))
    result = detect_saliency(raster, SaliencyParameters(parameters["saliency"]["threshold"], parameters["saliency"]["windowSize"]))
    saliency = directory / "saliency"
    saliency.mkdir(parents=True, exist_ok=True)
    score, mask, summary = saliency / "score.tif", saliency / "mask.tif", saliency / "summary.json"
    _write(result.score.values, score, result.score.crs, result.score.transform, ("saliency_score",), 0.0)
    _write(result.mask.astype(np.uint8), mask, result.score.crs, result.score.transform, ("saliency_mask",), 0)
    summary.write_text(json.dumps({
        "algorithm": "contrast-v1", "source": source_name, "threshold": parameters["saliency"]["threshold"],
        "windowSize": parameters["saliency"]["windowSize"], "candidatePixelCount": int(result.mask.sum()), "semanticLabel": "NONE",
    }, sort_keys=True), encoding="utf-8")
    return [("FY4B_SALIENCY_SCORE_COG", score), ("FY4B_SALIENCY_MASK_COG", mask), ("FY4B_SALIENCY_SUMMARY_JSON", summary)]


def _write_native_l1_windows(
    path: Path, geo_path: Path, role: str, science_path: Path, quality_path: Path, *, fallback: tuple[str, Affine],
    roi: list[float] | None = None,
) -> None:
    from .product_adapters.fy4b_agri import CHANNELS_BY_ROLE

    channels = CHANNELS_BY_ROLE[role]
    with h5py.File(path, "r") as handle, h5py.File(geo_path, "r") as geo_handle:
        first = handle[f"Data/NOMChannel{channels[0]:02d}"]
        height, width = first.shape
        crs, transform = _source_nominal_grid(path, width=width, height=height, fallback=fallback)
        source_window = _source_window_for_roi(
            crs=crs,
            transform=transform,
            width=width,
            height=height,
            roi=roi,
        )
        output_transform = window_transform(source_window, transform)
        output_height, output_width = int(source_window.height), int(source_window.width)
        descriptions = tuple(f"C{channel:02d}_{'REFLECTANCE' if channel <= 6 else 'BRIGHTNESS_TEMPERATURE'}" for channel in channels)
        science_profile = _tiled_profile(
            output_width, output_height, len(channels), "float32", crs, output_transform, np.nan,
        )
        quality_profile = _tiled_profile(
            output_width, output_height, len(channels), "uint32", crs, output_transform, np.uint32(0xFFFFFFFF),
        )
        file_quality_ok = _attribute_scalar(handle.attrs, "Data Quality", 0.0) == 0.0
        geo_line_dataset = geo_handle["Navigation/LineNumber"]
        geo_column_dataset = geo_handle["Navigation/ColumnNumber"]
        geo_line = _tile_read_source(
            geo_line_dataset,
            _nearest_dataset_window(
                source_window,
                target_height=height,
                target_width=width,
                source_height=geo_line_dataset.shape[0],
                source_width=geo_line_dataset.shape[1],
            ),
        )
        geo_column = _tile_read_source(
            geo_column_dataset,
            _nearest_dataset_window(
                source_window,
                target_height=height,
                target_width=width,
                source_height=geo_column_dataset.shape[0],
                source_width=geo_column_dataset.shape[1],
            ),
        )
        satellite_zenith_dataset = geo_handle.get("Navigation/NOMSatelliteZenith")
        geo_satellite_zenith = (
            _tile_read_source(
                satellite_zenith_dataset,
                _nearest_dataset_window(
                    source_window,
                    target_height=height,
                    target_width=width,
                    source_height=satellite_zenith_dataset.shape[0],
                    source_width=satellite_zenith_dataset.shape[1],
                ),
            )
            if satellite_zenith_dataset is not None else None
        )
        zenith_dataset = _solar_zenith_dataset(handle, geo_handle, first.shape)
        zenith = (
            _tile_read_source(
                zenith_dataset,
                _nearest_dataset_window(
                    source_window,
                    target_height=height,
                    target_width=width,
                    source_height=zenith_dataset.shape[0],
                    source_width=zenith_dataset.shape[1],
                ),
            )
            if zenith_dataset is not None else None
        )
        with rasterio.open(science_path, "w", **science_profile) as science_output, rasterio.open(quality_path, "w", **quality_profile) as quality_output:
            _write_full_grid_tags(science_output, width=width, height=height, transform=transform)
            _write_full_grid_tags(quality_output, width=width, height=height, transform=transform)
            for output_index, channel in enumerate(channels, 1):
                dataset = handle[f"Data/NOMChannel{channel:02d}"]
                fill_value = _attribute_scalar(dataset.attrs, "FillValue")
                valid_range = np.asarray(dataset.attrs.get("valid_range", []), dtype=np.float64).reshape(-1)
                if fill_value is None or valid_range.size != 2 or not np.all(np.isfinite(valid_range)):
                    raise ValueError("FY-4B science channel has invalid calibration metadata")
                lookup = np.asarray(handle[f"Calibration/CALChannel{channel:02d}"], dtype=np.float32)
                scale, offset = _calibration_coefficients(handle, dataset, channel)
                l1_navigation_ok = _channel_flag(handle, "QA/NavQualityFlag", channel)
                geo_navigation_ok = _channel_flag(geo_handle, "QA/NavQualityFlag", channel)
                calibration_ok = _channel_flag(handle, "QA/CalQualityFlag", channel)
                earth_sun_ratio = None
                if channel <= 6 and zenith is not None:
                    earth_sun_ratio = _attribute_scalar(handle.attrs, "Earth/Sun Distance Ratio")
                    if earth_sun_ratio is None or earth_sun_ratio <= 0:
                        raise ValueError("FY-4B Earth/Sun Distance Ratio must be finite and positive")
                tile_source = _tile_read_source(dataset, source_window)
                for window, raw in iter_dataset_tiles(tile_source, 256, 256, window=source_window):
                    line = _nearest_geo_window(geo_line, window, target_height=height, target_width=width)
                    column = _nearest_geo_window(geo_column, window, target_height=height, target_width=width)
                    satellite_outside = (
                        _geo_outside_disk_window(
                            geo_satellite_zenith, window, target_height=height, target_width=width,
                        )
                        if geo_satellite_zenith is not None else None
                    )
                    mask = build_quality_mask_from_geo(
                        raw, fill_value=fill_value, valid_range=(valid_range[0], valid_range[1]),
                        l1_navigation_ok=l1_navigation_ok, geo_navigation_ok=geo_navigation_ok,
                        geo_line=line, geo_column=column, geo_outside_disk=satellite_outside,
                        calibration_ok=calibration_ok, file_quality_ok=file_quality_ok,
                    ).reason_bits
                    calibration_raw = np.array(raw, copy=True)
                    calibration_raw[mask & (FY4B_QUALITY_FILL | FY4B_QUALITY_OUT_OF_RANGE) != 0] = fill_value
                    if channel <= 6:
                        values = calibrate_reflective(
                            calibration_raw, lookup=lookup, fill_value=fill_value, scale=scale, offset=offset,
                            esun=_channel_value(handle["Calibration"].attrs.get("ESUN", []), channel, 1.0),
                        ).reflectance
                        if zenith is not None:
                            solar, solar_quality = _solar_zenith_window(
                                zenith, window, target_height=height, target_width=width,
                            )
                            mask |= solar_quality
                            values = apparent_reflectance(
                                values, earth_sun_distance_ratio=earth_sun_ratio,
                                solar_zenith_degrees=solar,
                            )
                    else:
                        values = calibrate_emissive(calibration_raw, lookup=lookup, fill_value=fill_value, scale=scale, offset=offset).brightness_temperature
                    values = np.asarray(values, dtype=np.float32)
                    values[mask != 0] = np.nan
                    output_window = Window(
                        window.col_off - source_window.col_off,
                        window.row_off - source_window.row_off,
                        window.width,
                        window.height,
                    )
                    science_output.write(values, output_index, window=output_window)
                    quality_output.write(mask.astype(np.uint32), output_index, window=output_window)
            for output_index, description in enumerate(descriptions, 1):
                science_output.set_band_description(output_index, description)
                quality_output.set_band_description(output_index, "quality_mask")


def _nearest_geo_window(dataset: Any, window: Window, *, target_height: int, target_width: int) -> np.ndarray:
    source_height, source_width = dataset.shape[:2]
    rows = np.minimum(source_height - 1, ((np.arange(int(window.row_off), int(window.row_off + window.height)) + 0.5) * source_height / target_height).astype(int))
    columns = np.minimum(source_width - 1, ((np.arange(int(window.col_off), int(window.col_off + window.width)) + 0.5) * source_width / target_width).astype(int))
    block = np.asarray(dataset[rows.min():rows.max() + 1, columns.min():columns.max() + 1])
    return block[np.ix_(rows - rows.min(), columns - columns.min())]


def _source_window_for_roi(
    *,
    crs: str,
    transform: Affine,
    width: int,
    height: int,
    roi: list[float] | None,
    padding: int = _SOURCE_ROI_PADDING_PIXELS,
) -> Window:
    if roi is None:
        return Window(0, 0, width, height)
    try:
        bounds = transform_bounds("EPSG:4326", crs, *roi, densify_pts=101)
    except (ValueError, TypeError, rasterio.errors.RasterioError):
        # Some regions outside a geostationary satellite's visible limb cannot be
        # transformed. Keep the full source in that uncommon case so the later
        # warp retains its previous all-nodata/intersection behavior.
        return Window(0, 0, width, height)
    if not all(math.isfinite(value) for value in bounds):
        return Window(0, 0, width, height)
    requested = window_from_bounds(*bounds, transform=transform)
    column = max(0, int(math.floor(requested.col_off)) - padding)
    row = max(0, int(math.floor(requested.row_off)) - padding)
    right = min(width, int(math.ceil(requested.col_off + requested.width)) + padding)
    bottom = min(height, int(math.ceil(requested.row_off + requested.height)) + padding)
    if right <= column or bottom <= row:
        raise ValueError("FY-4B ROI does not intersect raster")
    return Window(column, row, right - column, bottom - row)


def _nearest_dataset_window(
    window: Window,
    *,
    target_height: int,
    target_width: int,
    source_height: int,
    source_width: int,
) -> Window:
    target = _bounded_integer_window(window, width=target_width, height=target_height)
    row, column = int(target.row_off), int(target.col_off)
    bottom = row + int(target.height)
    right = column + int(target.width)
    source_row = min(source_height - 1, int(math.floor((row + 0.5) * source_height / target_height)))
    source_column = min(source_width - 1, int(math.floor((column + 0.5) * source_width / target_width)))
    source_bottom = min(
        source_height,
        int(math.floor((bottom - 0.5) * source_height / target_height)) + 1,
    )
    source_right = min(
        source_width,
        int(math.floor((right - 0.5) * source_width / target_width)) + 1,
    )
    return Window(
        source_column,
        source_row,
        max(1, source_right - source_column),
        max(1, source_bottom - source_row),
    )


def _write_full_grid_tags(dataset: Any, *, width: int, height: int, transform: Affine) -> None:
    dataset.update_tags(
        ns=_FULL_GRID_TAG_NAMESPACE,
        full_width=str(width),
        full_height=str(height),
        full_transform=json.dumps(list(transform)[:6], separators=(",", ":")),
    )


def _tiled_profile(width: int, height: int, count: int, dtype: str, crs: str, transform: Affine, nodata: Any) -> dict[str, Any]:
    return {
        "driver": "GTiff", "width": width, "height": height, "count": count, "dtype": dtype,
        "crs": crs, "transform": transform, "nodata": nodata, "tiled": True,
        "blockxsize": 256, "blockysize": 256, "compress": "DEFLATE", "BIGTIFF": "IF_SAFER",
    }


def _solar_zenith_dataset(
    handle: h5py.File, geo_handle: h5py.File, shape: tuple[int, ...],
) -> Any | None:
    for name in ("Navigation/NOMSunZenith", "NOMObs/NOMSunZenith"):
        dataset = handle.get(name)
        if dataset is not None and dataset.shape == shape:
            return dataset
    for name in ("Navigation/NOMSunZenith", "NOMObs/NOMSunZenith"):
        dataset = geo_handle.get(name)
        if dataset is not None:
            if len(dataset.shape) != 2 or min(dataset.shape) < 1:
                raise ValueError("FY-4B GEO solar zenith dimensions are invalid")
            return dataset
    return None


def _solar_zenith_window(
    dataset: Any, window: Window, *, target_height: int, target_width: int,
) -> tuple[np.ndarray, np.ndarray]:
    raw = np.asarray(
        _nearest_geo_window(dataset, window, target_height=target_height, target_width=target_width),
        dtype=np.float32,
    )
    fill_value = _attribute_scalar(dataset.attrs, "FillValue")
    valid_range = np.asarray(dataset.attrs.get("valid_range", []), dtype=np.float64).reshape(-1)
    slope = _attribute_scalar(dataset.attrs, "Slope")
    intercept = _attribute_scalar(dataset.attrs, "Intercept")
    if (
        fill_value is None
        or valid_range.size != 2
        or not np.all(np.isfinite(valid_range))
        or valid_range[0] > valid_range[1]
        or slope is None
        or intercept is None
    ):
        raise ValueError("FY-4B solar zenith has invalid physical-value metadata")
    invalid = (
        ~np.isfinite(raw)
        | (raw == fill_value)
        | (raw < valid_range[0])
        | (raw > valid_range[1])
    )
    physical = raw * float(slope) + float(intercept)
    invalid |= ~np.isfinite(physical) | (physical < 0.0) | (physical > 180.0)
    night = ~invalid & (physical >= 90.0)
    quality = np.zeros(raw.shape, dtype=np.uint32)
    quality[invalid] |= FY4B_QUALITY_NAVIGATION_FAILURE
    quality[night] |= FY4B_QUALITY_NIGHT
    return np.where(invalid | night, np.nan, physical).astype(np.float32), quality


def _geo_outside_disk_window(
    dataset: Any, window: Window, *, target_height: int, target_width: int,
) -> np.ndarray:
    raw = np.asarray(
        _nearest_geo_window(dataset, window, target_height=target_height, target_width=target_width),
        dtype=np.float32,
    )
    fill_value = _attribute_scalar(dataset.attrs, "FillValue")
    valid_range = np.asarray(dataset.attrs.get("valid_range", []), dtype=np.float64).reshape(-1)
    if (
        fill_value is None
        or valid_range.size != 2
        or not np.all(np.isfinite(valid_range))
        or valid_range[0] > valid_range[1]
    ):
        raise ValueError("FY-4B satellite zenith has invalid disk-mask metadata")
    return (
        ~np.isfinite(raw)
        | (raw == fill_value)
        | (raw < valid_range[0])
        | (raw > valid_range[1])
    )


def _parameters(payload: dict[str, Any]) -> dict[str, Any]:
    from .fy4b_agri_profile import parse_fy4b_parameters

    raw = payload.get("parameterSnapshot")
    if not isinstance(raw, str):
        raise ValueError("FY-4B parameter snapshot must be a serialized JSON object")
    try:
        raw = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ValueError("FY-4B parameter snapshot is invalid") from error
    if not isinstance(raw, dict):
        raise ValueError("FY-4B parameter snapshot must decode to an object")
    return parse_fy4b_parameters(raw)


def _raw_paths(manifest: dict[str, Any]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for item in manifest.get("inputs", []):
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            continue
        key = item.get("artifactType", item.get("role"))
        if isinstance(key, str):
            result[key] = Path(item["path"])
    return result


def _require_paths(paths: dict[str, Path], keys: tuple[str, ...], message: str) -> None:
    missing = [key for key in keys if key not in paths]
    if missing:
        raise ValueError(f"{message}: {', '.join(missing)}")


def _read_nominal_grid(path: Path) -> tuple[str, Affine]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
        crs, transform = document["crs"], Affine(*document["transform"][:6])
    except (OSError, TypeError, ValueError, KeyError, json.JSONDecodeError) as error:
        raise ValueError("FY-4B nominal grid artifact is invalid") from error
    if not isinstance(crs, str) or not crs:
        raise ValueError("FY-4B nominal grid artifact is invalid")
    return crs, transform


def _source_nominal_grid(path: Path, *, width: int, height: int, fallback: tuple[str, Affine]) -> tuple[str, Affine]:
    try:
        with h5py.File(path, "r") as handle:
            attributes = handle.attrs
            grid = build_nominal_grid(
                nom_center_lon=_scalar_float(attributes["NOMCenterLon"]), nom_sat_height=_scalar_float(attributes["NOMSatHeight"]),
                earth_equatorial_radius=_scalar_float(attributes["dEA"]), inverse_flattening=_scalar_float(attributes["dObRecFlat"]),
                sampling_angle_micro_rad=_scalar_float(attributes["dSamplingAngle"]), stepping_angle_micro_rad=_scalar_float(attributes["dSteppingAngle"]),
                width=width, height=height,
            )
    except (OSError, KeyError, TypeError, ValueError, IndexError):
        return fallback
    return grid.crs, grid.transform


def _reproject_raster_windows(source_path: Path, destination_path: Path, parameters: dict[str, Any], resampling: Resampling) -> None:
    with rasterio.open(source_path) as source:
        transform, width, height = _target_grid(source, parameters)
        nodata = source.nodata
        profile = _tiled_profile(width, height, source.count, source.dtypes[0], parameters["targetCrs"], transform, nodata)
        with rasterio.open(destination_path, "w", **profile) as destination:
            destination_nodata = (
                np.nan
                if nodata is None and np.issubdtype(np.dtype(source.dtypes[0]), np.floating)
                else nodata
            )
            for index in range(1, source.count + 1):
                reproject(
                    source=rasterio.band(source, index),
                    destination=rasterio.band(destination, index),
                    src_transform=source.transform,
                    src_crs=source.crs,
                    src_nodata=source.nodata,
                    dst_transform=transform,
                    dst_crs=parameters["targetCrs"],
                    dst_nodata=destination_nodata,
                    resampling=resampling,
                    init_dest_nodata=True,
                    warp_mem_limit=256,
                    num_threads=_REPROJECT_THREADS,
                )
            for index, description in enumerate(source.descriptions, 1):
                destination.set_band_description(index, description or f"band-{index}")


def _target_grid(source: Any, parameters: dict[str, Any]) -> tuple[Affine, int, int]:
    source_transform, source_width, source_height = _full_source_grid(source)
    source_bounds = array_bounds(source_height, source_width, source_transform)
    transform, width, height = calculate_default_transform(
        source.crs, parameters["targetCrs"], source_width, source_height, *source_bounds,
    )
    roi = parameters.get("roi")
    if roi is None:
        return transform, width, height
    bounds = transform_bounds("EPSG:4326", parameters["targetCrs"], *roi, densify_pts=21)
    requested = window_from_bounds(*bounds, transform=transform).round_offsets().round_lengths()
    column = max(0, int(requested.col_off))
    row = max(0, int(requested.row_off))
    right = min(width, int(requested.col_off + requested.width))
    bottom = min(height, int(requested.row_off + requested.height))
    if right <= column or bottom <= row:
        raise ValueError("FY-4B ROI does not intersect raster")
    cropped = Window(column, row, right - column, bottom - row)
    return window_transform(cropped, transform), int(cropped.width), int(cropped.height)


def _full_source_grid(source: Any) -> tuple[Affine, int, int]:
    tags = source.tags(ns=_FULL_GRID_TAG_NAMESPACE)
    if not tags:
        return source.transform, source.width, source.height
    try:
        width = int(tags["full_width"])
        height = int(tags["full_height"])
        values = json.loads(tags["full_transform"])
        transform = Affine(*values[:6])
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise ValueError("FY-4B native full-grid metadata is invalid") from error
    if (
        width < 1
        or height < 1
        or len(values) < 6
        or not all(math.isfinite(float(value)) for value in values[:6])
    ):
        raise ValueError("FY-4B native full-grid metadata is invalid")
    return transform, width, height


def _raster_windows(width: int, height: int, block_size: int = 256) -> Iterator[Window]:
    for row in range(0, height, block_size):
        for column in range(0, width, block_size):
            yield Window(column, row, min(block_size, width - column), min(block_size, height - row))


def _copy_to_cog(source: Path, destination: Path) -> None:
    rio_copy(
        source, destination, driver="COG", BLOCKSIZE=512, COMPRESS="DEFLATE", BIGTIFF="IF_SAFER", copy_src_overviews=True,
    )


def _load_raster(path: Path) -> RasterData:
    with rasterio.open(path) as source:
        values = source.read()
        metadata = tuple(BandMetadata(name or f"band-{index}") for index, name in enumerate(source.descriptions, 1))
        return RasterData(values if source.count > 1 else values[0], source.crs.to_string(), source.transform, source.nodata, metadata)


def _write(values: np.ndarray, path: Path, crs: str, transform: Affine, descriptions: tuple[str, ...], nodata: Any = np.nan) -> Path:
    metadata = tuple(BandMetadata(name) for name in descriptions)
    return write_cog(RasterData(np.asarray(values), CRS.from_user_input(crs).to_wkt(), transform, nodata, metadata), path)


def _raster_summary(path: Path) -> dict[str, Any]:
    with rasterio.open(path) as source:
        finite_count = 0
        total_count = 0
        for _index, window in source.block_windows(1):
            values = source.read(window=window)
            finite_count += int(np.isfinite(values).sum())
            total_count += values.size
        return {
            "sha256": _sha256(path), "width": source.width, "height": source.height,
            "crs": source.crs.to_string() if source.crs else None, "bands": list(source.descriptions),
            "quality": {"validPixelRatio": finite_count / total_count if total_count else 0.0,
                        "nodataPixelCount": total_count - finite_count},
        }


def _attribute_scalar(attributes: Any, name: str, default: float | None = None) -> float | None:
    value = attributes.get(name)
    if value is None:
        return default
    values = np.asarray(value).reshape(-1)
    if values.size != 1:
        return default
    result = float(values[0])
    return result if math.isfinite(result) else default


def _scalar_float(value: Any) -> float:
    values = np.asarray(value).reshape(-1)
    if values.size != 1:
        raise ValueError("FY-4B navigation attribute is invalid")
    result = float(values[0])
    if not math.isfinite(result):
        raise ValueError("FY-4B navigation attribute is invalid")
    return result


def _channel_flag(handle: h5py.File, dataset_name: str, channel: int) -> bool:
    dataset = handle.get(dataset_name)
    if dataset is None:
        return True
    flags = np.asarray(dataset).reshape(-1)
    return flags.size <= channel - 1 or int(flags[channel - 1]) == 0


def _channel_value(values: Any, channel: int, default: float) -> float:
    flattened = np.asarray(values).reshape(-1)
    if flattened.size == 0:
        return default
    candidate = float(flattened[channel - 1] if flattened.size >= channel else flattened[0])
    return candidate if math.isfinite(candidate) else default


def _calibration_coefficients(handle: h5py.File, dataset: h5py.Dataset, channel: int) -> tuple[float, float]:
    slope, intercept = _attribute_scalar(dataset.attrs, "Slope"), _attribute_scalar(dataset.attrs, "Intercept")
    if slope is not None and intercept is not None:
        return slope, intercept
    calibration = handle.get("Calibration")
    raw = None if calibration is None else calibration.attrs.get("CALIBRATION_COEF(SCALE+OFFSET)")
    values = np.asarray(raw).reshape(-1) if raw is not None else np.array([])
    if values.size >= 2:
        if np.asarray(raw).ndim == 2 and np.asarray(raw).shape[0] >= channel:
            return float(np.asarray(raw)[channel - 1, 0]), float(np.asarray(raw)[channel - 1, 1])
        return float(values[0]), float(values[1])
    return 1.0, 0.0


def _named_band(raster: RasterData, prefix: str) -> np.ndarray | None:
    for index, metadata in enumerate(raster.band_metadata):
        if metadata.name.startswith(prefix):
            return raster.values if raster.values.ndim == 2 else raster.values[index]
    return None


def _required_artifact(artifacts: dict[str, Path], key: str, message: str) -> Path:
    path = artifacts.get(key)
    if path is None:
        raise ValueError(message)
    return path


def _saliency_source(standard: dict[str, Path], source_name: str) -> Path:
    if source_name in {"toa_nir_red_nd", "btd_c13_c14"} or ":" in source_name:
        return _required_artifact(standard, "FY4B_FEATURE_COG", "FY-4B saliency requires declared feature source")
    if source_name.startswith("C02_"):
        return _required_artifact(standard, "FY4B_RED_500M_COG", "FY-4B saliency requires declared standard source")
    if source_name.startswith("C03_"):
        return _required_artifact(standard, "FY4B_VIS_NIR_1000M_COG", "FY-4B saliency requires declared standard source")
    return _required_artifact(standard, "FY4B_MULTISPECTRAL_4000M_COG", "FY-4B saliency requires declared standard source")


def _remove_intermediates(directory: Path) -> None:
    for name in ("validation", "navigation", "native", "calibration", "grid"):
        path = directory / name
        if path.is_dir():
            shutil.rmtree(path)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact(output_uri: str, directory: Path, artifact_type: str, path: Path) -> dict[str, Any]:
    try:
        relative = path.resolve().relative_to(directory).as_posix()
    except ValueError as error:
        raise ValueError("FY-4B final artifact is outside its invocation directory") from error
    return {
        "uri": output_uri.rstrip("/") + "/" + relative,
        "type": artifact_type,
        "sizeBytes": path.stat().st_size,
        "checksum": "sha256:" + _sha256(path),
    }
