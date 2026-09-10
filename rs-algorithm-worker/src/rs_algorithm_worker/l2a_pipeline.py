from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
from typing import Any, Sequence

from affine import Affine
import h5py
import numpy as np
from PIL import Image
from pyproj import Transformer

from .algorithms.features import FeatureParameters, extract_features
from .algorithms.fusion import FusionParameters, fuse_bands
from .algorithms.saliency import SaliencyParameters, detect_saliency
from .models import BandMetadata, BandSet, RasterData
from .output_uri import logical_output_uri
from .operators.cog import COGWriteResult, write_cog
from .operators.quality_mask import apply_quality_mask
from .product_adapters.hy1c_czi_l2a import (
    ReviewedL2AMapping,
    inspect_l2a_manifest,
    load_reviewed_mapping,
)


WINDOW_ROWS = 64
MAX_OUTPUT_WORK_BYTES = 6 * 1024**3


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


def execute_l2a_product(
    manifest: dict[str, Any], parameter_snapshot: dict[str, Any], output_uri: str
) -> list[dict[str, Any]]:
    """Run a complete HY-1C CZI L2A standardization in one Worker invocation.

    Java's current local Worker contract dispatches one self-contained stage.  The
    source is therefore decoded, quality-masked, gridded, cropped and written
    here rather than relying on non-persisted intermediate stage outputs.
    """
    from .l2a_profile import parse_l2a_parameters

    candidate = inspect_l2a_manifest(manifest)
    centre = candidate["metadataSnapshot"]["sceneCentre"]
    parameters = parse_l2a_parameters(
        parameter_snapshot,
        centre_longitude=centre["longitude"],
        centre_latitude=centre["latitude"],
    )
    output_dir = _output_directory(manifest)
    output_dir.mkdir(parents=True, exist_ok=True)
    main_path = Path(next(file["path"] for file in candidate["files"] if file["role"] == "MAIN"))
    raster = _grid_l2a_source(main_path, load_reviewed_mapping(), parameters, output_dir)

    artifacts: list[dict[str, Any]] = []
    standard_path = output_dir / "standard" / "czi-l2a.tif"
    standard_path.parent.mkdir(parents=True, exist_ok=True)
    standard = write_cog(raster, standard_path)
    artifacts.append(_cog_artifact(output_uri, output_dir, "STANDARD_COG", standard))

    metadata_path = output_dir / "standard" / "czi-l2a.json"
    _write_json(metadata_path, {
        "productCode": "HY-1C-CZI",
        "profileVersion": "L2A-v1",
        "packageId": candidate["packageId"],
        "mappingVersion": candidate["metadataSnapshot"]["mappingVersion"],
        "bands": [metadata.name for metadata in raster.band_metadata],
        "grid": {
            "crs": raster.crs,
            "resolutionMeters": parameters.resolution_meters,
            "transform": [float(value) for value in tuple(raster.transform)[:6]],
        },
        "qualityPolicy": parameters.quality_policy,
        "validPixelCount": standard.valid_pixel_count,
    })
    artifacts.append(_file_artifact(output_uri, output_dir, "STANDARD_METADATA_JSON", metadata_path))

    artifacts.extend(_feature_artifacts(output_uri, output_dir, raster, parameters.algorithms.get("features", {})))
    artifacts.extend(_fusion_artifacts(output_uri, output_dir, raster, parameters.algorithms.get("fusion", {})))
    artifacts.extend(_saliency_artifacts(output_uri, output_dir, raster, parameters.algorithms.get("saliency", {})))
    return artifacts


