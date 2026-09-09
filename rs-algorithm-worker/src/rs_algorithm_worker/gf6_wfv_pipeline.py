from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any
from xml.sax.saxutils import escape

import numpy as np
import rasterio
from rasterio.enums import Resampling
from rasterio.warp import reproject
from rasterio.windows import Window

from .gf6_processing import detect_gf6_saliency, extract_gf6_features
from .models_gf6 import Gf6ProductEvidence
from .operators.gf6_rpc import as_rasterio_rpc, build_nominal_rpc_grid, crop_nominal_rpc_grid
from .operators.windowed_cog import open_windowed_cog
from .product_adapters.gf6_wfv_l1a import PROFILE_KEY, inspect_evidence, parse_parameters


_NODATA = -9999.0
_BLOCK = 256
_RPC_WARP_THREADS = 2
_MAX_OUTPUT_PIXELS = 2_000_000_000
_MAX_ESTIMATED_OUTPUT_BYTES = 224 * 1024**3
_FUSION_SAMPLE_STEP = 16
_MAX_FUSION_SAMPLES_PER_BAND = 8_000_000


def execute_gf6_product(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    if payload.get("stageKey") != "STANDARDIZE_GF6_WFV_L1A":
        raise ValueError("unsupported GF6 L1A stage")
    output_uri = payload.get("outputUri")
    if not isinstance(output_uri, str) or not output_uri.startswith("rs://output/"):
        raise ValueError("GF6 L1A logical output URI is required")
    output = manifest.get("output")
    if not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("GF6 L1A temporary output directory is required")
    parameters = parse_parameters(_payload_parameters(payload))
    evidence = inspect_evidence(manifest)
    directory = Path(output["path"]).resolve(); directory.mkdir(parents=True, exist_ok=True)
    standard = directory / "standard" / "gf6-standard.tif"
    standard.parent.mkdir(parents=True, exist_ok=True)
    _write_standard_cog(evidence, standard, parameters)
    artifacts: list[tuple[str, Path]] = [("GF6_STANDARD_COG", standard)]
    summary = _write_processing_summary(standard, evidence, parameters)
    artifacts.append(("GF6_PROCESSING_SUMMARY_JSON", summary))
    if parameters["features"]["enabled"]: artifacts.extend(_write_features(standard, directory, parameters))
    if parameters["fusion"]["enabled"]: artifacts.extend(_write_fusion(standard, directory, parameters))
    if parameters["saliency"]["enabled"]: artifacts.extend(_write_saliency(standard, directory, parameters))
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [_artifact(output_uri, kind, path, directory) for kind, path in artifacts], "metadata": {"navigationStatus": "NOMINAL_RPC_NO_DEM", "accuracyStatus": "UNVALIDATED"}}


def execute_gf6_stage(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    """Execute one GF-6 WFV L1A plan-v3 stage using the task output directory."""
    stage = payload.get("stageKey")
    output_uri = payload.get("outputUri")
    output = manifest.get("output")
    if not isinstance(stage, str) or not isinstance(output_uri, str) or not output_uri.startswith("rs://output/"):
        raise ValueError("GF6 L1A stage and logical output URI are required")
    if not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("GF6 L1A temporary output directory is required")
    directory = Path(output["path"]).resolve(); directory.mkdir(parents=True, exist_ok=True)
    if stage == "VALIDATE_GF6_WFV_L1A":
        evidence = inspect_evidence(manifest)
        path = directory / "validation" / "package.json"; path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"profileKey": PROFILE_KEY, "packageId": evidence.package_id, "valid": True}, sort_keys=True) + "\n", encoding="utf-8")
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [_artifact(output_uri, "GF6_INPUT_VALIDATION_JSON", path, directory)]}
    parameters = parse_parameters(_payload_parameters(payload))
    evidence = inspect_evidence(manifest)
    if stage == "WRITE_GF6_STANDARD_COG":
        standard = directory / "standard" / "gf6-standard.tif"; standard.parent.mkdir(parents=True, exist_ok=True)
        _write_standard_cog(evidence, standard, parameters)
        summary = _write_processing_summary(standard, evidence, parameters)
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [_artifact(output_uri, "GF6_STANDARD_COG", standard, directory), _artifact(output_uri, "GF6_PROCESSING_SUMMARY_JSON", summary, directory)], "metadata": {"navigationStatus": "NOMINAL_RPC_NO_DEM", "accuracyStatus": "UNVALIDATED"}}
    standard = directory / "standard" / "gf6-standard.tif"
    if stage == "EXTRACT_GF6_FEATURES":
        return _stage_artifacts(output_uri, directory, _write_features(standard, directory, parameters))
    if stage == "FUSE_GF6_RGB":
        return _stage_artifacts(output_uri, directory, _write_fusion(standard, directory, parameters))
    if stage == "DETECT_GF6_SALIENCY":
        return _stage_artifacts(output_uri, directory, _write_saliency(standard, directory, parameters))
    raise ValueError("unsupported GF6 L1A stage")


