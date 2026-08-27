from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
from typing import Any, Sequence

import numpy as np
import rasterio
from rasterio.transform import from_bounds

from .models_gf6 import Gf6ProductEvidence
from .operators.gf6_mosaic import iter_gf6_mosaic_windows, read_gf6_window
from .operators.gf6_rpc import build_nominal_rpc_grid
from .product_adapters.gf6_wfv_l1a import inspect_paths


STAGES = {
    "INSPECT_GF6_WFV_L1A", "MOSAIC_GF6_TILES", "CALIBRATE_GF6_WFV", "BUILD_GF6_RPC_GRID",
    "ALIGN_GF6_BANDS", "CROP_GF6_RASTER", "WRITE_GF6_STANDARD_COG", "EXTRACT_GF6_FEATURES",
    "FUSE_GF6_BANDS", "DETECT_GF6_SALIENCY",
}


def execute_gf6_stage(stage_code: str, inputs: Sequence[Path], parameters: dict[str, Any], output_dir: Path, output_uri: str = "rs://output/gf6") -> dict[str, Any]:
    if stage_code not in STAGES:
        raise ValueError("unsupported GF6 L1A stage")
    evidence = inspect_paths([Path(path) for path in inputs])
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    if stage_code == "INSPECT_GF6_WFV_L1A":
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [], "metadata": evidence.metadata_snapshot}
    if stage_code == "BUILD_GF6_RPC_GRID":
        grid = build_nominal_rpc_grid(evidence, parameters.get("targetCrs", "EPSG:4326"), float(parameters.get("resolution", 0.00015)), float(parameters.get("rpcHeight", 0.0)))
        path = output_dir / "nominal-rpc-grid.json"
        path.write_text(json.dumps({"crs": grid.crs, "transform": list(grid.transform), "width": grid.width, "height": grid.height,
                                    "bounds": grid.bounds, "heightMeters": grid.height_m, "navigationStatus": grid.navigation_status,
                                    "accuracyStatus": grid.accuracy_status}, sort_keys=True) + "\n", encoding="utf-8")
        return _result(output_uri, path, "GF6_NOMINAL_RPC_GRID_JSON")
    if stage_code in {"MOSAIC_GF6_TILES", "CALIBRATE_GF6_WFV", "ALIGN_GF6_BANDS", "CROP_GF6_RASTER", "WRITE_GF6_STANDARD_COG"}:
        path = output_dir / ("gf6-dn-mosaic.tif" if stage_code == "MOSAIC_GF6_TILES" else "gf6-standard-cog.tif")
        _write_streaming_raster(evidence, path, calibrated=stage_code != "MOSAIC_GF6_TILES", parameters=parameters)
        return _result(output_uri, path, "GF6_DN_MOSAIC_TIFF" if stage_code == "MOSAIC_GF6_TILES" else "GF6_STANDARD_COG")
    if stage_code == "FUSE_GF6_BANDS":
        path = output_dir / "gf6-fusion.tif"
        _write_fusion(evidence, path, parameters)
        return _result(output_uri, path, "GF6_RGB_FUSION_COG")
    if stage_code == "DETECT_GF6_SALIENCY":
        path = output_dir / "gf6-saliency.tif"
        _write_saliency(evidence, path, parameters)
        return _result(output_uri, path, "GF6_SALIENCY_SCORE_COG")
    path = output_dir / "gf6-features.tif"
    _write_features(evidence, path, parameters)
    return _result(output_uri, path, "GF6_FEATURES_COG")


def _write_streaming_raster(evidence: Gf6ProductEvidence, path: Path, *, calibrated: bool, parameters: dict[str, Any]) -> None:
    grid = build_nominal_rpc_grid(evidence, parameters.get("targetCrs", "EPSG:4326"), float(parameters.get("resolution", 0.00015)), float(parameters.get("rpcHeight", 0.0)))
    transform = from_bounds(*evidence.metadata_snapshot["footprint"][0], *evidence.metadata_snapshot["footprint"][0]) if False else from_bounds(
        min(point[0] for point in evidence.footprint), min(point[1] for point in evidence.footprint),
        max(point[0] for point in evidence.footprint), max(point[1] for point in evidence.footprint), evidence.width, evidence.height)
    nodata = -9999.0 if calibrated else 0
    profile = {"driver": "GTiff", "width": evidence.width, "height": evidence.height, "count": 8,
               "dtype": "float32" if calibrated else "uint16", "crs": "EPSG:4326", "transform": transform,
               "nodata": nodata, "tiled": True, "blockxsize": 256, "blockysize": 256, "compress": "deflate"}
    with rasterio.open(path, "w", **profile) as dataset:
        for window in iter_gf6_mosaic_windows(evidence, (256, 256)):
            values = read_gf6_window(evidence, window)
            if calibrated:
                values = values.astype(np.float32)
                for index, band in enumerate(evidence.bands):
                    values[index] = values[index] * np.float32(band.gain) + np.float32(band.offset)
                values[~np.isfinite(values)] = nodata
            dataset.write(values, window=rasterio.windows.Window(window.col_off, window.row_off, window.width, window.height))
        for index, band in enumerate(evidence.bands, start=1):
            dataset.set_band_description(index, f"B{band.number}_{band.wavelength}")
            dataset.update_tags(index, unit=evidence.calibration.unit, calibration="xml-again-aoffset-v1")


