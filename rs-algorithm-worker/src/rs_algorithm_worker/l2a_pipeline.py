from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import shutil
from typing import Any, Sequence
import zipfile

from affine import Affine
import h5py
import numpy as np
from PIL import Image
from pyproj import Transformer
import rasterio
from scipy.ndimage import uniform_filter

from .algorithms.features import FeatureParameters, extract_features, feature_tile
from .algorithms.fusion import FusionParameters, fuse_bands
from .algorithms.saliency import SaliencyParameters, detect_saliency
from .models import BandMetadata, BandSet, RasterData
from .output_uri import logical_output_uri
from .operators.cog import COGWriteResult, write_cog, write_cog_blocks
from .operators.quality_mask import apply_quality_mask
from .product_adapters.hy1c_czi_l2a import (
    ReviewedL2AMapping,
    inspect_l2a_manifest,
    load_reviewed_mapping,
)
from rasterio.windows import Window


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


def _decode_summary(path: Path, mapping: ReviewedL2AMapping) -> dict[str, Any]:
    """按行块验证比例解码规则，避免为仅供阶段展示的结果创建整景数组。"""
    summaries: list[dict[str, Any]] = []
    with h5py.File(path, "r") as handle:
        for band in mapping.bands:
            dataset = handle[band.path]
            if len(dataset.shape) != 2:
                raise ValueError(f"L2A band {band.band_id} must be two-dimensional")
            valid_count = 0
            total_count = int(dataset.size)
            for start in range(0, dataset.shape[0], WINDOW_ROWS):
                raw = np.asarray(dataset[start:min(start + WINDOW_ROWS, dataset.shape[0]), :], dtype=np.float32)
                decoded = raw * float(dataset.attrs[band.scale_attribute]) + float(dataset.attrs[band.offset_attribute])
                valid = np.isfinite(decoded)
                valid &= decoded >= float(dataset.attrs[band.valid_min_attribute])
                valid &= decoded <= float(dataset.attrs[band.valid_max_attribute])
                fill = float(dataset.attrs[band.fill_value_attribute])
                if np.isfinite(fill):
                    valid &= raw != fill
                valid_count += int(valid.sum())
            summaries.append({
                "band": band.band_id,
                "totalPixelCount": total_count,
                "validPixelCount": valid_count,
                "invalidPixelCount": total_count - valid_count,
            })
    return {
        "version": "hy1c-czi-l2a-decode-summary-v1",
        "processing": "按窗口执行比例解码和有效范围校验；实际像元值由后续网格化阶段再次按窗口读取。",
        "bands": summaries,
    }


def _quality_flag_summary(path: Path, mapping: ReviewedL2AMapping) -> dict[str, Any]:
    """按行块汇总 L2 质量位，避免为阶段展示保存整景有效掩膜。"""
    bad_bits = sum(1 << bit for name, bit in mapping.flag_bits if name in mapping.default_bad_flag_names)
    with h5py.File(path, "r") as handle:
        flags = handle[mapping.quality_flags_path]
        if len(flags.shape) != 2:
            raise ValueError("L2A quality flags must be two-dimensional")
        total_count = int(flags.size)
        valid_count = 0
        for start in range(0, flags.shape[0], WINDOW_ROWS):
            values = np.asarray(flags[start:min(start + WINDOW_ROWS, flags.shape[0]), :])
            valid_count += int(((values & np.uint32(bad_bits)) == 0).sum())
    return {
        "version": "hy1c-czi-l2a-quality-summary-v1",
        "processing": "按窗口汇总默认坏质量位；实际像元筛选由后续网格化阶段再次按窗口执行。",
        "badFlagBits": int(bad_bits),
        "totalPixelCount": total_count,
        "validPixelCount": valid_count,
        "invalidPixelCount": total_count - valid_count,
    }


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
        "explainability": _standard_explainability(
            raster,
            load_reviewed_mapping(),
            quality_policy=parameters.quality_policy,
            resolution_meters=parameters.resolution_meters,
        ),
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
        _write_json(path, {
            "packageId": candidate["packageId"],
            "metadata": candidate["metadataSnapshot"],
            "explainability": {
                "version": "hy1c-validation-explainability-v1",
                "algorithm": "HY-1C CZI L2A 输入产品校验",
                "purpose": "确认 HDF5、XML 和浏览图属于同一产品，并验证四个水色波段、导航数组及质量位定义符合已审查映射。",
                "interpretation": "校验通过表示文件结构和必要元数据满足处理前提，不代表水色反射率质量或地理定位精度已经验证。",
                "limitations": ["该阶段不计算有效像元比例、辐射误差或定位误差。"],
            },
        })
        return {"workerId": "worker-local", "status": "SUCCEEDED",
                "artifacts": [_file_artifact(output_uri, output_dir, "HY1C_INPUT_VALIDATION_JSON", path)]}
    if stage_key == "DECODE_SCALE_AND_NODATA":
        summary = _decode_summary(_h5_input(list(inputs.values())), load_reviewed_mapping())
        artifacts = _write_stage_summaries(
            payload, output_uri, output_dir, summary,
            (("DECODED_CZI_SUMMARY_JSON", "decoded/summary.json"),),
        )
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": artifacts}
    if stage_key == "APPLY_L2_FLAGS":
        summary = _quality_flag_summary(_h5_input(list(inputs.values())), load_reviewed_mapping())
        artifacts = _write_stage_summaries(
            payload, output_uri, output_dir, summary,
            (("QUALITY_MASK_SUMMARY_JSON", "quality/summary.json"),),
        )
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": artifacts}
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
        cropped_path = _required_input(inputs, "cropped")
        result, raster_metadata = _stream_standard_cog(output_dir, cropped_path)
        metadata_path = output_dir / "standard" / "czi-l2a.json"
        _write_json(metadata_path, {
            "productCode": "HY-1C-CZI",
            "profileVersion": "L2A-v1",
            "bands": [item.name for item in raster_metadata["bands"]],
            "grid": {"crs": raster_metadata["crs"], "transform": list(tuple(raster_metadata["transform"])[:6])},
            "explainability": _standard_explainability(
                RasterData(np.empty((len(raster_metadata["bands"]), 1, 1), dtype=np.float32), raster_metadata["crs"], raster_metadata["transform"], np.nan, raster_metadata["bands"]),
                load_reviewed_mapping(), quality_policy="default", resolution_meters=abs(raster_metadata["transform"].a),
            ),
        })
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [
            _cog_artifact(output_uri, output_dir, "STANDARD_COG", result),
            _file_artifact(output_uri, output_dir, "STANDARD_METADATA_JSON", metadata_path),
        ]}
    if stage_key == "EXTRACT_CZI_FEATURES":
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _stream_feature_artifacts(
                    output_uri, output_dir, _required_input(inputs, "cropped"),
                    parameters.algorithms["features"],
                )}
    if stage_key == "FUSE_CZI_BANDS":
        raster = _read_raster_intermediate(
            _required_input(inputs, "cropped"), "CROPPED_CZI"
        )[0]
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _fusion_artifacts(output_uri, output_dir, raster, parameters.algorithms["fusion"])}
    if stage_key == "DETECT_CZI_SALIENCY":
        parameters = _parameters_without_scene(payload)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts":
                _stream_saliency_artifacts(
                    output_uri, output_dir, _required_input(inputs, "source"),
                    parameters.algorithms["saliency"],
                )}
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


