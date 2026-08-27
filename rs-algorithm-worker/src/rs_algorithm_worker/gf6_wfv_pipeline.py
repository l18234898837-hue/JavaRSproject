from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Iterable, Sequence
from xml.sax.saxutils import escape

import numpy as np
import rasterio
from rasterio.enums import Resampling
from rasterio.warp import reproject
from rasterio.windows import Window

from .gf6_processing import calibrate_gf6, detect_gf6_saliency, extract_gf6_features
from .models_gf6 import Gf6ProductEvidence
from .operators.gf6_mosaic import iter_gf6_mosaic_windows, read_gf6_window
from .operators.gf6_rpc import as_rasterio_rpc, build_nominal_rpc_grid, crop_nominal_rpc_grid, window_transform
from .product_adapters.gf6_wfv_l1a import inspect_paths

STAGES = {"INSPECT_GF6_WFV_L1A", "MOSAIC_GF6_TILES", "CALIBRATE_GF6_WFV", "BUILD_GF6_RPC_GRID", "ALIGN_GF6_BANDS", "CROP_GF6_RASTER", "WRITE_GF6_STANDARD_COG", "EXTRACT_GF6_FEATURES", "FUSE_GF6_BANDS", "DETECT_GF6_SALIENCY"}
_NODATA, _BLOCK = -9999.0, 256


def execute_gf6_stage(stage_code: str, inputs: Sequence[Path], parameters: dict[str, Any], output_dir: Path, output_uri: str = "rs://output/gf6") -> dict[str, Any]:
    if stage_code not in STAGES:
        raise ValueError("unsupported GF6 L1A stage")
    evidence = inspect_paths([Path(path) for path in inputs])
    output_dir = Path(output_dir); output_dir.mkdir(parents=True, exist_ok=True)
    if stage_code == "INSPECT_GF6_WFV_L1A":
        return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [], "metadata": evidence.metadata_snapshot}
    if stage_code == "BUILD_GF6_RPC_GRID":
        grid = _grid(evidence, parameters); path = output_dir / "nominal-rpc-grid.json"
        path.write_text(json.dumps(_grid_metadata(grid), sort_keys=True) + "\n", encoding="utf-8")
        return _result(output_uri, [(path, "GF6_NOMINAL_RPC_GRID_JSON")])
    if stage_code == "MOSAIC_GF6_TILES":
        path = output_dir / "gf6-dn-mosaic.tif"; _write_source_mosaic(evidence, path)
        return _result(output_uri, [(path, "GF6_DN_MOSAIC_TIFF")])
    if stage_code in {"CALIBRATE_GF6_WFV", "ALIGN_GF6_BANDS", "CROP_GF6_RASTER", "WRITE_GF6_STANDARD_COG"}:
        path = output_dir / "gf6-standard-cog.tif"; _write_standard_cog(evidence, path, parameters)
        return _result(output_uri, [(path, "GF6_STANDARD_COG")])
    if stage_code == "EXTRACT_GF6_FEATURES":
        return _write_features(evidence, output_dir, parameters, output_uri)
    if stage_code == "FUSE_GF6_BANDS":
        return _write_fusion(evidence, output_dir, parameters, output_uri)
    return _write_saliency(evidence, output_dir, parameters, output_uri)


def _grid(evidence: Gf6ProductEvidence, parameters: dict[str, Any]):
    grid = build_nominal_rpc_grid(evidence, parameters.get("targetCrs", "EPSG:4326"), float(parameters.get("resolution", 0.00015)), float(parameters.get("rpcHeight", 0.0)))
    return crop_nominal_rpc_grid(grid, tuple(parameters["roi"])) if parameters.get("roi") else grid


def _grid_metadata(grid: Any) -> dict[str, Any]:
    return {"crs": grid.crs, "transform": list(grid.transform), "width": grid.width, "height": grid.height, "bounds": grid.bounds, "heightMeters": grid.height_m, "navigationStatus": grid.navigation_status, "accuracyStatus": grid.accuracy_status}