def execute_l2a_staged_stage(
    payload: dict[str, Any], manifest: dict[str, Any]
) -> dict[str, Any]:
    """Execute one HY-1C plan-v3 stage using manifest-authorized local paths."""
    stage_key = payload.get("stageKey")
    output = manifest.get("output")
    if not isinstance(stage_key, str) or not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("HY-1C staged execution requires stageKey and output directory")
    output_dir = Path(output["path"]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    inputs = _manifest_inputs(manifest)
    output_uri = logical_output_uri(payload.get("outputUri"))

    if stage_key == "VALIDATE_SOURCE":
        candidate = inspect_l2a_manifest(manifest)
        path = output_dir / "validation" / "source.json"
        _write_json(path, {"packageId": candidate["packageId"], "metadata": candidate["metadataSnapshot"]})
        return {"workerId": "worker-local", "status": "SUCCEEDED",
                "artifacts": [_file_artifact(output_uri, output_dir, "HY1C_INPUT_VALIDATION_JSON", path)]}
    if stage_key == "DECODE_SCALE_AND_NODATA":
        raster = _decode(_h5_input(list(inputs.values())), load_reviewed_mapping())
        path = output_dir / "decoded" / "czi.npz"
        _write_raster_intermediate(path, raster, None, stage_key)
        return {"workerId": "worker-local", "status": "SUCCEEDED",
                "artifacts": [_file_artifact(output_uri, output_dir, "DECODED_CZI_RASTER", path)]}
    if stage_key == "APPLY_L2_FLAGS":
        decoded = _read_raster_intermediate(_required_input(inputs, "decoded"), "DECODED_CZI_RASTER")
        result = _apply_flags(decoded[0], _h5_input([_required_input(inputs, "main")]), load_reviewed_mapping())
        quality_path = output_dir / "quality" / "czi.npz"
        mask_path = output_dir / "quality" / "valid-mask.npz"
        _write_raster_intermediate(quality_path, result.raster, result.valid_mask, stage_key)
        mask_path.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(mask_path, values=np.asarray(result.valid_mask, dtype=np.uint8), producerStage=stage_key)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [
            _file_artifact(output_uri, output_dir, "QUALITY_MASKED_CZI_RASTER", quality_path),
            _file_artifact(output_uri, output_dir, "VALID_MASK", mask_path),
        ]}
    if stage_key == "GEOLOCATE_AND_GRID":
        main = _required_input(inputs, "main")
        parameters = _parameters_from_main_input(payload, main)
        raster = _grid_l2a_source(main, load_reviewed_mapping(), parameters, output_dir)
        path = output_dir / "grid" / "czi.npz"
        _write_raster_intermediate(path, raster, None, stage_key)
        return {"workerId": "worker-local", "status": "SUCCEEDED",
                "artifacts": [_file_artifact(output_uri, output_dir, "GEOLOCATED_CZI_RASTER", path)]}
    if stage_key == "CROP_CZI":
        source = _read_raster_intermediate(_required_input(inputs, "grid"), "GEOLOCATED_CZI_RASTER")
        path = output_dir / "crop" / "czi.npz"
        _write_raster_intermediate(path, source[0], source[1], stage_key)
        return {"workerId": "worker-local", "status": "SUCCEEDED",
                "artifacts": [_file_artifact(output_uri, output_dir, "CROPPED_CZI", path)]}
    if stage_key == "WRITE_STANDARD_COG":
        raster = _read_raster_intermediate(_required_input(inputs, "cropped"), "CROPPED_CZI")[0]
        standard_path = output_dir / "standard" / "czi-l2a.tif"
        standard_path.parent.mkdir(parents=True, exist_ok=True)
        result = write_cog(raster, standard_path)
        metadata_path = output_dir / "standard" / "czi-l2a.json"
        _write_json(metadata_path, {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1",
                                    "bands": [item.name for item in raster.band_metadata],
                                    "grid": {"crs": raster.crs, "transform": list(tuple(raster.transform)[:6])}})
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [
            _cog_artifact(output_uri, output_dir, "STANDARD_COG", result),
            _file_artifact(output_uri, output_dir, "STANDARD_METADATA_JSON", metadata_path),
        ]}
    if stage_key == "EXTRACT_CZI_FEATURES":
        raster = _read_raster_intermediate(
            _required_input(inputs, "cropped"), "CROPPED_CZI"
        )[0]
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _feature_artifacts(output_uri, output_dir, raster, parameters.algorithms["features"])}
    if stage_key == "FUSE_CZI_BANDS":
        raster = _read_raster_intermediate(
            _required_input(inputs, "cropped"), "CROPPED_CZI"
        )[0]
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _fusion_artifacts(output_uri, output_dir, raster, parameters.algorithms["fusion"])}
    if stage_key == "DETECT_CZI_SALIENCY":
        raster = _read_raster_intermediate(
            _required_input(inputs, "source"), "CROPPED_CZI"
        )[0]
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _saliency_artifacts(output_uri, output_dir, raster, parameters.algorithms["saliency"])}
    raise ValueError(f"unsupported HY-1C CZI L2A staged stage: {stage_key}")


