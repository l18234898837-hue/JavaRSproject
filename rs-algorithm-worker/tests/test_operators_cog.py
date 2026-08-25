from affine import Affine
import numpy as np
import pytest

from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.operators.cog import write_cog
import rasterio


def test_write_cog_writes_readable_tiled_raster_with_metadata(tmp_path):
    source = RasterData(
        values=np.arange(32 * 32, dtype=np.float32).reshape(32, 32),
        crs="EPSG:4326",
        transform=Affine(0.01, 0, 120, 0, -0.01, 31),
        nodata=-9999.0,
        band_metadata=(BandMetadata("B1", 460.0, "1/sr"),),
    )

    result = write_cog(source, tmp_path / "standard.tif")

    with rasterio.open(result.path) as dataset:
        assert dataset.driver == "GTiff"
        assert dataset.crs.to_string() == "EPSG:4326"
        assert dataset.count == 1
        assert dataset.descriptions == ("B1",)
        assert dataset.nodata == -9999.0
        assert dataset.block_shapes == [(128, 128)]
    assert result.sha256
    assert result.valid_pixel_count == 1024


def test_write_cog_rejects_target_below_missing_parent(tmp_path):
    source = RasterData(
        values=np.ones((32, 32), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(1, 0, 0, 0, -1, 32),
        nodata=None,
        band_metadata=(BandMetadata("B1"),),
    )

    with pytest.raises(ValueError, match="parent directory"):
        write_cog(source, tmp_path / "missing" / "standard.tif")


def test_write_cog_preserves_band_order(tmp_path):
    source = RasterData(
        values=np.ones((2, 32, 32), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(1, 0, 0, 0, -1, 32),
        nodata=None,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2")),
    )

    result = write_cog(source, tmp_path / "two-band.tif")

    with rasterio.open(result.path) as dataset:
        assert dataset.descriptions == ("B1", "B2")
