from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

import h5py
import numpy as np

from .models_fy3h import CalibratedMersi, DecodedMersi
from .operators.hdf import iter_dataset_windows
from .product_adapters.fy3h_mersi import EMISSIVE_SOURCES, GEO_FIELDS, REFLECTIVE_SOURCES


FORMULA_VERSION = "fy3h-mersi-l1-v1"
FORMULA_REVIEW_STATUS = "pending-manual-pdf-review"
ROWS_PER_SCAN = 10
PLANCK_C1_MW_CM = 1.191042e-5
PLANCK_C2_CM_K = 1.4387752


@dataclass(frozen=True)
class ReflectiveCalibration:
    reflectance: np.ndarray
    radiance: np.ndarray


@dataclass(frozen=True)
class EmissiveCalibration:
    radiance: np.ndarray
    brightness_temperature: np.ndarray


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
                                    wave_number=float(np.asarray(calibration["wave_numbers"])[channel - 1]) * 1e4,
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
    for sources in (REFLECTIVE_SOURCES, EMISSIVE_SOURCES):
        for path, band_numbers in sources.items():
            dataset = handle[path]
            for local, channel in enumerate(band_numbers):
                channels[channel] = _read_channel_windows(dataset, local, rows_per_window)
                slopes[channel - 1], intercepts[channel - 1], fills[channel - 1] = _channel_attributes(dataset, local)
    calibration = {
        "vis_coefficients": np.asarray(handle["Calibration/VIS_Cal_Coeff"][:], dtype=np.float32),
        "ir_coefficients": np.asarray(handle["Calibration/IR_Cal_Coeff"][:], dtype=np.float32),
        "earth_sun_distance_ratio": np.asarray(handle.attrs["EarthSun Distance Ratio"], dtype=np.float64),
        "solar_irradiance": np.asarray(handle.attrs["Solar_Irradiance"], dtype=np.float32),
        "wave_numbers": np.asarray(handle.attrs["Effect_Center_WaveLength"], dtype=np.float32),
        "tbb_coefficients": np.asarray(handle.attrs["TBB_Trans_Coefficient"], dtype=np.float32),
        "slope": slopes, "intercept": intercepts, "fill_value": fills,
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
