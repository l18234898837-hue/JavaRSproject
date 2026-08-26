from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping

import h5py
import numpy as np
import rasterio
from affine import Affine

from .models_fy3h import (
    CalibratedMersi, DecodedMersi, QualityMask, QUALITY_CALIBRATION_FAILURE,
    QUALITY_DEAD_DETECTOR, QUALITY_FRAME_CHANNEL_FAILURE,
    QUALITY_FRAME_PREPROCESS_FAILURE, QUALITY_GEOLOCATION_FAILURE,
    QUALITY_INVALID_NAVIGATION, QUALITY_MISSING, QUALITY_NON_DAYLIGHT,
    QUALITY_OUT_OF_RANGE, QUALITY_SATURATED,
)
from .models_fy3h import read_decoded_mersi, write_decoded_mersi, read_calibrated_mersi, write_calibrated_mersi
from .algorithms.features import FeatureParameters, extract_features
from .algorithms.fusion import FusionParameters, fuse_bands
from .algorithms.saliency import SaliencyParameters, detect_saliency
from .models import BandMetadata, BandSet, RasterData
from .operators.cog import write_cog
from .operators.swath_grid import grid_swath
from .fy3h_mersi_profile import parse_fy3h_parameters
from .operators.hdf import iter_dataset_windows
from .product_adapters.fy3h_mersi import EMISSIVE_SOURCES, GEO_FIELDS, REFLECTIVE_SOURCES


FORMULA_VERSION = "fy3h-mersi-l1-v1"
FORMULA_REVIEW_STATUS = "pending-manual-pdf-review"
ROWS_PER_SCAN = 10
PLANCK_C1_MW_CM = 1.191042e-5
PLANCK_C2_CM_K = 1.4387752
QUALITY_RULE_VERSION = "fy3h-mersi-l1-quality-v1"
FRAME_QUALITY_RULES = {
    "channelFailure": (range(0, 25), QUALITY_FRAME_CHANNEL_FAILURE),
    "preprocessFailure": ((25,), QUALITY_FRAME_PREPROCESS_FAILURE),
    "calibrationFailure": ((26, 29), QUALITY_CALIBRATION_FAILURE),
    "geolocationFailure": ((33,), QUALITY_GEOLOCATION_FAILURE),
}


@dataclass(frozen=True)
class ReflectiveCalibration:
    reflectance: np.ndarray
    radiance: np.ndarray


@dataclass(frozen=True)
class EmissiveCalibration:
    radiance: np.ndarray
    brightness_temperature: np.ndarray


@dataclass(frozen=True)
class StandardMersiOutput:
    standard_cog: Path
    quality_cog: Path
    summary_path: Path


def write_standard_mersi_cogs(science: RasterData, quality: RasterData, output_dir: Path,
                              summary: Mapping[str, Any]) -> StandardMersiOutput:
    if science.values.ndim != 3 or science.values.shape[0] != 25:
        raise ValueError("FY-3H standard COG requires 25 science bands")
    if quality.values.ndim != 2 or quality.values.shape != science.values.shape[-2:]:
        raise ValueError("FY-3H standard COG requires a matching quality grid")
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    standard = write_cog(science, output_dir / "mersi.tif")
    quality_result = write_cog(quality, output_dir / "quality.tif")
    document = dict(summary)
    document.update({"profileKey": {"productCode": "FY-3H-MERSI", "profileVersion": "L1-1000M-GEO1K-v1"},
                     "ruleVersion": QUALITY_RULE_VERSION, "formulaVersion": FORMULA_VERSION,
                     "targetGrid": {"crs": science.crs, "width": science.values.shape[-1], "height": science.values.shape[-2]},
                     "artifacts": {"standardSha256": standard.sha256, "qualitySha256": quality_result.sha256}})
    summary_path = output_dir / "summary.json"
    summary_path.write_text(json.dumps(document, ensure_ascii=True, sort_keys=True, allow_nan=False), encoding="utf-8")
    return StandardMersiOutput(standard.path, quality_result.path, summary_path)


