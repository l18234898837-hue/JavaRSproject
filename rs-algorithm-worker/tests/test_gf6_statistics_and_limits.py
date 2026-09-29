from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest
import rasterio
from affine import Affine
from rasterio.transform import from_origin

from gf6_fixture import make_gf6_package
import rs_algorithm_worker.gf6_wfv_pipeline as gf6_pipeline
from rs_algorithm_worker.gf6_processing import detect_gf6_saliency
from rs_algorithm_worker.gf6_wfv_pipeline import (
    _write_features,
    _write_fusion,
    _write_grid_metadata,
    _write_processing_summary,
    _write_saliency,
    _write_standard_cog,
)
from rs_algorithm_worker.operators.gf6_rpc import NominalRpcGrid
from rs_algorithm_worker.product_adapters.gf6_wfv_l1a import inspect_paths, parse_parameters


def _standard_raster(path):
    width = 300
    band_one = (np.float64(100_000_000) + np.arange(width, dtype=np.float64) * 16).astype(np.float32)
    band_two = np.linspace(10, 40, width, dtype=np.float32)
    values = np.repeat(np.stack([band_one, band_two])[:, None, :], 2, axis=1)
    values[0, 1] += np.float32(8.0)
    values[1, 1] += np.float32(1.0)
    values[0, 0, 3] = -9999.0
    values[0, 0, 270] = np.nan
    values[1, 0, 4] = -9999.0
    values[1, 0, 280] = np.nan
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=width,
        height=2,
        count=2,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_origin(100, 10, 0.001, 0.001),
        nodata=-9999.0,
    ) as dataset:
        dataset.write(values)
        dataset.set_band_description(1, "B2_520-590nm")
        dataset.set_band_description(2, "B5_690-730nm")
    return values


def test_gf6_processing_metadata_explains_the_main_chain(tmp_path):
    manifest = make_gf6_package(tmp_path / "input")
    evidence = gf6_pipeline.inspect_evidence(manifest)
    parameters = parse_parameters({
        "targetCrs": "EPSG:4326",
        "resolution": 0.003,
        "bands": [1, 2, 3],
        "roi": [110.0, 20.0, 111.0, 21.0],
    })
    standard = tmp_path / "standard" / "gf6-standard.tif"
    standard.parent.mkdir()

    summary_path = _write_processing_summary(standard, evidence, parameters)
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    assert summary["explainability"]["version"] == "gf6-processing-explainability-v1"
    assert [item["key"] for item in summary["explainability"]["stages"]] == [
        "VALIDATE_GF6_WFV_L1A",
        "MOSAIC_GF6_TILES",
        "GEOLOCATE_GF6_RPC",
        "STANDARDIZE_GF6_PROJECTION",
        "WRITE_GF6_STANDARD_COG",
    ]
    assert summary["explainability"]["parameters"]["roi"] == [110.0, 20.0, 111.0, 21.0]

    grid_path = tmp_path / "grid.json"
    _write_grid_metadata(
        grid_path,
        NominalRpcGrid(
            "EPSG:4326",
            Affine(0.003, 0, 110, 0, -0.003, 21),
            333,
            333,
            (110, 20, 111, 21),
            0.0,
        ),
    )
    grid = json.loads(grid_path.read_text(encoding="utf-8"))
    assert grid["explainability"]["method"] == "nominal-RPC-grid"
    assert grid["explainability"]["heightMode"] == "CONSTANT"


