from __future__ import annotations

import csv
from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq
from netCDF4 import Dataset

from rs_algorithm_worker.algorithms.track_anomaly import TrackAnomalyResult
from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.operators.ssh import SshResult
from rs_algorithm_worker.operators.track_quality import TrackQualityResult


_SCIENTIFIC_BOUNDARY = (
    "HY-2D IDR/IGDR full official specification was unavailable during implementation; "
    "quality and SSH engineering-baseline values require official review before scientific use."
)
_COLUMN_NAMES = ("time", "longitude", "latitude", "quality_status", "quality_reason_codes", "ssh")


@dataclass(frozen=True)
class StandardTrackOutput:
    output_dir: Path
    paths: Mapping[str, Path]
    summary: Mapping[str, Any]


def write_standard_track_output(
    *,
    track: TrackData,
    ssh: SshResult,
    quality: TrackQualityResult,
    features: Sequence[Mapping[str, Any]],
    anomalies: TrackAnomalyResult,
    output_dir: Path,
    processing_version: str,
) -> StandardTrackOutput:
    """Write interoperable HY-2D along-track artifacts to one caller-owned directory."""
    _validate_result_lengths(track, ssh, quality, anomalies)
    destination = Path(output_dir).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    paths = {
        "netcdf": _safe_child(destination, "track.nc"),
        "parquet": _safe_child(destination, "track.parquet"),
        "csv": _safe_child(destination, "track.csv"),
        "track_geojson": _safe_child(destination, "track.geojson"),
        "anomaly_geojson": _safe_child(destination, "anomalies.geojson"),
        "summary": _safe_child(destination, "summary.json"),
    }
    rows = _rows(track, ssh.values, quality)
    _write_csv(paths["csv"], rows)
    _write_parquet(paths["parquet"], rows)
    _write_netcdf(paths["netcdf"], track, ssh, quality, processing_version)
    _write_track_geojson(paths["track_geojson"], track)
    _write_anomaly_geojson(paths["anomaly_geojson"], track, ssh.values, anomalies)
    summary = _summary(track, ssh, quality, features, anomalies, processing_version)
    paths["summary"].write_text(json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8")
    return StandardTrackOutput(destination, paths, summary)


def _safe_child(output_dir: Path, filename: str) -> Path:
    path = (output_dir / filename).resolve()
    if path.parent != output_dir:
        raise ValueError("output path escapes the requested output directory")
    return path


def _validate_result_lengths(track: TrackData, ssh: SshResult, quality: TrackQualityResult, anomalies: TrackAnomalyResult) -> None:
    length = track.time.size
    if ssh.values.shape != (length,) or quality.status.shape != (length,):
        raise ValueError("SSH and quality results must match track length")
    if len(quality.reason_codes) != length or len(anomalies.reason_codes) != length:
        raise ValueError("quality and anomaly reason codes must match track length")


def _rows(track: TrackData, ssh: np.ndarray, quality: TrackQualityResult) -> list[dict[str, Any]]:
    return [{
        "time": float(track.time[index]),
        "longitude": float(track.longitude[index]),
        "latitude": float(track.latitude[index]),
        "quality_status": bool(quality.status[index]),
        "quality_reason_codes": json.dumps(quality.reason_codes[index]),
        "ssh": float(ssh[index]),
    } for index in range(track.time.size)]


def _write_csv(path: Path, rows: Sequence[Mapping[str, Any]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=_COLUMN_NAMES)
        writer.writeheader()
        writer.writerows(rows)


def _write_parquet(path: Path, rows: Sequence[Mapping[str, Any]]) -> None:
    columns = {name: [row[name] for row in rows] for name in _COLUMN_NAMES}
    pq.write_table(pa.table(columns), path)


def _write_netcdf(path: Path, track: TrackData, ssh: SshResult, quality: TrackQualityResult, processing_version: str) -> None:
    with Dataset(path, "w", format="NETCDF4") as dataset:
        dataset.createDimension("observation", track.time.size)
        for name, values, dtype in (
            ("time", track.time, "f8"),
            ("longitude", track.longitude, "f8"),
            ("latitude", track.latitude, "f8"),
            ("quality_status", quality.status.astype(np.int8), "i1"),
            ("ssh", ssh.values, "f8"),
        ):
            variable = dataset.createVariable(name, dtype, ("observation",), fill_value=np.nan if dtype == "f8" else None)
            variable[:] = values
        codes = dataset.createVariable("quality_reason_codes", str, ("observation",))
        codes[:] = np.asarray([json.dumps(value) for value in quality.reason_codes], dtype=object)
        dataset.setncattr("processing_version", processing_version)
        dataset.setncattr("quality_policy_version", quality.policy.version)
        dataset.setncattr("ssh_formula_version", ssh.summary["sshFormulaVersion"])


def _write_track_geojson(path: Path, track: TrackData) -> None:
    coordinates = [[float(lon), float(lat)] for lon, lat in zip(track.longitude, track.latitude)
                   if np.isfinite(lon) and np.isfinite(lat)]
    payload = {"type": "FeatureCollection", "features": [{
        "type": "Feature", "properties": {"observationCount": len(coordinates)},
        "geometry": {"type": "LineString", "coordinates": coordinates},
    }]}
    path.write_text(json.dumps(payload), encoding="utf-8")


def _write_anomaly_geojson(path: Path, track: TrackData, ssh: np.ndarray, anomalies: TrackAnomalyResult) -> None:
    features = []
    for index, reasons in enumerate(anomalies.reason_codes):
        if not reasons or not (np.isfinite(track.longitude[index]) and np.isfinite(track.latitude[index])):
            continue
        features.append({"type": "Feature", "properties": {
            "index": index, "time": float(track.time[index]), "ssh": float(ssh[index]), "reason_codes": list(reasons),
        }, "geometry": {"type": "Point", "coordinates": [float(track.longitude[index]), float(track.latitude[index])]}})
    path.write_text(json.dumps({"type": "FeatureCollection", "features": features}), encoding="utf-8")


def _summary(
    track: TrackData, ssh: SshResult, quality: TrackQualityResult,
    features: Sequence[Mapping[str, Any]], anomalies: TrackAnomalyResult, processing_version: str,
) -> dict[str, Any]:
    valid_ssh = np.asarray(quality.status, dtype=bool) & np.isfinite(ssh.values)
    return {
        "processingVersion": processing_version,
        "qualityPolicyVersion": quality.policy.version,
        "sshFormulaVersion": ssh.summary["sshFormulaVersion"],
        "observationCount": int(track.time.size),
        "passRate": float(valid_ssh.mean()) if track.time.size else 0.0,
        "anomalyCount": int(anomalies.summary["anomalyCount"]),
        "fieldMissingRates": {
            name: float((~np.isfinite(np.asarray(values, dtype=float))).mean()) if track.time.size else 0.0
            for name, values in track.fields.items()
        },
        "featureWindowCount": len(features),
        "features": [dict(feature) for feature in features],
        "anomalySummary": dict(anomalies.summary),
        "scientificBoundary": _SCIENTIFIC_BOUNDARY,
    }
