import json
from copy import deepcopy
from pathlib import Path

import pytest

from fy4b_fixture import make_fy4b_package
from gf6_fixture import make_gf6_package
from rs_algorithm_worker import registry
from rs_algorithm_worker.product_adapters import fy4b_agri, gf6_wfv_l1a
from rs_algorithm_worker.product_adapters.gf6_wfv_browse import inspect_manifest
from rs_algorithm_worker.registry import dispatch
from fy3h_fixture import make_fy3h_pair
from fy4b_fixture import make_fy4b_package


def _fy3h_manifest(*paths):
    return {"inputs": [
        {"path": str(path), "role": "MAIN", "ordinal": ordinal, "storageUri": f"file:///{path.name}"}
        for ordinal, path in enumerate(paths)
    ]}


def test_registry_builds_fy3h_plan_v2_for_recognized_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    manifest = _fy3h_manifest(observation, geo)
    candidate = dispatch({"operation": "inspect"}, manifest)["candidates"][0]

    response = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate,
        "parameterSnapshot": {"targetCrs": "EPSG:4326", "resolutionDegrees": 0.01},
    }}, manifest)

    assert response["version"] == "plan-v2"
    assert response["stages"][0]["stageKey"] == "VALIDATE_FY3H_PAIR"


def test_registry_builds_plan_v3_for_generic_supported_manifest(tmp_path):
    source = tmp_path / "generic.bin"
    source.write_bytes(b"generic")
    manifest = {"inputs": [{"path": str(source), "role": "MAIN", "ordinal": 0}]}
    candidate = {"packageId": "generic", "profileKey": {"productCode": "GENERIC", "profileVersion": "v1"}, "dataShape": "RASTER_GRID", "supportStatus": "SUPPORTED", "files": [{"role": "MAIN", "ordinal": 0, "path": str(source)}]}
    response = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate, "requestedPlanVersion": "plan-v3", "parameterSnapshot": {}
    }}, manifest)
    assert response["version"] == "plan-v3"
    assert response["stages"][0]["stageKey"] == "VALIDATE_SOURCE"


def test_registry_builds_requested_plan_v3_for_fy3h_pair(tmp_path):
    observation, geo = make_fy3h_pair(tmp_path)
    manifest = _fy3h_manifest(observation, geo)
    candidate = dispatch({"operation": "inspect"}, manifest)["candidates"][0]

    response = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate,
        "requestedPlanVersion": "plan-v3",
        "parameterSnapshot": {"targetCrs": "EPSG:4326", "resolutionDegrees": 0.01},
    }}, manifest)

    assert response["version"] == "plan-v3"
    assert response["stages"][0]["stageKey"] == "VALIDATE_FY3H_PAIR"


def test_registry_routes_only_recognized_fy4b_package_and_returns_logical_artifact(tmp_path):
    manifest = make_fy4b_package(tmp_path)
    manifest["output"] = {"path": str(tmp_path / "output")}
    candidate = dispatch({"operation": "inspect"}, manifest)["candidates"][0]

    plan = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate,
        "parameterSnapshot": {"schemaVersion": "1", "targetCrs": "EPSG:4326"},
    }}, manifest)
    response = dispatch({"operation": "execute-stage", "payload": {
        "profileCode": "FY-4B-AGRI", "stageKey": "VALIDATE_FY4B_PACKAGE",
        "outputUri": "rs://output/fy4b-validation", "parameterSnapshot": {"schemaVersion": "1", "targetCrs": "EPSG:4326"},
    }}, manifest)

    assert plan["stages"][0]["stageKey"] == "VALIDATE_FY4B_PACKAGE"
    artifact = response["artifacts"][0]
    assert artifact["uri"] == "rs://output/fy4b-validation/validation/package.json"
    assert artifact["checksum"].startswith("sha256:")
    assert str(tmp_path) not in json.dumps(response)


def _manifest_with_storage_uris(manifest):
    manifest = deepcopy(manifest)
    for ordinal, item in enumerate(manifest["inputs"]):
        item["storageUri"] = f"rs://input/test-{ordinal}"
    return manifest


