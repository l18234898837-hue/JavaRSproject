from affine import Affine
import numpy as np
import pytest

from rs_algorithm_worker.models import BandMetadata, RasterData
from rs_algorithm_worker.operators.align import align_to_reference
from rs_algorithm_worker.operators.crop import crop_grid
from rs_algorithm_worker.operators.reproject import reproject_raster
from rs_algorithm_worker.operators.swath_grid import grid_swath
from rasterio.enums import Resampling


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


def test_reproject_raster_changes_crs_and_retains_band_metadata():
    result = reproject_raster(_raster(), destination_crs="EPSG:3857", resolution=100_000.0)

    assert result.crs == "EPSG:3857"
    assert result.values.ndim == 2
    assert result.band_metadata == _raster().band_metadata
    assert result.transform is not None


def test_align_to_reference_uses_exact_reference_grid():
    reference = RasterData(
        values=np.zeros((3, 3), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(0.5, 0, 120, 0, -0.5, 32),
        nodata=-9999.0,
        band_metadata=(BandMetadata("reference"),),
    )

    result = align_to_reference(_raster(), reference, resampling=Resampling.nearest)

    assert result.values.shape == (3, 3)
    assert result.crs == reference.crs
    assert result.transform == reference.transform


def test_alignment_preserves_declared_nodata_for_uncovered_cells():
    source = RasterData(
        values=np.ones((2, 2), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(1, 0, 120, 0, -1, 32),
        nodata=-9999.0,
        band_metadata=(BandMetadata("B1"),),
    )
    reference = RasterData(
        values=np.zeros((4, 4), dtype=np.float32),
        crs="EPSG:4326",
        transform=Affine(1, 0, 118, 0, -1, 35),
        nodata=-9999.0,
        band_metadata=(BandMetadata("reference"),),
    )

    result = align_to_reference(source, reference)

    assert result.nodata == -9999.0
    assert (result.values == -9999.0).any()


def test_swath_grid_uses_valid_navigation_and_nearest_quality_samples():
    science, quality = grid_swath(
        np.array([[1.0, np.nan], [3.0, 5.0]], dtype=np.float32),
        np.array([[0, 1], [0, 0]], dtype=np.uint32),
        np.array([[120.00, 120.01], [120.00, 120.01]]),
        np.array([[30.01, 30.01], [30.00, 30.00]]),
        target_crs="EPSG:4326", resolution=0.01, continuous_resampling="nearest",
    )

    assert np.isfinite(science.values).any()
    assert set(np.unique(quality.values[quality.values != quality.nodata])).issubset({0, 1})