def _write_source_mosaic(evidence: Gf6ProductEvidence, path: Path) -> None:
    profile = {"driver": "GTiff", "width": evidence.width, "height": evidence.height, "count": 8, "dtype": "uint16", "transform": rasterio.Affine.identity(), "nodata": 0, "tiled": True, "blockxsize": _BLOCK, "blockysize": _BLOCK, "compress": "deflate", "BIGTIFF": "IF_SAFER"}
    with rasterio.open(path, "w", **profile) as dataset:
        dataset.update_tags(ns="RPC", **as_rasterio_rpc(evidence.rpc).to_gdal())
        for mosaic_window in iter_gf6_mosaic_windows(evidence, (_BLOCK, _BLOCK)):
            values = read_gf6_window(evidence, mosaic_window)
            dataset.write(values, window=Window(mosaic_window.col_off, mosaic_window.row_off, mosaic_window.width, mosaic_window.height))


def _write_standard_cog(evidence: Gf6ProductEvidence, path: Path, parameters: dict[str, Any]) -> None:
    source_path = path.with_name(f".{path.stem}-source.vrt")
    _write_mosaic_vrt(evidence, source_path)
    try:
        _resample_source_to_grid(evidence, source_path, path, _grid(evidence, parameters))
    finally:
        source_path.unlink(missing_ok=True)


def _write_mosaic_vrt(evidence: Gf6ProductEvidence, path: Path) -> None:
    """Build a disk-light TIL mosaic whose RPC metadata drives GDAL warping."""
    rpc_metadata = "".join(
        f'<MDI key="{key}">{value}</MDI>' for key, value in as_rasterio_rpc(evidence.rpc).to_gdal().items()
    )
    bands = []
    for band_index in range(1, 9):
        sources = []
        for tile in evidence.tiles:
            sources.append(
                '<SimpleSource>'
                f'<SourceFilename relativeToVRT="0">{escape(str(tile.path))}</SourceFilename>'
                f'<SourceBand>{band_index}</SourceBand>'
                f'<SrcRect xOff="0" yOff="0" xSize="{tile.width}" ySize="{tile.height}"/>'
                f'<DstRect xOff="{tile.column_offset}" yOff="{tile.row_offset}" xSize="{tile.width}" ySize="{tile.height}"/>'
                '</SimpleSource>'
            )
        bands.append(f'<VRTRasterBand dataType="UInt16" band="{band_index}"><NoDataValue>0</NoDataValue>{"".join(sources)}</VRTRasterBand>')
    path.write_text(
        f'<VRTDataset rasterXSize="{evidence.width}" rasterYSize="{evidence.height}">'
        f'<Metadata domain="RPC">{rpc_metadata}</Metadata>{"".join(bands)}</VRTDataset>',
        encoding="utf-8",
    )


def _resample_source_to_grid(evidence: Gf6ProductEvidence, source_path: Path, path: Path, grid: Any) -> None:
    profile = {"driver": "COG", "width": grid.width, "height": grid.height, "count": 8, "dtype": "float32", "crs": grid.crs, "transform": grid.transform, "nodata": _NODATA, "BLOCKSIZE": _BLOCK, "COMPRESS": "DEFLATE", "BIGTIFF": "IF_SAFER"}
    with rasterio.open(source_path) as source, rasterio.open(path, "w", **profile) as destination:
        if source.rpcs is None:
            raise ValueError("GF6 RPC source metadata is unavailable")
        for output_window in _windows(grid.width, grid.height):
            transform = window_transform(grid.transform, output_window)
            for band_index in range(1, 9):
                output = np.full((int(output_window.height), int(output_window.width)), _NODATA, dtype=np.float32)
                reproject(source=rasterio.band(source, band_index), destination=output, rpcs=source.rpcs, src_nodata=0, dst_transform=transform, dst_crs=grid.crs, dst_nodata=_NODATA, resampling=Resampling.bilinear, RPC_HEIGHT=grid.height_m)
                calibrated = calibrate_gf6(output[np.newaxis, ...], gains=[evidence.bands[band_index - 1].gain], offsets=[evidence.bands[band_index - 1].offset], fill_value=int(_NODATA))[0]
                calibrated[output == _NODATA] = _NODATA
                destination.write(calibrated, band_index, window=output_window)
        destination.update_tags(calibrationFormula=evidence.calibration.formula, calibrationUnit=evidence.calibration.unit, navigationStatus="NOMINAL_RPC_NO_DEM", accuracyStatus="UNVALIDATED", heightMode="CONSTANT", heightMeters=str(grid.height_m), rpcResampling="bilinear")
        for index, band in enumerate(evidence.bands, start=1):
            destination.set_band_description(index, f"B{band.number}_{band.wavelength}")
            destination.update_tags(index, unit=evidence.calibration.unit, gain=str(band.gain), offset=str(band.offset))


