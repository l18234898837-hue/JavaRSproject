from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import numpy as np

from .algorithms.track_anomaly import TrackAnomalyResult, detect_track_anomalies
from .algorithms.track_features import extract_track_features
from .models_track import TrackData
from .operators.netcdf_track import decode_tar_gz
from .operators.ssh import SshResult, calculate_baseline_ssh
from .operators.track_grid import TrackGridSpec, bin_track_ssh, write_track_grid_outputs
from .operators.track_output import write_standard_track_output
from .operators.track_quality import QualityPolicy, TrackQualityResult, apply_quality_policy
from .product_adapters.hy2d_altimeter import inspect_manifest as inspect_hy2d_manifest, parse_hy2d_parameters


MAX_TRACK_GRID_CELLS = 10_000_000


def execute_hy2d_stage(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    """Execute one declared HY-2D stage using only manifest-authorized paths."""
    stage_key = payload.get("stageKey")
    if not isinstance(stage_key, str):
        raise ValueError("HY-2D stageKey is required")
    output_dir = _output_directory(manifest)
    parameters = _parameters(payload)
    if stage_key == "VALIDATE_SOURCE":
        track = decode_tar_gz(_input_path(manifest, "MAIN"))
        path = _write_json(output_dir / "validation" / "source.json", {"metadata": track.metadata, "observationCount": int(track.time.size)})
        return _response(payload, [("VALIDATION_METADATA_JSON", path, "validation/source.json")])
    if stage_key == "DECODE_ALTIMETER_TRACK":
        track = decode_tar_gz(_input_path(manifest, "MAIN"))
        path = _write_track(output_dir / "decoded" / "track.npz", track)
        return _response(payload, [("DECODED_ALTIMETER_TRACK", path, "decoded/track.npz")])

    track = _input_track(manifest)
    if stage_key == "APPLY_TRACK_QUALITY":
        quality = apply_quality_policy(track, QualityPolicy.baseline())
        track_path = _write_quality_track(output_dir / "quality" / "track.npz", quality)
        summary_path = _write_json(output_dir / "quality" / "summary.json", quality.summary)
        return _response(payload, [("QUALITY_FILTERED_ALTIMETER_TRACK", track_path, "quality/track.npz"),
                                   ("TRACK_QUALITY_SUMMARY_JSON", summary_path, "quality/summary.json")])

    quality = _quality_from_track(track)
    ssh = calculate_baseline_ssh(track)
    if stage_key == "CALCULATE_BASELINE_SSH":
        path = _write_ssh_track(output_dir / "ssh" / "track.npz", track, quality, ssh)
        return _response(payload, [("BASELINE_SSH_TRACK", path, "ssh/track.npz")])

    features = extract_track_features(ssh.values, quality.status, window_size=parameters["featureWindowSize"])
    anomaly = detect_track_anomalies(ssh.values, quality.status, window_size=parameters["featureWindowSize"],
                                     mad_threshold=parameters["anomaly"]["madThreshold"],
                                     jump_threshold_meters=parameters["anomaly"]["jumpThresholdMeters"])
    if stage_key == "EXTRACT_TRACK_FEATURES":
        summary_path = _write_json(output_dir / "features" / "summary.json", {"version": "track-statistics-v1", "windows": features})
        parquet_path = output_dir / "features" / "windows.parquet"
        parquet_path.parent.mkdir(parents=True, exist_ok=True)
        pa, pq = _pyarrow_modules()
        pq.write_table(pa.Table.from_pylist(features), parquet_path)
        return _response(payload, [("TRACK_FEATURES_JSON", summary_path, "features/summary.json"),
                                   ("TRACK_FEATURE_WINDOWS_PARQUET", parquet_path, "features/windows.parquet")])
    if stage_key == "DETECT_TRACK_ANOMALIES":
        geojson = _anomaly_geojson(track, ssh.values, anomaly)
        geojson_path = _write_json(output_dir / "anomalies" / "points.geojson", geojson)
        summary_path = _write_json(output_dir / "anomalies" / "summary.json", anomaly.summary)
        return _response(payload, [("TRACK_ANOMALIES_GEOJSON", geojson_path, "anomalies/points.geojson"),
                                   ("TRACK_ANOMALY_SUMMARY_JSON", summary_path, "anomalies/summary.json")])
    if stage_key == "WRITE_STANDARD_TRACK_OUTPUT":
        result = write_standard_track_output(track=track, ssh=ssh, quality=quality, features=features, anomalies=anomaly,
                                             output_dir=output_dir / "standard", processing_version="hy2d-idr-2mc-baseline-v1")
        names = [("STANDARD_TRACK_NETCDF", result.paths["netcdf"], "standard/track.nc"),
                 ("STANDARD_TRACK_PARQUET", result.paths["parquet"], "standard/track.parquet"),
                 ("STANDARD_TRACK_CSV", result.paths["csv"], "standard/track.csv"),
                 ("STANDARD_TRACK_GEOJSON", result.paths["track_geojson"], "standard/track.geojson"),
                 ("STANDARD_TRACK_ANOMALIES_GEOJSON", result.paths["anomaly_geojson"], "standard/anomalies.geojson"),
                 ("STANDARD_TRACK_SUMMARY_JSON", result.paths["summary"], "standard/summary.json")]
        return _response(payload, names)
    if stage_key == "GRID_TRACK_OPTIONAL":
        if not parameters["grid"]["enabled"]:
            raise ValueError("HY-2D grid stage requires grid.enabled=true")
        grid_longitude = _continuous_grid_longitude(track.longitude)
        grid = _grid_spec(grid_longitude, track.latitude, parameters["grid"]["spacingDegrees"])
        result = bin_track_ssh(ssh=ssh.values, longitude=grid_longitude, latitude=track.latitude, quality_status=quality.status, grid=grid)
        output = write_track_grid_outputs(result, output_dir / "grid")
        return _response(payload, [("TRACK_GRID_NETCDF", output.paths["netcdf"], "grid/track-grid.nc"),
                                   ("TRACK_GRID_COG", output.paths["cog"], "grid/track-grid.tif"),
                                   ("TRACK_GRID_SUMMARY_JSON", output.paths["summary"], "grid/summary.json")])
    raise ValueError("unsupported HY-2D stage")


def execute_hy2d_product(payload: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    """Decode and produce the HY-2D baseline outputs in one Worker stage."""
    candidates = inspect_hy2d_manifest(manifest)
    if len(candidates) != 1:
        raise ValueError("one recognized HY-2D package is required")
    parameters = _parameters(payload)
    output_dir = _output_directory(manifest)
    track = decode_tar_gz(_input_path(manifest, "MAIN"))
    quality = apply_quality_policy(track, QualityPolicy.baseline())
    ssh = calculate_baseline_ssh(quality.track)
    features = extract_track_features(
        ssh.values,
        quality.status,
        window_size=parameters["featureWindowSize"],
    )
    anomaly = detect_track_anomalies(
        ssh.values,
        quality.status,
        window_size=parameters["featureWindowSize"],
        mad_threshold=parameters["anomaly"]["madThreshold"],
        jump_threshold_meters=parameters["anomaly"]["jumpThresholdMeters"],
    )

    feature_summary = _write_json(
        output_dir / "features" / "summary.json",
        {"version": "track-statistics-v1", "ssh": ssh.summary, "windows": features},
    )
    feature_parquet = output_dir / "features" / "windows.parquet"
    feature_parquet.parent.mkdir(parents=True, exist_ok=True)
    pa, pq = _pyarrow_modules()
    pq.write_table(pa.Table.from_pylist(features), feature_parquet)
    anomaly_geojson = _write_json(output_dir / "anomalies" / "points.geojson", _anomaly_geojson(track, ssh.values, anomaly))
    anomaly_summary = _write_json(output_dir / "anomalies" / "summary.json", anomaly.summary)
    standard = write_standard_track_output(
        track=quality.track,
        ssh=ssh,
        quality=quality,
        features=features,
        anomalies=anomaly,
        output_dir=output_dir / "standard",
        processing_version="hy2d-idr-2mc-baseline-v1",
    )
    files = [
        ("TRACK_FEATURES_JSON", feature_summary, "features/summary.json"),
        ("TRACK_FEATURE_WINDOWS_PARQUET", feature_parquet, "features/windows.parquet"),
        ("TRACK_ANOMALIES_GEOJSON", anomaly_geojson, "anomalies/points.geojson"),
        ("TRACK_ANOMALY_SUMMARY_JSON", anomaly_summary, "anomalies/summary.json"),
        ("STANDARD_TRACK_NETCDF", standard.paths["netcdf"], "standard/track.nc"),
        ("STANDARD_TRACK_PARQUET", standard.paths["parquet"], "standard/track.parquet"),
        ("STANDARD_TRACK_CSV", standard.paths["csv"], "standard/track.csv"),
        ("STANDARD_TRACK_GEOJSON", standard.paths["track_geojson"], "standard/track.geojson"),
        ("STANDARD_TRACK_ANOMALIES_GEOJSON", standard.paths["anomaly_geojson"], "standard/anomalies.geojson"),
        ("STANDARD_TRACK_SUMMARY_JSON", standard.paths["summary"], "standard/summary.json"),
    ]
    if parameters["grid"]["enabled"]:
        grid_longitude = _continuous_grid_longitude(track.longitude)
        grid = _grid_spec(grid_longitude, track.latitude, parameters["grid"]["spacingDegrees"])
        gridded = bin_track_ssh(
            ssh=ssh.values,
            longitude=grid_longitude,
            latitude=track.latitude,
            quality_status=quality.status,
            grid=grid,
        )
        grid_output = write_track_grid_outputs(gridded, output_dir / "grid")
        files.extend([
            ("TRACK_GRID_NETCDF", grid_output.paths["netcdf"], "grid/track-grid.nc"),
            ("TRACK_GRID_COG", grid_output.paths["cog"], "grid/track-grid.tif"),
            ("TRACK_GRID_SUMMARY_JSON", grid_output.paths["summary"], "grid/summary.json"),
        ])
    return _response(payload, files)


def _pyarrow_modules():
    try:
        import pyarrow as pa
        import pyarrow.parquet as pq
    except ModuleNotFoundError as error:
        raise RuntimeError("HY-2D processing requires the pyarrow package") from error
    return pa, pq


def _parameters(payload: dict[str, Any]) -> dict[str, Any]:
    raw = payload.get("parameterSnapshot", {})
    if isinstance(raw, str):
        raw = json.loads(raw)
    if not isinstance(raw, dict):
        raise ValueError("HY-2D parameter snapshot is invalid")
    candidate = {"profileKey": {"productCode": "HY-2D-ALTIMETER", "profileVersion": "v1"},
                 "dataShape": "ALONG_TRACK_OBSERVATION", "supportStatus": "SUPPORTED", "files": [True]}
    return parse_hy2d_parameters(raw)


def _output_directory(manifest: dict[str, Any]) -> Path:
    output = manifest.get("output")
    path = output.get("path") if isinstance(output, dict) else None
    if not isinstance(path, str):
        raise ValueError("HY-2D output directory is required")
    result = Path(path).resolve()
    result.mkdir(parents=True, exist_ok=True)
    return result


def _input_path(manifest: dict[str, Any], role: str) -> Path:
    paths = [Path(item["path"]) for item in manifest.get("inputs", [])
             if isinstance(item, dict)
             and str(item.get("role", "")).upper() == role.upper()
             and isinstance(item.get("path"), str)]
    if len(paths) != 1 or not paths[0].is_file():
        raise ValueError(f"HY-2D stage requires one authorized {role} input")
    return paths[0]


def _input_track(manifest: dict[str, Any]) -> TrackData:
    for item in manifest.get("inputs", []):
        path = item.get("path") if isinstance(item, dict) else None
        if isinstance(path, str) and Path(path).suffix == ".npz":
            return _read_track(Path(path))
    main = _input_path(manifest, "MAIN")
    return decode_tar_gz(main)


def _write_track(path: Path, track: TrackData) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    data: dict[str, Any] = {"time": track.time, "longitude": track.longitude, "latitude": track.latitude, "quality": track.quality,
                            "metadata": json.dumps(track.metadata, sort_keys=True)}
    data.update({"field__" + name: value for name, value in track.fields.items()})
    np.savez_compressed(path, **data)
    return path


def _read_track(path: Path) -> TrackData:
    with np.load(path, allow_pickle=False) as data:
        fields = {name.removeprefix("field__"): data[name] for name in data.files if name.startswith("field__")}
        metadata = json.loads(str(data["metadata"])) if "metadata" in data else {}
        return TrackData(time=data["time"], longitude=data["longitude"], latitude=data["latitude"], fields=fields,
                         quality=data["quality"].astype(bool), metadata=metadata)


def _write_quality_track(path: Path, quality: TrackQualityResult) -> Path:
    _write_track(path, quality.track)
    with np.load(path, allow_pickle=False) as data:
        values = {key: data[key] for key in data.files}
    values["quality_reasons"] = np.asarray([json.dumps(item) for item in quality.reason_codes])
    np.savez_compressed(path, **values)
    return path


def _quality_from_track(track: TrackData) -> TrackQualityResult:
    return apply_quality_policy(track, QualityPolicy.baseline())


def _write_ssh_track(path: Path, track: TrackData, quality: TrackQualityResult, ssh: SshResult) -> Path:
    _write_quality_track(path, quality)
    with np.load(path, allow_pickle=False) as data:
        values = {key: data[key] for key in data.files}
    values["ssh"] = ssh.values
    np.savez_compressed(path, **values)
    return path


def _write_json(path: Path, value: Any) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=True, sort_keys=True, allow_nan=False), encoding="utf-8")
    return path