def test_statistics_only_writes_valid_value_carriers_and_stable_per_band_summary(tmp_path):
    standard = tmp_path / "standard.tif"
    values = _standard_raster(standard)
    parameters = {
        "bands": [2, 5],
        "features": {"enabled": True, "metrics": ["statistics"]},
    }

    artifacts = _write_features(standard, tmp_path / "output", parameters)

    feature_path = artifacts[0][1]
    with rasterio.open(feature_path) as feature:
        assert feature.count == 2
        assert feature.descriptions == (
            "statistics:value:B2_520-590nm",
            "statistics:value:B5_690-730nm",
        )
        carried = feature.read()
        assert np.count_nonzero(carried != -9999.0) > 0
        assert carried[0, 0, 10] == values[0, 0, 10]
        assert carried[1, 0, 10] == values[1, 0, 10]
        assert carried[0, 0, 270] == -9999.0
        assert feature.tags()["statisticsRasterSemantics"] == "VALID_STANDARDIZED_VALUE_CARRIER"

    summary = json.loads(artifacts[1][1].read_text(encoding="utf-8"))
    assert summary["statisticsMethod"] == "windowed-chan-population-v1"
    assert summary["nodataPolicy"] == "IGNORE_NODATA_AND_NAN"
    assert summary["explainability"]["version"] == "gf6-feature-explainability-v1"
    assert summary["explainability"]["metricDefinitions"][0]["name"] == "有效像元统计"
    assert summary["explainability"]["outputBands"][0]["meaning"] == "原始有效标准化值载体"
    assert [item["sourceBand"] for item in summary["bandStatistics"]] == ["B2", "B5"]
    assert [item["sourceBandIndex"] for item in summary["bandStatistics"]] == [1, 2]
    assert all("outputBand" not in item for item in summary["bandStatistics"])
    for index, item in enumerate(summary["bandStatistics"]):
        expected = values[index][np.isfinite(values[index]) & (values[index] != -9999.0)].astype(np.float64)
        assert item["count"] == expected.size
        assert item["min"] == pytest.approx(float(expected.min()))
        assert item["max"] == pytest.approx(float(expected.max()))
        assert item["mean"] == pytest.approx(float(expected.mean()), rel=1e-12)
        assert item["std"] == pytest.approx(float(expected.std()), rel=1e-10)


def test_statistics_summary_is_retained_when_raster_metrics_are_mixed(tmp_path):
    standard = tmp_path / "standard.tif"
    values = _standard_raster(standard)

    artifacts = _write_features(
        standard,
        tmp_path / "output",
        {
            "bands": [2, 5],
            "features": {
                "enabled": True,
                "metrics": ["statistics", "gradient"],
            },
        },
    )

    with rasterio.open(artifacts[0][1]) as feature:
        assert feature.count == 2
        assert feature.descriptions == (
            "gradient:B2_520-590nm",
            "gradient:B5_690-730nm",
        )
    summary = json.loads(artifacts[1][1].read_text(encoding="utf-8"))
    assert summary["metrics"] == ["statistics", "gradient"]
    assert [item["name"] for item in summary["explainability"]["metricDefinitions"]] == [
        "有效像元统计",
        "局部梯度",
    ]
    assert summary["explainability"]["parameters"]["windowSize"] == 3
    assert [item["sourceBandIndex"] for item in summary["bandStatistics"]] == [1, 2]
    assert all("outputBand" not in item for item in summary["bandStatistics"])
    assert [item["count"] for item in summary["bandStatistics"]] == [
        int(np.count_nonzero(np.isfinite(band) & (band != -9999.0)))
        for band in values
    ]


def _write_feature_seam_source(path):
    values = np.zeros((1, 3, 258), dtype=np.float32)
    values[:, :, 20:200] = 7.0
    values[:, :, 256:] = 10.0
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=258,
        height=3,
        count=1,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_origin(100, 10, 0.001, 0.001),
        nodata=-9999.0,
    ) as dataset:
        dataset.write(values)
        dataset.set_band_description(1, "B3_630-690nm")
    return values


def test_raster_features_read_halo_across_the_256_pixel_block_boundary(tmp_path):
    standard = tmp_path / "standard.tif"
    _write_feature_seam_source(standard)

    artifacts = _write_features(
        standard,
        tmp_path / "output",
        {
            "bands": [3],
            "features": {
                "enabled": True,
                "metrics": ["gradient", "local_variance"],
                "windowSize": 3,
            },
        },
    )

    with rasterio.open(artifacts[0][1]) as feature:
        gradient, variance = feature.read()
    assert gradient[1, 255] == pytest.approx(5.0)
    assert gradient[1, 256] == pytest.approx(5.0)
    assert variance[1, 255] == pytest.approx(200.0 / 9.0, rel=1e-6)
    assert variance[1, 256] == pytest.approx(200.0 / 9.0, rel=1e-6)


def test_raster_features_keep_the_centre_valid_mask_and_ignore_nodata_neighbours(tmp_path):
    standard = tmp_path / "standard.tif"
    values = _write_feature_seam_source(standard)
    values[0, 1, 100] = -9999.0
    values[0, 1, 120] = np.nan
    with rasterio.open(standard, "r+") as dataset:
        dataset.write(values)

    artifacts = _write_features(
        standard,
        tmp_path / "output",
        {
            "bands": [3],
            "features": {
                "enabled": True,
                "metrics": ["gradient", "local_variance"],
                "windowSize": 3,
            },
        },
    )

    with rasterio.open(artifacts[0][1]) as feature:
        gradient, variance = feature.read()
    assert gradient[1, 100] == -9999.0
    assert variance[1, 100] == -9999.0
    assert gradient[1, 120] == -9999.0
    assert variance[1, 120] == -9999.0
    assert variance[1, 101] == pytest.approx(0.0, abs=1e-6)