def _write_stage_summaries(
    payload: dict[str, Any], output_uri: str, output_dir: Path, summary: dict[str, Any],
    defaults: tuple[tuple[str, str], ...],
) -> list[dict[str, Any]]:
    """兼容已入库的旧计划输出合同，同时让新计划只保留轻量摘要文件。"""
    declared = payload.get("outputs")
    specifications = defaults
    if isinstance(declared, list) and declared:
        parsed: list[tuple[str, str]] = []
        for item in declared:
            if not isinstance(item, dict):
                raise ValueError("L2A stage output declaration is invalid")
            artifact_type = item.get("artifactType")
            relative_path = item.get("relativePath")
            if not isinstance(artifact_type, str) or not isinstance(relative_path, str):
                raise ValueError("L2A stage output declaration is invalid")
            parsed.append((artifact_type, relative_path))
        specifications = tuple(parsed)
    artifacts: list[dict[str, Any]] = []
    for artifact_type, relative_path in specifications:
        relative = Path(relative_path)
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError("L2A stage output path is invalid")
        path = (output_dir / relative).resolve()
        if output_dir not in path.parents:
            raise ValueError("L2A stage output path is invalid")
        payload_summary = {**summary, "artifactType": artifact_type}
        _write_json(path, payload_summary)
        artifacts.append(_file_artifact(output_uri, output_dir, artifact_type, path))
    return artifacts


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


def _standard_explainability(
    raster: RasterData,
    mapping: ReviewedL2AMapping,
    *,
    quality_policy: str,
    resolution_meters: float,
) -> dict[str, Any]:
    flag_bits = dict(mapping.flag_bits)
    bad_flags = [
        {"name": name, "bit": flag_bits[name]}
        for name in mapping.default_bad_flag_names
    ]
    return {
        "version": "hy1c-processing-explainability-v1",
        "algorithm": "HY-1C CZI L2A 标准化处理",
        "purpose": "将 L2A 水色遥感反射率完成比例解码、有效范围过滤、质量位筛选、地理定位和规则网格化，并写出标准 COG。",
        "stages": [
            {
                "key": "DECODE_SCALE_AND_NODATA",
                "name": "比例解码与无效值处理",
                "meaning": "按各波段 Slope、Offset、FillValue、ValidMin 和 ValidMax 将源值转换为遥感反射率。",
            },
            {
                "key": "APPLY_L2_FLAGS",
                "name": "L2 质量位筛选",
                "meaning": "使用已审查映射中的默认坏质量位剔除不可靠像元。",
            },
            {
                "key": "GEOLOCATE_AND_GRID",
                "name": "地理定位与规则网格化",
                "meaning": "使用逐像元经纬度将有效观测映射到目标投影网格；同一目标格选择距离格心最近的观测。",
            },
            {
                "key": "WRITE_STANDARD_COG",
                "name": "标准 COG 写出",
                "meaning": "生成包含所选水色波段、统一投影和 NoData 的地图可读栅格。",
            },
        ],
        "bands": [
            {
                "name": metadata.name,
                "wavelengthNm": metadata.wavelength_nm,
                "unit": metadata.unit,
                "meaning": "离水遥感反射率波段；有效范围和比例因子来自已审查产品映射。",
            }
            for metadata in raster.band_metadata
        ],
        "qualityPolicy": {
            "name": quality_policy,
            "mappingVersion": mapping.mapping_version,
            "excludedFlags": bad_flags,
            "meaning": "命中任一默认坏质量位或任一所选波段数值无效时，该像元不会进入标准栅格。",
        },
        "grid": {
            "crs": raster.crs,
            "resolutionMeters": float(resolution_meters),
            "resampling": "nearest-source-to-grid-cell",
            "meaning": "输出为规则投影网格；当前实现不对水色值做双线性或高阶插值。",
        },
        "interpretation": "标准 COG 表示通过当前 L2 质量规则的水色反射率观测；空白区域表示无有效观测或被质量规则剔除，不表示反射率为零。",
        "limitations": [
            "质量筛选依据已审查的产品质量位，但不等同于与现场测量对比得到的水色产品精度。",
            "最近观测映射可能留下无覆盖网格，也可能受到源导航密度和目标分辨率影响。",
            "没有验证样本时，不输出水色参数反演或专题识别准确率。",
        ],
    }