def execute_fy3h_stage(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    stage = payload.get("stageKey")
    output_uri = payload.get("outputUri")
    output = manifest.get("output")
    if not isinstance(stage, str) or not isinstance(output_uri, str) or not output_uri.startswith("rs://output/"):
        raise ValueError("FY-3H MERSI stage and logical output URI are required")
    if not isinstance(output, dict) or not isinstance(output.get("path"), str):
        raise ValueError("FY-3H MERSI temporary output directory is required")
    directory = Path(output["path"]).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    roles = _input_roles(manifest)
    if stage == "VALIDATE_FY3H_PAIR":
        _require_roles(roles, {"OBSERVATION", "GEO1K"})
        path = directory / "validation" / "pair.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"profileKey": "FY-3H-MERSI", "valid": True}), encoding="utf-8")
        return _stage_response(output_uri, [("FY3H_INPUT_VALIDATION_JSON", path, "validation/pair.json")])
    if stage == "DECODE_MERSI_L1":
        _require_roles(roles, {"OBSERVATION", "GEO1K"})
        path = directory / "decoded" / "mersi-l1.npz"
        write_decoded_mersi(path, decode_mersi_pair(roles["OBSERVATION"], roles["GEO1K"], rows_per_window=100))
        return _stage_response(output_uri, [("DECODED_MERSI_L1", path, "decoded/mersi-l1.npz")])
    if stage == "CALIBRATE_MERSI_L1":
        _require_roles(roles, {"decoded"})
        path = directory / "calibrated" / "mersi.npz"
        write_calibrated_mersi(path, calibrate_mersi(read_decoded_mersi(roles["decoded"])))
        return _stage_response(output_uri, [("CALIBRATED_MERSI_RASTER", path, "calibrated/mersi.npz")])
    if stage == "APPLY_MERSI_QUALITY":
        _require_roles(roles, {"decoded", "calibrated"})
        decoded = read_decoded_mersi(roles["decoded"])
        calibrated = read_calibrated_mersi(roles["calibrated"])
        quality = {channel: build_quality_mask(decoded.dn_by_channel[channel], decoded.frame_flags, decoded.geo_flags,
                   rows_per_scan=ROWS_PER_SCAN, valid_range=decoded.calibration["valid_range"][channel - 1],
                   longitude=decoded.longitude, latitude=decoded.latitude, solar_zenith=decoded.solar_zenith_degrees,
                   sensor_zenith=decoded.sensor_zenith_degrees, reflective=channel <= 19)
                   for channel in range(1, 26)}
        path = directory / "quality" / "mersi.npz"; path.parent.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(path, longitude=decoded.longitude, latitude=decoded.latitude,
                            **{f"quality__{key}": value.reason_bits for key, value in quality.items()},
                            **{f"science__{key}": (calibrated.reflectance_by_channel[key] if key <= 19 else calibrated.brightness_temperature_by_channel[key]) for key in range(1, 26)})
        summary = directory / "quality" / "summary.json"; summary.write_text(json.dumps(summarize_quality(quality), sort_keys=True), encoding="utf-8")
        return _stage_response(output_uri, [("QUALITY_MERSI_RASTER", path, "quality/mersi.npz"), ("MERSI_QUALITY_SUMMARY_JSON", summary, "quality/summary.json")])
    if stage == "GRID_MERSI_SWATH":
        _require_roles(roles, {"quality"})
        parameters = parse_fy3h_parameters(_payload_parameters(payload))
        with np.load(roles["quality"], allow_pickle=False) as data:
            longitude, latitude = data["longitude"], data["latitude"]
            grids = [grid_swath(data[f"science__{channel}"], data[f"quality__{channel}"], longitude, latitude,
                                target_crs=parameters["targetCrs"], resolution=parameters.get("resolutionDegrees", parameters.get("resolutionMeters")),
                                roi=parameters.get("roi"), continuous_resampling=parameters["continuousResampling"])
                     for channel in range(1, 26)]
        path = directory / "grid" / "mersi.npz"; path.parent.mkdir(parents=True, exist_ok=True)
        quality_path = directory / "grid" / "quality-mask.npz"
        np.savez_compressed(path, values=np.stack([item[0].values for item in grids]),
                            crs=np.asarray(grids[0][0].crs), transform=np.asarray(grids[0][0].transform)[:6])
        np.savez_compressed(quality_path, values=grids[0][1].values, crs=np.asarray(grids[0][1].crs), transform=np.asarray(grids[0][1].transform)[:6])
        return _stage_response(output_uri, [("GRIDDED_MERSI_RASTER", path, "grid/mersi.npz"), ("GRIDDED_MERSI_QUALITY_MASK", quality_path, "grid/quality-mask.npz")])
    if stage == "WRITE_MERSI_STANDARD_COG":
        _require_roles(roles, {"grid", "qualityGrid"})
        with np.load(roles["grid"], allow_pickle=False) as data:
            values, crs, transform = data["values"], str(data["crs"]), Affine(*data["transform"])
        with np.load(roles["qualityGrid"], allow_pickle=False) as data:
            quality_values = data["values"]
        metadata = tuple(BandMetadata(f"CH{channel:02d}_{'REFLECTANCE' if channel <= 19 else 'BRIGHTNESS_TEMPERATURE'}") for channel in range(1, 26))
        output = write_standard_mersi_cogs(RasterData(values, crs, transform, np.nan, metadata),
                                           RasterData(quality_values.astype(np.uint32), crs, transform, np.uint32(0xFFFFFFFF), (BandMetadata("quality_mask", unit="bitmask"),)),
                                           directory / "standard", {})
        return _stage_response(output_uri, [("STANDARD_MERSI_COG", output.standard_cog, "standard/mersi.tif"),
                                           ("STANDARD_MERSI_QUALITY_COG", output.quality_cog, "standard/quality.tif"),
                                           ("STANDARD_MERSI_SUMMARY_JSON", output.summary_path, "standard/summary.json")])
    if stage == "EXTRACT_MERSI_FEATURES":
        _require_roles(roles, {"standard"})
        parameters = parse_fy3h_parameters(_payload_parameters(payload))
        standard = _selected_standard_bands(_read_cog(roles["standard"]), parameters["bands"])
        feature_set = extract_features(standard, FeatureParameters(
            metrics=tuple(parameters["features"]["metrics"]),
            window_size=parameters["features"].get("windowSize", 3),
        ))
        feature_dir = directory / "features"
        feature_dir.mkdir(parents=True, exist_ok=True)
        summary_path = feature_dir / "summary.json"
        summary_path.write_text(json.dumps(feature_set.statistics, ensure_ascii=True, sort_keys=True, allow_nan=False), encoding="utf-8")
        artifacts: list[tuple[Any, ...]] = [("MERSI_FEATURE_SUMMARY_JSON", summary_path, "features/summary.json")]
        if feature_set.rasters:
            values = np.stack([raster.values for raster in feature_set.rasters.values()])
            first = next(iter(feature_set.rasters.values()))
            feature_raster = RasterData(values, first.crs, first.transform, np.nan,
                                        tuple(BandMetadata(name) for name in feature_set.rasters))
            feature_path = write_cog(feature_raster, feature_dir / "mersi.tif").path
            artifacts.insert(0, ("MERSI_FEATURE_COG", feature_path, "features/mersi.tif"))
        return _stage_response(output_uri, artifacts)
    if stage == "FUSE_MERSI_BANDS":
        _require_roles(roles, {"standard"})
        parameters = parse_fy3h_parameters(_payload_parameters(payload))
        fusion = parameters["fusion"]
        raster = _selected_standard_bands(_read_cog(roles["standard"]), parameters["bands"])
        result = fuse_bands(BandSet(raster), FusionParameters(
            red=_channel_name(fusion["red"]), green=_channel_name(fusion["green"]), blue=_channel_name(fusion["blue"]),
            low_percentile=fusion["percentiles"][0], high_percentile=fusion["percentiles"][1],
        ))
        output_dir = directory / "fusion"
        output_dir.mkdir(parents=True, exist_ok=True)
        path = write_cog(result, output_dir / "mersi-rgb.tif").path
        return _stage_response(output_uri, [("MERSI_FUSION_COG", path, "fusion/mersi-rgb.tif", True)])
    if stage == "DETECT_MERSI_SALIENCY":
        parameters = parse_fy3h_parameters(_payload_parameters(payload))
        saliency = parameters["saliency"]
        expected_role = "features" if saliency["source"].startswith(("gradient:", "local_variance:")) else "standard"
        _require_roles(roles, {expected_role})
        source = _read_cog(roles[expected_role])
        if expected_role == "standard":
            source = _selected_standard_bands(source, parameters["bands"])
        source = _named_band(source, saliency["source"])
        result = detect_saliency(source, SaliencyParameters(
            threshold=saliency["threshold"], window_size=saliency["windowSize"],
        ))
        output_dir = directory / "saliency"
        output_dir.mkdir(parents=True, exist_ok=True)
        score_path = write_cog(result.score, output_dir / "score.tif").path
        mask = RasterData(result.mask.astype(np.uint8), result.score.crs, result.score.transform, np.uint8(0),
                          (BandMetadata("saliency_mask"),))
        mask_path = write_cog(mask, output_dir / "mask.tif").path
        return _stage_response(output_uri, [
            ("MERSI_SALIENCY_SCORE_COG", score_path, "saliency/score.tif"),
            ("MERSI_SALIENCY_MASK_COG", mask_path, "saliency/mask.tif"),
        ])
    raise ValueError("unsupported FY-3H MERSI stage")


def _require_roles(roles: Mapping[str, Path], expected: set[str]) -> None:
    if set(roles) != expected or any(not path.is_file() for path in roles.values()):
        raise ValueError(f"FY-3H MERSI stage requires exactly {', '.join(sorted(expected))}")


def _input_roles(manifest: Mapping[str, Any]) -> dict[str, Path]:
    inputs = manifest.get("inputs")
    if not isinstance(inputs, list):
        return {}
    roles: dict[str, Path] = {}
    for item in inputs:
        if not isinstance(item, dict) or not isinstance(item.get("role"), str) or not isinstance(item.get("path"), str):
            return {}
        role = item["role"]
        if role in roles:
            raise ValueError("FY-3H MERSI stage input roles must be unique")
        roles[role] = Path(item["path"])
    return roles


def _read_cog(path: Path) -> RasterData:
    try:
        with rasterio.open(path) as dataset:
            if dataset.crs is None or dataset.transform is None or any(name is None or not name.strip() for name in dataset.descriptions):
                raise ValueError("FY-3H MERSI COG metadata is invalid")
            return RasterData(dataset.read(), dataset.crs.to_string(), dataset.transform, dataset.nodata,
                              tuple(BandMetadata(name) for name in dataset.descriptions))
    except rasterio.errors.RasterioError as error:
        raise ValueError("FY-3H MERSI COG input is unreadable") from error


def _channel_name(channel: int) -> str:
    return f"CH{channel:02d}_{'REFLECTANCE' if channel <= 19 else 'BRIGHTNESS_TEMPERATURE'}"


def _selected_standard_bands(raster: RasterData, channels: list[int]) -> RasterData:
    return _named_bands(raster, [_channel_name(channel) for channel in channels])


def _named_band(raster: RasterData, name: str) -> RasterData:
    return _named_bands(raster, [name])


def _named_bands(raster: RasterData, names: list[str]) -> RasterData:
    by_name = {metadata.name: index for index, metadata in enumerate(raster.band_metadata)}
    if any(name not in by_name for name in names):
        raise ValueError("FY-3H MERSI requested COG band is unavailable")
    indices = [by_name[name] for name in names]
    return RasterData(raster.values[indices], raster.crs, raster.transform, raster.nodata,
                      tuple(raster.band_metadata[index] for index in indices))


def _payload_parameters(payload: Mapping[str, Any]) -> dict[str, Any]:
    value = payload.get("parameterSnapshot", {})
    if isinstance(value, str):
        value = json.loads(value)
    if not isinstance(value, dict):
        raise ValueError("FY-3H MERSI parameter snapshot is invalid")
    return value


def _stage_response(root_uri: str, artifacts: list[tuple[Any, ...]]) -> dict[str, Any]:
    result = []
    for kind, path, relative, *display in artifacts:
        content = path.read_bytes()
        artifact = {"uri": root_uri.rstrip("/") + "/" + relative, "type": kind,
                    "sizeBytes": len(content), "checksum": "sha256:" + hashlib.sha256(content).hexdigest()}
        if display and display[0]:
            artifact["displayProduct"] = True
        result.append(artifact)
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": result}


def build_quality_mask(raw: np.ndarray, frame_flags: np.ndarray, geo_flags: np.ndarray, *, rows_per_scan: int,
                       valid_range: tuple[float, float] | np.ndarray | None = None,
                       longitude: np.ndarray | None = None, latitude: np.ndarray | None = None,
                       solar_zenith: np.ndarray | None = None, sensor_zenith: np.ndarray | None = None,
                       reflective: bool = False) -> QualityMask:
    """Map source sentinels and frame-level QA into stable product reason bits."""
    values = np.asarray(raw)
    if values.ndim != 2 or rows_per_scan < 1 or values.shape[0] % rows_per_scan:
        raise ValueError("FY-3H MERSI quality mask requires complete two-dimensional scan frames")
    frames = values.shape[0] // rows_per_scan
    flags = np.asarray(frame_flags).reshape(-1)
    geo = np.asarray(geo_flags)
    if flags.shape != (frames,) or geo.ndim < 1 or geo.shape[0] != frames:
        raise ValueError("FY-3H MERSI frame quality dimensions are invalid")
    if not np.issubdtype(flags.dtype, np.integer) or np.any(flags < 0):
        raise ValueError("FY-3H MERSI frame quality flags are invalid")
    flags = flags.astype(np.uint64, copy=False)
    bits = np.zeros(values.shape, dtype=np.uint32)
    bits[values == 65535] |= QUALITY_MISSING
    bits[values == 65534] |= QUALITY_SATURATED
    bits[values == 65533] |= QUALITY_DEAD_DETECTOR
    if valid_range is not None:
        lower, upper = _finite_vector(np.asarray(valid_range), 2, "valid range")
        if lower > upper:
            raise ValueError("FY-3H MERSI valid range is invalid")
        bits[(values < lower) | (values > upper)] |= QUALITY_OUT_OF_RANGE
    frame_bits = np.zeros(frames, dtype=np.uint32)
    for source_bits, reason in FRAME_QUALITY_RULES.values():
        for bit in source_bits:
            frame_bits[(flags & (np.uint64(1) << np.uint64(bit))) != 0] |= reason
    frame_bits[np.any(geo != 0, axis=tuple(range(1, geo.ndim)))] |= QUALITY_GEOLOCATION_FAILURE
    bits |= np.repeat(frame_bits, rows_per_scan)[:, None]
    _apply_navigation_quality(bits, longitude, latitude, solar_zenith, sensor_zenith, reflective)
    return QualityMask(valid=bits == 0, reason_bits=bits)


def summarize_quality(quality_by_channel: Mapping[int, QualityMask]) -> dict[str, Any]:
    names = {
        QUALITY_MISSING: "MISSING", QUALITY_SATURATED: "SATURATED", QUALITY_DEAD_DETECTOR: "DEAD_DETECTOR",
        QUALITY_OUT_OF_RANGE: "OUT_OF_RANGE", QUALITY_FRAME_CHANNEL_FAILURE: "FRAME_CHANNEL_FAILURE",
        QUALITY_FRAME_PREPROCESS_FAILURE: "FRAME_PREPROCESS_FAILURE", QUALITY_CALIBRATION_FAILURE: "CALIBRATION_FAILURE",
        QUALITY_GEOLOCATION_FAILURE: "GEOLOCATION_FAILURE", QUALITY_INVALID_NAVIGATION: "INVALID_NAVIGATION",
        QUALITY_NON_DAYLIGHT: "NON_DAYLIGHT",
    }
    counts = {name: 0 for name in names.values()}
    channels: dict[str, dict[str, int]] = {}
    for channel, mask in quality_by_channel.items():
        channel_name = f"CH{channel:02d}"
        channels[channel_name] = {"validPixelCount": int(mask.valid.sum()), "totalPixelCount": int(mask.valid.size)}
        for bit, name in names.items():
            counts[name] += int(np.count_nonzero(mask.reason_bits & bit))
    return {"ruleVersion": QUALITY_RULE_VERSION, "sourceRules": {key: list(bits) for key, (bits, _) in FRAME_QUALITY_RULES.items()},
            "channelQuality": channels, "reasonCounts": counts}


def _apply_navigation_quality(bits: np.ndarray, longitude: np.ndarray | None, latitude: np.ndarray | None,
                              solar_zenith: np.ndarray | None, sensor_zenith: np.ndarray | None,
                              reflective: bool) -> None:
    if any(value is not None for value in (longitude, latitude, solar_zenith, sensor_zenith)):
        if any(value is None or np.asarray(value).shape != bits.shape for value in (longitude, latitude, solar_zenith, sensor_zenith)):
            raise ValueError("FY-3H MERSI navigation dimensions must match quality values")
        lon, lat, solar, sensor = (np.asarray(value) for value in (longitude, latitude, solar_zenith, sensor_zenith))
        invalid = (~np.isfinite(lon) | ~np.isfinite(lat) | ~np.isfinite(solar) | ~np.isfinite(sensor) |
                   (lon < -180) | (lon > 180) | (lat < -90) | (lat > 90) |
                   (solar < 0) | (solar > 180) | (sensor < 0) | (sensor > 180))
        bits[invalid] |= QUALITY_INVALID_NAVIGATION
        if reflective:
            bits[solar >= 88] |= QUALITY_NON_DAYLIGHT


def calibrate_reflective(values: np.ndarray, *, slope: float, intercept: float, coefficients: np.ndarray,
                         solar_zenith: np.ndarray, earth_sun_distance_ratio: float, solar_irradiance: float,
                         fill_value: float | int = 65535) -> ReflectiveCalibration:
    """Calibrate a reflective band; coefficients are (linear, constant, quadratic)."""
    coefficients = _finite_vector(coefficients, 3, "reflective coefficients")
    _finite_scalars(slope=slope, intercept=intercept, earth_sun_distance_ratio=earth_sun_distance_ratio,
                    solar_irradiance=solar_irradiance)
    if earth_sun_distance_ratio <= 0 or solar_irradiance <= 0:
        raise ValueError("reflective calibration requires positive physical constants")
    raw = np.asarray(values)
    zenith = np.asarray(solar_zenith, dtype=np.float32)
    if raw.shape != zenith.shape:
        raise ValueError("solar zenith dimensions must match reflective values")
    mask = raw == fill_value
    dn = raw.astype(np.float32) * float(slope) + float(intercept)
    linear, constant, quadratic = coefficients
    ref_star = (quadratic * dn * dn + linear * dn + constant) * 0.01
    cosine = np.cos(np.deg2rad(zenith))
    with np.errstate(divide="ignore", invalid="ignore"):
        reflectance = ref_star / cosine * float(earth_sun_distance_ratio) ** 2
        radiance = ref_star * float(solar_irradiance) / np.pi
    invalid = mask | ~np.isfinite(zenith) | (cosine <= 0) | ~np.isfinite(reflectance) | ~np.isfinite(radiance)
    reflectance = np.where(invalid, np.nan, reflectance).astype(np.float32)
    radiance = np.where(invalid, np.nan, radiance).astype(np.float32)
    return ReflectiveCalibration(reflectance, radiance)


def calibrate_emissive(values: np.ndarray, *, slope: float, intercept: float, scan_coefficients: np.ndarray,
                       scan_index: np.ndarray, wave_number: float, tbb_coefficients: np.ndarray,
                       fill_value: float | int = 65535) -> EmissiveCalibration:
    _finite_scalars(slope=slope, intercept=intercept, wave_number=wave_number)
    if wave_number <= 0:
        raise ValueError("thermal calibration requires a positive wave number")
    tbb_a, tbb_b = _finite_vector(tbb_coefficients, 2, "TBB coefficients")
    coefficients = np.asarray(scan_coefficients, dtype=np.float32)
    if coefficients.ndim == 3 and coefficients.shape[0] == 1:
        coefficients = coefficients[0]
    if coefficients.ndim != 2 or coefficients.shape[1] != 4 or not np.all(np.isfinite(coefficients)):
        raise ValueError("thermal calibration requires finite scan coefficients")
    index = _expanded_scan_index(scan_index, np.asarray(values).shape)
    if index.size and (index.min() < 0 or index.max() >= coefficients.shape[0]):
        raise ValueError("thermal calibration scan index has no matching coefficient")
    raw = np.asarray(values)
    dn = raw.astype(np.float32) * float(slope) + float(intercept)
    coefficient_grid = coefficients[index]
    radiance = (coefficient_grid[..., 0] + coefficient_grid[..., 1] * dn +
                coefficient_grid[..., 2] * dn * dn + coefficient_grid[..., 3] * dn * dn * dn)
    invalid = (raw == fill_value) | ~np.isfinite(radiance) | (radiance <= 0)
    with np.errstate(divide="ignore", invalid="ignore", over="ignore"):
        planck_temperature = PLANCK_C2_CM_K * float(wave_number) / np.log1p(
            PLANCK_C1_MW_CM * float(wave_number) ** 3 / radiance)
        brightness_temperature = float(tbb_a) * planck_temperature + float(tbb_b)
    invalid |= ~np.isfinite(brightness_temperature)
    return EmissiveCalibration(np.where(invalid, np.nan, radiance).astype(np.float32),
                               np.where(invalid, np.nan, brightness_temperature).astype(np.float32))


def decode_mersi_pair(observation_path: Path, geo_path: Path, *, rows_per_window: int) -> DecodedMersi:
    if rows_per_window < 1:
        raise ValueError("rows per window must be positive")
    try:
        with h5py.File(observation_path, "r") as observation, h5py.File(geo_path, "r") as geo:
            dn_by_channel, calibration, observation_metadata = _decode_observation(observation, rows_per_window)
            navigation = _decode_navigation(geo, rows_per_window)
            rows = navigation["latitude"].shape[0]
            if rows % ROWS_PER_SCAN:
                raise ValueError("FY-3H MERSI rows must map to whole scan frames")
            frame_flags = _read_all( observation["QA/QA_Frame_Flag"], rows_per_window)
            geo_flags = _read_all(geo["Geolocation/QA_GEO"], rows_per_window)
            frames = rows // ROWS_PER_SCAN
            if frame_flags.shape != (frames,) or geo_flags.shape[0] != frames:
                raise ValueError("FY-3H MERSI frame quality dimensions are invalid")
    except OSError as error:
        raise ValueError("FY-3H MERSI source is unreadable") from error
    metadata = {"formulaVersion": FORMULA_VERSION, "formulaReviewStatus": FORMULA_REVIEW_STATUS,
                "rowsPerScan": ROWS_PER_SCAN,
                "observation": observation_metadata, "channelIndex": sorted(dn_by_channel)}
    return DecodedMersi(dn_by_channel, navigation["longitude"], navigation["latitude"], navigation["solarZenith"],
                        navigation["sensorZenith"], frame_flags, geo_flags,
                        np.arange(rows, dtype=np.int32) // ROWS_PER_SCAN, metadata, calibration, navigation)


def calibrate_mersi(decoded: DecodedMersi) -> CalibratedMersi:
    calibration = decoded.calibration
    required = {"vis_coefficients", "ir_coefficients", "earth_sun_distance_ratio", "solar_irradiance",
                "wave_numbers", "tbb_coefficients", "slope", "intercept", "fill_value"}
    if not required <= calibration.keys():
        raise ValueError("decoded MERSI calibration metadata is incomplete")
    reflectance: dict[int, np.ndarray] = {}
    radiance: dict[int, np.ndarray] = {}
    brightness_temperature: dict[int, np.ndarray] = {}
    for channel in range(1, 20):
        result = calibrate_reflective(decoded.dn_by_channel[channel], slope=calibration["slope"][channel - 1],
                                      intercept=calibration["intercept"][channel - 1],
                                      coefficients=np.asarray(calibration["vis_coefficients"])[channel - 1][[1, 0, 2]],
                                      solar_zenith=decoded.solar_zenith_degrees,
                                      earth_sun_distance_ratio=float(np.asarray(calibration["earth_sun_distance_ratio"])[0]),
                                      solar_irradiance=float(np.asarray(calibration["solar_irradiance"])[channel - 1]),
                                      fill_value=calibration["fill_value"][channel - 1])
        reflectance[channel], radiance[channel] = result.reflectance, result.radiance
    for channel in range(20, 26):
        result = calibrate_emissive(decoded.dn_by_channel[channel], slope=calibration["slope"][channel - 1],
                                    intercept=calibration["intercept"][channel - 1],
                                    scan_coefficients=np.asarray(calibration["ir_coefficients"])[channel - 20].T,
                                    scan_index=decoded.scan_index,
                                    wave_number=_wave_number_cm(calibration["wave_numbers"][channel - 1]),
                                    tbb_coefficients=np.asarray(calibration["tbb_coefficients"])[[channel - 20, channel - 14]],
                                    fill_value=calibration["fill_value"][channel - 1])
        radiance[channel], brightness_temperature[channel] = result.radiance, result.brightness_temperature
    return CalibratedMersi(reflectance, radiance, brightness_temperature,
                           {"formulaVersion": FORMULA_VERSION, "formulaReviewStatus": FORMULA_REVIEW_STATUS})


def _decode_observation(handle: h5py.File, rows_per_window: int) -> tuple[dict[int, np.ndarray], dict[str, np.ndarray], dict[str, Any]]:
    channels: dict[int, np.ndarray] = {}
    slopes = np.empty(25, dtype=np.float32)
    intercepts = np.empty(25, dtype=np.float32)
    fills = np.empty(25, dtype=np.float32)
    valid_ranges = np.empty((25, 2), dtype=np.float32)
    for sources in (REFLECTIVE_SOURCES, EMISSIVE_SOURCES):
        for path, band_numbers in sources.items():
            dataset = handle[path]
            for local, channel in enumerate(band_numbers):
                channels[channel] = _read_channel_windows(dataset, local, rows_per_window)
                slopes[channel - 1], intercepts[channel - 1], fills[channel - 1] = _channel_attributes(dataset, local)
                valid_ranges[channel - 1] = _channel_valid_range(dataset)
    calibration = {
        "vis_coefficients": np.asarray(handle["Calibration/VIS_Cal_Coeff"][:], dtype=np.float32),
        "ir_coefficients": np.asarray(handle["Calibration/IR_Cal_Coeff"][:], dtype=np.float32),
        "earth_sun_distance_ratio": np.asarray(handle.attrs["EarthSun Distance Ratio"], dtype=np.float64),
        "solar_irradiance": np.asarray(handle.attrs["Solar_Irradiance"], dtype=np.float32),
        "wave_numbers": np.asarray(handle.attrs["Effect_Center_WaveLength"], dtype=np.float32),
        "tbb_coefficients": np.asarray(handle.attrs["TBB_Trans_Coefficient"], dtype=np.float32),
        "slope": slopes, "intercept": intercepts, "fill_value": fills, "valid_range": valid_ranges,
    }
    if calibration["ir_coefficients"].shape[1] != 4:
        raise ValueError("FY-3H MERSI thermal calibration dimensions are invalid")
    return channels, calibration, {"rootAttributes": {name: _attribute_value(handle.attrs[name]) for name in
        ("Satellite Name", "Sensor Identification Code", "Orbit Number", "Version Of Calibration Parameter")}}


def _decode_navigation(handle: h5py.File, rows_per_window: int) -> dict[str, np.ndarray]:
    selected = {"latitude", "longitude", "solarZenith", "sensorZenith", "solarAzimuth"}
    navigation: dict[str, np.ndarray] = {}
    for name, path in GEO_FIELDS.items():
        if name not in selected:
            continue
        navigation[name] = _decode_navigation_dataset(handle[path], rows_per_window)
    if set(navigation) != selected:
        raise ValueError("FY-3H MERSI required navigation arrays are missing")
    return navigation


def _decode_navigation_dataset(dataset: h5py.Dataset, rows_per_window: int) -> np.ndarray:
    scale, offset = _navigation_scale(dataset)
    pieces = []
    for raw in iter_dataset_windows(dataset, rows_per_window=rows_per_window):
        values = raw.astype(np.float32) * scale + offset
        fill = _attribute_scalar(dataset.attrs.get("FillValue"))
        if fill is not None:
            values = np.where(raw == fill, np.nan, values)
        pieces.append(values.astype(np.float32))
    return np.concatenate(pieces, axis=0)


def _navigation_scale(dataset: h5py.Dataset) -> tuple[float, float]:
    if "scale_factor" in dataset.attrs:
        scale = _attribute_scalar(dataset.attrs["scale_factor"])
        offset = _attribute_scalar(dataset.attrs.get("add_offset", 0.0))
    else:
        scale = _attribute_scalar(dataset.attrs.get("Slope"))
        offset = _attribute_scalar(dataset.attrs.get("Intercept"))
    if scale is None or offset is None or not np.isfinite(scale) or not np.isfinite(offset):
        raise ValueError("FY-3H MERSI navigation scale and offset must be finite")
    return float(scale), float(offset)


def _read_channel_windows(dataset: h5py.Dataset, channel: int, rows_per_window: int) -> np.ndarray:
    if dataset.ndim != 3:
        raise ValueError("FY-3H MERSI observation dataset must be three-dimensional")
    chunks = [dataset[channel, start:start + rows_per_window, :] for start in range(0, dataset.shape[1], rows_per_window)]
    return np.concatenate(chunks, axis=0)


def _read_all(dataset: h5py.Dataset, rows_per_window: int) -> np.ndarray:
    return np.concatenate(list(iter_dataset_windows(dataset, rows_per_window=rows_per_window)), axis=0)


def _channel_attributes(dataset: h5py.Dataset, channel: int) -> tuple[float, float, float]:
    return tuple(_channel_attribute(dataset, name, channel) for name in ("Slope", "Intercept", "FillValue"))


def _channel_attribute(dataset: h5py.Dataset, name: str, channel: int) -> float:
    values = np.asarray(dataset.attrs[name]).reshape(-1)
    index = 0 if values.size == 1 else channel
    if index >= values.size or not np.isfinite(values[index]):
        raise ValueError(f"FY-3H MERSI {name} is invalid")
    return float(values[index])


def _channel_valid_range(dataset: h5py.Dataset) -> np.ndarray:
    values = np.asarray(dataset.attrs["valid_range"], dtype=np.float32).reshape(-1)
    if values.shape != (2,) or not np.all(np.isfinite(values)) or values[0] > values[1]:
        raise ValueError("FY-3H MERSI valid_range is invalid")
    return values


def _wave_number_cm(wavelength_micrometers: float) -> float:
    wavelength = float(wavelength_micrometers)
    if not np.isfinite(wavelength) or wavelength <= 0:
        raise ValueError("FY-3H MERSI effective center wavelength is invalid")
    return 1.0e4 / wavelength


def _expanded_scan_index(index: np.ndarray, shape: tuple[int, ...]) -> np.ndarray:
    values = np.asarray(index, dtype=np.int64)
    if values.shape == shape:
        return values
    if len(shape) == 2 and values.shape == (shape[0],):
        return np.broadcast_to(values[:, None], shape)
    raise ValueError("thermal calibration scan index dimensions must match values")


def _finite_vector(values: np.ndarray, length: int, name: str) -> np.ndarray:
    values = np.asarray(values, dtype=np.float64).reshape(-1)
    if values.shape != (length,) or not np.all(np.isfinite(values)):
        raise ValueError(f"{name} must be finite and have length {length}")
    return values


def _finite_scalars(**values: float) -> None:
    if not all(np.isfinite(value) for value in values.values()):
        raise ValueError("calibration constants must be finite")


def _attribute_scalar(value: Any) -> float | None:
    if value is None:
        return None
    values = np.asarray(value).reshape(-1)
    if values.size != 1:
        return None
    return float(values[0])


def _attribute_value(value: Any) -> Any:
    if isinstance(value, bytes):
        return value.decode("utf-8")
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, np.generic):
        return value.item()
    return value
