from affine import Affine
import numpy as np
import pytest

from rs_algorithm_worker.algorithms.features import FeatureParameters, extract_features
from rs_algorithm_worker.algorithms.fusion import FusionParameters, fuse_bands
from rs_algorithm_worker.algorithms.saliency import SaliencyParameters, detect_saliency
from rs_algorithm_worker.models import BandMetadata, BandSet, RasterData


def _bands() -> BandSet:
    return BandSet(RasterData(
        values=np.array([[[1.0, 2.0], [3.0, 4.0]], [[4.0, 3.0], [2.0, 1.0]], [[2.0, 2.0], [2.0, 2.0]]]),
        crs="EPSG:4326", transform=Affine(1, 0, 0, 0, -1, 2), nodata=None,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2"), BandMetadata("B3")),
    ))


def test_feature_baseline_reports_valid_pixel_statistics():
    result = extract_features(_bands().raster, FeatureParameters(metrics=("statistics",)))
    assert result.statistics["validPixelRatio"] == 1.0
    assert {"min", "max", "mean", "std"}.issubset(result.statistics["B1"])


def test_fusion_baseline_stacks_explicit_bands_after_percentile_stretch():
    result = fuse_bands(_bands(), FusionParameters(red="B3", green="B2", blue="B1"))
    assert result.values.shape == (3, 2, 2)
    assert tuple(item.name for item in result.band_metadata) == ("red", "green", "blue")
    assert result.values.dtype == np.uint8


def test_saliency_baseline_returns_score_and_threshold_mask():
    result = detect_saliency(_bands().raster, SaliencyParameters(threshold=0.5, window_size=3))
    assert result.score.values.shape == (2, 2)
    assert result.mask.dtype == np.bool_


def test_feature_baseline_reports_gradient_and_local_variance_when_requested():
    result = extract_features(
        _bands().raster,
        FeatureParameters(metrics=("gradient", "local_variance"), window_size=3),
    )

    assert set(result.rasters) == {
        "gradient:B1", "gradient:B2", "gradient:B3",
        "local_variance:B1", "local_variance:B2", "local_variance:B3",
    }
    assert result.rasters["gradient:B1"].values.shape == (2, 2)


def test_fusion_rejects_a_bandset_without_a_grid():
    ungridded = BandSet(RasterData(
        values=np.ones((3, 2, 2), dtype=np.float32),
        crs=None, transform=None, nodata=None,
        band_metadata=(BandMetadata("B1"), BandMetadata("B2"), BandMetadata("B3")),
    ))

    with pytest.raises(ValueError, match="CRS and transform"):
        fuse_bands(ungridded, FusionParameters(red="B3", green="B2", blue="B1"))


def test_saliency_uses_local_contrast_and_returns_zero_for_constant_image():
    raster = RasterData(
        values=np.array([[5.0, 5.0], [5.0, 5.0]], dtype=np.float32),
        crs="EPSG:4326", transform=Affine(1, 0, 0, 0, -1, 2), nodata=None,
        band_metadata=(BandMetadata("B1"),),
    )

    result = detect_saliency(raster, SaliencyParameters(threshold=0.1, window_size=3))

    assert not result.mask.any()
    np.testing.assert_array_equal(result.score.values, np.zeros((2, 2), dtype=np.float32))