def _windows(width: int, height: int) -> Iterable[Window]:
    for row in range(0, height, _BLOCK):
        for col in range(0, width, _BLOCK):
            yield Window(col, row, min(_BLOCK, width - col), min(_BLOCK, height - row))


def calculate_global_stretches(chunks: Iterable[Sequence[Sequence[float] | np.ndarray]], *, percentiles: tuple[float, float] = (2.0, 98.0)) -> tuple[tuple[float, float], ...]:
    low, high = percentiles
    if not 0 <= low < high <= 100:
        raise ValueError("GF6 fusion percentiles are invalid")
    values: list[list[np.ndarray]] = [[], [], []]
    for chunk in chunks:
        if len(chunk) != 3:
            raise ValueError("GF6 fusion requires three bands")
        for index, band in enumerate(chunk):
            array = np.asarray(band, dtype=np.float32).reshape(-1)
            array = array[np.isfinite(array) & (array != _NODATA)]
            if array.size:
                values[index].append(array)
    result = []
    for bands in values:
        result.append((0.0, 1.0) if not bands else tuple(float(value) for value in np.percentile(np.concatenate(bands), [low, high])))
    return tuple(result)


def _standard_source(evidence: Gf6ProductEvidence, output_dir: Path, parameters: dict[str, Any]) -> Path:
    path = output_dir / ".gf6-standard-source.tif"; _write_standard_cog(evidence, path, parameters); return path


