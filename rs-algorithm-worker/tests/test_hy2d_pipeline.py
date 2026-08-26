import io
import os
import tarfile
from pathlib import Path

import numpy as np
import pytest
from netCDF4 import Dataset

from rs_algorithm_worker.models_track import TrackData
from rs_algorithm_worker.hy2d_pipeline import execute_hy2d_stage
from rs_algorithm_worker.registry import dispatch
from rs_algorithm_worker.operators.netcdf_track import decode_values, decode_tar_gz
from rs_algorithm_worker.operators.ssh import SshResult
from rs_algorithm_worker.operators.track_output import write_standard_track_output
from rs_algorithm_worker.operators.track_quality import apply_quality_policy
from rs_algorithm_worker.algorithms.track_anomaly import detect_track_anomalies
from rs_algorithm_worker.algorithms.track_anomaly import TrackAnomalyResult
from rs_algorithm_worker.algorithms.track_features import extract_track_features


def _write_nc(path: Path, *, format="NETCDF3_CLASSIC", length=3):
    with Dataset(path, "w", format=format) as ds:
        ds.createDimension("time", length)
        for name, values in {
            "lat": [10.0, 11.0, 12.0][:length],
            "lon": [120.0, 121.0, 122.0][:length],
            "alt": [700.0, 701.0, 702.0][:length],
            "range_ku": [690.0, 691.0, 692.0][:length],
            "range_corrected": [0.1, 0.1, 0.1][:length],
            "ocean_tide_sol1": [0.2, 0.2, 0.2][:length],
            "surface_type": [0, 0, 0][:length],
            "rain_flag": [0, 0, 0][:length],
            "ice_flag": [0, 0, 0][:length],
            "qual_alt_1hz_range_ku": [0, 0, 0][:length],
            "orb_state_flag": [0, 0, 0][:length],
        }.items():
            variable = ds.createVariable(name, "f8" if name not in {"surface_type", "rain_flag", "ice_flag", "qual_alt_1hz_range_ku", "orb_state_flag"} else "i4", ("time",))
            variable[:] = values
        scaled = ds.createVariable("scaled", "i2", ("time",), fill_value=-32768)
        scaled.scale_factor = 0.01
        scaled.add_offset = 1.0
        scaled.set_auto_scale(False)
        scaled[:] = np.array([100, -32768, 200], dtype=np.int16)


def _archive(tmp_path, members):
    package = tmp_path / "sample.tar.gz"
    with tarfile.open(package, "w:gz") as archive:
        for name, content in members:
            info = tarfile.TarInfo(name)
            info.size = len(content)
            archive.addfile(info, io.BytesIO(content))
    return package


def test_decode_variable_applies_scale_offset_and_fill_value():
    raw = np.array([100, -32768], dtype=np.int16)
    decoded = decode_values(raw, scale_factor=0.01, add_offset=1.0, fill_value=-32768)
    np.testing.assert_allclose(decoded[:1], [2.0])
    assert np.isnan(decoded[1])