def _stage_artifacts(output_uri: str, directory: Path, artifacts: list[tuple[str, Path]]) -> dict[str, Any]:
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [_artifact(output_uri, kind, path, directory) for kind, path in artifacts]}


def _write_standard_cog(evidence: Gf6ProductEvidence, path: Path, parameters: dict[str, Any]) -> None:
    grid = build_nominal_rpc_grid(evidence, parameters["targetCrs"], parameters["resolution"], 0.0)
    if parameters["roi"]: grid = crop_nominal_rpc_grid(grid, tuple(parameters["roi"]))
    _validate_output_budget(grid, parameters)
    vrt = path.with_name(f".{path.stem}-source.vrt")
    _write_mosaic_vrt(evidence, vrt)
    try:
        _resample_vrt_to_standard(evidence, vrt, path, grid, parameters["bands"])
    finally:
        vrt.unlink(missing_ok=True)


def _validate_output_budget(grid, parameters: dict[str, Any]) -> dict[str, int]:
    width, height = int(grid.width), int(grid.height)
    if width <= 0 or height <= 0:
        raise ValueError("GF6_OUTPUT_LIMIT_EXCEEDED: grid dimensions must be positive")
    pixels = width * height
    if pixels > _MAX_OUTPUT_PIXELS:
        raise ValueError(
            f"GF6_OUTPUT_LIMIT_EXCEEDED: grid pixels {pixels} exceed {_MAX_OUTPUT_PIXELS} "
            f"({grid.width}x{grid.height}); increase resolution or reduce ROI"
        )
    bytes_per_pixel = 4 * len(parameters["bands"])
    if parameters["features"]["enabled"]:
        raster_metric_count = sum(
            metric != "statistics" for metric in parameters["features"]["metrics"]
        )
        feature_band_count = len(parameters["bands"]) * max(1, raster_metric_count)
        bytes_per_pixel += 4 * feature_band_count
    if parameters["fusion"]["enabled"]:
        bytes_per_pixel += 3
    if parameters["saliency"]["enabled"]:
        bytes_per_pixel += 5
    pyramid_pixels = _cog_pyramid_pixel_counts(width, height)
    estimated_bytes = sum(pyramid_pixels) * bytes_per_pixel
    if parameters["fusion"]["enabled"]:
        # The RGB COG keeps valid zero-valued pixels distinguishable from
        # NoData through an internal one-bit dataset mask at every level.
        estimated_bytes += sum((level_pixels + 7) // 8 for level_pixels in pyramid_pixels)
    if estimated_bytes > _MAX_ESTIMATED_OUTPUT_BYTES:
        raise ValueError(
            f"GF6_OUTPUT_LIMIT_EXCEEDED: estimated output bytes {estimated_bytes} exceed "
            f"{_MAX_ESTIMATED_OUTPUT_BYTES}; reduce bands/derivatives/ROI or increase resolution"
        )
    return {
        "width": int(grid.width),
        "height": int(grid.height),
        "pixels": pixels,
        "estimatedBytes": estimated_bytes,
    }


def _cog_pyramid_pixel_counts(width: int, height: int) -> tuple[int, ...]:
    levels: list[int] = []
    while True:
        levels.append(width * height)
        if width <= _BLOCK and height <= _BLOCK:
            return tuple(levels)
        width = max(1, (width + 1) // 2)
        height = max(1, (height + 1) // 2)


def _write_mosaic_vrt(evidence: Gf6ProductEvidence, path: Path) -> None:
    rpc = "".join(f'<MDI key="{key}">{value}</MDI>' for key, value in as_rasterio_rpc(evidence.rpc).to_gdal().items())
    bands = []
    for index in range(1, 9):
        sources = "".join(
            '<SimpleSource>'
            f'<SourceFilename relativeToVRT="0">{escape(str(tile.path))}</SourceFilename>'
            f'<SourceBand>{index}</SourceBand><SrcRect xOff="0" yOff="0" xSize="{tile.width}" ySize="{tile.height}"/>'
            f'<DstRect xOff="{tile.column_offset}" yOff="{tile.row_offset}" xSize="{tile.width}" ySize="{tile.height}"/>'
            '</SimpleSource>' for tile in evidence.tiles
        )
        bands.append(f'<VRTRasterBand dataType="UInt16" band="{index}"><NoDataValue>0</NoDataValue>{sources}</VRTRasterBand>')
    path.write_text(f'<VRTDataset rasterXSize="{evidence.width}" rasterYSize="{evidence.height}"><Metadata domain="RPC">{rpc}</Metadata>{"".join(bands)}</VRTDataset>', encoding="utf-8")


def _resample_vrt_to_standard(evidence: Gf6ProductEvidence, vrt: Path, path: Path, grid, selected_bands: list[int]) -> None:
    profile = {"driver": "GTiff", "width": grid.width, "height": grid.height, "count": len(selected_bands), "dtype": "float32", "crs": grid.crs, "transform": grid.transform, "nodata": _NODATA, "interleave": "band", "BLOCKSIZE": _BLOCK, "COMPRESS": "DEFLATE", "BIGTIFF": "IF_SAFER"}
    with rasterio.open(vrt) as source, open_windowed_cog(
        path,
        profile,
        block_size=_BLOCK,
        overview_resampling="AVERAGE",
    ) as destination:
        if source.rpcs is None:
            raise ValueError("GF6_EVIDENCE_INVALID: VRT RPC metadata is unavailable")
        for output_index, band in enumerate(selected_bands, 1):
            reproject(
                rasterio.band(source, band),
                rasterio.band(destination, output_index),
                rpcs=source.rpcs,
                src_nodata=0,
                dst_transform=grid.transform,
                dst_crs=grid.crs,
                dst_nodata=_NODATA,
                resampling=Resampling.bilinear,
                init_dest_nodata=True,
                warp_mem_limit=256,
                num_threads=_RPC_WARP_THREADS,
                RPC_HEIGHT=0.0,
            )
            spec = evidence.bands[band - 1]
            for target_window in _windows(grid.width, grid.height):
                destination_values = destination.read(output_index, window=target_window)
                valid = destination_values != _NODATA
                destination_values[valid] = spec.gain * destination_values[valid] + spec.offset
                destination.write(destination_values, output_index, window=target_window)
            destination.set_band_description(output_index, f"B{spec.number}_{spec.wavelength}")
        destination.update_tags(calibrationFormula="AGain*DN+AOffset", calibrationUnit="W.m-2.sr-1.um-1", navigationStatus="NOMINAL_RPC_NO_DEM", accuracyStatus="UNVALIDATED", heightMode="CONSTANT", heightMeters="0", rpcResampling="bilinear")


def _write_processing_summary(standard: Path, evidence: Gf6ProductEvidence, parameters: dict[str, Any]) -> Path:
    path = standard.with_name("summary.json")
    path.write_text(json.dumps({"profileKey": {"productCode": "GF-6-WFV-L1A", "profileVersion": "v1"}, "declaredDimensions": evidence.metadata_snapshot["declaredDimensions"], "tileCoverageDimensions": evidence.metadata_snapshot["tileCoverageDimensions"], "tileCoverageDelta": evidence.metadata_snapshot["tileCoverageDelta"], "navigationStatus": "NOMINAL_RPC_NO_DEM", "accuracyStatus": "UNVALIDATED", "rpcHeightMode": "CONSTANT", "rpcHeight": 0.0, "bands": parameters["bands"]}, sort_keys=True) + "\n", encoding="utf-8")
    return path


def _write_features(standard: Path, directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    path, summary = directory / "features" / "gf6-features.tif", directory / "features" / "summary.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    with rasterio.open(standard) as source:
        metrics = parameters["features"]["metrics"]
        raster_metrics = [metric for metric in metrics if metric != "statistics"]
        statistics_only = not raster_metrics
        output_count = source.count if statistics_only else len(raster_metrics) * source.count
        descriptions = tuple(description or f"B{band}" for description, band in zip(source.descriptions, parameters["bands"]))
        statistics = [_empty_statistics() for _ in range(source.count)]
        profile = source.profile.copy(); profile.update(driver="GTiff", count=output_count, dtype="float32", nodata=_NODATA, interleave="band", BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE", BIGTIFF="IF_SAFER")
        with open_windowed_cog(
            path,
            profile,
            block_size=_BLOCK,
            overview_resampling="AVERAGE",
        ) as dest:
            if statistics_only:
                for index, description in enumerate(descriptions, 1):
                    dest.set_band_description(index, f"statistics:value:{description}")
                dest.update_tags(statisticsRasterSemantics="VALID_STANDARDIZED_VALUE_CARRIER")
            else:
                index = 1
                for metric in raster_metrics:
                    for description in descriptions:
                        dest.set_band_description(index, f"{metric}:{description}")
                        index += 1
            halo = _feature_halo(metrics, parameters["features"].get("windowSize", 3))
            for window in _windows(source.width, source.height):
                read_window, centre_rows, centre_columns = _window_with_halo(
                    window, source.width, source.height, halo
                )
                values = source.read(window=read_window)
                valid = np.isfinite(values)
                if source.nodata is not None and np.isfinite(source.nodata):
                    valid &= values != source.nodata
                centre_values = values[:, centre_rows, centre_columns]
                centre_valid = valid[:, centre_rows, centre_columns]
                if "statistics" in metrics:
                    for band_index in range(source.count):
                        _update_statistics(
                            statistics[band_index],
                            centre_values[band_index][centre_valid[band_index]],
                        )
                values = np.where(valid, values, np.nan)
                features = extract_gf6_features(values, metrics=raster_metrics or ["statistics"], window_size=parameters["features"].get("windowSize", 3))
                index = 1
                for metric in raster_metrics:
                    for band_index, band in enumerate(features[metric]):
                        centre_band = band[centre_rows, centre_columns]
                        output = np.where(
                            centre_valid[band_index] & np.isfinite(centre_band),
                            centre_band,
                            _NODATA,
                        ).astype(np.float32)
                        dest.write(output, index, window=window)
                        index += 1
                if statistics_only:
                    dest.write(
                        np.where(centre_valid, centre_values, _NODATA).astype(np.float32),
                        window=window,
                    )
    payload: dict[str, Any] = {"metrics": parameters["features"]["metrics"]}
    if "statistics" in parameters["features"]["metrics"]:
        payload.update({
            "statisticsMethod": "windowed-chan-population-v1",
            "nodataPolicy": "IGNORE_NODATA_AND_NAN",
            "bandStatistics": [
                _finish_statistics(item, output_index, band, description)
                for output_index, (item, band, description) in enumerate(
                    zip(statistics, parameters["bands"], descriptions), 1
                )
            ],
        })
    summary.write_text(json.dumps(payload, allow_nan=False, sort_keys=True) + "\n", encoding="utf-8")
    return [("GF6_FEATURES_COG", path), ("GF6_FEATURES_SUMMARY_JSON", summary)]


def _empty_statistics() -> dict[str, float | int | None]:
    return {"count": 0, "min": None, "max": None, "mean": 0.0, "m2": 0.0}


def _update_statistics(statistics: dict[str, float | int | None], pixels: np.ndarray) -> None:
    if pixels.size == 0:
        return
    values = pixels.astype(np.float64, copy=False)
    chunk_count = int(values.size)
    chunk_mean = float(values.mean(dtype=np.float64))
    centered = values - chunk_mean
    chunk_m2 = float(np.sum(centered * centered, dtype=np.float64))
    count = int(statistics["count"])
    if count == 0:
        statistics.update({
            "count": chunk_count,
            "min": float(values.min()),
            "max": float(values.max()),
            "mean": chunk_mean,
            "m2": chunk_m2,
        })
        return
    total = count + chunk_count
    mean = float(statistics["mean"])
    delta = chunk_mean - mean
    statistics["count"] = total
    statistics["min"] = min(float(statistics["min"]), float(values.min()))
    statistics["max"] = max(float(statistics["max"]), float(values.max()))
    statistics["mean"] = mean + delta * chunk_count / total
    statistics["m2"] = float(statistics["m2"]) + chunk_m2 + delta * delta * count * chunk_count / total


def _finish_statistics(
    statistics: dict[str, float | int | None],
    source_band_index: int,
    source_band: int,
    description: str,
) -> dict[str, Any]:
    count = int(statistics["count"])
    return {
        "sourceBandIndex": source_band_index,
        "sourceBand": f"B{source_band}",
        "description": description,
        "count": count,
        "min": statistics["min"],
        "max": statistics["max"],
        "mean": float(statistics["mean"]) if count else None,
        "std": math.sqrt(max(0.0, float(statistics["m2"]) / count)) if count else None,
    }


def _write_fusion(standard: Path, directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    path, summary = directory / "fusion" / "gf6-rgb.tif", directory / "fusion" / "summary.json"; path.parent.mkdir(parents=True, exist_ok=True); config = parameters["fusion"]
    with rasterio.open(standard) as source:
        selected = tuple(parameters["bands"].index(config[name]) + 1 for name in ("red", "green", "blue"))
        stretches, valid_sample_counts, sampling = _global_fusion_stretches(
            source, selected, tuple(config["percentiles"])
        )
        profile = source.profile.copy(); profile.update(driver="GTiff", count=3, dtype="uint8", BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE", BIGTIFF="IF_SAFER", photometric="RGB")
        profile.pop("nodata", None)
        with open_windowed_cog(
            path,
            profile,
            block_size=_BLOCK,
            overview_resampling="AVERAGE",
            internal_mask=True,
        ) as dest:
            for window in _windows(source.width, source.height):
                values = source.read(selected, window=window).astype(np.float32)
                valid_channels = np.isfinite(values) & (values != _NODATA)
                output = np.zeros(values.shape, dtype=np.uint8)
                for index, (lower, upper) in enumerate(stretches):
                    valid = valid_channels[index]
                    output[index, valid] = np.clip(
                        (values[index, valid] - lower)
                        * 255.0
                        / max(upper - lower, 1e-6),
                        0,
                        255,
                    ).astype(np.uint8)
                dest.write(output, window=window)
                dest.write_mask(
                    np.where(valid_channels.all(axis=0), 255, 0).astype(np.uint8),
                    window=window,
                )
            for index, name in enumerate(("red", "green", "blue"), 1):
                dest.set_band_description(index, f"{name}_B{config[name]}")
    names = ("red", "green", "blue")
    summary.write_text(
        json.dumps(
            {
                "version": "percentile-rgb-v1",
                "bands": {name: config[name] for name in names},
                "percentiles": config["percentiles"],
                "stretches": stretches,
                "outputNodata": None,
                "validityPolicy": "INTERNAL_DATASET_MASK_ALL_RGB_SOURCE_BANDS_VALID",
                "datasetMaskValues": {"invalid": 0, "valid": 255},
                "sampling": {
                    **sampling,
                    "validSamplesPerBand": dict(zip(names, valid_sample_counts)),
                },
            },
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    return [("GF6_RGB_FUSION_COG", path), ("GF6_FUSION_SUMMARY_JSON", summary)]


def _fusion_sampling_plan(width: int, height: int) -> dict[str, int]:
    if width <= 0 or height <= 0:
        raise ValueError("GF6 fusion source dimensions are invalid")
    sample_columns = (int(width) + _FUSION_SAMPLE_STEP - 1) // _FUSION_SAMPLE_STEP
    sample_rows = (int(height) + _FUSION_SAMPLE_STEP - 1) // _FUSION_SAMPLE_STEP
    base_sample_count = sample_columns * sample_rows
    thinning_stride = max(
        1,
        (base_sample_count + _MAX_FUSION_SAMPLES_PER_BAND - 1)
        // _MAX_FUSION_SAMPLES_PER_BAND,
    )
    retained_upper_bound = (
        base_sample_count + thinning_stride - 1
    ) // thinning_stride
    return {
        "step": _FUSION_SAMPLE_STEP,
        "baseSampleCountPerBand": base_sample_count,
        "thinningStride": thinning_stride,
        "maximumSamplesPerBand": _MAX_FUSION_SAMPLES_PER_BAND,
        "retainedSampleUpperBoundPerBand": retained_upper_bound,
    }


def _global_fusion_stretches(source, selected: tuple[int, int, int], percentiles: tuple[float, float]):
    low, high = percentiles
    if not 0 <= low < high <= 100:
        raise ValueError("GF6 fusion percentiles are invalid")
    sampling = _fusion_sampling_plan(source.width, source.height)
    stretches: list[tuple[float, float]] = []
    valid_sample_counts: list[int] = []
    for band_index in selected:
        samples = np.empty(
            sampling["retainedSampleUpperBoundPerBand"], dtype=np.float32
        )
        retained_count = 0
        visited_count = 0
        for window in _windows(source.width, source.height):
            sampled = source.read(band_index, window=window)[
                ::_FUSION_SAMPLE_STEP, ::_FUSION_SAMPLE_STEP
            ].reshape(-1)
            start = (-visited_count) % sampling["thinningStride"]
            retained = sampled[start::sampling["thinningStride"]]
            visited_count += int(sampled.size)
            valid = np.isfinite(retained) & (retained != _NODATA)
            retained = retained[valid]
            next_count = retained_count + int(retained.size)
            if next_count > samples.size:
                raise ValueError("GF6 fusion sampling exceeded its memory bound")
            samples[retained_count:next_count] = retained
            retained_count = next_count
        if retained_count:
            lower, upper = np.percentile(samples[:retained_count], (low, high))
            stretches.append((float(lower), float(upper)))
        else:
            stretches.append((0.0, 1.0))
        valid_sample_counts.append(retained_count)
    return tuple(stretches), tuple(valid_sample_counts), sampling


def _write_saliency(standard: Path, directory: Path, parameters: dict[str, Any]) -> list[tuple[str, Path]]:
    score_path, mask_path, summary = directory / "saliency" / "score.tif", directory / "saliency" / "mask.tif", directory / "saliency" / "summary.json"; score_path.parent.mkdir(parents=True, exist_ok=True); config = parameters["saliency"]
    with rasterio.open(standard) as source:
        profile = source.profile.copy(); profile.update(driver="GTiff", count=1, dtype="float32", nodata=_NODATA, BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE", BIGTIFF="IF_SAFER"); mask_profile = profile.copy(); mask_profile.update(dtype="uint8", nodata=255)
        source_index = parameters["bands"].index(int(config["source"][1:])) + 1
        with open_windowed_cog(
            score_path,
            profile,
            block_size=_BLOCK,
            overview_resampling="AVERAGE",
        ) as score, open_windowed_cog(
            mask_path,
            mask_profile,
            block_size=_BLOCK,
            overview_resampling="NEAREST",
        ) as mask:
            for window in _windows(source.width, source.height):
                read_window, centre_rows, centre_columns = _window_with_halo(
                    window,
                    source.width,
                    source.height,
                    config["windowSize"] // 2,
                )
                values = source.read(source_index, window=read_window)
                valid = np.isfinite(values) & (values != _NODATA)
                centre_valid = valid[centre_rows, centre_columns]
                values = np.where(valid, values, np.nan)
                scores, masks = detect_gf6_saliency(
                    values,
                    window_size=config["windowSize"],
                    threshold=config["threshold"],
                )
                centre_scores = scores[centre_rows, centre_columns]
                centre_masks = masks[centre_rows, centre_columns]
                score.write(
                    np.where(
                        centre_valid & np.isfinite(centre_scores),
                        centre_scores,
                        _NODATA,
                    ).astype(np.float32),
                    1,
                    window=window,
                )
                mask.write(
                    np.where(centre_valid, centre_masks.astype(np.uint8), 255).astype(np.uint8),
                    1,
                    window=window,
                )
            score.set_band_description(1, f"saliency_score:{config['source']}")
            mask.set_band_description(1, f"saliency_mask:{config['source']}")
    summary.write_text(json.dumps({"interpretation": "CANDIDATE_ONLY", **config, "scoreNodata": _NODATA, "maskNodata": 255, "maskValues": {"notSalient": 0, "salient": 1, "invalid": 255}, "validityPolicy": "SOURCE_PIXEL_MUST_BE_VALID"}, sort_keys=True) + "\n", encoding="utf-8")
    return [("GF6_SALIENCY_SCORE_COG", score_path), ("GF6_SALIENCY_MASK_COG", mask_path), ("GF6_SALIENCY_SUMMARY_JSON", summary)]


def _windows(width: int, height: int):
    for row in range(0, height, _BLOCK):
        for col in range(0, width, _BLOCK): yield Window(col, row, min(_BLOCK, width-col), min(_BLOCK, height-row))
def _feature_halo(metrics: list[str], window_size: int) -> int:
    radius = 1 if "gradient" in metrics else 0
    if "local_variance" in metrics:
        radius = max(radius, window_size // 2)
    return radius
def _window_with_halo(window: Window, width: int, height: int, radius: int):
    left = max(0, int(window.col_off) - radius)
    top = max(0, int(window.row_off) - radius)
    right = min(width, int(window.col_off + window.width) + radius)
    bottom = min(height, int(window.row_off + window.height) + radius)
    expanded = Window(left, top, right - left, bottom - top)
    row_offset = int(window.row_off) - top
    column_offset = int(window.col_off) - left
    return (
        expanded,
        slice(row_offset, row_offset + int(window.height)),
        slice(column_offset, column_offset + int(window.width)),
    )
def _payload_parameters(payload: dict[str, Any]) -> dict[str, Any]:
    snapshot = payload.get("parameterSnapshot", payload.get("parameters", {}))
    if isinstance(snapshot, str):
        try: snapshot = json.loads(snapshot)
        except json.JSONDecodeError as error: raise ValueError("GF6_PARAMETER_INVALID: parameterSnapshot") from error
    if not isinstance(snapshot, dict): raise ValueError("GF6_PARAMETER_INVALID: parameterSnapshot")
    return snapshot
def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""): digest.update(chunk)
    return digest.hexdigest()
def _artifact(uri: str, kind: str, path: Path, output_root: Path) -> dict[str, Any]:
    relative = path.relative_to(output_root).as_posix()
    return {"uri": uri.rstrip("/") + "/" + relative, "type": kind, "sizeBytes": path.stat().st_size, "checksum": "sha256:" + _sha256(path)}