def test_all_optional_gf6_cogs_stage_windowed_writes_as_safe_bigtiffs(tmp_path, monkeypatch):
    standard = tmp_path / "standard.tif"
    values = _standard_raster(standard)
    values[np.isnan(values)] = 0.0
    with rasterio.open(standard, "r+") as dataset:
        dataset.write(values)
    create_options = {}
    real_open = rasterio.open

    def recording_open(path, *args, **kwargs):
        mode = args[0] if args else kwargs.get("mode", "r")
        if mode.startswith("w"):
            create_options[Path(path).name] = dict(kwargs)
        return real_open(path, *args, **kwargs)

    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline.rasterio.open", recording_open)
    root = tmp_path / "output"
    _write_features(
        standard,
        root,
        {"bands": [2, 5], "features": {"enabled": True, "metrics": ["gradient"]}},
    )
    _write_fusion(
        standard,
        root,
        {
            "bands": [2, 5],
            "fusion": {"enabled": True, "red": 2, "green": 5, "blue": 2, "percentiles": [2, 98]},
        },
    )
    _write_saliency(
        standard,
        root,
        {
            "bands": [2, 5],
            "saliency": {"enabled": True, "source": "B2", "windowSize": 9, "threshold": 0.95},
        },
    )

    assert len(create_options) == 4
    assert all(options.get("driver") == "GTiff" for options in create_options.values())
    assert all(options.get("BIGTIFF") == "IF_SAFER" for options in create_options.values())
    feature_options = next(
        options
        for name, options in create_options.items()
        if name.startswith(".gf6-features.staging-")
    )
    assert feature_options["interleave"] == "band"


def test_standardization_uses_one_streaming_warp_per_selected_band(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input")
    evidence = inspect_paths([Path(item["path"]) for item in manifest["inputs"]])
    target = tmp_path / "standard" / "gf6-standard.tif"
    target.parent.mkdir()
    parameters = parse_parameters({"resolution": 0.003, "bands": [1, 2, 3]})
    real_reproject = gf6_pipeline.reproject
    real_open_windowed_cog = gf6_pipeline.open_windowed_cog
    destinations = []
    staging_profiles = []
    warp_threads = []

    def recording_reproject(*args, **kwargs):
        destinations.append(args[1] if len(args) > 1 else kwargs["destination"])
        warp_threads.append(kwargs.get("num_threads"))
        return real_reproject(*args, **kwargs)

    def recording_open_windowed_cog(path, profile, **kwargs):
        staging_profiles.append(dict(profile))
        return real_open_windowed_cog(path, profile, **kwargs)

    monkeypatch.setattr(gf6_pipeline, "reproject", recording_reproject)
    monkeypatch.setattr(gf6_pipeline, "open_windowed_cog", recording_open_windowed_cog)

    _write_standard_cog(evidence, target, parameters)

    assert len(destinations) == 3
    assert staging_profiles[0]["interleave"] == "band"
    assert warp_threads == [gf6_pipeline._RPC_WARP_THREADS] * 3
    with rasterio.open(target) as standard:
        assert standard.width > 256
        assert standard.count == 3
        assert standard.descriptions == (
            "B1_450-520nm",
            "B2_520-590nm",
            "B3_630-690nm",
        )


def _write_saliency_seam_source(path):
    rows, columns = np.indices((7, 260), dtype=np.float32)
    values = (
        np.sin(columns / 3.0)
        + np.cos(rows * 0.7)
        + (columns >= 256).astype(np.float32) * 4.0
    ).astype(np.float32)
    values[1, 100] = -9999.0
    values[2, 101] = np.nan
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=values.shape[1],
        height=values.shape[0],
        count=1,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_origin(100, 10, 0.001, 0.001),
        nodata=-9999.0,
    ) as dataset:
        dataset.write(values, 1)
        dataset.set_band_description(1, "B3_630-690nm")
    return values


