import numpy as np
import pytest

from rs_algorithm_worker.capabilities import CapabilityRegistry, CapabilityStatus, CapabilitySpec
from rs_algorithm_worker.models import ProductContext
from rs_algorithm_worker.operators.registry import OperatorRegistry


def test_operator_registry_rejects_unknown_version():
    registry = OperatorRegistry()
    with pytest.raises(KeyError, match="operator version"):
        registry.resolve("calibration", "does-not-exist")


def test_capability_registry_reports_unsupported_data_shape():
    registry = CapabilityRegistry([
        CapabilitySpec(
            name="fusion",
            version="percentile-rgb-v1",
            supported_shapes=frozenset({"RASTER_GRID"}),
            required_bands=(),
        )
    ])
    result = registry.check(
        "fusion",
        "percentile-rgb-v1",
        ProductContext(profile="HY-2D-ALTIMETER/v1", data_shape="ALONG_TRACK_OBSERVATION"),
        available_bands=(),
    )
    assert result.status is CapabilityStatus.NOT_SUPPORTED
    assert "data shape" in result.reason