def _stream_feature_artifacts(
    output_uri: str,
    output_dir: Path,
    cropped_path: Path,
    configuration: dict[str, Any],
) -> list[dict[str, Any]]:
    """Extract HY-1C features using bounded reads from the cropped intermediate."""
    if not configuration.get("enabled"):
        return []
    metrics = tuple(configuration.get("metrics", ()))
    window_size = int(configuration.get("windowSize", 3))
    supported_metrics = {"statistics", "gradient", "local_variance"}
    if not metrics or set(metrics).difference(supported_metrics):
        raise ValueError("unsupported feature metric")
    if "local_variance" in metrics and (window_size < 3 or window_size % 2 == 0):
        raise ValueError("local variance window size must be an odd integer of at least three")

    work_dir = output_dir / ".l2a-work"
    work_dir.mkdir(parents=True, exist_ok=True)
    values_path = work_dir / "czi-features-values.npy"
    values = None
    try:
        metadata = _extract_intermediate_metadata(cropped_path, values_path)
        values = np.load(values_path, mmap_mode="r", allow_pickle=False)
        if values.ndim != 3:
            raise ValueError("CROPPED_CZI values must be a band-first three-dimensional array")
        band_count, height, width = (int(item) for item in values.shape)
        band_metadata = tuple(
            BandMetadata(name, None if wavelength < 0 else wavelength, "1/sr")
            for name, wavelength in zip(metadata["names"], metadata["wavelengths"])
        )
        if len(band_metadata) != band_count:
            raise ValueError("CROPPED_CZI band metadata does not match values")
        if metadata["transform"] is None or metadata["crs"] is None:
            raise ValueError("CROPPED_CZI must have a projected grid for feature extraction")

        statistics, gradient_fills, valid_pixel_ratio = _stream_feature_statistics(
            values, band_metadata, metrics, tile_size=512,
        )
        feature_names = tuple(
            f"{metric}:{band.name}"
            for band in band_metadata
            for metric in ("gradient", "local_variance")
            if metric in metrics
        )
        artifacts: list[dict[str, Any]] = []
        if feature_names:
            feature_path = output_dir / "features" / "czi-features.tif"
            feature_path.parent.mkdir(parents=True, exist_ok=True)
            feature_band_metadata = tuple(BandMetadata(name) for name in feature_names)
            result = write_cog_blocks(
                output_path=feature_path,
                width=width,
                height=height,
                count=len(feature_names),
                dtype="float32",
                crs=metadata["crs"],
                transform=metadata["transform"],
                nodata=np.nan,
                band_metadata=feature_band_metadata,
                blocks=_feature_blocks(
                    values, band_metadata, metrics, window_size, gradient_fills, tile_size=512,
                ),
            )
            artifacts.append(_cog_artifact(output_uri, output_dir, "FEATURE_COG", result))

        metadata_path = output_dir / "features" / "czi-features.json"
        _write_json(metadata_path, {
            "version": "statistics-v1",
            "purpose": "局部梯度和纹理变化指标，不是目标分类结果",
            "statistics": statistics,
            "metrics": list(metrics),
            "windowSize": window_size,
            "validPixelRatio": valid_pixel_ratio,
            "interpretation": "gradient 表示局部变化强度；local_variance 表示窗口内纹理变化；空白区域表示没有有效观测。",
            "explainability": {
                "version": "hy1c-feature-explainability-v1",
                "algorithm": "HY-1C CZI L2A 特征提取",
                "purpose": "从质量筛选并网格化后的水色遥感反射率生成统计和局部纹理特征。",
                "metricDefinitions": {
                    "statistics": "统计各水色波段有效像元的数量、范围、均值和离散程度。",
                    "gradient": "衡量相邻像元反射率的局部变化强度，用于描述边界和突变。",
                    "local_variance": "衡量窗口内反射率的离散程度，用于描述局部纹理复杂度。",
                },
                "parameters": {"metrics": list(metrics), "windowSize": window_size, "windowUnit": "pixel"},
                "validityPolicy": "仅使用通过 L2 质量位筛选且数值处于波段有效范围内的像元；无效区域保持 NoData。",
                "interpretation": "特征用于描述影像空间变化，不直接代表藻华、浑浊水体或其他海洋现象的分类结论。",
                "limitations": [
                    "窗口大小和输出空间分辨率会影响梯度及局部方差。",
                    "没有人工标注或参考产品时，不能计算专题识别准确率。",
                ],
            },
        })
        artifacts.append(_file_artifact(output_uri, output_dir, "FEATURE_METADATA_JSON", metadata_path))
        return artifacts
    finally:
        if isinstance(values, np.memmap):
            values._mmap.close()
        values_path.unlink(missing_ok=True)


