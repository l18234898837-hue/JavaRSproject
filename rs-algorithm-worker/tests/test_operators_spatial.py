from affine import Affine
import numpy as np
import pytest

from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.operators.crop import crop_grid


def _raster() -> RasterData:
    return RasterData(
        values=np.arange(16, dtype=np.float32).reshape(4, 4),
        crs="EPSG:4326",
        transform=Affine(1, 0, 120, 0, -1, 34),
        nodata=np.nan,
        band_metadata=(BandMetadata("B1"),),
    )


def test_crop_grid_returns_intersecting_pixels_and_window_transform():
    result = crop_grid(_raster(), roi=(121, 31, 123, 33))

    assert result.values.shape == (2, 2)
    assert result.values.tolist() == [[5.0, 6.0], [9.0, 10.0]]
    assert result.transform == Affine(1, 0, 121, 0, -1, 33)


def test_crop_grid_rejects_raster_without_grid_metadata():
    with pytest.raises(ValueError, match="CRS and transform"):
        crop_grid(
            RasterData(np.ones((2, 2)), None, None, None, (BandMetadata("B1"),)),
            roi=(0, 0, 1, 1),
        )


def test_crop_grid_preserves_band_first_shape():
    source = RasterData(
        values=np.ones((2, 4, 4), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(1, 0, 120, 0, -1, 34),
        nodata=-9999.0,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2")),
    )

    result = crop_grid(source, roi=(120, 30, 122, 32))

    assert result.values.shape == (2, 2, 2)
