import numpy as np
import pytest

from rs_algorithm_worker.models import BandMetadata, RasterData, SwathData


def test_raster_data_rejects_band_metadata_shape_mismatch():
    with pytest.raises(ValueError, match="band metadata"):
        RasterData(
            values=np.zeros((2, 3, 4), dtype=np.float32),
            crs="EPSG:4326",
            transform=None,
            nodata=None,
            band_metadata=(BandMetadata("B1"),),
        )


def test_swath_data_requires_matching_value_and_coordinate_shapes():
    with pytest.raises(ValueError, match="shape"):
        SwathData(
            values=np.zeros((2, 2), dtype=np.float32),
            longitude=np.zeros((2, 3), dtype=np.float32),
            latitude=np.zeros((2, 2), dtype=np.float32),
            metadata={},
        )

