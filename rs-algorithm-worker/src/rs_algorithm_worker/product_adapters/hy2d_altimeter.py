from __future__ import annotations

import json
import tarfile
from typing import Any

from .base import candidate, input_paths


def inspect_manifest(manifest):
    candidates = []
    for path in input_paths(manifest):
        if not path.name.lower().endswith(".tar.gz"):
            raise ValueError("HY-2D altimeter evidence requires tar.gz packages")
        try:
            with tarfile.open(path, "r:gz") as archive:
                members = [member for member in archive.getmembers() if member.isfile()]
                if not members:
                    raise ValueError("HY-2D altimeter evidence is incomplete")
        except (OSError, tarfile.TarError) as error:
            raise ValueError("HY-2D altimeter evidence is unreadable") from error
        candidates.append(candidate(path.stem, "HY-2D-ALTIMETER", "ALONG_TRACK_OBSERVATION", [path],
                                    {"memberCount": len(members)}))
    return candidates


_PROFILE = {"productCode": "HY-2D-ALTIMETER", "profileVersion": "v1"}


def build_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Build the declared HY-2D along-track DAG and freeze validated parameters."""
    if product.get("profileKey") != _PROFILE or product.get("dataShape") != "ALONG_TRACK_OBSERVATION":
        raise ValueError("HY-2D altimeter candidate is required")
    if product.get("supportStatus") != "SUPPORTED" or not product.get("files"):
        raise ValueError("supported HY-2D candidate with files is required")
    parameters = _parameters(parameter_snapshot or {})
    snapshot = json.dumps(parameters, sort_keys=True, separators=(",", ":"))
    main = _input("main", "RAW_INPUT", "MAIN")
    stages = [
        _stage("VALIDATE_SOURCE", [], [main], [_output("VALIDATION_METADATA_JSON", "validation/source.json", "INTERMEDIATE")], snapshot),
        _stage("DECODE_ALTIMETER_TRACK", ["VALIDATE_SOURCE"], [main], [_output("DECODED_ALTIMETER_TRACK", "decoded/track.npz", "INTERMEDIATE")], snapshot),
        _stage("APPLY_TRACK_QUALITY", ["DECODE_ALTIMETER_TRACK"], [_input("decoded", "UPSTREAM_ARTIFACT", "DECODED_ALTIMETER_TRACK")], [
            _output("QUALITY_FILTERED_ALTIMETER_TRACK", "quality/track.npz", "INTERMEDIATE"),
            _output("TRACK_QUALITY_SUMMARY_JSON", "quality/summary.json", "INTERMEDIATE"),
        ], snapshot),
        _stage("CALCULATE_BASELINE_SSH", ["APPLY_TRACK_QUALITY"], [_input("qualityTrack", "UPSTREAM_ARTIFACT", "QUALITY_FILTERED_ALTIMETER_TRACK")], [
            _output("BASELINE_SSH_TRACK", "ssh/track.npz", "INTERMEDIATE"),
        ], snapshot),
        _stage("EXTRACT_TRACK_FEATURES", ["CALCULATE_BASELINE_SSH"], [_input("ssh", "UPSTREAM_ARTIFACT", "BASELINE_SSH_TRACK")], [
            _output("TRACK_FEATURES_JSON", "features/summary.json", "FINAL"),
            _output("TRACK_FEATURE_WINDOWS_PARQUET", "features/windows.parquet", "FINAL"),
        ], snapshot),
        _stage("DETECT_TRACK_ANOMALIES", ["CALCULATE_BASELINE_SSH"], [_input("ssh", "UPSTREAM_ARTIFACT", "BASELINE_SSH_TRACK")], [
            _output("TRACK_ANOMALIES_GEOJSON", "anomalies/points.geojson", "FINAL"),
            _output("TRACK_ANOMALY_SUMMARY_JSON", "anomalies/summary.json", "FINAL"),
        ], snapshot),
        _stage("WRITE_STANDARD_TRACK_OUTPUT", ["CALCULATE_BASELINE_SSH", "EXTRACT_TRACK_FEATURES", "DETECT_TRACK_ANOMALIES"], [
            _input("ssh", "UPSTREAM_ARTIFACT", "BASELINE_SSH_TRACK"),
            _input("quality", "UPSTREAM_ARTIFACT", "QUALITY_FILTERED_ALTIMETER_TRACK"),
            _input("features", "UPSTREAM_ARTIFACT", "TRACK_FEATURES_JSON"),
            _input("anomalies", "UPSTREAM_ARTIFACT", "TRACK_ANOMALIES_GEOJSON"),
        ], [
            _output("STANDARD_TRACK_NETCDF", "standard/track.nc", "FINAL"),
            _output("STANDARD_TRACK_PARQUET", "standard/track.parquet", "FINAL"),
            _output("STANDARD_TRACK_CSV", "standard/track.csv", "FINAL"),
            _output("STANDARD_TRACK_GEOJSON", "standard/track.geojson", "FINAL"),
            _output("STANDARD_TRACK_SUMMARY_JSON", "standard/summary.json", "FINAL"),
        ], snapshot),
    ]
    if parameters["grid"]["enabled"]:
        stages.append(_stage("GRID_TRACK_OPTIONAL", ["CALCULATE_BASELINE_SSH"], [_input("ssh", "UPSTREAM_ARTIFACT", "BASELINE_SSH_TRACK")], [
            _output("TRACK_GRID_NETCDF", "grid/track-grid.nc", "FINAL"),
            _output("TRACK_GRID_COG", "grid/track-grid.tif", "FINAL"),
            _output("TRACK_GRID_SUMMARY_JSON", "grid/summary.json", "FINAL"),
        ], snapshot))
    return {"version": "plan-v2", "stages": stages}


def _parameters(value: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value).difference({"qualityPolicyVersion", "featureWindowSize", "anomaly", "grid"}):
        raise ValueError("unsupported HY-2D parameter")
    quality = value.get("qualityPolicyVersion", "hy2d-idr-2mc-baseline-v1")
    feature_window = value.get("featureWindowSize", 21)
    anomaly = value.get("anomaly", {})
    grid = value.get("grid", {})
    if quality != "hy2d-idr-2mc-baseline-v1" or isinstance(feature_window, bool) or not isinstance(feature_window, int) or feature_window <= 0 or feature_window % 2 == 0:
        raise ValueError("invalid HY-2D quality policy or feature window")
    if not isinstance(anomaly, dict) or set(anomaly).difference({"madThreshold", "jumpThresholdMeters"}):
        raise ValueError("invalid HY-2D anomaly parameters")
    mad = anomaly.get("madThreshold", 5.0)
    jump = anomaly.get("jumpThresholdMeters", 0.5)
    if not _positive(mad) or not _positive(jump):
        raise ValueError("HY-2D anomaly thresholds must be positive")
    if not isinstance(grid, dict) or set(grid).difference({"enabled", "spacingDegrees"}):
        raise ValueError("invalid HY-2D grid parameters")
    enabled = grid.get("enabled", False)
    spacing = grid.get("spacingDegrees", 0.25)
    if not isinstance(enabled, bool) or not _positive(spacing) or spacing > 5.0:
        raise ValueError("HY-2D grid parameters are invalid")
    return {"qualityPolicyVersion": quality, "featureWindowSize": feature_window,
            "anomaly": {"madThreshold": float(mad), "jumpThresholdMeters": float(jump)},
            "grid": {"enabled": enabled, "spacingDegrees": float(spacing)}}


def _positive(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and value > 0


def _stage(key: str, dependencies: list[str], inputs: list[dict[str, Any]], outputs: list[dict[str, Any]], snapshot: str) -> dict[str, Any]:
    return {"stageKey": key, "stageCode": key, "dependsOnStageKeys": dependencies,
            "inputs": inputs, "outputs": outputs, "parameterSnapshot": snapshot}


def _input(key: str, source: str, artifact_type: str) -> dict[str, Any]:
    return {"key": key, "source": source, "artifactType": artifact_type, "required": True}


def _output(artifact_type: str, relative_path: str, retention: str) -> dict[str, Any]:
    return {"artifactType": artifact_type, "relativePath": relative_path, "retention": retention}
