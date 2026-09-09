from __future__ import annotations

from pathlib import Path
import json

from gf6_fixture import make_gf6_package
from rs_algorithm_worker.gf6_wfv_pipeline import execute_gf6_product, execute_gf6_stage


def test_integrated_executor_writes_standard_once_and_reuses_it_for_all_optional_outputs(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input")
    manifest["output"] = {"path": str(tmp_path / "output")}
    standard = tmp_path / "output" / "standard" / "gf6-standard.tif"
    standard.parent.mkdir(parents=True)
    standard.write_bytes(b"standard")
    calls = []

    def write_summary(*_):
        summary = tmp_path / "output" / "summary.json"
        summary.write_text("{}", encoding="utf-8")
        return summary

    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_standard_cog", lambda *_: calls.append("standard"))
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_processing_summary", write_summary)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_features", lambda path, *_: calls.append(path) or [])
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_fusion", lambda path, *_: calls.append(path) or [])
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_saliency", lambda path, *_: calls.append(path) or [])

    execute_gf6_product({"stageKey": "STANDARDIZE_GF6_WFV_L1A", "outputUri": "rs://output/gf6",
                         "parameters": {"features": {"enabled": True, "metrics": ["gradient"]},
                                        "fusion": {"enabled": True, "red": 3, "green": 2, "blue": 1, "percentiles": [2, 98]},
                                        "saliency": {"enabled": True, "source": "B3", "windowSize": 9, "threshold": .95}}}, manifest)

    assert calls[0] == "standard"
    assert calls[1:] == [standard, standard, standard]


def test_staged_executor_validates_then_writes_standard_artifacts(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input-staged")
    root = tmp_path / "output-staged"; manifest["output"] = {"path": str(root)}
    def write_standard(_, path, __):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"standard")
    def write_summary(*_):
        path = root / "standard" / "summary.json"; path.write_text("{}", encoding="utf-8"); return path
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_standard_cog", write_standard)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_processing_summary", write_summary)

    validation = execute_gf6_stage({"stageKey": "VALIDATE_GF6_WFV_L1A", "outputUri": "rs://output/gf6"}, manifest)
    assert validation["status"] == "SUCCEEDED"
    assert validation["artifacts"][0]["type"] == "GF6_INPUT_VALIDATION_JSON"

    standard = execute_gf6_stage({"stageKey": "WRITE_GF6_STANDARD_COG", "outputUri": "rs://output/gf6", "parameterSnapshot": {}}, manifest)
    assert standard["status"] == "SUCCEEDED"
    assert {item["type"] for item in standard["artifacts"]} == {"GF6_STANDARD_COG", "GF6_PROCESSING_SUMMARY_JSON"}


def test_execute_stage_accepts_uuid_physical_names_with_manifest_original_names(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input-uuid")
    for index, item in enumerate(manifest["inputs"]):
        source = Path(item["path"])
        physical = source.with_name(f"20000000-0000-0000-0000-{index:012d}{source.suffix.lower()}")
        source.rename(physical)
        item["path"] = str(physical)
        item["originalFileName"] = source.name
    root = tmp_path / "output-uuid"
    manifest["output"] = {"path": str(root)}

    def write_standard(_, path, __):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"standard")

    def write_summary(*_):
        summary = root / "standard" / "summary.json"
        summary.write_text("{}", encoding="utf-8")
        return summary

    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_standard_cog", write_standard)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_processing_summary", write_summary)

    response = execute_gf6_product(
        {"stageKey": "STANDARDIZE_GF6_WFV_L1A", "outputUri": "rs://output/gf6-uuid", "parameters": {}},
        manifest,
    )

    assert response["status"] == "SUCCEEDED"


def test_snapshot_execution_returns_exact_plan_relative_paths_for_all_enabled_outputs(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input")
    root = tmp_path / "output"; manifest["output"] = {"path": str(root)}
    parameters = {"features": {"enabled": True, "metrics": ["local_variance"], "windowSize": 5},
                  "fusion": {"enabled": True, "red": 3, "green": 2, "blue": 1, "percentiles": [2, 98]},
                  "saliency": {"enabled": True, "source": "B3", "windowSize": 9, "threshold": .95}}
    from rs_algorithm_worker.product_adapters.gf6_wfv_l1a import build_integrated_plan, inspect_manifest
    plan = build_integrated_plan(inspect_manifest(manifest)[0], parameters)["stages"][0]
    standard = root / "standard" / "gf6-standard.tif"

    def write(_, path, __): path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"standard")
    def summary(*_): path = root / "standard" / "summary.json"; path.write_text("{}", encoding="utf-8"); return path
    def optional(path, directory, _, names):
        assert path == standard
        result = []
        for kind, relative in names:
            output = root / relative; output.parent.mkdir(parents=True, exist_ok=True); output.write_bytes(b"x"); result.append((kind, output))
        return result
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_standard_cog", write)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_processing_summary", summary)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_features", lambda path, directory, config: optional(path, directory, config, [("GF6_FEATURES_COG", "features/gf6-features.tif"), ("GF6_FEATURES_SUMMARY_JSON", "features/summary.json")]))
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_fusion", lambda path, directory, config: optional(path, directory, config, [("GF6_RGB_FUSION_COG", "fusion/gf6-rgb.tif"), ("GF6_FUSION_SUMMARY_JSON", "fusion/summary.json")]))
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_saliency", lambda path, directory, config: optional(path, directory, config, [("GF6_SALIENCY_SCORE_COG", "saliency/score.tif"), ("GF6_SALIENCY_MASK_COG", "saliency/mask.tif"), ("GF6_SALIENCY_SUMMARY_JSON", "saliency/summary.json")]))

    response = execute_gf6_product({"stageKey": "STANDARDIZE_GF6_WFV_L1A", "outputUri": "rs://output/gf6", "parameterSnapshot": json.dumps(parameters)}, manifest)

    expected = {(item["artifactType"], f"rs://output/gf6/{item['relativePath']}") for item in plan["outputs"]}
    actual = {(item["type"], item["uri"]) for item in response["artifacts"]}
    assert actual == expected
    assert all((root / item["relativePath"]).is_file() for item in plan["outputs"])