def _stream_standard_cog(output_dir: Path, cropped_path: Path) -> tuple[COGWriteResult, dict[str, Any]]:
    work_dir = output_dir / ".l2a-work"
    work_dir.mkdir(parents=True, exist_ok=True)
    values_path = work_dir / "czi-standard-values.npy"
    values = None
    try:
        metadata = _extract_intermediate_metadata(cropped_path, values_path)
        values = np.load(values_path, mmap_mode="r", allow_pickle=False)
        if values.ndim != 3:
            raise ValueError("CROPPED_CZI values must be a band-first three-dimensional array")
        bands = tuple(BandMetadata(name, None if wavelength < 0 else wavelength, "1/sr")
                      for name, wavelength in zip(metadata["names"], metadata["wavelengths"]))
        if len(bands) != values.shape[0] or metadata["crs"] is None or metadata["transform"] is None:
            raise ValueError("CROPPED_CZI grid metadata is invalid")
        path = output_dir / "standard" / "czi-l2a.tif"
        path.parent.mkdir(parents=True, exist_ok=True)
        height, width = int(values.shape[1]), int(values.shape[2])
        result = write_cog_blocks(
            output_path=path, width=width, height=height, count=len(bands), dtype="float32",
            crs=metadata["crs"], transform=metadata["transform"], nodata=np.nan,
            band_metadata=bands,
            blocks=((Window(column, row, min(512, width - column), min(512, height - row)),
                     np.asarray(values[:, row:min(row + 512, height), column:min(column + 512, width)]))
                    for row in range(0, height, 512) for column in range(0, width, 512)),
        )
        return result, {"bands": bands, "crs": metadata["crs"], "transform": metadata["transform"]}
    finally:
        if isinstance(values, np.memmap):
            values._mmap.close()
        values_path.unlink(missing_ok=True)