def _rename_inputs_to_uuid_files(manifest):
    manifest = deepcopy(manifest)
    for index, item in enumerate(manifest["inputs"]):
        source = Path(item["path"])
        physical = source.with_name(f"10000000-0000-0000-0000-{index:012d}{source.suffix.lower()}")
        source.rename(physical)
        item["path"] = str(physical)
        item["originalFileName"] = source.name
    return manifest


def test_registry_selects_gf6_l1a_before_legacy_browse_when_both_evidence_sets_are_present():
    manifest = {
        "inputs": [
            {"path": r"C:\incoming\tile-1.tif", "role": "MAIN_TIFF_TILE"},
            {"path": r"C:\incoming\tile-2.tif", "role": "MAIN_TIFF_TILE"},
            {"path": r"C:\incoming\tile-3.tif", "role": "MAIN_TIFF_TILE"},
            {"path": r"C:\incoming\metadata.xml", "role": "METADATA_XML"},
            {"path": r"C:\incoming\main.rpb", "role": "RPC_RPB"},
            {"path": r"C:\incoming\navigation.nav", "role": "NAV"},
            {"path": r"C:\incoming\tiles.til", "role": "TILE_INDEX_TIL"},
            {"path": r"C:\incoming\browse.jpg", "role": "BROWSE_IMAGE"},
            {"path": r"C:\incoming\browse.jgw", "role": "GEOREFERENCE"},
            {"path": r"C:\incoming\browse.json", "role": "METADATA"},
        ],
    }

    assert registry._adapter_for_manifest(manifest) is gf6_wfv_l1a


def test_registry_selects_fy4b_from_complete_declared_roles_without_storage_path_tokens():
    manifest = {
        "inputs": [
            {"path": rf"C:\incoming\asset-{ordinal}.bin", "role": role}
            for ordinal, role in enumerate(("L1_0500M", "L1_1000M", "L1_2000M", "L1_4000M", "GEO_4000M"))
        ],
    }

    assert registry._adapter_for_manifest(manifest) is fy4b_agri


def test_registry_inspects_and_builds_the_fy4b_integrated_plan(tmp_path):
    root = tmp_path / "fy4b"
    root.mkdir()
    manifest = _manifest_with_storage_uris(make_fy4b_package(root))

    inspected = dispatch({"operation": "inspect", "payload": {}}, manifest)
    candidate = inspected["candidates"][0]
    plan = dispatch({
        "operation": "build-plan",
        "payload": {"candidate": candidate, "parameterSnapshot": {"schemaVersion": "1"}},
    }, manifest)

    assert candidate["profileKey"] == {"productCode": "FY-4B-AGRI", "profileVersion": "v1"}
    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_FY4B_AGRI"]


def test_registry_builds_requested_plan_v3_for_fy4b_package(tmp_path):
    root = tmp_path / "fy4b-v3"
    root.mkdir()
    manifest = _manifest_with_storage_uris(make_fy4b_package(root))
    candidate = dispatch({"operation": "inspect", "payload": {}}, manifest)["candidates"][0]
    plan = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate, "requestedPlanVersion": "plan-v3", "parameterSnapshot": {},
    }}, manifest)

    assert plan["version"] == "plan-v3"
    assert plan["stages"][0]["stageKey"] == "VALIDATE_FY4B_PACKAGE"
    assert plan["stages"][0]["stageKey"] != "STANDARDIZE_FY4B_AGRI"


def test_registry_inspect_and_build_plan_accept_fy4b_uuid_physical_names(tmp_path):
    root = tmp_path / "fy4b-uuid"
    root.mkdir()
    manifest = _manifest_with_storage_uris(_rename_inputs_to_uuid_files(make_fy4b_package(root)))

    candidate = dispatch({"operation": "inspect", "payload": {}}, manifest)["candidates"][0]
    plan = dispatch({"operation": "build-plan", "payload": {"candidate": candidate, "parameterSnapshot": {}}}, manifest)

    assert candidate["profileKey"]["productCode"] == "FY-4B-AGRI"
    assert plan["stages"][0]["stageKey"] == "STANDARDIZE_FY4B_AGRI"


