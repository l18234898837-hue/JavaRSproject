from __future__ import annotations

import json
import re
import tarfile
from typing import Any
from xml.etree import ElementTree as ET

from .base import candidate, input_paths


_PACKAGE_NAME = re.compile(r"^H2D_OPER_(?:IDR|IGDR)_2MC_.+\.tar\.gz$", re.IGNORECASE)


def inspect_manifest(manifest):
    candidates = []
    for path in input_paths(manifest):
        try:
            with tarfile.open(path, "r:gz") as archive:
                members = [member for member in archive.getmembers() if member.isfile()]
                nc_members = [member for member in members if member.name.lower().endswith(".nc")]
                xml_members = [member for member in members if member.name.lower().endswith(".meta.xml")]
                if len(nc_members) != 1 or len(xml_members) != 1:
                    raise ValueError("HY-2D altimeter evidence is incomplete")
                nc_stem = nc_members[0].name.rsplit("/", 1)[-1][:-3]
                xml_stem = xml_members[0].name.rsplit("/", 1)[-1][:-9]
                package_id = nc_stem
                if nc_stem != xml_stem or _PACKAGE_NAME.fullmatch(package_id + ".tar.gz") is None:
                    raise ValueError("HY-2D altimeter package members do not match")
                xml_file = archive.extractfile(xml_members[0])
                if xml_file is None:
                    raise ValueError("HY-2D altimeter metadata is unreadable")
                root = ET.fromstring(xml_file.read())
                evidence = {
                    "satelliteId": root.findtext("SatelliteID", "").strip(),
                    "sensorId": root.findtext("SensorID", "").strip(),
                    "productLevel": root.findtext(".//ProductLevel", "").strip(),
                    "productFormat": root.findtext(".//ProductFormat", "").strip(),
                    "orbitId": root.findtext("OrbitID", "").strip(),
                    "orbitPass": root.findtext("OrbitPass", "").strip(),
                }
                if evidence["satelliteId"] != "HY2D" or evidence["sensorId"] != "ALT" \
                        or not evidence["productLevel"].startswith("IDR") or evidence["productFormat"] != "NETCDF":
                    raise ValueError("HY-2D altimeter evidence is incomplete")
        except (OSError, tarfile.TarError, ET.ParseError) as error:
            raise ValueError("HY-2D altimeter evidence is unreadable") from error
        candidates.append(candidate(package_id, "HY-2D-ALTIMETER", "ALONG_TRACK_OBSERVATION", [path],
                                    {"memberCount": len(members), **evidence}))
    return candidates


_PROFILE = {"productCode": "HY-2D-ALTIMETER", "profileVersion": "v1"}


def _build_validated_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Build the declared HY-2D along-track DAG and freeze validated parameters."""
    if product.get("profileKey") != _PROFILE or product.get("dataShape") != "ALONG_TRACK_OBSERVATION":
        raise ValueError("HY-2D altimeter candidate is required")
    if product.get("supportStatus") != "SUPPORTED" or not product.get("files"):
        raise ValueError("supported HY-2D candidate with files is required")
    parameters = parse_hy2d_parameters(parameter_snapshot or {})
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
        _stage("WRITE_STANDARD_TRACK_OUTPUT", ["APPLY_TRACK_QUALITY", "CALCULATE_BASELINE_SSH", "EXTRACT_TRACK_FEATURES", "DETECT_TRACK_ANOMALIES"], [
            _input("ssh", "UPSTREAM_ARTIFACT", "BASELINE_SSH_TRACK"),
            _input("quality", "UPSTREAM_ARTIFACT", "QUALITY_FILTERED_ALTIMETER_TRACK"),
            _input("features", "UPSTREAM_ARTIFACT", "TRACK_FEATURES_JSON"),
            _input("anomalies", "UPSTREAM_ARTIFACT", "TRACK_ANOMALIES_GEOJSON"),
        ], [
            _output("STANDARD_TRACK_NETCDF", "standard/track.nc", "FINAL"),
            _output("STANDARD_TRACK_PARQUET", "standard/track.parquet", "FINAL"),
            _output("STANDARD_TRACK_CSV", "standard/track.csv", "FINAL"),
            _output("STANDARD_TRACK_GEOJSON", "standard/track.geojson", "FINAL"),
            _output("STANDARD_TRACK_ANOMALIES_GEOJSON", "standard/anomalies.geojson", "FINAL"),
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


def build_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Compatibility entry point for historical plan-v2 callers."""
    return _build_validated_plan(product, parameter_snapshot)


def build_integrated_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Declare one self-contained along-track stage for the current dispatcher."""
    source_plan = _build_validated_plan(product, parameter_snapshot)
    parameters = json.loads(source_plan["stages"][0]["parameterSnapshot"])
    outputs = [
        _output("TRACK_FEATURES_JSON", "features/summary.json", "FINAL"),
        _output("TRACK_FEATURE_WINDOWS_PARQUET", "features/windows.parquet", "FINAL"),
        _output("TRACK_ANOMALIES_GEOJSON", "anomalies/points.geojson", "FINAL"),
        _output("TRACK_ANOMALY_SUMMARY_JSON", "anomalies/summary.json", "FINAL"),
        _output("STANDARD_TRACK_NETCDF", "standard/track.nc", "FINAL"),
        _output("STANDARD_TRACK_PARQUET", "standard/track.parquet", "FINAL"),
        _output("STANDARD_TRACK_CSV", "standard/track.csv", "FINAL"),
        _output("STANDARD_TRACK_GEOJSON", "standard/track.geojson", "FINAL"),
        _output("STANDARD_TRACK_ANOMALIES_GEOJSON", "standard/anomalies.geojson", "FINAL"),
        _output("STANDARD_TRACK_SUMMARY_JSON", "standard/summary.json", "FINAL"),
    ]
    if parameters["grid"]["enabled"]:
        outputs.extend([
            _output("TRACK_GRID_NETCDF", "grid/track-grid.nc", "FINAL"),
            _output("TRACK_GRID_COG", "grid/track-grid.tif", "FINAL"),
            _output("TRACK_GRID_SUMMARY_JSON", "grid/summary.json", "FINAL"),
        ])
    snapshot = json.dumps(parameters, sort_keys=True, separators=(",", ":"))
    return {"version": "plan-v2", "stages": [
        _stage("STANDARDIZE_ALTIMETER_TRACK", [], [_input("main", "RAW_INPUT", "MAIN")], outputs, snapshot)
    ]}


def build_staged_plan(product: dict[str, Any], parameter_snapshot: dict[str, Any] | None = None) -> dict[str, Any]:
    """Return the existing validated along-track DAG as plan-v3."""
    plan = _build_validated_plan(product, parameter_snapshot)
    return {**plan, "version": "plan-v3"}


def parse_hy2d_parameters(value: dict[str, Any]) -> dict[str, Any]:
    """Normalize and validate HY-2D parameters without constructing a plan.

    Execution and planning both use this neutral helper so the v3 path does
    not depend on the historical integrated builder.
    """
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
    if not isinstance(enabled, bool) or not _positive(spacing) or spacing < 0.1 or spacing > 5.0:
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
