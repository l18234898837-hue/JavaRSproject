from __future__ import annotations

import json
from pathlib import Path

import pytest

from gf6_fixture import make_gf6_package
from rs_algorithm_worker.product_adapters.gf6_wfv_l1a import (
    build_integrated_plan,
    inspect_manifest,
    parse_parameters,
)
from rs_algorithm_worker.models_gf6 import Gf6BandSpec, Gf6CalibrationSpec


def test_inspection_requires_ordered_main_rpb_and_retains_tile_coverage_delta(tmp_path):
    product = inspect_manifest(make_gf6_package(tmp_path))[0]

    assert product["profileKey"] == {"productCode": "GF-6-WFV-L1A", "profileVersion": "v1"}
    assert product["dataShape"] == "RASTER_GRID"
    assert [(item["role"], item["ordinal"]) for item in product["files"][:3]] == [
        ("MAIN_TIFF_TILE", 0), ("MAIN_TIFF_TILE", 1), ("MAIN_TIFF_TILE", 2),
    ]
    assert product["metadataSnapshot"]["declaredDimensions"] == [6, 2]
    assert product["metadataSnapshot"]["tileCoverageDimensions"] == [6, 2]
    assert product["metadataSnapshot"]["tileCoverageDelta"] == [0, 0]


def test_uuid_physical_names_use_manifest_original_names_for_inspect_and_build_plan(tmp_path):
    manifest = make_gf6_package(tmp_path)
    for index, item in enumerate(manifest["inputs"]):
        source = Path(item["path"])
        physical = source.with_name(f"00000000-0000-0000-0000-{index:012d}{source.suffix.lower()}")
        source.rename(physical)
        item["path"] = str(physical)
        item["originalFileName"] = source.name

    product = inspect_manifest(manifest)[0]
    plan = build_integrated_plan(product)

    assert [item["originalFileName"] for item in product["files"][:3]] == [
        "GF6_WFV_SAMPLE-1.tif", "GF6_WFV_SAMPLE-2.tif", "GF6_WFV_SAMPLE-3.tif",
    ]
    assert plan["stages"][0]["stageKey"] == "STANDARDIZE_GF6_WFV_L1A"


def test_parameters_are_strict_and_pin_zero_constant_rpc_height():
    assert parse_parameters({"resolution": 0.01}) == {
        "schemaVersion": "gf6-wfv-l1a-v1", "targetCrs": "EPSG:4326", "resolution": 0.01,
        "rpcHeightMode": "CONSTANT", "rpcHeight": 0.0, "roi": None, "bands": list(range(1, 9)),
        "features": {"enabled": False, "metrics": ["statistics"]},
        "fusion": {"enabled": False, "red": 3, "green": 2, "blue": 1, "percentiles": [2.0, 98.0]},
        "saliency": {"enabled": False, "source": "B3", "windowSize": 9, "threshold": 0.95},
    }
    with pytest.raises(ValueError, match="GF6_PARAMETER_INVALID: rpcHeight"):
        parse_parameters({"rpcHeight": 1})
    with pytest.raises(ValueError, match="GF6_PARAMETER_INVALID: features"):
        parse_parameters({"features": {"enabled": True}})
    with pytest.raises(ValueError, match="GF6_PARAMETER_INVALID: unknown"):
        parse_parameters({"unknown": True})


def test_integrated_plan_has_one_stage_raw_inputs_and_exact_enabled_finals(tmp_path):
    product = inspect_manifest(make_gf6_package(tmp_path))[0]
    plan = build_integrated_plan(product, {"features": {"enabled": True, "metrics": ["gradient"]}})

    assert [stage["stageKey"] for stage in plan["stages"]] == ["STANDARDIZE_GF6_WFV_L1A"]
    stage = plan["stages"][0]
    assert [item["artifactType"] for item in stage["inputs"]] == [
        "MAIN_TIFF_TILE", "MAIN_TIFF_TILE", "MAIN_TIFF_TILE", "METADATA_XML", "RPC_RPB", "NAV", "TILE_INDEX_TIL"
    ]
    assert {item["artifactType"] for item in stage["outputs"]} == {
        "GF6_STANDARD_COG", "GF6_PROCESSING_SUMMARY_JSON", "GF6_FEATURES_COG", "GF6_FEATURES_SUMMARY_JSON"
    }
    assert json.loads(stage["parameterSnapshot"])["rpcHeight"] == 0.0


def test_optional_footprint_sidecars_are_retained_and_declared_optional(tmp_path):
    manifest = make_gf6_package(tmp_path)
    for suffix in (".shp", ".dbf", ".shx"):
        (tmp_path / f"GF6_WFV_SAMPLE{suffix}").write_bytes(b"footprint")
    product = inspect_manifest(make_gf6_package(tmp_path))[0]
    plan = build_integrated_plan(product)

    assert [(item["role"], item["ordinal"]) for item in product["files"][-3:]] == [
        ("FOOTPRINT_SHP", 0), ("FOOTPRINT_DBF", 0), ("FOOTPRINT_SHX", 0),
    ]
    assert [(item["artifactType"], item["required"]) for item in plan["stages"][0]["inputs"][-3:]] == [
        ("FOOTPRINT_SHP", False), ("FOOTPRINT_DBF", False), ("FOOTPRINT_SHX", False),
    ]


