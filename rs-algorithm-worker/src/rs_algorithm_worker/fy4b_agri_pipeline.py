from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import h5py
import numpy as np
import rasterio
from affine import Affine
from rasterio.enums import Resampling

from .fy4b_agri_navigation import apparent_reflectance, build_nominal_grid
from .models import BandMetadata, BandSet, RasterData
from .operators.cog import write_cog
from .operators.reproject import reproject_raster
from .algorithms.features import FeatureParameters, extract_features
from .algorithms.fusion import FusionParameters, fuse_bands
from .algorithms.saliency import SaliencyParameters, detect_saliency

FY4B_QUALITY_FILL = 1
FY4B_QUALITY_OUT_OF_RANGE = 2
FY4B_QUALITY_NAVIGATION_FAILURE = 4
FY4B_QUALITY_CALIBRATION_DEGRADED = 8
FY4B_QUALITY_FILE_DEGRADED = 16
FY4B_QUALITY_OUTSIDE_DISK = 32

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

def _valid_indices(raw, fill_value, table_size):
    valid = (raw != fill_value) & (raw >= 0)
    if np.any(valid & (raw >= table_size)):
        raise ValueError("FY-4B calibration table does not cover valid DN")
    return valid

def calibrate_reflective(values, *, lookup, fill_value, scale=1.0, offset=0.0, esun=1.0, solar_zenith=None):
    raw = np.asarray(values); table = np.asarray(lookup, dtype=np.float32)
    valid = _valid_indices(raw, fill_value, table.size)
    reflectance = np.full(raw.shape, np.nan, dtype=np.float32)
    radiance = np.full(raw.shape, np.nan, dtype=np.float32)
    reflectance[valid] = table[raw[valid]] * float(scale) + float(offset)
    radiance[valid] = reflectance[valid] * float(esun) / np.pi
    if solar_zenith is not None:
        reflectance = apparent_reflectance(reflectance, earth_sun_distance_ratio=1.0, solar_zenith_degrees=np.asarray(solar_zenith))
    return ReflectiveCalibration(reflectance, radiance)

def calibrate_emissive(values, *, lookup, fill_value, scale=1.0, offset=0.0):
    raw = np.asarray(values); table = np.asarray(lookup, dtype=np.float32)
    valid = _valid_indices(raw, fill_value, table.size)
    brightness = np.full(raw.shape, np.nan, dtype=np.float32)
    radiance = np.full(raw.shape, np.nan, dtype=np.float32)
    brightness[valid] = table[raw[valid]]
    radiance[valid] = raw[valid].astype(np.float32) * float(scale) + float(offset)
    return EmissiveCalibration(brightness, radiance)

def build_quality_mask(values, fill_value, valid_range, *, navigation_ok=True, calibration_ok=True, file_quality_ok=True):
    raw = np.asarray(values); bits = np.zeros(raw.shape, dtype=np.uint32)
    bits[raw == fill_value] |= FY4B_QUALITY_FILL
    bits[(raw < valid_range[0]) | (raw > valid_range[1])] |= FY4B_QUALITY_OUT_OF_RANGE
    if not navigation_ok: bits |= FY4B_QUALITY_NAVIGATION_FAILURE
    if not calibration_ok: bits |= FY4B_QUALITY_CALIBRATION_DEGRADED
    if not file_quality_ok: bits |= FY4B_QUALITY_FILE_DEGRADED
    return QualityMask(bits)

def iter_dataset_windows(dataset, rows_per_window):
    if rows_per_window < 1: raise ValueError("rows_per_window must be positive")
    for start in range(0, dataset.shape[0], rows_per_window):
        yield dataset[start:start + rows_per_window, ...]

def read_and_calibrate_l1(path: Path, role: str, rows_per_window: int = 256):
    from .product_adapters.fy4b_agri import CHANNELS_BY_ROLE
    science = {}; quality = {}
    with h5py.File(path, "r") as handle:
        for channel in CHANNELS_BY_ROLE[role]:
            dataset = handle[f"Data/NOMChannel{channel:02d}"]
            values = np.concatenate([np.asarray(chunk, dtype=np.float32) for chunk in iter_dataset_windows(dataset, rows_per_window)], axis=0)
            science[channel] = values; quality[channel] = np.zeros(values.shape, dtype=np.uint32)
    return science, quality, {"role": role}

def _paths(manifest):
    return {item.get("artifactType", item.get("role")): Path(item["path"]) for item in manifest.get("inputs", []) if isinstance(item, dict) and isinstance(item.get("path"), str)}

