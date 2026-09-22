from rs_algorithm_worker.algorithm_evaluation import (
    CATALOG_VERSION,
    build_evaluation_contract,
    metric_catalog,
    observe_artifact_metrics,
)


PROFILE_CODES = (
    "FY-3H-MERSI",
    "FY-4B-AGRI",
    "GF-6-WFV-L1A",
    "GF-6-WFV-BROWSE",
    "HY-1C-CZI",
    "HY-2D-ALTIMETER",
)


def test_every_supported_profile_has_a_versioned_metric_contract():
    for profile_code in PROFILE_CODES:
        contract = build_evaluation_contract(profile_code)
        assert contract["catalogVersion"] == CATALOG_VERSION
        assert contract["profileCode"] == profile_code
        assert contract["metrics"]
        assert contract["scoringStatus"] == "METRICS_DEFINED_ONLY"
        assert all("reference_required" in metric for metric in contract["metrics"])


def test_reference_dependent_metrics_are_explicitly_marked():
    for profile_code in PROFILE_CODES:
        metrics = metric_catalog(profile_code)
        assert any(metric.reference_required for metric in metrics)
        for metric in metrics:
            if metric.reference_required:
                assert metric.source.startswith("reference.")


def test_observation_maps_existing_raster_quality_without_claiming_accuracy():
    result = observe_artifact_metrics(
        "GF-6-WFV-L1A",
        {
            "quality": {
                "boundsValid": True,
                "validPixelRatio": 0.92,
                "hasNanOrInf": False,
                "width": 100,
                "height": 200,
                "bandCount": 8,
            }
        },
    )
    observed = {item["key"]: item for item in result["metrics"]}
    assert result["scoringStatus"] == "OBSERVED_OUTPUT_METRICS_ONLY"
    assert observed["bounds_valid"]["value"] is True
    assert observed["finite_values"]["value"] is True
    assert observed["rpc_geolocation_error"]["status"] == "UNAVAILABLE"


def test_track_observation_uses_existing_quality_fields():
    result = observe_artifact_metrics(
        "HY-2D-ALTIMETER",
        {"quality": {"validRatio": 0.8, "rowCount": 10, "emptyValueRatio": 0.1}},
    )
    observed = {item["key"]: item for item in result["metrics"]}
    assert observed["track_valid_ratio"]["value"] == 0.8
    assert observed["row_count"]["value"] == 10
    assert observed["empty_value_ratio"]["value"] == 0.1
    assert observed["anomaly_precision"]["status"] == "UNAVAILABLE"