def _manifest_inputs(manifest: dict[str, Any]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for item in manifest.get("inputs", []):
        if not isinstance(item, dict) or not isinstance(item.get("path"), str):
            continue
        key = str(item.get("key") or item.get("role") or "").lower()
        if key:
            result[key] = Path(item["path"])
    return result


def _required_input(inputs: dict[str, Path], key: str) -> Path:
    value = inputs.get(key.lower())
    if value is None or not value.is_file():
        raise ValueError(f"HY-1C staged input is missing: {key}")
    return value


def _parameters_from_main_input(payload: dict[str, Any], main: Path) -> Any:
    from .l2a_profile import parse_l2a_parameters
    with h5py.File(main, "r") as handle:
        longitude = float(handle.attrs["Scene Center Longitude"])
        latitude = float(handle.attrs["Scene Center Latitude"])
    return parse_l2a_parameters(_parameter_snapshot_value(payload),
                                centre_longitude=longitude, centre_latitude=latitude)


def _parameters_without_scene(payload: dict[str, Any]) -> Any:
    from .l2a_profile import parse_l2a_parameters
    return parse_l2a_parameters(
        _parameter_snapshot_value(payload), centre_longitude=0.0, centre_latitude=0.0
    )


def _parameter_snapshot_value(payload: dict[str, Any]) -> dict[str, Any]:
    value = payload.get("parameterSnapshot", {})
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        return json.loads(value)
    raise ValueError("HY-1C parameter snapshot is invalid")


def _write_raster_intermediate(path: Path, raster: RasterData, valid_mask: np.ndarray | None, producer_stage: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(path, values=np.asarray(raster.values), validMask=np.asarray(valid_mask) if valid_mask is not None else np.array([], dtype=np.uint8),
                        crs=np.asarray(raster.crs or ""), transform=np.asarray(tuple(raster.transform)[:6] if raster.transform else [], dtype=np.float64),
                        nodata=np.asarray(raster.nodata if raster.nodata is not None else np.nan),
                        bandNames=np.asarray([item.name for item in raster.band_metadata]),
                        bandWavelengths=np.asarray([item.wavelength_nm if item.wavelength_nm is not None else -1 for item in raster.band_metadata]),
                        producerStage=np.asarray(producer_stage))


def _read_raster_intermediate(path: Path, expected_type: str) -> tuple[RasterData, np.ndarray | None]:
    with np.load(path, allow_pickle=False) as data:
        producer = str(np.asarray(data["producerStage"]).item())
        if producer not in {"DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS", "GEOLOCATE_AND_GRID", "CROP_CZI"}:
            raise ValueError(f"invalid intermediate producer for {expected_type}")
        values = np.asarray(data["values"])
        names = [str(item) for item in np.asarray(data["bandNames"]).reshape(-1)]
        wavelengths = [int(item) for item in np.asarray(data["bandWavelengths"]).reshape(-1)]
        transform_values = np.asarray(data["transform"]).reshape(-1)
        transform = Affine(*[float(item) for item in transform_values]) if transform_values.size == 6 else None
        crs = str(np.asarray(data["crs"]).item()) or None
        nodata = float(np.asarray(data["nodata"]).item())
        metadata = tuple(BandMetadata(name, None if wavelength < 0 else wavelength, "1/sr") for name, wavelength in zip(names, wavelengths))
        raster = RasterData(values, crs, transform, nodata, metadata)
        mask = np.asarray(data["validMask"])
        return raster, (mask.astype(bool) if mask.size else None)


def _grid_l2a_source(h5_path: Path, mapping: ReviewedL2AMapping, parameters, output_dir: Path) -> RasterData:
    source_window, source_bounds = _source_window(h5_path, mapping, parameters.roi_wgs84)
    transformer = Transformer.from_crs("EPSG:4326", parameters.resolved_crs, always_xy=True)
    geographic_bounds = parameters.roi_wgs84 or source_bounds
    min_x, min_y, max_x, max_y = _aligned_projected_bounds(
        transformer, geographic_bounds, parameters.resolution_meters
    )
    width = int(round((max_x - min_x) / parameters.resolution_meters))
    height = int(round((max_y - min_y) / parameters.resolution_meters))
    selected_bands = tuple(band for band in mapping.bands if band.band_id in parameters.bands)
    if len(selected_bands) != len(parameters.bands):
        raise ValueError("L2A selected bands are not covered by the reviewed mapping")
    _validate_output_grid(width, height, len(selected_bands))
    work_dir = output_dir / ".l2a-work"
    work_dir.mkdir(parents=True, exist_ok=True)
    values = np.memmap(
        work_dir / "gridded-values.f32", mode="w+", dtype=np.float32,
        shape=(len(selected_bands), height, width),
    )
    best_distance = np.memmap(
        work_dir / "nearest-distance.f32", mode="w+", dtype=np.float32, shape=(height, width)
    )
    values[...] = np.nan
    best_distance[...] = np.inf
    bad_bits = sum(1 << bit for name, bit in mapping.flag_bits if name in mapping.default_bad_flag_names)

    row_start, row_end, column_start, column_end = source_window
    with h5py.File(h5_path, "r") as handle:
        longitude_dataset = handle[mapping.longitude_path]
        latitude_dataset = handle[mapping.latitude_path]
        flags_dataset = handle[mapping.quality_flags_path]
        datasets = [handle[band.path] for band in selected_bands]
        for start in range(row_start, row_end, WINDOW_ROWS):
            row_slice = slice(start, min(start + WINDOW_ROWS, row_end))
            column_slice = slice(column_start, column_end)
            longitude = _decode_navigation(longitude_dataset[row_slice, column_slice], longitude_dataset, mapping)
            latitude = _decode_navigation(latitude_dataset[row_slice, column_slice], latitude_dataset, mapping)
            source_indexes, target_cells, squared_distances = _project_window(
                longitude, latitude, transformer, parameters.roi_wgs84,
                min_x, max_y, parameters.resolution_meters, width, height,
            )
            if source_indexes.size == 0:
                continue
            quality = (np.asarray(flags_dataset[row_slice, column_slice]).reshape(-1)[source_indexes]
                       & np.uint32(bad_bits)) == 0
            samples = [_decode_band_samples(dataset[row_slice, column_slice], dataset, band, source_indexes)
                       for dataset, band in zip(datasets, selected_bands)]
            valid = quality.copy()
            for sample in samples:
                valid &= np.isfinite(sample)
            if not valid.any():
                continue
            _accumulate_nearest(
                values, best_distance, target_cells[valid], squared_distances[valid],
                np.asarray([sample[valid] for sample in samples], dtype=np.float32),
            )

    values.flush()
    best_distance.flush()
    if not np.isfinite(values).any():
        raise ValueError("L2A quality filtering left no valid pixels in the requested output area")
    return RasterData(
        values=values,
        crs=parameters.resolved_crs,
        transform=Affine(parameters.resolution_meters, 0, min_x, 0, -parameters.resolution_meters, max_y),
        nodata=np.nan,
        band_metadata=tuple(BandMetadata(band.band_id, band.wavelength_nm, "1/sr") for band in selected_bands),
    )


def _validate_output_grid(width: int, height: int, band_count: int) -> None:
    # The gridding stage keeps one float32 array per band plus one nearest-distance array.
    work_bytes = width * height * (band_count + 1) * np.dtype(np.float32).itemsize
    if width <= 0 or height <= 0 or band_count <= 0 or work_bytes > MAX_OUTPUT_WORK_BYTES:
        raise ValueError("L2A output grid is too large; enable ROI or use a coarser resolution")


def _source_window(
    h5_path: Path, mapping: ReviewedL2AMapping, roi_wgs84: tuple[float, float, float, float] | None
) -> tuple[tuple[int, int, int, int], tuple[float, float, float, float]]:
    with h5py.File(h5_path, "r") as handle:
        longitude_dataset = handle[mapping.longitude_path]
        latitude_dataset = handle[mapping.latitude_path]
        if longitude_dataset.shape != latitude_dataset.shape or len(longitude_dataset.shape) != 2:
            raise ValueError("L2A navigation grids are invalid")
        height, width = longitude_dataset.shape
        min_row, max_row, min_column, max_column = height, -1, width, -1
        min_lon, min_lat, max_lon, max_lat = math.inf, math.inf, -math.inf, -math.inf
        for start in range(0, height, WINDOW_ROWS):
            row_slice = slice(start, min(start + WINDOW_ROWS, height))
            longitude = _decode_navigation(longitude_dataset[row_slice, :], longitude_dataset, mapping)
            latitude = _decode_navigation(latitude_dataset[row_slice, :], latitude_dataset, mapping)
            valid = _valid_navigation(longitude, latitude)
            if roi_wgs84 is not None:
                min_roi_lon, min_roi_lat, max_roi_lon, max_roi_lat = roi_wgs84
                valid &= ((longitude >= min_roi_lon) & (longitude <= max_roi_lon)
                          & (latitude >= min_roi_lat) & (latitude <= max_roi_lat))
            if not valid.any():
                continue
            indexes = np.flatnonzero(valid)
            rows, columns = np.divmod(indexes, width)
            min_row = min(min_row, start + int(rows.min()))
            max_row = max(max_row, start + int(rows.max()))
            min_column = min(min_column, int(columns.min()))
            max_column = max(max_column, int(columns.max()))
            valid_longitude = longitude[valid]
            valid_latitude = latitude[valid]
            min_lon = min(min_lon, float(valid_longitude.min()))
            min_lat = min(min_lat, float(valid_latitude.min()))
            max_lon = max(max_lon, float(valid_longitude.max()))
            max_lat = max(max_lat, float(valid_latitude.max()))
    if max_row < min_row or max_column < min_column:
        raise ValueError("L2A ROI does not intersect the source swath")
    return (min_row, max_row + 1, min_column, max_column + 1), (min_lon, min_lat, max_lon, max_lat)


def _project_window(
    longitude: np.ndarray,
    latitude: np.ndarray,
    transformer: Transformer,
    roi_wgs84: tuple[float, float, float, float] | None,
    min_x: float,
    max_y: float,
    resolution: float,
    width: int,
    height: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    valid = _valid_navigation(longitude, latitude)
    if roi_wgs84 is not None:
        min_lon, min_lat, max_lon, max_lat = roi_wgs84
        valid &= ((longitude >= min_lon) & (longitude <= max_lon)
                  & (latitude >= min_lat) & (latitude <= max_lat))
    source_indexes = np.flatnonzero(valid)
    if source_indexes.size == 0:
        empty = np.empty(0, dtype=np.int64)
        return empty, empty, np.empty(0, dtype=np.float32)
    x, y = transformer.transform(longitude.reshape(-1)[source_indexes], latitude.reshape(-1)[source_indexes])
    x = np.asarray(x, dtype=np.float64)
    y = np.asarray(y, dtype=np.float64)
    finite = np.isfinite(x) & np.isfinite(y)
    source_indexes = source_indexes[finite]
    x, y = x[finite], y[finite]
    columns = np.floor((x - min_x) / resolution).astype(np.int64)
    rows = np.floor((max_y - y) / resolution).astype(np.int64)
    inside = (columns >= 0) & (columns < width) & (rows >= 0) & (rows < height)
    source_indexes = source_indexes[inside]
    columns, rows = columns[inside], rows[inside]
    x, y = x[inside], y[inside]
    cells = rows * width + columns
    center_x = min_x + (columns + 0.5) * resolution
    center_y = max_y - (rows + 0.5) * resolution
    distances = ((x - center_x) ** 2 + (y - center_y) ** 2).astype(np.float32)
    return source_indexes, cells, distances


def _accumulate_nearest(
    values: np.ndarray, best_distance: np.ndarray, cells: np.ndarray,
    distances: np.ndarray, samples: np.ndarray,
) -> None:
    if cells.size == 0:
        return
    order = np.lexsort((distances, cells))
    ordered_cells = cells[order]
    first_for_cell = np.empty(ordered_cells.size, dtype=bool)
    first_for_cell[0] = True
    first_for_cell[1:] = ordered_cells[1:] != ordered_cells[:-1]
    chosen = order[first_for_cell]
    flat_distances = best_distance.reshape(-1)
    better = distances[chosen] < flat_distances[cells[chosen]]
    if not better.any():
        return
    chosen = chosen[better]
    target_cells = cells[chosen]
    flat_distances[target_cells] = distances[chosen]
    values.reshape(values.shape[0], -1)[:, target_cells] = samples[:, chosen]


def _decode_navigation(raw: np.ndarray, dataset: h5py.Dataset, mapping: ReviewedL2AMapping) -> np.ndarray:
    return (np.asarray(raw, dtype=np.float64) * float(dataset.attrs[mapping.navigation_scale_attribute])
            + float(dataset.attrs[mapping.navigation_offset_attribute]))


def _decode_band_samples(
    raw: np.ndarray, dataset: h5py.Dataset, band, source_indexes: np.ndarray
) -> np.ndarray:
    raw = np.asarray(raw, dtype=np.float32)
    decoded = raw * float(dataset.attrs[band.scale_attribute]) + float(dataset.attrs[band.offset_attribute])
    fill = float(dataset.attrs[band.fill_value_attribute])
    minimum = float(dataset.attrs[band.valid_min_attribute])
    maximum = float(dataset.attrs[band.valid_max_attribute])
    invalid = ~np.isfinite(decoded) | (decoded < minimum) | (decoded > maximum)
    if np.isfinite(fill):
        invalid |= raw == fill
    decoded[invalid] = np.nan
    return decoded.reshape(-1)[source_indexes].copy()


def _valid_navigation(longitude: np.ndarray, latitude: np.ndarray) -> np.ndarray:
    return (np.isfinite(longitude) & np.isfinite(latitude)
            & (longitude >= -180) & (longitude <= 180)
            & (latitude >= -90) & (latitude <= 90))


def _aligned_projected_bounds(
    transformer: Transformer, bounds: tuple[float, float, float, float], resolution: float
) -> tuple[float, float, float, float]:
    min_lon, min_lat, max_lon, max_lat = bounds
    min_x, min_y, max_x, max_y = transformer.transform_bounds(
        min_lon, min_lat, max_lon, max_lat, densify_pts=21
    )
    if not all(math.isfinite(value) for value in (min_x, min_y, max_x, max_y)) or max_x <= min_x or max_y <= min_y:
        raise ValueError("L2A projected output bounds are invalid")
    return (
        math.floor(min_x / resolution) * resolution,
        math.floor(min_y / resolution) * resolution,
        math.ceil(max_x / resolution) * resolution,
        math.ceil(max_y / resolution) * resolution,
    )


def _output_directory(manifest: dict[str, Any]) -> Path:
    output = manifest.get("output")
    path = output.get("path") if isinstance(output, dict) else None
    if not isinstance(path, str) or not path:
        raise ValueError("L2A output directory is required")
    return Path(path)


def _feature_artifacts(output_uri: str, output_dir: Path, raster: RasterData, configuration: dict[str, Any]) -> list[dict[str, Any]]:
    if not configuration.get("enabled"):
        return []
    metrics = tuple(configuration.get("metrics", ()))
    feature_set = extract_features(raster, FeatureParameters(metrics, int(configuration.get("windowSize", 3))))
    artifacts: list[dict[str, Any]] = []
    if feature_set.rasters:
        feature_path = output_dir / "features" / "czi-features.tif"
        feature_path.parent.mkdir(parents=True, exist_ok=True)
        feature_rasters = tuple(feature_set.rasters.values())
        feature_values = np.stack([item.values for item in feature_rasters])
        feature_raster = RasterData(
            feature_values, raster.crs, raster.transform, np.nan,
            tuple(item.band_metadata[0] for item in feature_rasters),
        )
        artifacts.append(_cog_artifact(output_uri, output_dir, "FEATURE_COG", write_cog(feature_raster, feature_path)))
    metadata_path = output_dir / "features" / "czi-features.json"
    _write_json(metadata_path, {"version": feature_set.version, "statistics": feature_set.statistics, "metrics": list(metrics)})
    artifacts.append(_file_artifact(output_uri, output_dir, "FEATURE_METADATA_JSON", metadata_path))
    return artifacts


def _fusion_artifacts(output_uri: str, output_dir: Path, raster: RasterData, configuration: dict[str, Any]) -> list[dict[str, Any]]:
    if not configuration.get("enabled"):
        return []
    fused = fuse_bands(BandSet(raster), FusionParameters(
        red=configuration["red"], green=configuration["green"], blue=configuration["blue"],
        low_percentile=float(configuration.get("lowPercentile", 2)),
        high_percentile=float(configuration.get("highPercentile", 98)),
    ))
    fusion_path = output_dir / "fusion" / "czi-rgb.tif"
    fusion_path.parent.mkdir(parents=True, exist_ok=True)
    preview_path = output_dir / "fusion" / "czi-rgb.png"
    Image.fromarray(np.ascontiguousarray(np.moveaxis(fused.values, 0, -1)), mode="RGB").save(preview_path, format="PNG")
    return [
        _cog_artifact(output_uri, output_dir, "FUSION_COG", write_cog(fused, fusion_path)),
        _file_artifact(output_uri, output_dir, "FUSION_PREVIEW_PNG", preview_path),
    ]


def _saliency_artifacts(output_uri: str, output_dir: Path, raster: RasterData, configuration: dict[str, Any]) -> list[dict[str, Any]]:
    if not configuration.get("enabled"):
        return []
    source = configuration["source"]
    try:
        index = next(index for index, metadata in enumerate(raster.band_metadata) if metadata.name == source)
    except StopIteration as error:
        raise ValueError("L2A saliency source band is unavailable") from error
    source_raster = RasterData(raster.values[index], raster.crs, raster.transform, np.nan, (raster.band_metadata[index],))
    saliency = detect_saliency(source_raster, SaliencyParameters(
        threshold=float(configuration.get("threshold", 0.5)), window_size=int(configuration.get("windowSize", 5))
    ))
    directory = output_dir / "saliency"
    directory.mkdir(parents=True, exist_ok=True)
    score_path = directory / "score.tif"
    mask_path = directory / "mask.tif"
    preview_path = directory / "preview.png"
    mask_raster = RasterData(
        saliency.mask.astype(np.uint8), raster.crs, raster.transform, 0,
        (BandMetadata("saliency_mask"),),
    )
    Image.fromarray((saliency.mask.astype(np.uint8) * 255), mode="L").save(preview_path, format="PNG")
    return [
        _cog_artifact(output_uri, output_dir, "SALIENCY_SCORE_COG", write_cog(saliency.score, score_path)),
        _cog_artifact(output_uri, output_dir, "SALIENCY_MASK_COG", write_cog(mask_raster, mask_path)),
        _file_artifact(output_uri, output_dir, "SALIENCY_PREVIEW_PNG", preview_path),
    ]


def _write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")), encoding="utf-8")


def _cog_artifact(output_uri: str, output_dir: Path, artifact_type: str, result: COGWriteResult) -> dict[str, Any]:
    return _artifact(output_uri, output_dir, artifact_type, result.path, result.byte_size, result.sha256)


def _file_artifact(output_uri: str, output_dir: Path, artifact_type: str, path: Path) -> dict[str, Any]:
    return _artifact(output_uri, output_dir, artifact_type, path, path.stat().st_size, _sha256(path))


def _artifact(
    output_uri: str, output_dir: Path, artifact_type: str, path: Path, byte_size: int, checksum: str
) -> dict[str, Any]:
    relative_path = path.relative_to(output_dir).as_posix()
    return {
        "uri": output_uri.rstrip("/") + "/" + relative_path,
        "type": artifact_type,
        "sizeBytes": byte_size,
        "checksum": "sha256:" + checksum,
    }


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