def _write_fusion(evidence: Gf6ProductEvidence, path: Path, parameters: dict[str, Any]) -> None:
    bands = parameters.get("fusion", {})
    selection = [int(bands.get(key, default)) for key, default in (("red", 3), ("green", 2), ("blue", 1))]
    source = path.with_name(".fusion-source.tif")
    _write_streaming_raster(evidence, source, calibrated=True, parameters=parameters)
    with rasterio.open(source) as src:
        profile = src.profile.copy(); profile.update(count=3, dtype="uint8", nodata=0, photometric="RGB")
        with rasterio.open(path, "w", **profile) as dst:
            for window in (rasterio.windows.Window(w.col_off, w.row_off, w.width, w.height) for w in iter_gf6_mosaic_windows(evidence, (256, 256))):
                raw = src.read(selection, window=window).astype(np.float32)
                out = np.zeros(raw.shape, dtype=np.uint8)
                for i in range(3):
                    finite = np.isfinite(raw[i])
                    if finite.any():
                        low, high = np.percentile(raw[i][finite], [2, 98])
                        out[i] = np.clip((raw[i] - low) * 255 / max(high - low, 1e-6), 0, 255).astype(np.uint8)
                dst.write(out, window=window)
    source.unlink(missing_ok=True)


def _write_features(evidence: Gf6ProductEvidence, path: Path, parameters: dict[str, Any]) -> None:
    source = path.with_name(".features-source.tif")
    _write_streaming_raster(evidence, source, calibrated=True, parameters=parameters)
    with rasterio.open(source) as src:
        profile = src.profile.copy(); profile.update(count=1, dtype="float32", nodata=-9999.0)
        with rasterio.open(path, "w", **profile) as dst:
            for window in (rasterio.windows.Window(w.col_off, w.row_off, w.width, w.height) for w in iter_gf6_mosaic_windows(evidence, (256, 256))):
                values = src.read(1, window=window).astype(np.float32)
                values[values == src.nodata] = np.nan
                dst.write(np.nan_to_num(values, nan=-9999.0), 1, window=window)
    source.unlink(missing_ok=True)


def _write_saliency(evidence: Gf6ProductEvidence, path: Path, parameters: dict[str, Any]) -> None:
    source = path.with_name(".saliency-source.tif")
    _write_streaming_raster(evidence, source, calibrated=True, parameters=parameters)
    with rasterio.open(source) as src:
        profile = src.profile.copy(); profile.update(count=1, dtype="float32", nodata=-9999.0)
        with rasterio.open(path, "w", **profile) as dst:
            for window in (rasterio.windows.Window(w.col_off, w.row_off, w.width, w.height) for w in iter_gf6_mosaic_windows(evidence, (256, 256))):
                values = src.read(1, window=window).astype(np.float32)
                finite = np.isfinite(values) & (values != src.nodata)
                score = np.zeros(values.shape, dtype=np.float32)
                if finite.any():
                    low, high = np.percentile(values[finite], [2, 98])
                    score[finite] = np.clip((values[finite] - low) / max(high - low, 1e-6), 0, 1)
                dst.write(np.where(finite, score, -9999.0), 1, window=window)
    source.unlink(missing_ok=True)


def _result(output_uri: str, path: Path, artifact_type: str) -> dict[str, Any]:
    digestor = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digestor.update(chunk)
    digest = digestor.hexdigest()
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [{"uri": output_uri.rstrip("/") + "/" + path.name,
            "type": artifact_type, "sizeBytes": path.stat().st_size, "checksum": "sha256:" + digest}]}