def test_saliency_halo_matches_a_whole_raster_reference_across_block_boundaries(tmp_path):
    standard = tmp_path / "standard.tif"
    values = _write_saliency_seam_source(standard)
    config = {
        "bands": [3],
        "saliency": {
            "enabled": True,
            "source": "B3",
            "windowSize": 5,
            "threshold": 0.6,
        },
    }

    artifacts = _write_saliency(standard, tmp_path / "output", config)

    valid = np.isfinite(values) & (values != -9999.0)
    expected_score, expected_mask = detect_gf6_saliency(
        np.where(valid, values, np.nan), window_size=5, threshold=0.6
    )
    with rasterio.open(artifacts[0][1]) as score, rasterio.open(artifacts[1][1]) as mask:
        actual_score = score.read(1)
        actual_mask = mask.read(1)
    assert np.allclose(actual_score[valid], expected_score[valid], rtol=1e-6, atol=1e-6)
    assert np.array_equal(actual_mask[valid], expected_mask[valid].astype(np.uint8))
    assert np.all(actual_score[~valid] == -9999.0)
    assert np.array_equal(actual_mask[~valid], np.full(np.count_nonzero(~valid), 255, dtype=np.uint8))

    with rasterio.open(artifacts[1][1]) as mask:
        masked = mask.read(1, masked=True)
        assert mask.nodata == 255
        assert np.all(masked.mask[~valid])
        assert not np.any(masked.mask[valid])
        assert set(np.unique(masked.data[valid])).issubset({0, 1})
        assert np.any(masked.data[valid] == 0)
        assert np.any(masked.data[valid] == 1)

    summary = json.loads(artifacts[2][1].read_text(encoding="utf-8"))
    assert summary["scoreNodata"] == -9999.0
    assert summary["maskNodata"] == 255
    assert summary["maskValues"] == {
        "notSalient": 0,
        "salient": 1,
        "invalid": 255,
    }
    assert summary["validityPolicy"] == "SOURCE_PIXEL_MUST_BE_VALID"


def _write_two_distribution_rgb_source(path):
    base = np.empty(512, dtype=np.float32)
    base[:256] = np.arange(256, dtype=np.float32)
    base[256:] = 1000.0 + np.arange(256, dtype=np.float32)
    values = np.broadcast_to(base, (3, 32, 512)).copy()
    values[:, :, 0] = -9999.0
    values[:, :, 16] = np.nan
    values[0, :, 17] = np.nan
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=512,
        height=32,
        count=3,
        dtype="float32",
        crs="EPSG:4326",
        transform=from_origin(100, 10, 0.001, 0.001),
        nodata=-9999.0,
    ) as dataset:
        dataset.write(values)
        for index in range(1, 4):
            dataset.set_band_description(index, f"B{index}")


def test_fusion_uses_one_global_sampled_stretch_for_different_output_blocks(tmp_path):
    standard = tmp_path / "standard.tif"
    _write_two_distribution_rgb_source(standard)
    parameters = {
        "bands": [1, 2, 3],
        "fusion": {
            "enabled": True,
            "red": 3,
            "green": 2,
            "blue": 1,
            "percentiles": [0, 100],
        },
    }

    artifacts = _write_fusion(standard, tmp_path / "output", parameters)

    with rasterio.open(artifacts[0][1]) as fusion:
        output = fusion.read()
    assert output[:, 0, 0].tolist() == [0, 0, 0]
    assert output[:, 0, 16].tolist() == [0, 0, 0]
    assert output[:, 0, 128].tolist() == [20, 20, 20]
    assert output[:, 0, 384].tolist() == [231, 231, 231]

    summary = json.loads(artifacts[1][1].read_text(encoding="utf-8"))
    assert summary["version"] == "percentile-rgb-v1"
    assert summary["bands"] == {"red": 3, "green": 2, "blue": 1}
    assert summary["stretches"] == [[32.0, 1240.0]] * 3
    assert summary["sampling"]["step"] == 16
    assert summary["sampling"]["validSamplesPerBand"] == {
        "red": 60,
        "green": 60,
        "blue": 60,
    }