def _write_fusion(evidence: Gf6ProductEvidence, output_dir: Path, parameters: dict[str, Any], output_uri: str) -> dict[str, Any]:
    config = parameters.get("fusion", {}); selected = tuple(int(config.get(key, fallback)) for key, fallback in (("red", 3), ("green", 2), ("blue", 1)))
    source_path = _standard_source(evidence, output_dir, parameters); path = output_dir / "gf6-rgb-fusion.tif"; summary = output_dir / "gf6-fusion-summary.json"
    try:
        with rasterio.open(source_path) as source:
            chunks = (tuple(source.read(index, window=window)[::16, ::16] for index in selected) for window in _windows(source.width, source.height))
            stretches = calculate_global_stretches(chunks, percentiles=tuple(config.get("percentiles", (2.0, 98.0))))
            profile = source.profile.copy(); profile.update(driver="COG", count=3, dtype="uint8", nodata=0, photometric="RGB", BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE")
            with rasterio.open(path, "w", **profile) as destination:
                for window in _windows(source.width, source.height):
                    values = source.read(selected, window=window).astype(np.float32); output = np.zeros(values.shape, dtype=np.uint8)
                    for index, (lower, upper) in enumerate(stretches):
                        valid = np.isfinite(values[index]) & (values[index] != _NODATA)
                        output[index, valid] = np.clip((values[index, valid] - lower) * 255.0 / max(upper - lower, 1e-6), 0, 255).astype(np.uint8)
                    destination.write(output, window=window)
                for index, name in enumerate(("red", "green", "blue"), start=1): destination.set_band_description(index, f"{name}_B{selected[index - 1]}")
        summary.write_text(json.dumps({"version": "percentile-rgb-v1", "bands": dict(zip(("red", "green", "blue"), selected)), "stretches": stretches}, sort_keys=True) + "\n", encoding="utf-8")
    finally:
        source_path.unlink(missing_ok=True)
    return _result(output_uri, [(path, "GF6_RGB_FUSION_COG"), (summary, "GF6_FUSION_SUMMARY_JSON")])


def _write_features(evidence: Gf6ProductEvidence, output_dir: Path, parameters: dict[str, Any], output_uri: str) -> dict[str, Any]:
    config = parameters.get("features", {}); metrics = tuple(metric for metric in config.get("metrics", ("gradient", "local_variance")) if metric in {"gradient", "local_variance", "statistics"})
    if not metrics: raise ValueError("GF6 feature metrics are empty")
    selected = tuple(int(value) for value in parameters.get("bands", list(range(1, 9)))); raster_metrics = tuple(metric for metric in metrics if metric != "statistics")
    source_path = _standard_source(evidence, output_dir, parameters); path = output_dir / "gf6-features.tif"; summary = output_dir / "gf6-features-summary.json"
    try:
        with rasterio.open(source_path) as source:
            profile = source.profile.copy(); profile.update(driver="COG", count=max(1, len(selected) * len(raster_metrics)), dtype="float32", nodata=_NODATA, BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE"); statistics: dict[str, Any] = {}
            with rasterio.open(path, "w", **profile) as destination:
                for window in _windows(source.width, source.height):
                    values = source.read(selected, window=window).astype(np.float32); values[values == _NODATA] = np.nan
                    if raster_metrics:
                        features = extract_gf6_features(values, metrics=raster_metrics); destination_index = 1
                        for metric in raster_metrics:
                            for band_offset, _ in enumerate(selected):
                                destination.write(np.nan_to_num(features[metric][band_offset], nan=_NODATA), destination_index, window=window); destination_index += 1
                if not raster_metrics:
                    for window in _windows(source.width, source.height):
                        destination.write(np.full((int(window.height), int(window.width)), _NODATA, dtype=np.float32), 1, window=window)
                for metric_offset, metric in enumerate(raster_metrics):
                    for band_offset, band in enumerate(selected): destination.set_band_description(metric_offset * len(selected) + band_offset + 1, f"{metric}:B{band}")
            if "statistics" in metrics:
                for index in selected:
                    arrays = [array for window in _windows(source.width, source.height) if (array := source.read(index, window=window)) is not None]
                    joined = np.concatenate([array[(array != _NODATA) & np.isfinite(array)] for array in arrays]); statistics[f"B{index}"] = None if not joined.size else {"min": float(joined.min()), "max": float(joined.max()), "mean": float(joined.mean()), "std": float(joined.std())}
        summary.write_text(json.dumps({"version": "features-v1", "metrics": metrics, "statistics": statistics}, sort_keys=True) + "\n", encoding="utf-8")
    finally:
        source_path.unlink(missing_ok=True)
    return _result(output_uri, [(path, "GF6_FEATURES_COG"), (summary, "GF6_FEATURES_SUMMARY_JSON")])


def _write_saliency(evidence: Gf6ProductEvidence, output_dir: Path, parameters: dict[str, Any], output_uri: str) -> dict[str, Any]:
    config = parameters.get("saliency", {}); band = int(str(config.get("source", "B3")).removeprefix("B")); window_size = int(config.get("windowSize", 9)); threshold = float(config.get("threshold", 0.95))
    source_path = _standard_source(evidence, output_dir, parameters); score_path = output_dir / "gf6-saliency-score.tif"; mask_path = output_dir / "gf6-saliency-mask.tif"; summary = output_dir / "gf6-saliency-summary.json"
    try:
        with rasterio.open(source_path) as source:
            profile = source.profile.copy(); profile.update(driver="COG", count=1, dtype="float32", nodata=_NODATA, BLOCKSIZE=_BLOCK, COMPRESS="DEFLATE"); mask_profile = profile.copy(); mask_profile.update(dtype="uint8", nodata=0)
            with rasterio.open(score_path, "w", **profile) as score, rasterio.open(mask_path, "w", **mask_profile) as mask:
                for window in _windows(source.width, source.height):
                    values = source.read(band, window=window).astype(np.float32); valid = values != _NODATA; values[~valid] = np.nan; scores, masks = detect_gf6_saliency(values, window_size=window_size, threshold=threshold)
                    score.write(np.where(valid, scores, _NODATA), 1, window=window); mask.write(np.where(valid, masks, 0).astype(np.uint8), 1, window=window)
                score.set_band_description(1, f"saliency_score:B{band}"); mask.set_band_description(1, f"saliency_mask:B{band}")
        summary.write_text(json.dumps({"version": "contrast-v1", "source": f"B{band}", "windowSize": window_size, "threshold": threshold, "interpretation": "CANDIDATE_ONLY"}, sort_keys=True) + "\n", encoding="utf-8")
    finally:
        source_path.unlink(missing_ok=True)
    return _result(output_uri, [(score_path, "GF6_SALIENCY_SCORE_COG"), (mask_path, "GF6_SALIENCY_MASK_COG"), (summary, "GF6_SALIENCY_SUMMARY_JSON")])


def _result(output_uri: str, artifacts: Sequence[tuple[Path, str]]) -> dict[str, Any]:
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": [{"uri": output_uri.rstrip("/") + "/" + path.name, "type": artifact_type, "sizeBytes": path.stat().st_size, "checksum": "sha256:" + _sha256(path)} for path, artifact_type in artifacts]}


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""): digest.update(chunk)
    return digest.hexdigest()