@pytest.mark.parametrize("raw", [
    {"targetCrs": "not-a-crs"}, {"rpcHeight": True}, {"fusion": {"enabled": True, "red": True, "green": 2, "blue": 1, "percentiles": [2, 98]}},
    {"fusion": {"enabled": True, "red": 3, "green": 2, "blue": 1, "percentiles": [float("nan"), 98]}},
    {"saliency": {"enabled": True, "source": 3, "windowSize": 9, "threshold": .5}},
    {"features": {"enabled": True, "metrics": ["gradient", "gradient"]}},
])
def test_parameters_reject_malformed_nested_values_with_value_error(raw):
    with pytest.raises(ValueError, match="GF6_PARAMETER_INVALID"):
        parse_parameters(raw)


def test_parameters_canonicalize_crs_and_allow_local_variance_window_size():
    value = parse_parameters({"targetCrs": "epsg:4326", "features": {"enabled": True, "metrics": ["local_variance"], "windowSize": 5}})
    assert value["targetCrs"] == "EPSG:4326"
    assert value["features"]["windowSize"] == 5


@pytest.mark.parametrize(
    ("parameters", "field"),
    [
        (
            {"features": {"enabled": True, "metrics": ["local_variance"], "windowSize": 257}},
            "features.windowSize",
        ),
        (
            {"saliency": {"enabled": True, "source": "B3", "windowSize": 257, "threshold": 0.5}},
            "saliency.windowSize",
        ),
    ],
)
def test_parameters_reject_neighbourhood_windows_above_255(parameters, field):
    with pytest.raises(ValueError, match=rf"GF6_PARAMETER_INVALID: {field}"):
        parse_parameters(parameters)


def test_evidence_rejects_tile_rpb_and_til_order_and_partial_footprint(tmp_path):
    manifest = make_gf6_package(tmp_path / "tile-rpb")
    main = tmp_path / "tile-rpb" / "GF6_WFV_SAMPLE.rpb"
    tile_rpb = tmp_path / "tile-rpb" / "GF6_WFV_SAMPLE-1.rpb"
    main.rename(tile_rpb)
    for item in manifest["inputs"]:
        if item["path"] == str(main): item["path"] = str(tile_rpb)
    with pytest.raises(ValueError, match="main basename RPC"):
        inspect_manifest(manifest)

    manifest = make_gf6_package(tmp_path / "til-order")
    til = tmp_path / "til-order" / "GF6_WFV_SAMPLE.til"
    til.write_text(til.read_text(encoding="utf-8").replace("GF6_WFV_SAMPLE-1.tif", "GF6_WFV_SAMPLE-X.tif", 1), encoding="utf-8")
    with pytest.raises(ValueError, match="TIL tile order"):
        inspect_manifest(manifest)

    manifest = make_gf6_package(tmp_path / "partial-footprint")
    shape = tmp_path / "partial-footprint" / "GF6_WFV_SAMPLE.shp"
    shape.write_bytes(b"one")
    manifest["inputs"].append({"path": str(shape)})
    with pytest.raises(ValueError, match="all-or-none"):
        inspect_manifest(manifest)


def test_evidence_records_xml_vs_til_coverage_mismatch(tmp_path):
    manifest = make_gf6_package(tmp_path)
    xml = tmp_path / "GF6_WFV_SAMPLE.xml"
    xml.write_text(xml.read_text(encoding="utf-8").replace("<WidthInPixels>6</WidthInPixels>", "<WidthInPixels>10</WidthInPixels>"), encoding="utf-8")
    metadata = inspect_manifest(manifest)[0]["metadataSnapshot"]
    assert metadata["declaredDimensions"] == [10, 2]
    assert metadata["tileCoverageDimensions"] == [6, 2]
    assert metadata["tileCoverageDelta"] == [-4, 0]


def test_evidence_ignores_non_product_xml_sidecars(tmp_path):
    manifest = make_gf6_package(tmp_path)
    (tmp_path / "order.xml").write_text("<Order><id>not-product-metadata</id></Order>", encoding="utf-8")
    manifest["inputs"].append({"path": str(tmp_path / "order.xml")})
    assert inspect_manifest(manifest)[0]["metadataSnapshot"]["productId"] == "SAMPLE"


def test_calibration_model_requires_all_eight_ordered_bands():
    bands = tuple(Gf6BandSpec(index, "visible", 1.0, 0.0) for index in range(1, 9))
    assert Gf6CalibrationSpec(bands).formula == "AGain*DN+AOffset"