def _anomaly_geojson(track: TrackData, ssh: np.ndarray, anomaly: TrackAnomalyResult) -> dict[str, Any]:
    features = []
    for index, reasons in enumerate(anomaly.reason_codes):
        if reasons and np.isfinite(track.longitude[index]) and np.isfinite(track.latitude[index]):
            features.append({"type": "Feature", "properties": {"index": index, "reason_codes": list(reasons)},
                             "geometry": {"type": "Point", "coordinates": [float(track.longitude[index]), float(track.latitude[index])]}})
    return {"type": "FeatureCollection", "features": features}


def _continuous_grid_longitude(longitude: np.ndarray) -> np.ndarray:
    values = np.asarray(longitude, dtype=np.float64).copy()
    finite = np.isfinite(values)
    wrapped = (values[finite] + 180.0) % 360.0 - 180.0
    if wrapped.size < 2:
        values[finite] = wrapped
        return values
    ordered = np.sort(wrapped)
    gaps = np.diff(np.concatenate((ordered, ordered[:1] + 360.0)))
    start = ordered[(int(np.argmax(gaps)) + 1) % ordered.size]
    wrapped[wrapped < start] += 360.0
    values[finite] = wrapped
    return values


def _grid_spec(longitude: np.ndarray, latitude: np.ndarray, resolution: float) -> TrackGridSpec:
    coordinates = (np.isfinite(longitude) & np.isfinite(latitude)
                   & (latitude >= -90.0) & (latitude <= 90.0))
    lon = longitude[coordinates]
    lat = latitude[coordinates]
    if lon.size == 0 or lat.size == 0:
        raise ValueError("HY-2D grid requires finite coordinates")
    min_lon = np.floor(lon.min() / resolution) * resolution
    min_lat = np.floor(lat.min() / resolution) * resolution
    max_lon = (np.floor(lon.max() / resolution) + 1) * resolution
    max_lat = (np.floor(lat.max() / resolution) + 1) * resolution
    width = int(round((max_lon - min_lon) / resolution))
    height = int(round((max_lat - min_lat) / resolution))
    if width <= 0 or height <= 0 or width * height > MAX_TRACK_GRID_CELLS:
        raise ValueError("HY-2D output grid is too large; use a coarser spacing")
    return TrackGridSpec(float(min_lon), float(min_lat), float(max_lon), float(max_lat), resolution)


def _response(payload: dict[str, Any], files: list[tuple[str, Path, str]]) -> dict[str, Any]:
    root_uri = payload.get("outputUri")
    if not isinstance(root_uri, str) or not root_uri.startswith("rs://output/"):
        raise ValueError("HY-2D stage requires logical output URI")
    artifacts = []
    for artifact_type, path, relative in files:
        artifacts.append({"uri": root_uri.rstrip("/") + "/" + relative, "type": artifact_type,
                          "sizeBytes": path.stat().st_size, "checksum": "sha256:" + _file_sha256(path)})
    return {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": artifacts}


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()