def test_fusion_cog_uses_an_internal_validity_mask_without_reserving_dark_zero(tmp_path):
    standard = tmp_path / "standard.tif"
    _write_two_distribution_rgb_source(standard)
    artifacts = _write_fusion(
        standard,
        tmp_path / "output",
        {
            "bands": [1, 2, 3],
            "fusion": {
                "enabled": True,
                "red": 3,
                "green": 2,
                "blue": 1,
                "percentiles": [0, 100],
            },
        },
    )
    fusion_path = artifacts[0][1]

    with rasterio.open(fusion_path) as fusion:
        masked = fusion.read(masked=True)
        dataset_mask = fusion.dataset_mask()
        assert fusion.nodata is None
        assert masked.data[:, 0, 1].tolist() == [0, 0, 0]
        assert not masked.mask[:, 0, 1].any()
        assert masked.mask[:, 0, 0].all()
        assert masked.mask[:, 0, 16].all()
        assert masked.mask[:, 0, 17].all()
        assert dataset_mask[0, 1] == 255
        assert dataset_mask[0, 0] == 0
        assert dataset_mask[0, 16] == 0
        assert dataset_mask[0, 17] == 0
    assert not Path(str(fusion_path) + ".msk").exists()

    summary = json.loads(artifacts[1][1].read_text(encoding="utf-8"))
    assert summary["outputNodata"] is None
    assert summary["validityPolicy"] == "INTERNAL_DATASET_MASK_ALL_RGB_SOURCE_BANDS_VALID"
    assert summary["datasetMaskValues"] == {"invalid": 0, "valid": 255}
    assert summary["pixelCounts"] == {
        "total": 32 * 512,
        "valid": 32 * (512 - 3),
        "invalid": 32 * 3,
        "validRatio": pytest.approx((512 - 3) / 512),
    }


def test_fusion_inherits_an_upstream_internal_source_mask(tmp_path):
    standard = tmp_path / "standard-with-mask.tif"
    _write_two_distribution_rgb_source(standard)
    with rasterio.open(standard, "r+") as source:
        source.write_mask(
            np.where(
                np.indices((source.height, source.width))[1] == 200,
                0,
                255,
            ).astype(np.uint8)
        )

    artifacts = _write_fusion(
        standard,
        tmp_path / "output",
        {
            "bands": [1, 2, 3],
            "fusion": {
                "enabled": True,
                "red": 3,
                "green": 2,
                "blue": 1,
                "percentiles": [0, 100],
            },
        },
    )

    with rasterio.open(artifacts[0][1]) as fusion:
        assert fusion.dataset_mask()[0, 200] == 0
        assert fusion.dataset_mask()[0, 199] == 255
    summary = json.loads(artifacts[1][1].read_text(encoding="utf-8"))
    assert summary["pixelCounts"]["invalid"] == 32 * 4


def test_global_fusion_sampling_is_memory_bounded_at_the_output_pixel_limit():
    plan = gf6_pipeline._fusion_sampling_plan(2_000_000_000, 1)

    assert plan["step"] == 16
    assert plan["baseSampleCountPerBand"] == 125_000_000
    assert plan["thinningStride"] > 1
    assert plan["retainedSampleUpperBoundPerBand"] * np.dtype(np.float32).itemsize <= 32 * 1024**2


def test_fusion_and_saliency_restore_their_band_descriptions(tmp_path):
    standard = tmp_path / "standard.tif"
    _write_two_distribution_rgb_source(standard)
    root = tmp_path / "output"

    fusion_artifacts = _write_fusion(
        standard,
        root,
        {
            "bands": [1, 2, 3],
            "fusion": {
                "enabled": True,
                "red": 3,
                "green": 2,
                "blue": 1,
                "percentiles": [0, 100],
            },
        },
    )
    saliency_artifacts = _write_saliency(
        standard,
        root,
        {
            "bands": [1, 2, 3],
            "saliency": {
                "enabled": True,
                "source": "B2",
                "windowSize": 5,
                "threshold": 0.6,
            },
        },
    )

    with rasterio.open(fusion_artifacts[0][1]) as fusion:
        assert fusion.descriptions == ("red_B3", "green_B2", "blue_B1")
    with rasterio.open(saliency_artifacts[0][1]) as score, rasterio.open(saliency_artifacts[1][1]) as mask:
        assert score.descriptions == ("saliency_score:B2",)
        assert mask.descriptions == ("saliency_mask:B2",)
    fusion_summary = json.loads(fusion_artifacts[1][1].read_text(encoding="utf-8"))
    assert fusion_summary["explainability"]["algorithm"] == "GF-6 WFV RGB 融合"
    assert fusion_summary["explainability"]["parameters"]["stretchMethod"] == "global-sampled-percentile-linear"
    saliency_summary = json.loads(saliency_artifacts[2][1].read_text(encoding="utf-8"))
    assert saliency_summary["explainability"]["algorithm"] == "GF-6 WFV 显著性检测"
    assert saliency_summary["explainability"]["parameters"] == {"windowSize": 5, "threshold": 0.6}
    assert saliency_summary["candidatePixelRatio"] == pytest.approx(
        saliency_summary["candidatePixelCount"] / (32 * 512)
    )