def _extract_intermediate_metadata(path: Path, values_path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        try:
            values_member = archive.getinfo("values.npy")
        except KeyError as error:
            raise ValueError("CROPPED_CZI intermediate does not contain values") from error
        values_path.parent.mkdir(parents=True, exist_ok=True)
        with archive.open(values_member) as source, values_path.open("wb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
    with np.load(path, allow_pickle=False) as data:
        producer = str(np.asarray(data["producerStage"]).item())
        if producer not in {"DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS", "GEOLOCATE_AND_GRID", "CROP_CZI"}:
            raise ValueError("invalid intermediate producer for CROPPED_CZI")
        names = [str(item) for item in np.asarray(data["bandNames"]).reshape(-1)]
        wavelengths = [int(item) for item in np.asarray(data["bandWavelengths"]).reshape(-1)]
        transform_values = np.asarray(data["transform"]).reshape(-1)
        transform = Affine(*[float(item) for item in transform_values]) if transform_values.size == 6 else None
        crs = str(np.asarray(data["crs"]).item()) or None
    return {"names": names, "wavelengths": wavelengths, "transform": transform, "crs": crs}


def _stream_feature_statistics(
    values: np.ndarray,
    band_metadata: tuple[BandMetadata, ...],
    metrics: tuple[str, ...],
    *,
    tile_size: int,
) -> tuple[dict[str, object], tuple[float | None, ...], float]:
    band_count, height, width = values.shape
    counts = np.zeros(band_count, dtype=np.int64)
    minima = np.full(band_count, np.inf, dtype=np.float64)
    maxima = np.full(band_count, -np.inf, dtype=np.float64)
    sums = np.zeros(band_count, dtype=np.float64)
    gradient_sums = np.zeros(band_count, dtype=np.float32)
    squares = np.zeros(band_count, dtype=np.float64)
    valid_all = 0
    for row in range(0, height, tile_size):
        for column in range(0, width, tile_size):
            tile = np.asarray(values[:, row:min(row + tile_size, height), column:min(column + tile_size, width)])
            finite = np.isfinite(tile)
            valid_all += int(finite.all(axis=0).sum())
            for index in range(band_count):
                valid_values = tile[index][finite[index]].astype(np.float64, copy=False)
                if valid_values.size:
                    counts[index] += valid_values.size
                    minima[index] = min(minima[index], float(valid_values.min()))
                    maxima[index] = max(maxima[index], float(valid_values.max()))
                    sums[index] += float(valid_values.sum())
                    squares[index] += float(np.square(valid_values).sum())
                    gradient_sums[index] = np.float32(
                        gradient_sums[index] + np.sum(tile[index][finite[index]], dtype=np.float32)
                    )
    result: dict[str, object] = {"validPixelRatio": float(counts.sum() / (band_count * height * width))}
    if "statistics" in metrics:
        for index, metadata in enumerate(band_metadata):
            if counts[index] == 0:
                result[metadata.name] = {"min": None, "max": None, "mean": None, "std": None}
            else:
                mean = sums[index] / counts[index]
                variance = max(squares[index] / counts[index] - mean * mean, 0.0)
                result[metadata.name] = {
                    "min": float(minima[index]), "max": float(maxima[index]),
                    "mean": float(mean), "std": float(math.sqrt(variance)),
                }
    # _gradient uses a float32 nanmean for float32 source rasters; round the
    # streamed mean the same way before filling invalid pixels.
    gradient_fills = tuple(
        float(np.float32(gradient_sums[index] / np.float32(counts[index]))) if counts[index] else None
        for index in range(band_count)
    )
    return result, gradient_fills, float(valid_all / (height * width)) if height and width else 0.0


def _feature_blocks(
    values: np.ndarray,
    band_metadata: tuple[BandMetadata, ...],
    metrics: tuple[str, ...],
    window_size: int,
    gradient_fills: tuple[float | None, ...],
    *,
    tile_size: int,
):
    _, height, width = values.shape
    halo = max(1 if "gradient" in metrics else 0, window_size // 2 if "local_variance" in metrics else 0)
    output_metrics = tuple(metric for metric in ("gradient", "local_variance") if metric in metrics)
    for row in range(0, height, tile_size):
        for column in range(0, width, tile_size):
            row_end = min(row + tile_size, height)
            column_end = min(column + tile_size, width)
            source_row = max(0, row - halo)
            source_column = max(0, column - halo)
            source_row_end = min(height, row_end + halo)
            source_column_end = min(width, column_end + halo)
            tile = np.asarray(values[:, source_row:source_row_end, source_column:source_column_end])
            core = (row - source_row, row_end - source_row, column - source_column, column_end - source_column)
            output = np.empty((len(band_metadata) * len(output_metrics), row_end - row, column_end - column), dtype=np.float32)
            output_index = 0
            for index in range(len(band_metadata)):
                features = feature_tile(tile[index], output_metrics, window_size, core, gradient_fill=gradient_fills[index])
                for metric in output_metrics:
                    output[output_index] = features[metric]
                    output_index += 1
            yield Window(column, row, column_end - column, row_end - row), output


def _stream_saliency_artifacts(
    output_uri: str,
    output_dir: Path,
    source_path: Path,
    configuration: dict[str, Any],
) -> list[dict[str, Any]]:
    """Compute HY-1C saliency in bounded tiles and write both outputs incrementally."""
    if not configuration.get("enabled"):
        return []
    source_name = configuration["source"]
    threshold = float(configuration.get("threshold", 0.5))
    window_size = int(configuration.get("windowSize", 5))
    if window_size < 3 or window_size % 2 == 0:
        raise ValueError("saliency window size must be an odd integer of at least three")

    work_dir = output_dir / ".l2a-work"
    work_dir.mkdir(parents=True, exist_ok=True)
    values_path = work_dir / "czi-saliency-values.npy"
    values = None
    raster_source = None
    try:
        if source_path.suffix.lower() == ".npz":
            metadata = _extract_intermediate_metadata(source_path, values_path)
            values = np.load(values_path, mmap_mode="r", allow_pickle=False)
            names = metadata["names"]
            index = names.index(source_name) if source_name in names else -1
            if index < 0:
                raise ValueError("L2A saliency source band is unavailable")
            source_values = values[index]
            crs, transform = metadata["crs"], metadata["transform"]
        else:
            raster_source = rasterio.open(source_path)
            names = list(raster_source.descriptions)
            index = names.index(source_name) if source_name in names else -1
            if index < 0:
                raise ValueError("L2A saliency source band is unavailable")
            source_values = raster_source
            crs, transform = raster_source.crs, raster_source.transform
        height, width = (int(source_values.shape[-2]), int(source_values.shape[-1]))
        if crs is None or transform is None:
            raise ValueError("L2A saliency source must have a geospatial grid")

        valid_count = 0
        candidate_count = 0
        global_max = 0.0
        for window, tile, core in _saliency_blocks(source_values, index, height, width, window_size, tile_size=512):
            raw_score, valid = _saliency_tile(tile, window_size, core)
            finite = np.isfinite(raw_score)
            if finite.any():
                global_max = max(global_max, float(np.nanmax(raw_score)))
            valid_count += int(valid.sum())

        directory = output_dir / "saliency"
        directory.mkdir(parents=True, exist_ok=True)
        score_path = directory / "score.tif"
        mask_path = directory / "mask.tif"
        score_metadata = (BandMetadata("saliency_score"),)
        mask_metadata = (BandMetadata("saliency_mask"),)
        preview_scale = max(1, int(math.ceil(max(height, width) / 2048)))
        preview = np.zeros((math.ceil(height / preview_scale), math.ceil(width / preview_scale), 4), dtype=np.uint8)
        candidate_count = 0
        for window, tile, core in _saliency_blocks(source_values, index, height, width, window_size, tile_size=512):
            raw_score, valid = _saliency_tile(tile, window_size, core)
            score = np.full(raw_score.shape, np.nan, dtype=np.float32)
            if global_max > 0:
                score[valid] = raw_score[valid] / global_max
            elif valid.any():
                score[valid] = 0.0
            candidate_count += int((np.isfinite(score) & (score >= threshold)).sum())
            row, column = int(window.row_off), int(window.col_off)
            row_end = row + int(window.height)
            column_end = column + int(window.width)
            preview_row_start = (row + preview_scale - 1) // preview_scale
            preview_row_end = (row_end + preview_scale - 1) // preview_scale
            preview_column_start = (column + preview_scale - 1) // preview_scale
            preview_column_end = (column_end + preview_scale - 1) // preview_scale
            # Sample at global preview coordinates. Restarting at score[0, 0]
            # for every tile shifts blocks whose origin is not scale-aligned.
            row_offset = preview_row_start * preview_scale - row
            column_offset = preview_column_start * preview_scale - column
            preview_rgba = _saliency_preview_rgba(
                score[row_offset::preview_scale, column_offset::preview_scale], threshold,
            )
            preview_row = slice(preview_row_start, preview_row_end)
            preview_column = slice(preview_column_start, preview_column_end)
            preview[preview_row, preview_column] = preview_rgba
        score_result = write_cog_blocks(
            output_path=score_path, width=width, height=height, count=1, dtype="float32",
            crs=crs, transform=transform, nodata=np.nan, band_metadata=score_metadata,
            blocks=_normalized_saliency_blocks(
                source_values, index, height, width, window_size, threshold, global_max,
                preview_scale=None, preview=None,
            ),
        )
        mask_result = write_cog_blocks(
            output_path=mask_path, width=width, height=height, count=1, dtype="uint8",
            crs=crs, transform=transform, nodata=0, band_metadata=mask_metadata,
            blocks=_normalized_saliency_blocks(
                source_values, index, height, width, window_size, threshold, global_max,
                preview_scale=None, preview=None,
                mask_only=True,
            ),
        )
        preview_path = directory / "preview.png"
        Image.fromarray(preview, mode="RGBA").save(preview_path, format="PNG")
        metadata_path = directory / "metadata.json"
        _write_json(metadata_path, {
            "purpose": "基于局部均值差异的显著性候选区域，不代表已确认目标",
            "sourceBand": source_name, "windowSize": window_size, "threshold": threshold,
            "validPixelRatio": float(valid_count / (height * width)) if height and width else 0.0,
            "candidatePixelRatio": float(candidate_count / (height * width)) if height and width else 0.0,
            "nodataDisplay": "透明", "previewDisplay": "低分区域透明，高分候选区域使用高亮热力色带",
            "explainability": {
                "version": "hy1c-saliency-explainability-v1",
                "algorithm": "HY-1C CZI L2A 局部对比显著性检测",
                "purpose": "在指定水色波段中识别相对于局部邻域变化较强的候选区域。",
                "parameters": {"sourceBand": source_name, "windowSize": window_size, "threshold": threshold},
                "outputs": {
                    "score": "归一化显著性分数，数值越高表示相对局部背景越突出。",
                    "mask": "达到阈值的候选区域掩膜。",
                    "preview": "低分及 NoData 区域透明、高分区域使用热力色带的浏览图。",
                },
                "interpretation": "显著区域仅表示局部光谱变化线索，不等同于藻华、污染、悬浮物或其他目标的确认。",
                "limitations": [
                    "窗口、阈值、空间分辨率和有效像元覆盖会共同影响候选区域范围。",
                    "没有人工标注或参考产品时，不能计算检测准确率和召回率。",
                ],
            },
        })
        return [
            _cog_artifact(output_uri, output_dir, "SALIENCY_SCORE_COG", score_result),
            _cog_artifact(output_uri, output_dir, "SALIENCY_MASK_COG", mask_result),
            _file_artifact(output_uri, output_dir, "SALIENCY_PREVIEW_PNG", preview_path),
            _file_artifact(output_uri, output_dir, "SALIENCY_METADATA_JSON", metadata_path),
        ]
    finally:
        if isinstance(values, np.memmap):
            values._mmap.close()
        values_path.unlink(missing_ok=True)
        if raster_source is not None:
            raster_source.close()


def _saliency_blocks(source: Any, band_index: int, height: int, width: int, window_size: int, *, tile_size: int):
    halo = window_size // 2
    for row in range(0, height, tile_size):
        for column in range(0, width, tile_size):
            row_end, column_end = min(row + tile_size, height), min(column + tile_size, width)
            source_row, source_column = max(0, row - halo), max(0, column - halo)
            source_row_end, source_column_end = min(height, row_end + halo), min(width, column_end + halo)
            window = Window(column, row, column_end - column, row_end - row)
            if hasattr(source, "read"):
                tile = source.read(band_index + 1, window=Window(source_column, source_row, source_column_end - source_column, source_row_end - source_row))
            else:
                tile = np.asarray(source[source_row:source_row_end, source_column:source_column_end])
            core = (row - source_row, row_end - source_row, column - source_column, column_end - source_column)
            yield window, tile, core


def _normalized_saliency_blocks(
    source: Any,
    band_index: int,
    height: int,
    width: int,
    window_size: int,
    threshold: float,
    global_max: float,
    *,
    preview_scale: int | None,
    preview: np.ndarray | None,
    mask_only: bool = False,
):
    for window, tile, core in _saliency_blocks(source, band_index, height, width, window_size, tile_size=512):
        raw_score, valid = _saliency_tile(tile, window_size, core)
        score = np.full(raw_score.shape, np.nan, dtype=np.float32)
        if global_max > 0:
            score[valid] = raw_score[valid] / global_max
        elif valid.any():
            score[valid] = 0.0
        mask = np.isfinite(score) & (score >= threshold)
        yield window, (mask.astype(np.uint8) if mask_only else score)[np.newaxis, ...]


def _saliency_tile(values: np.ndarray, window_size: int, core: tuple[int, int, int, int]) -> tuple[np.ndarray, np.ndarray]:
    valid = np.isfinite(values)
    counts = uniform_filter(valid.astype(np.float32), size=window_size, mode="nearest")
    local_mean = uniform_filter(np.where(valid, values, 0.0).astype(np.float32), size=window_size, mode="nearest")
    local_mean /= np.where(counts == 0, 1.0, counts)
    score = np.abs(values - local_mean).astype(np.float32)
    score[(counts == 0) | ~valid] = np.nan
    top, bottom, left, right = core
    return score[top:bottom, left:right], valid[top:bottom, left:right]


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
    values = raster.values if raster.values.ndim == 3 else raster.values[np.newaxis, ...]
    valid = np.isfinite(values)
    _write_json(metadata_path, {
        "version": feature_set.version,
        "purpose": "局部梯度和纹理变化指标，不是目标分类结果",
        "statistics": feature_set.statistics,
        "metrics": list(metrics),
        "windowSize": int(configuration.get("windowSize", 3)),
        "validPixelRatio": float(valid.all(axis=0).mean()) if valid.size else 0.0,
        "interpretation": "gradient 表示局部变化强度；local_variance 表示窗口内纹理变化；空白区域表示没有有效观测。",
        "explainability": {
            "version": "hy1c-feature-explainability-v1",
            "algorithm": "HY-1C CZI L2A 特征提取",
            "purpose": "从质量筛选并网格化后的水色遥感反射率生成统计和局部纹理特征。",
            "metricDefinitions": {
                "statistics": "统计各水色波段有效像元的数量、范围、均值和离散程度。",
                "gradient": "衡量相邻像元反射率的局部变化强度，用于描述边界和突变。",
                "local_variance": "衡量窗口内反射率的离散程度，用于描述局部纹理复杂度。",
            },
            "parameters": {
                "metrics": list(metrics),
                "windowSize": int(configuration.get("windowSize", 3)),
                "windowUnit": "pixel",
            },
            "validityPolicy": "仅使用通过 L2 质量位筛选且数值处于波段有效范围内的像元；无效区域保持 NoData。",
            "interpretation": "特征用于描述影像空间变化，不直接代表藻华、浑浊水体或其他海洋现象的分类结论。",
            "limitations": [
                "窗口大小和输出空间分辨率会影响梯度及局部方差。",
                "没有人工标注或参考产品时，不能计算专题识别准确率。",
            ],
        },
    })
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
    rgb = np.ascontiguousarray(np.moveaxis(fused.values, 0, -1))
    source_values = np.stack([raster.values[{metadata.name: index for index, metadata in enumerate(raster.band_metadata)}[name]]
                              for name in (configuration["red"], configuration["green"], configuration["blue"])])
    alpha = np.where(np.isfinite(source_values).all(axis=0), 255, 0).astype(np.uint8)
    Image.fromarray(np.dstack((rgb, alpha)), mode="RGBA").save(preview_path, format="PNG")
    metadata_path = output_dir / "fusion" / "czi-rgb.json"
    _write_json(metadata_path, {
        "purpose": "按指定波段组合生成的拉伸 RGB 预览",
        "bands": {"red": configuration["red"], "green": configuration["green"], "blue": configuration["blue"]},
        "stretch": {"lowPercentile": float(configuration.get("lowPercentile", 2)), "highPercentile": float(configuration.get("highPercentile", 98))},
        "validPixelRatio": float(np.isfinite(source_values).all(axis=0).mean()),
        "nodataDisplay": "透明",
        "explainability": {
            "version": "hy1c-fusion-explainability-v1",
            "algorithm": "HY-1C CZI L2A RGB 融合",
            "purpose": "将指定水色反射率波段映射到 RGB，生成便于人工浏览和地图叠加的增强影像。",
            "inputs": {
                "red": configuration["red"],
                "green": configuration["green"],
                "blue": configuration["blue"],
            },
            "processing": "分别按配置分位数对有效像元进行线性拉伸，然后映射到红、绿、蓝显示通道。",
            "interpretation": "融合颜色是显示增强结果，不是原始物理反射率，也不直接代表水体类型或污染类别。",
            "limitations": [
                "分位数拉伸会改变视觉对比度，不适合跨任务直接比较像素绝对值。",
                "三个源波段必须同时有效，输出像元才可见。",
            ],
        },
    })
    return [
        _cog_artifact(output_uri, output_dir, "FUSION_COG", write_cog(fused, fusion_path)),
        _file_artifact(output_uri, output_dir, "FUSION_PREVIEW_PNG", preview_path),
        _file_artifact(output_uri, output_dir, "FUSION_METADATA_JSON", metadata_path),
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
    preview = _saliency_preview_rgba(
        saliency.score.values,
        threshold=float(configuration.get("threshold", 0.5)),
    )
    Image.fromarray(preview, mode="RGBA").save(preview_path, format="PNG")
    metadata_path = directory / "metadata.json"
    _write_json(metadata_path, {
        "purpose": "基于局部均值差异的显著性候选区域，不代表已确认目标",
        "sourceBand": source,
        "windowSize": int(configuration.get("windowSize", 5)),
        "threshold": float(configuration.get("threshold", 0.5)),
        "validPixelRatio": float(np.isfinite(source_raster.values).mean()),
        "candidatePixelRatio": float(saliency.mask[np.isfinite(source_raster.values)].mean()) if np.isfinite(source_raster.values).any() else 0.0,
        "nodataDisplay": "透明",
        "previewDisplay": "低分区域透明，高分候选区域使用高亮热力色带",
        "explainability": {
            "version": "hy1c-saliency-explainability-v1",
            "algorithm": "HY-1C CZI L2A 局部对比显著性检测",
            "purpose": "在指定水色波段中识别相对于局部邻域变化较强的候选区域。",
            "parameters": {
                "sourceBand": source,
                "windowSize": int(configuration.get("windowSize", 5)),
                "threshold": float(configuration.get("threshold", 0.5)),
            },
            "outputs": {
                "score": "归一化显著性分数，数值越高表示相对局部背景越突出。",
                "mask": "达到阈值的候选区域掩膜。",
                "preview": "低分及 NoData 区域透明、高分区域使用热力色带的浏览图。",
            },
            "interpretation": "显著区域仅表示局部光谱变化线索，不等同于藻华、污染、悬浮物或其他目标的确认。",
            "limitations": [
                "窗口、阈值、空间分辨率和有效像元覆盖会共同影响候选区域范围。",
                "没有人工标注或参考产品时，不能计算检测准确率和召回率。",
            ],
        },
    })
    return [
        _cog_artifact(output_uri, output_dir, "SALIENCY_SCORE_COG", write_cog(saliency.score, score_path)),
        _cog_artifact(output_uri, output_dir, "SALIENCY_MASK_COG", write_cog(mask_raster, mask_path)),
        _file_artifact(output_uri, output_dir, "SALIENCY_PREVIEW_PNG", preview_path),
        _file_artifact(output_uri, output_dir, "SALIENCY_METADATA_JSON", metadata_path),
    ]


def _saliency_preview_rgb(score_values: np.ndarray) -> np.ndarray:
    """Render normalized saliency scores as a compact, human-readable heatmap."""
    values = np.asarray(score_values, dtype=np.float32)
    if values.ndim != 2:
        raise ValueError("saliency preview expects a two-dimensional score raster")
    values = np.nan_to_num(values, nan=0.0, posinf=1.0, neginf=0.0)
    values = np.clip(values, 0.0, 1.0)
    anchors = np.array(
        [
            [0, 0, 0],
            [49, 54, 149],
            [0, 174, 239],
            [255, 221, 0],
            [215, 25, 28],
        ],
        dtype=np.float32,
    )
    positions = np.linspace(0.0, 1.0, len(anchors), dtype=np.float32)
    flattened = values.reshape(-1)
    channels = [np.interp(flattened, positions, anchors[:, channel]) for channel in range(3)]
    return np.rint(np.stack(channels, axis=1).reshape(*values.shape, 3)).astype(np.uint8)


def _saliency_preview_rgba(score_values: np.ndarray, threshold: float) -> np.ndarray:
    """Render saliency as a readable overlay instead of a dark full-frame image."""
    values = np.asarray(score_values, dtype=np.float32)
    if values.ndim != 2:
        raise ValueError("saliency preview expects a two-dimensional score raster")
    if not 0.0 <= threshold <= 1.0:
        raise ValueError("saliency preview threshold must be between zero and one")
    valid = np.isfinite(values)
    # Scores far below the detection threshold are not useful on a map and
    # obscure the basemap when rendered as black. Keep them transparent.
    cutoff = min(0.2, max(0.02, threshold * 0.1))
    normalized = np.clip((np.nan_to_num(values, nan=0.0) - cutoff) / max(1e-6, 1.0 - cutoff), 0.0, 1.0)
    anchors = np.array(
        [
            [0, 200, 255],
            [0, 255, 170],
            [255, 235, 0],
            [255, 120, 0],
            [255, 0, 0],
        ],
        dtype=np.float32,
    )
    positions = np.linspace(0.0, 1.0, len(anchors), dtype=np.float32)
    flattened = normalized.reshape(-1)
    channels = [np.interp(flattened, positions, anchors[:, channel]) for channel in range(3)]
    rgb = np.rint(np.stack(channels, axis=1).reshape(*values.shape, 3)).astype(np.uint8)
    alpha = np.rint(np.clip((normalized - 0.05) / 0.95, 0.0, 1.0) * 255.0).astype(np.uint8)
    alpha[~valid] = 0
    alpha[values <= cutoff] = 0
    return np.dstack((rgb, alpha))


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