def test_decode_tar_gz_reads_track_data_and_metadata(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    result = decode_tar_gz(package)
    assert isinstance(result, TrackData)
    assert result.latitude.shape == (3,)
    assert "alt" in result.fields
    assert np.isnan(result.fields["scaled"][1])
    assert result.metadata["netcdf_format"] == "NETCDF3_CLASSIC"


def test_decode_tar_gz_rejects_unsafe_member(tmp_path):
    package = _archive(tmp_path, [("../evil.nc", b"bad"), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="unsafe"):
        decode_tar_gz(package)


def test_decode_tar_gz_rejects_multiple_nc_or_missing_metadata(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("a.nc", nc.read_bytes()), ("b.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="exactly one"):
        decode_tar_gz(package)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes())])
    with pytest.raises(ValueError, match="meta.xml"):
        decode_tar_gz(package)


def test_decode_tar_gz_rejects_non_netcdf3_and_length_mismatch(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc, format="NETCDF4")
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    with pytest.raises(ValueError, match="NETCDF3_CLASSIC"):
        decode_tar_gz(package)


def test_execute_hy2d_decode_stage_returns_hashed_logical_artifact(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    output = tmp_path / "execution" / "output"
    result = execute_hy2d_stage({
        "stageKey": "DECODE_ALTIMETER_TRACK", "outputUri": "rs://output/task-1",
        "parameterSnapshot": {},
    }, {"inputs": [{"role": "MAIN", "path": str(package)}], "output": {"path": str(output)}})

    artifact = result["artifacts"][0]
    assert artifact["type"] == "DECODED_ALTIMETER_TRACK"
    assert artifact["uri"] == "rs://output/task-1/decoded/track.npz"
    assert artifact["checksum"].startswith("sha256:")
    assert (output / "decoded" / "track.npz").is_file()


def test_execute_hy2d_quality_stage_uses_only_declared_upstream_track(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    decoded_output = tmp_path / "decoded-output"
    execute_hy2d_stage({"stageKey": "DECODE_ALTIMETER_TRACK", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
        "inputs": [{"role": "MAIN", "path": str(package)}], "output": {"path": str(decoded_output)},
    })
    quality_output = tmp_path / "quality-output"
    result = execute_hy2d_stage({"stageKey": "APPLY_TRACK_QUALITY", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
        "inputs": [{"role": "decoded", "path": str(decoded_output / "decoded" / "track.npz")}],
        "output": {"path": str(quality_output)},
    })

    assert {item["type"] for item in result["artifacts"]} == {
        "QUALITY_FILTERED_ALTIMETER_TRACK", "TRACK_QUALITY_SUMMARY_JSON",
    }


def test_registry_builds_hy2d_plan_v2_for_recognized_package(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    manifest = {"inputs": [{"role": "MAIN", "path": str(package), "storageUri": "rs://input/sample"}]}
    inspected = dispatch({"operation": "inspect"}, manifest)
    candidate = inspected["candidates"][0]
    request = {"operation": "build-plan", "payload": {"candidate": candidate, "parameterSnapshot": {}}}

    plan = dispatch(request, manifest)

    assert plan["version"] == "plan-v2"
    assert "WRITE_STANDARD_TRACK_OUTPUT" in {stage["stageKey"] for stage in plan["stages"]}


def test_execute_hy2d_ssh_stage_uses_only_quality_track(tmp_path):
    nc = tmp_path / "sample.nc"
    _write_nc(nc)
    package = _archive(tmp_path, [("sample.nc", nc.read_bytes()), ("sample.meta.xml", b"<meta />")])
    decoded = tmp_path / "decoded"
    execute_hy2d_stage({"stageKey": "DECODE_ALTIMETER_TRACK", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
        "inputs": [{"role": "MAIN", "path": str(package)}], "output": {"path": str(decoded)},
    })
    quality = tmp_path / "quality"
    execute_hy2d_stage({"stageKey": "APPLY_TRACK_QUALITY", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
        "inputs": [{"role": "decoded", "path": str(decoded / "decoded" / "track.npz")}], "output": {"path": str(quality)},
    })
    ssh_output = tmp_path / "ssh"
    result = execute_hy2d_stage({"stageKey": "CALCULATE_BASELINE_SSH", "outputUri": "rs://output/task-1", "parameterSnapshot": {}}, {
        "inputs": [{"role": "qualityTrack", "path": str(quality / "quality" / "track.npz")}], "output": {"path": str(ssh_output)},
    })

    assert result["artifacts"][0]["type"] == "BASELINE_SSH_TRACK"
    assert (ssh_output / "ssh" / "track.npz").is_file()


def test_write_standard_track_output_writes_interoperable_track_artifacts(tmp_path):
    track = TrackData(
        time=np.array([1.0, 2.0, 3.0]),
        longitude=np.array([120.0, 121.0, 122.0]),
        latitude=np.array([20.0, 21.0, 22.0]),
        fields={"alt": np.array([700.0, np.nan, 702.0]), "range_ku": np.array([690.0, 691.0, 692.0])},
        quality=np.ones(3, dtype=bool),
        metadata={"processingVersion": "worker-test-v1"},
    )
    quality = apply_quality_policy(track)
    ssh = SshResult(
        values=np.array([1.0, np.nan, 3.0]),
        summary={"sshFormulaVersion": "hy2d-idr-2mc-baseline-v1"},
    )
    anomalies = detect_track_anomalies(ssh.values, quality.status, window_size=3)
    result = write_standard_track_output(
        track=quality.track,
        ssh=ssh,
        quality=quality,
        features=extract_track_features(ssh.values, quality.status, window_size=2),
        anomalies=anomalies,
        output_dir=tmp_path / "output",
        processing_version="worker-test-v1",
    )

    assert {path.name for path in result.paths.values()} == {
        "track.nc", "track.parquet", "track.csv", "track.geojson", "anomalies.geojson", "summary.json",
    }
    assert all(path.is_file() and path.parent == result.output_dir for path in result.paths.values())
    csv_lines = result.paths["csv"].read_text(encoding="utf-8").splitlines()
    assert csv_lines[0].split(",") == [
        "time", "longitude", "latitude", "quality_status", "quality_reason_codes", "ssh",
    ]
    parquet = pytest.importorskip("pyarrow.parquet").read_table(result.paths["parquet"])
    assert parquet.column_names == ["time", "longitude", "latitude", "quality_status", "quality_reason_codes", "ssh"]
    assert parquet.num_rows == 3
    with Dataset(result.paths["netcdf"]) as dataset:
        assert dataset.getncattr("processing_version") == "worker-test-v1"
        assert dataset.getncattr("quality_policy_version") == "hy2d-idr-2mc-baseline-v1"
        assert dataset.getncattr("ssh_formula_version") == "hy2d-idr-2mc-baseline-v1"
    track_geojson = __import__("json").loads(result.paths["track_geojson"].read_text(encoding="utf-8"))
    anomaly_geojson = __import__("json").loads(result.paths["anomaly_geojson"].read_text(encoding="utf-8"))
    assert track_geojson["features"][0]["geometry"]["type"] == "LineString"
    assert anomaly_geojson["type"] == "FeatureCollection"
    summary = __import__("json").loads(result.paths["summary"].read_text(encoding="utf-8"))
    assert summary["passRate"] == pytest.approx(2 / 3)
    assert summary["anomalyCount"] == anomalies.summary["anomalyCount"]
    assert summary["features"][0]["sshMean"] == pytest.approx(1.0)
    assert summary["anomalySummary"] == anomalies.summary
    assert summary["fieldMissingRates"]["alt"] == pytest.approx(1 / 3)
    assert "official review" in summary["scientificBoundary"].lower()


def test_track_geojson_is_empty_when_fewer_than_two_finite_coordinates(tmp_path) -> None:
    track = TrackData(
        time=np.array([1.0, 2.0]), longitude=np.array([120.0, np.nan]), latitude=np.array([20.0, np.nan]),
        fields={"alt": np.array([700.0, 701.0]), "range_ku": np.array([690.0, 691.0])}, quality=np.ones(2, dtype=bool),
    )
    quality = apply_quality_policy(track)
    result = write_standard_track_output(
        track=quality.track, ssh=SshResult(np.array([1.0, np.nan]), {"sshFormulaVersion": "v1"}), quality=quality,
        features=[], anomalies=TrackAnomalyResult(((), ()), np.array([np.nan, np.nan]), {"anomalyCount": 0}),
        output_dir=tmp_path / "output", processing_version="v1",
    )

    geojson = __import__("json").loads(result.paths["track_geojson"].read_text(encoding="utf-8"))
    assert geojson == {"type": "FeatureCollection", "features": []}


def test_anomaly_geojson_serializes_nonfinite_time_and_ssh_as_null(tmp_path) -> None:
    track = TrackData(
        time=np.array([np.nan]), longitude=np.array([120.0]), latitude=np.array([20.0]),
        fields={"alt": np.array([700.0]), "range_ku": np.array([690.0])}, quality=np.ones(1, dtype=bool),
    )
    quality = apply_quality_policy(track)
    result = write_standard_track_output(
        track=quality.track, ssh=SshResult(np.array([np.nan]), {"sshFormulaVersion": "v1"}), quality=quality,
        features=[], anomalies=TrackAnomalyResult((("ADJACENT_JUMP",),), np.array([np.nan]), {"anomalyCount": 1}),
        output_dir=tmp_path / "output", processing_version="v1",
    )

    geojson = __import__("json").loads(result.paths["anomaly_geojson"].read_text(encoding="utf-8"))
    assert geojson["features"][0]["properties"]["time"] is None
    assert geojson["features"][0]["properties"]["ssh"] is None


@pytest.mark.real_sample
def test_real_hy2d_sample_read_only_smoke():
    root = os.getenv("MULTISOURCE_HY2D_SAMPLE_ROOT")
    if not root or not Path(root).is_dir():
        pytest.skip("MULTISOURCE_HY2D_SAMPLE_ROOT is not configured")
    packages = sorted(Path(root).glob("*.tar.gz"))
    if not packages:
        pytest.skip("HY-2D sample root contains no tar.gz package")
    result = decode_tar_gz(packages[0])
    assert isinstance(result, TrackData)
    assert result.latitude.size > 0