@pytest.mark.parametrize(
    ("parameters", "message"),
    [
        ({"targetCrs": "EPSG:4326", "resolution": 0.000001}, "resolution below 0.00001 degree"),
        ({"targetCrs": "EPSG:32648", "resolution": 0.5}, "resolution below 1 metre"),
        ({"targetCrs": "EPSG:2263", "resolution": 1.0}, "target CRS axes must use degrees or metres"),
    ],
)
def test_parameters_reject_unsafe_resolution_and_non_metric_projected_units(parameters, message):
    with pytest.raises(ValueError, match=rf"GF6_OUTPUT_LIMIT_EXCEEDED: {message}"):
        parse_parameters(parameters)


def test_safe_geographic_and_projected_resolution_combinations_are_retained():
    assert parse_parameters({"targetCrs": "EPSG:4326", "resolution": 0.00015})["resolution"] == 0.00015
    assert parse_parameters({"targetCrs": "EPSG:32648", "resolution": 16})["resolution"] == 16.0


def _full_parameters():
    return parse_parameters(
        {
            "features": {"enabled": True, "metrics": ["statistics", "gradient", "local_variance"], "windowSize": 3},
            "fusion": {"enabled": True, "red": 3, "green": 2, "blue": 1, "percentiles": [2, 98]},
            "saliency": {"enabled": True, "source": "B3", "windowSize": 9, "threshold": 0.95},
        }
    )


def test_pixel_limit_fails_before_vrt_or_cog_is_created(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input")
    evidence = inspect_paths([Path(item["path"]) for item in manifest["inputs"]])
    target = tmp_path / "standard" / "gf6-standard.tif"
    grid = NominalRpcGrid(
        "EPSG:4326",
        Affine(0.00015, 0, 100, 0, -0.00015, 10),
        2_000_000_001,
        1,
        (100, 9, 101, 10),
    )
    writes = []
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline.build_nominal_rpc_grid", lambda *_: grid)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_mosaic_vrt", lambda *_: writes.append("vrt"))

    with pytest.raises(ValueError, match=r"GF6_OUTPUT_LIMIT_EXCEEDED: grid pixels"):
        _write_standard_cog(evidence, target, _full_parameters())

    assert writes == []
    assert not target.exists()
    assert not target.with_name(".gf6-standard-source.vrt").exists()


def test_estimated_derivative_bytes_are_limited_even_below_pixel_cap(tmp_path, monkeypatch):
    manifest = make_gf6_package(tmp_path / "input")
    evidence = inspect_paths([Path(item["path"]) for item in manifest["inputs"]])
    target = tmp_path / "standard" / "gf6-standard.tif"
    grid = NominalRpcGrid(
        "EPSG:4326",
        Affine(0.00015, 0, 100, 0, -0.00015, 10),
        1_800_000_000,
        1,
        (100, 9, 101, 10),
    )
    writes = []
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline.build_nominal_rpc_grid", lambda *_: grid)
    monkeypatch.setattr("rs_algorithm_worker.gf6_wfv_pipeline._write_mosaic_vrt", lambda *_: writes.append("vrt"))

    with pytest.raises(ValueError, match=r"GF6_OUTPUT_LIMIT_EXCEEDED: estimated output bytes"):
        _write_standard_cog(evidence, target, _full_parameters())

    assert writes == []
    assert not target.exists()


def test_output_budget_includes_the_internal_fusion_validity_mask():
    grid = NominalRpcGrid(
        "EPSG:4326",
        Affine(0.00015, 0, 100, 0, -0.00015, 10),
        1_734_000_000,
        1,
        (100, 9, 101, 10),
    )

    with pytest.raises(ValueError, match=r"GF6_OUTPUT_LIMIT_EXCEEDED: estimated output bytes"):
        gf6_pipeline._validate_output_budget(grid, _full_parameters())


def test_output_budget_counts_overviews_for_extreme_aspect_ratios():
    grid = NominalRpcGrid(
        "EPSG:4326",
        Affine(0.00015, 0, 100, 0, -0.00015, 10),
        1_200_000_000,
        1,
        (100, 9, 101, 10),
    )

    with pytest.raises(ValueError, match=r"GF6_OUTPUT_LIMIT_EXCEEDED: estimated output bytes"):
        gf6_pipeline._validate_output_budget(grid, _full_parameters())
