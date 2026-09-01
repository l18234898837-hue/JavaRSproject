import pytest
from pathlib import Path

from rs_algorithm_worker.product_adapters.hy2d_altimeter import build_integrated_plan, build_plan, inspect_manifest
from rs_algorithm_worker.hy2d_pipeline import execute_hy2d_stage


@pytest.mark.real_sample
def test_hy2d_is_along_track(hy2d_manifest):
    candidates = inspect_manifest(hy2d_manifest)
    assert len(candidates) == 10
    assert {item["dataShape"] for item in candidates} == {"ALONG_TRACK_OBSERVATION"}
    assert build_plan(candidates[0])["stages"][0]["stageCode"] == "VALIDATE_SOURCE"


@pytest.mark.real_sample
def test_real_hy2d_package_produces_standard_track_outputs(hy2d_manifest, tmp_path):
    package = Path(hy2d_manifest["inputs"][0]["path"])
    parameters = {"grid": {"enabled": True, "spacingDegrees": 0.25}}
    root_uri = "rs://output/hy2d-real-test"

    def run(stage_key: str, role: str, path: Path, name: str):
        return execute_hy2d_stage({"stageKey": stage_key, "outputUri": root_uri, "parameterSnapshot": parameters}, {
            "inputs": [{"role": role, "path": str(path)}], "output": {"path": str(tmp_path / name)},
        })

    run("DECODE_ALTIMETER_TRACK", "MAIN", package, "decoded")
    run("APPLY_TRACK_QUALITY", "decoded", tmp_path / "decoded" / "decoded" / "track.npz", "quality")
    run("CALCULATE_BASELINE_SSH", "qualityTrack", tmp_path / "quality" / "quality" / "track.npz", "ssh")
    result = run("WRITE_STANDARD_TRACK_OUTPUT", "ssh", tmp_path / "ssh" / "ssh" / "track.npz", "standard")

    assert {item["type"] for item in result["artifacts"]} == {
        "STANDARD_TRACK_NETCDF", "STANDARD_TRACK_PARQUET", "STANDARD_TRACK_CSV",
        "STANDARD_TRACK_GEOJSON", "STANDARD_TRACK_ANOMALIES_GEOJSON",
        "STANDARD_TRACK_SUMMARY_JSON",
    }
    assert (tmp_path / "standard" / "standard" / "track.parquet").is_file()


def _candidate() -> dict:
    return {
        "packageId": "HY2D-TRACK",
        "profileKey": {"productCode": "HY-2D-ALTIMETER", "profileVersion": "v1"},
        "dataShape": "ALONG_TRACK_OBSERVATION",
        "supportStatus": "SUPPORTED",
        "files": [{"role": "MAIN", "ordinal": 0, "path": "sample.tar.gz"}],
    }


def test_hy2d_plan_declares_track_artifacts_and_optional_grid() -> None:
    plan = build_plan(_candidate(), {"grid": {"enabled": True, "spacingDegrees": 0.25}})

    stages = {stage["stageKey"]: stage for stage in plan["stages"]}
    assert plan["version"] == "plan-v2"
    assert "GRID_TRACK_OPTIONAL" in stages
    assert stages["APPLY_TRACK_QUALITY"]["outputs"][0]["artifactType"] == "QUALITY_FILTERED_ALTIMETER_TRACK"


def test_hy2d_integrated_plan_matches_the_single_dispatch_contract() -> None:
    plan = build_integrated_plan(_candidate(), {"grid": {"enabled": True, "spacingDegrees": 0.25}})

    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_ALTIMETER_TRACK"]
    stage = plan["stages"][0]
    assert [item["artifactType"] for item in stage["inputs"]] == ["MAIN"]
    assert {"STANDARD_TRACK_NETCDF", "TRACK_ANOMALIES_GEOJSON", "TRACK_GRID_COG"}.issubset(
        {item["artifactType"] for item in stage["outputs"]}
    )


@pytest.mark.parametrize("parameters", [
    {"unexpected": True},
    {"featureWindowSize": 4},
    {"anomaly": {"madThreshold": 0}},
    {"grid": {"enabled": True, "spacingDegrees": 0}},
    {"grid": {"enabled": True, "spacingDegrees": 0.05}},
])
def test_hy2d_plan_rejects_unknown_or_invalid_parameters(parameters: dict) -> None:
    with pytest.raises(ValueError):
        build_plan(_candidate(), parameters)
