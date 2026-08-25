from rs_algorithm_worker.capabilities import COMMON_CAPABILITIES, CapabilityStatus
from rs_algorithm_worker.models import ProductContext


def test_common_raster_capabilities_expose_supported_baselines():
    result = COMMON_CAPABILITIES.check(
        "fusion",
        "percentile-rgb-v1",
        ProductContext(profile="TEST/v1", data_shape="RASTER_GRID"),
        available_bands=("B1", "B2", "B3"),
    )

    assert result.status is CapabilityStatus.SUPPORTED


def test_common_fusion_requires_three_declared_input_bands():
    result = COMMON_CAPABILITIES.check(
        "fusion",
        "percentile-rgb-v1",
        ProductContext(profile="TEST/v1", data_shape="RASTER_GRID"),
        available_bands=("B1", "B2"),
    )

    assert result.status is CapabilityStatus.NOT_SUPPORTED
    assert "B3" in result.reason