def test_registry_executes_only_the_fy4b_integrated_stage(tmp_path, monkeypatch):
    root = tmp_path / "fy4b"
    root.mkdir()
    manifest = _manifest_with_storage_uris(make_fy4b_package(root))
    expected = {
        "workerId": "worker-local", "status": "SUCCEEDED",
        "artifacts": [{"uri": "rs://output/task-fy4b/standard/summary.json", "type": "FY4B_STANDARD_SUMMARY_JSON",
                       "sizeBytes": 1, "checksum": "sha256:test"}],
    }
    monkeypatch.setattr(registry, "execute_fy4b_product", lambda _payload, _manifest: expected)

    response = dispatch({
        "operation": "execute-stage",
        "payload": {"profileCode": "FY-4B-AGRI", "stageKey": "STANDARDIZE_FY4B_AGRI"},
    }, manifest)

    assert response == expected


def test_registry_routes_fy4b_plan_v3_stage_to_staged_executor(tmp_path, monkeypatch):
    root = tmp_path / "fy4b-stage"; root.mkdir()
    manifest = _manifest_with_storage_uris(make_fy4b_package(root))
    expected = {"workerId": "worker-local", "status": "SUCCEEDED", "artifacts": []}
    monkeypatch.setattr(registry, "execute_fy4b_stage", lambda _payload, _manifest: expected)

    response = dispatch({"operation": "execute-stage", "payload": {
        "profileCode": "FY-4B-AGRI", "planVersion": "plan-v3", "stageKey": "VALIDATE_FY4B_PACKAGE",
        "outputUri": "rs://output/fy4b-validation", "parameterSnapshot": "{}",
    }}, manifest)

    assert response == expected


def test_registry_inspects_and_builds_the_gf6_l1a_integrated_plan(tmp_path):
    manifest = _manifest_with_storage_uris(make_gf6_package(tmp_path / "gf6"))

    inspected = dispatch({"operation": "inspect", "payload": {}}, manifest)
    candidate = inspected["candidates"][0]
    plan = dispatch({
        "operation": "build-plan",
        "payload": {"candidate": candidate, "parameterSnapshot": {"schemaVersion": "gf6-wfv-l1a-v1"}},
    }, manifest)

    assert candidate["profileKey"] == {"productCode": "GF-6-WFV-L1A", "profileVersion": "v1"}
    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_GF6_WFV_L1A"]


def test_registry_builds_requested_plan_v3_for_gf6_l1a_with_staged_dag(tmp_path):
    manifest = _manifest_with_storage_uris(make_gf6_package(tmp_path / "gf6-v3"))
    candidate = dispatch({"operation": "inspect", "payload": {}}, manifest)["candidates"][0]

    plan = dispatch({"operation": "build-plan", "payload": {
        "candidate": candidate,
        "requestedPlanVersion": "plan-v3",
        "parameterSnapshot": {},
    }}, manifest)

    assert plan["version"] == "plan-v3"
    keys = [stage["stageKey"] for stage in plan["stages"]]
    assert keys[:5] == [
        "VALIDATE_GF6_WFV_L1A",
        "MOSAIC_GF6_TILES",
        "GEOLOCATE_GF6_RPC",
        "STANDARDIZE_GF6_PROJECTION",
        "WRITE_GF6_STANDARD_COG",
    ]
    assert "STANDARDIZE_GF6_WFV_L1A" not in keys
    assert plan["stages"][0]["outputs"][0]["retention"] == "INTERMEDIATE"
    allowed_sources = {"RAW_INPUT", "UPSTREAM_ARTIFACT"}
    assert all(
        item["source"] in allowed_sources
        for stage in plan["stages"]
        for item in stage["inputs"]
    )
    assert plan["stages"][1]["inputs"] == plan["stages"][0]["inputs"]