def _response(output_uri, artifacts):
    result=[]
    for artifact_type, path, relative in artifacts:
        content=path.read_bytes(); result.append({"uri": output_uri.rstrip("/")+"/"+relative, "type": artifact_type, "sizeBytes": len(content), "checksum": "sha256:"+hashlib.sha256(content).hexdigest()})
    return {"workerId":"worker-local", "status":"SUCCEEDED", "artifacts":result}

def _load_raster(path):
    with rasterio.open(path) as source:
        values=source.read(); metadata=tuple(BandMetadata(name or f"band-{i}") for i,name in enumerate(source.descriptions,1))
        return RasterData(values if source.count > 1 else values[0], source.crs.to_string(), source.transform, source.nodata, metadata)

def _write(values, path, crs, transform, descriptions, nodata=np.nan):
    metadata=tuple(BandMetadata(name) for name in descriptions)
    return write_cog(RasterData(np.asarray(values), crs, transform, nodata, metadata), path)

def _params(payload):
    from .fy4b_agri_profile import parse_fy4b_parameters
    value=payload.get("parameterSnapshot", {})
    if isinstance(value,str): value=json.loads(value)
    return parse_fy4b_parameters(value)

def execute_fy4b_stage(payload: dict[str,Any], manifest: dict[str,Any]):
    stage=payload.get("stageKey"); output_uri=payload.get("outputUri"); output=manifest.get("output",{})
    if not isinstance(stage,str) or not isinstance(output_uri,str) or not output_uri.startswith("rs://output/") or not isinstance(output.get("path"),str): raise ValueError("FY-4B stage and logical output URI are required")
    directory=Path(output["path"]).resolve(); directory.mkdir(parents=True,exist_ok=True); paths=_paths(manifest); params=_params(payload)
    if stage == "VALIDATE_FY4B_PACKAGE":
        from .product_adapters.fy4b_agri import inspect_manifest
        candidate=inspect_manifest(manifest)[0]; path=directory/"validation"/"package.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(candidate["metadataSnapshot"],sort_keys=True),encoding="utf-8"); return _response(output_uri,[("FY4B_INPUT_VALIDATION_JSON",path,"validation/package.json")])
    if stage == "BUILD_FY4B_NOMINAL_GRID":
        geo=paths.get("GEO_4000M") or paths.get("GEO");
        if geo is None: raise ValueError("FY-4B nominal grid requires GEO_4000M")
        with h5py.File(geo,"r") as handle:
            a=handle.attrs; grid=build_nominal_grid(nom_center_lon=float(np.asarray(a["NOMCenterLon"]).reshape(-1)[0]),nom_sat_height=float(np.asarray(a["NOMSatHeight"]).reshape(-1)[0]),earth_equatorial_radius=float(np.asarray(a["dEA"]).reshape(-1)[0]),inverse_flattening=float(np.asarray(a["dObRecFlat"]).reshape(-1)[0]),sampling_angle_micro_rad=float(np.asarray(a["dSamplingAngle"]).reshape(-1)[0]),stepping_angle_micro_rad=float(np.asarray(a["dSteppingAngle"]).reshape(-1)[0]),width=2748,height=2748)
        path=directory/"navigation"/"nominal-grid.json"; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps({"crs":grid.crs,"transform":list(grid.transform),"width":grid.width,"height":grid.height,"status":grid.status,"parameters":grid.parameters},sort_keys=True),encoding="utf-8"); return _response(output_uri,[("FY4B_NOMINAL_GRID_JSON",path,"navigation/nominal-grid.json")])
    if stage == "CALIBRATE_AND_GEOREFERENCE_FY4B":
        native=directory/"native"; native.mkdir(parents=True,exist_ok=True); artifacts=[]
        for role,resolution in (("L1_0500M","0500M"),("L1_1000M","1000M"),("L1_2000M","2000M"),("L1_4000M","4000M")):
            path=paths.get(role)
            if path is None: continue
            science,quality,_=read_and_calibrate_l1(path,role); channels=sorted(science); values=np.stack([science[c] for c in channels]); names=tuple(f"C{c:02d}_{'REFLECTANCE' if c <= 6 else 'BRIGHTNESS_TEMPERATURE'}" for c in channels); transform=Affine(1,0,0,0,-1,values.shape[-2]); science_path=native/f"{resolution.lower()}.tif"; quality_path=native/f"quality-{resolution.lower()}.tif"; _write(values,science_path,"EPSG:4326",transform,names); _write(np.stack([quality[c] for c in channels[:1]]).astype(np.uint32),quality_path,"EPSG:4326",transform,("quality_mask",),np.uint32(0xFFFFFFFF)); artifacts += [(f"FY4B_NATIVE_{resolution}_RASTER",science_path,f"native/{resolution.lower()}.tif"),(f"FY4B_NATIVE_{resolution}_QUALITY",quality_path,f"native/quality-{resolution.lower()}.tif")]
        summary=directory/"calibration"/"summary.json"; summary.parent.mkdir(parents=True,exist_ok=True); summary.write_text(json.dumps({"formulaVersion":"fy4b-agri-l1-v1"}),encoding="utf-8"); artifacts.append(("FY4B_CALIBRATION_SUMMARY_JSON",summary,"calibration/summary.json")); return _response(output_uri,artifacts)
    if stage == "REPROJECT_AND_CROP_FY4B":
        grid=directory/"grid"; grid.mkdir(parents=True,exist_ok=True); artifacts=[]
        for resolution in ("0500M","1000M","2000M","4000M"):
            source=paths.get(f"FY4B_NATIVE_{resolution}_RASTER") or paths.get(f"NATIVE_{resolution}_RASTER")
            if source is None: continue
            raster=_load_raster(source); target=reproject_raster(raster,destination_crs=params["targetCrs"],resampling=Resampling.bilinear); out=grid/f"{resolution.lower()}.tif"; _write(target.values,out,target.crs,target.transform,tuple(item.name for item in target.band_metadata)); artifacts.append((f"FY4B_GRIDDED_{resolution}_RASTER",out,f"grid/{resolution.lower()}.tif"))
            quality_source = paths.get(f"FY4B_NATIVE_{resolution}_QUALITY")
            if quality_source is not None:
                quality = _load_raster(quality_source); qtarget = reproject_raster(quality,destination_crs=params["targetCrs"],resampling=Resampling.nearest); qout = grid/f"quality-{resolution.lower()}.tif"; _write(qtarget.values.astype(np.uint32), qout, qtarget.crs, qtarget.transform, ("quality_mask",), np.uint32(0xFFFFFFFF)); artifacts.append((f"FY4B_GRIDDED_{resolution}_QUALITY", qout, f"grid/quality-{resolution.lower()}.tif"))
        return _response(output_uri,artifacts)
    if stage == "WRITE_FY4B_STANDARD_COG":
        standard=directory/"standard"; standard.mkdir(parents=True,exist_ok=True); artifacts=[]; labels={"0500M":"RED_500M","1000M":"VIS_NIR_1000M","2000M":"SPECTRAL_2000M","4000M":"MULTISPECTRAL_4000M"}
        for resolution,label in labels.items():
            source=paths.get(f"FY4B_GRIDDED_{resolution}_RASTER")
            if source is None: continue
            raster=_load_raster(source); out=standard/f"{resolution.lower()}.tif"; _write(raster.values,out,raster.crs,raster.transform,tuple(item.name for item in raster.band_metadata)); artifacts.append((f"FY4B_{label}_COG",out,f"standard/{resolution.lower()}.tif"))
            quality_source = paths.get(f"FY4B_GRIDDED_{resolution}_QUALITY")
            if quality_source is not None:
                q = _load_raster(quality_source); qout = standard/f"quality-{resolution.lower()}.tif"; _write(q.values.astype(np.uint32), qout, q.crs, q.transform, ("quality_mask",), np.uint32(0xFFFFFFFF)); artifacts.append((f"FY4B_{label}_QUALITY_COG", qout, f"standard/quality-{resolution.lower()}.tif"))
        summary=standard/"summary.json"; summary.write_text(json.dumps({"profileKey":{"productCode":"FY-4B-AGRI","profileVersion":"v1"},"formulaVersion":"fy4b-agri-l1-v1","qualityRuleVersion":"fy4b-agri-l1-quality-v1","navigationStatus":"NOMINAL_UNVERIFIED"},sort_keys=True),encoding="utf-8"); artifacts.append(("FY4B_STANDARD_SUMMARY_JSON",summary,"standard/summary.json")); return _response(output_uri,artifacts)
    if stage == "EXTRACT_FY4B_FEATURES":
        source = paths.get("FY4B_RED_500M_COG") or paths.get("FY4B_STANDARD_500M")
        if source is None: raise ValueError("FY-4B features requires standard COG")
        raster = _load_raster(source)
        metrics = tuple(m for m in params["features"]["metrics"] if m in {"statistics", "gradient", "local_variance"})
        feature_set = extract_features(raster, FeatureParameters(metrics or ("statistics",), params["features"].get("windowSize", 3)))
        names = list(feature_set.rasters); values = [r.values for r in feature_set.rasters.values()]
        by_name = {item.name: raster.values[i] for i, item in enumerate(raster.band_metadata)}
        if "toa_nir_red_nd" in params["features"]["metrics"] and len(raster.band_metadata) >= 2:
            denominator = by_name.get("C03_REFLECTANCE_1000M", raster.values[0]) + by_name.get("C02_REFLECTANCE_500M", raster.values[1]); values.append(np.where(np.abs(denominator) > 1e-6, (by_name.get("C03_REFLECTANCE_1000M", raster.values[0]) - by_name.get("C02_REFLECTANCE_500M", raster.values[1])) / denominator, np.nan).astype(np.float32)); names.append("toa_nir_red_nd")
        if "btd_c13_c14" in params["features"]["metrics"] and len(raster.band_metadata) >= 2:
            values.append((raster.values[0] - raster.values[1]).astype(np.float32)); names.append("btd_c13_c14")
        feature_dir = directory / "features"; feature_dir.mkdir(parents=True, exist_ok=True); artifacts=[]
        if values:
            out = feature_dir / "fy4b-features.tif"; _write(np.stack(values), out, raster.crs, raster.transform, tuple(names), np.nan); artifacts.append(("FY4B_FEATURE_COG", out, "features/fy4b-features.tif"))
        summary = feature_dir / "summary.json"; summary.write_text(json.dumps({"classification":"NONE", **feature_set.statistics}, allow_nan=False, sort_keys=True), encoding="utf-8"); artifacts.append(("FY4B_FEATURE_SUMMARY_JSON", summary, "features/summary.json")); return _response(output_uri, artifacts)
    if stage == "FUSE_FY4B_BANDS":
        source = paths.get("FY4B_VIS_NIR_1000M_COG")
        if source is None: raise ValueError("FY-4B fusion requires 1000M standard COG")
        raster = _load_raster(source); result = fuse_bands(BandSet(raster), FusionParameters("C03_REFLECTANCE", "C02_REFLECTANCE", "C01_REFLECTANCE", *params["fusion"]["percentiles"]))
        out = directory / "fusion" / "false-color.tif"; out.parent.mkdir(parents=True, exist_ok=True); _write(result.values, out, result.crs, result.transform, ("NIR_RED_BLUE_FALSE_COLOR_R", "NIR_RED_BLUE_FALSE_COLOR_G", "NIR_RED_BLUE_FALSE_COLOR_B"), 0); return _response(output_uri, [("FY4B_FALSE_COLOR_COG", out, "fusion/false-color.tif")])
    if stage == "DETECT_FY4B_SALIENCY":
        source_name = params["saliency"]["source"]; source = paths.get(source_name) or paths.get("FY4B_FEATURE_COG") or paths.get("FY4B_RED_500M_COG")
        if source is None: raise ValueError("FY-4B saliency requires source artifact")
        raster = _load_raster(source); result = detect_saliency(raster, SaliencyParameters(params["saliency"]["threshold"], params["saliency"]["windowSize"]))
        out_dir = directory / "saliency"; out_dir.mkdir(parents=True, exist_ok=True); score = out_dir / "score.tif"; mask = out_dir / "mask.tif"; _write(result.score.values, score, result.score.crs, result.score.transform, ("saliency_score",), 0.0); _write(result.mask.astype(np.uint8), mask, result.score.crs, result.score.transform, ("saliency_mask",), 0); summary = out_dir / "summary.json"; summary.write_text(json.dumps({"algorithm":"contrast-v1","source":source_name,"threshold":params["saliency"]["threshold"],"windowSize":params["saliency"]["windowSize"],"candidatePixelCount":int(result.mask.sum()),"semanticLabel":"NONE"}, sort_keys=True), encoding="utf-8"); return _response(output_uri, [("FY4B_SALIENCY_SCORE_COG", score, "saliency/score.tif"),("FY4B_SALIENCY_MASK_COG", mask, "saliency/mask.tif"),("FY4B_SALIENCY_SUMMARY_JSON", summary, "saliency/summary.json")])
    raise ValueError(f"unsupported FY-4B stage: {stage}")
