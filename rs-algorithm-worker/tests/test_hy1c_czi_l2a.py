import pytest
import h5py
import numpy as np

from rs_algorithm_worker.l2a_profile import parse_l2a_parameters
from rs_algorithm_worker.product_adapters.hy1c_czi_l2a import (
    build_l2a_plan,
    inspect_l2a_manifest,
    load_reviewed_mapping,
    validate_source_against_mapping,
)
from rs_algorithm_worker.registry import dispatch
from rs_algorithm_worker.l2a_pipeline import execute_l2a_stage


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


@pytest.fixture
def synthetic_l2a_h5(tmp_path):
    path = tmp_path / "synthetic-l2a.h5"
    with h5py.File(path, "w") as handle:
        handle.attrs["Satellite Name"] = "HY-1C"
        handle.attrs["Sensor Name"] = "CZI"
        handle.attrs["Number of Bands"] = 4
        geo = handle.create_group("Geophysical Data")
        for wavelength in (460, 560, 650, 825):
            dataset = geo.create_dataset(f"Rrc{wavelength}", data=np.array([[np.nan, 0.02], [0.2, 0.03]], dtype=np.float32))
            dataset.attrs.update({"Slope": 1.0, "Offset": 0.0, "FillValue": np.nan, "ValidMin": -0.015, "ValidMax": 0.115})
        flags = geo.create_dataset("l2_flags", data=np.array([[1, 0], [0, 0]], dtype=np.uint32))
        flags.attrs["Flag Names"] = b"ATMFAIL,LAND,BADANC,HISUNGLINT,HILT,HISENZ,SPARE,NEGLW,STLIGHT,CLOUD,SPARE,TURBID,HISOLZ,HITAUA,LOWLW,CHLFAIL,SPARE,ABSAER,SPARE,MAXAERITER,MODSUNGLINT,CHLRANGE,ATMWARN,DARKPIXEL,SEAICE,NAVFAIL,FILTER,BADSST,BADSSTALG,SPARE,SPARE,SPARE"
        nav = handle.create_group("Navigation Data")
        for name, values in (("Longitude", [[1200000, 1200100], [1200000, 1200100]]), ("Latitude", [[300100, 300100], [300000, 300000]])):
            dataset = nav.create_dataset(name, data=np.array(values, dtype=np.int32))
            dataset.attrs.update({"Slope": 0.0001, "Offset": 0.0, "ValidMin": -180.0 if name == "Longitude" else -90.0, "ValidMax": 180.0 if name == "Longitude" else 90.0})
        sensor = handle.create_group("Sensor Band parameters")
        sensor.create_dataset("Wavelength", data=np.array([[460], [560], [650], [825]], dtype=np.int32))
    return path


def test_decode_masks_fill_and_out_of_range_values(synthetic_l2a_h5, tmp_path):
    result = execute_l2a_stage("DECODE_SCALE_AND_NODATA", [synthetic_l2a_h5], {}, tmp_path)

    assert np.isnan(result.raster.values[:, 0, 0]).all()
    assert np.isnan(result.raster.values[:, 1, 0]).all()
    assert np.isfinite(result.raster.values[:, 0, 1]).all()


def test_quality_stage_applies_reviewed_bad_bits(synthetic_l2a_h5, tmp_path):
    decoded = execute_l2a_stage("DECODE_SCALE_AND_NODATA", [synthetic_l2a_h5], {}, tmp_path)
    result = execute_l2a_stage("APPLY_L2_FLAGS", [decoded, synthetic_l2a_h5], {}, tmp_path)

    assert not result.valid_mask[0, 0]
    assert result.valid_mask[0, 1]