def test_registry_inspect_and_build_plan_accept_gf6_uuid_physical_names(tmp_path):
    manifest = _manifest_with_storage_uris(
        _rename_inputs_to_uuid_files(make_gf6_package(tmp_path / "gf6-uuid"))
    )

    candidate = dispatch({"operation": "inspect", "payload": {}}, manifest)["candidates"][0]
    plan = dispatch({"operation": "build-plan", "payload": {"candidate": candidate, "parameterSnapshot": {}}}, manifest)

    assert candidate["profileKey"]["productCode"] == "GF-6-WFV-L1A"
    assert plan["stages"][0]["stageKey"] == "STANDARDIZE_GF6_WFV_L1A"


def test_registry_preserves_optional_gf6_footprint_required_flags(tmp_path):
    manifest = make_gf6_package(tmp_path / "gf6-footprint")
    for suffix in (".shp", ".dbf", ".shx"):
        path = tmp_path / "gf6-footprint" / f"GF6_WFV_SAMPLE{suffix}"
        path.write_bytes(b"footprint")
        manifest["inputs"].append({"path": str(path)})
    manifest = _manifest_with_storage_uris(manifest)

    candidate = dispatch({"operation": "inspect", "payload": {}}, manifest)["candidates"][0]

    assert [(item["role"], item["required"]) for item in candidate["files"][-3:]] == [
        ("FOOTPRINT_SHP", False), ("FOOTPRINT_DBF", False), ("FOOTPRINT_SHX", False),
    ]


def test_registry_executes_only_the_gf6_l1a_integrated_stage(tmp_path, monkeypatch):
    manifest = _manifest_with_storage_uris(make_gf6_package(tmp_path / "gf6"))
    expected = {
        "workerId": "worker-local", "status": "SUCCEEDED",
        "artifacts": [{"uri": "rs://output/task-gf6/standard/summary.json", "type": "GF6_PROCESSING_SUMMARY_JSON",
                       "sizeBytes": 1, "checksum": "sha256:test"}],
    }
    monkeypatch.setattr(registry, "execute_gf6_product", lambda _payload, _manifest: expected)

    response = dispatch({
        "operation": "execute-stage",
        "payload": {"profileCode": "GF-6-WFV-L1A", "stageKey": "STANDARDIZE_GF6_WFV_L1A"},
    }, manifest)

    assert response == expected


@pytest.mark.real_sample
def test_registry_returns_logical_uris_without_local_paths(gf_manifest):
    request = {
        "operation": "inspect",
        "requestId": "req-01",
        "payload": {"inputs": [{"storageUriSnapshot": {"value": item["storageUri"]}}
                                 for item in gf_manifest["inputs"]]},
    }
    response = dispatch(request, gf_manifest)
    assert len(response["candidates"]) == 2
    encoded = json.dumps(response)
    assert "asset-manifest" not in encoded
    assert not any(str(Path(item["path"])) in encoded for item in gf_manifest["inputs"])


@pytest.mark.real_sample
def test_registry_builds_the_gf_browse_dag_for_exact_public_parameters(gf_manifest):
    candidate = inspect_manifest(gf_manifest)[0]

    response = dispatch({
        "operation": "build-plan",
        "payload": {
            "candidate": candidate,
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
        },
    }, gf_manifest)

    assert response == {"version": "plan-v1", "stages": [
        {"stageKey": "VALIDATE_BROWSE", "stageCode": "VALIDATE_BROWSE", "dependsOnStageKeys": []},
        {"stageKey": "GEOREFERENCE_BROWSE", "stageCode": "GEOREFERENCE_BROWSE",
         "dependsOnStageKeys": ["VALIDATE_BROWSE"]},
        {"stageKey": "EXTRACT_VISUAL_FEATURES", "stageCode": "EXTRACT_VISUAL_FEATURES",
         "dependsOnStageKeys": ["GEOREFERENCE_BROWSE"]},
        {"stageKey": "WRITE_BROWSE_REPORT", "stageCode": "WRITE_BROWSE_REPORT",
         "dependsOnStageKeys": ["EXTRACT_VISUAL_FEATURES"]},
    ]}


