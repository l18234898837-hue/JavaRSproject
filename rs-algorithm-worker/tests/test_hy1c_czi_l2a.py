import pytest

from rs_algorithm_worker.l2a_profile import parse_l2a_parameters
from rs_algorithm_worker.product_adapters.hy1c_czi_l2a import (
    build_l2a_plan,
    inspect_l2a_manifest,
    load_reviewed_mapping,
    validate_source_against_mapping,
)
from rs_algorithm_worker.registry import dispatch


def test_reviewed_l2a_mapping_declares_bands_navigation_and_quality():
    mapping = load_reviewed_mapping()

    assert tuple(mapping.band_ids) == ("B460", "B560", "B650", "B825")
    assert mapping.longitude_path.startswith("/")
    assert mapping.latitude_path.startswith("/")
    assert mapping.quality_flags_path.startswith("/")
    assert mapping.default_bad_flag_names


@pytest.mark.real_sample
def test_l2a_sample_conforms_to_reviewed_mapping(hy1c_l2a_manifest):
    main = next(item["path"] for item in hy1c_l2a_manifest["inputs"] if item["role"] == "MAIN")
    validate_source_against_mapping(main, load_reviewed_mapping())


def test_l2a_parameters_resolve_auto_utm_and_preserve_nearest_resampling():
    result = parse_l2a_parameters({
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"}, "algorithms": {},
    }, centre_longitude=120.0, centre_latitude=30.0)

    assert result.resolved_crs == "EPSG:32651"
    assert result.resolution_meters == 50
    assert result.science_resampling == "nearest"


def test_l2a_rejects_fusion_band_not_selected():
    with pytest.raises(ValueError, match="fusion bands"):
        parse_l2a_parameters({
            "schemaVersion": "1", "operation": "STANDARDIZE_L2A", "bands": ["B460", "B560"],
            "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
            "quality": {"policy": "default"},
            "algorithms": {"fusion": {"enabled": True, "red": "B650", "green": "B560", "blue": "B460"}},
        }, centre_longitude=120.0, centre_latitude=30.0)


@pytest.mark.real_sample
def test_l2a_adapter_selects_l2a_and_builds_standardization_dag(hy1c_l2a_manifest):
    candidate = inspect_l2a_manifest(hy1c_l2a_manifest)
    plan = build_l2a_plan(candidate, {
        "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
        "bands": ["B460", "B560", "B650", "B825"],
        "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
        "quality": {"policy": "default"}, "algorithms": {},
    })

    assert candidate["profileKey"] == {"productCode": "HY-1C-CZI", "profileVersion": "L2A-v1"}
    assert [stage["stageCode"] for stage in plan["stages"][:6]] == [
        "VALIDATE_SOURCE", "DECODE_SCALE_AND_NODATA", "APPLY_L2_FLAGS",
        "GEOLOCATE_AND_GRID", "CROP_OPTIONAL", "WRITE_STANDARD_COG",
    ]


@pytest.mark.real_sample
def test_registry_routes_selected_l2a_candidate_to_the_l2a_plan(hy1c_l2a_manifest):
    candidate = inspect_l2a_manifest(hy1c_l2a_manifest)
    response = dispatch({
        "operation": "build-plan",
        "payload": {
            "candidate": candidate,
            "parameterSnapshot": {
                "schemaVersion": "1", "operation": "STANDARDIZE_L2A",
                "bands": ["B460", "B560", "B650", "B825"],
                "grid": {"targetCrs": "AUTO_UTM", "resolutionMeters": 50, "scienceResampling": "nearest"},
                "quality": {"policy": "default"}, "algorithms": {},
            },
        },
    }, hy1c_l2a_manifest)

    assert response["version"] == "plan-v2"
    assert response["stages"][0]["stageKey"] == "VALIDATE_SOURCE"