@pytest.mark.real_sample
def test_registry_rejects_any_non_default_gf_browse_operation(gf_manifest):
    candidate = inspect_manifest(gf_manifest)[0]

    with pytest.raises(ValueError, match="UNSUPPORTED_BROWSE_OPERATION"):
        dispatch({
            "operation": "build-plan",
            "payload": {
                "candidate": candidate,
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "BROWSE_VISUAL_FEATURES"}),
            },
        }, gf_manifest)


@pytest.mark.real_sample
def test_registry_writes_report_only_for_the_selected_recognized_gf_package(gf_manifest, tmp_path):
    candidate = inspect_manifest(gf_manifest)[1]
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}
    output_uri = "rs://output/task-101"

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "packageId": candidate["packageId"],
            "stageKey": "WRITE_BROWSE_REPORT",
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
            "outputUri": output_uri,
        },
    }, manifest)

    assert response["workerId"] == "worker-local"
    assert response["status"] == "SUCCEEDED"
    assert len(response["artifacts"]) == 1
    artifact = response["artifacts"][0]
    assert artifact["uri"] == f"{output_uri}/browse-report.json"
    assert artifact["type"] == "BROWSE_REPORT_JSON"
    report = tmp_path / "output" / "browse-report.json"
    content = report.read_bytes()
    assert artifact["sizeBytes"] == len(content)
    assert artifact["checksum"].startswith("sha256:")
    value = json.loads(content)
    assert value["packageId"] == candidate["packageId"]
    assert value["georeferencingMethod"] == "AFFINE_WORLD_FILE_APPROXIMATE"
    assert {"width", "height", "rgbStats"}.issubset(value["image"])
    assert str(Path(gf_manifest["inputs"][0]["path"]).parent) not in content.decode("utf-8")


@pytest.mark.real_sample
@pytest.mark.parametrize("output_uri", [
    r"C:\\worker\\output",
    "/var/worker/output",
    "",
    "file:///var/worker/output",
    "https://example.test/output",
    "rs://output/../escape",
])
def test_registry_rejects_non_logical_output_uris(gf_manifest, tmp_path, output_uri):
    candidate = inspect_manifest(gf_manifest)[0]
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}

    with pytest.raises(ValueError, match="logical output URI"):
        dispatch({
            "operation": "execute-stage",
            "payload": {
                "packageId": candidate["packageId"],
                "stageKey": "WRITE_BROWSE_REPORT",
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
                "outputUri": output_uri,
            },
        }, manifest)


@pytest.mark.real_sample
def test_registry_does_not_infer_another_gf_package_for_stage_execution(gf_manifest, tmp_path):
    manifest = deepcopy(gf_manifest)
    manifest["output"] = {"path": str(tmp_path / "output")}

    with pytest.raises(ValueError, match="recognized GF-6 package"):
        dispatch({
            "operation": "execute-stage",
            "payload": {
                "packageId": "GF6-WFV-LEVEL1A-not-present",
                "stageKey": "VALIDATE_BROWSE",
                "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
                "outputUri": "rs://output/task-101",
            },
        }, manifest)


@pytest.mark.real_sample
def test_registry_infers_the_only_gf_package_for_stage_execution(gf_manifest, tmp_path):
    candidate = inspect_manifest(gf_manifest)[0]
    selected_paths = {str(item["path"]) for item in candidate["files"]}
    manifest = deepcopy(gf_manifest)
    manifest["inputs"] = [item for item in manifest["inputs"] if item["path"] in selected_paths]
    manifest["output"] = {"path": str(tmp_path / "output")}

    response = dispatch({
        "operation": "execute-stage",
        "payload": {
            "stageKey": "VALIDATE_BROWSE",
            "parameterSnapshot": json.dumps({"schemaVersion": "1", "operation": "DEFAULT_PROCESS"}),
            "outputUri": "rs://output/task-101",
        },
    }, manifest)

    assert response["status"] == "SUCCEEDED"
