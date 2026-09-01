from __future__ import annotations

import shutil
from pathlib import Path

import numpy as np
import pytest
import rasterio
from rasterio.transform import from_origin
from rasterio.windows import Window

from rs_algorithm_worker.operators import windowed_cog
from rs_algorithm_worker.operators.windowed_cog import open_windowed_cog


def _profile():
    return {
        "driver": "COG",
        "width": 512,
        "height": 32,
        "count": 1,
        "dtype": "float32",
        "crs": "EPSG:4326",
        "transform": from_origin(100, 10, 0.001, 0.001),
        "nodata": -9999.0,
        "BLOCKSIZE": 256,
        "COMPRESS": "DEFLATE",
        "BIGTIFF": "IF_SAFER",
    }


def test_windowed_cog_writes_through_disk_gtiff_before_final_cog(tmp_path):
    target = tmp_path / "gf6-standard.tif"
    stale_staging = tmp_path / ".gf6-standard.staging-old.tif"
    stale_final = tmp_path / ".gf6-standard.cog-old.tif"
    orphaned_overview = tmp_path / ".gf6-standard.staging-orphan.tif.ovr.tmp"
    nested_orphaned_overview = tmp_path / ".gf6-standard.cog-orphan.tif.ovr.tmp_tmp_ovr.tif"
    stale_staging.write_bytes(b"incomplete")
    stale_final.write_bytes(b"incomplete")
    orphaned_overview.write_bytes(b"incomplete overview")
    nested_orphaned_overview.write_bytes(b"incomplete nested overview")

    with open_windowed_cog(target, _profile()) as destination:
        assert destination.driver == "GTiff"
        destination.write(
            np.full((1, 32, 256), 3.5, dtype=np.float32),
            window=Window(0, 0, 256, 32),
        )
        destination.write(
            np.full((1, 32, 256), 7.5, dtype=np.float32),
            window=Window(256, 0, 256, 32),
        )
        destination.set_band_description(1, "B1")
        destination.update_tags(processing="windowed")

    with rasterio.open(target) as result:
        assert result.tags(ns="IMAGE_STRUCTURE").get("LAYOUT") == "COG"
        assert result.overviews(1) == [2]
        assert result.descriptions == ("B1",)
        assert result.tags()["processing"] == "windowed"
        assert np.all(result.read(1, window=Window(0, 0, 1, 1)) == 3.5)
        assert np.all(result.read(1, window=Window(511, 0, 1, 1)) == 7.5)
    assert sorted(tmp_path.iterdir()) == [target]


def test_windowed_cog_removes_staging_files_when_windowed_write_fails(tmp_path):
    target = tmp_path / "gf6-standard.tif"

    with pytest.raises(RuntimeError, match="simulated write failure"):
        with open_windowed_cog(target, _profile()):
            raise RuntimeError("simulated write failure")

    assert list(tmp_path.iterdir()) == []


def test_windowed_cog_can_publish_a_cog_without_overviews(tmp_path):
    target = tmp_path / "gf6-standard.tif"

    with open_windowed_cog(target, _profile(), overview_max_bytes=0) as destination:
        destination.write(np.ones((1, 32, 512), dtype=np.float32))

    with rasterio.open(target) as result:
        assert result.tags(ns="IMAGE_STRUCTURE").get("LAYOUT") == "COG"
        assert result.overviews(1) == []


@pytest.mark.parametrize(
    ("overview_limit", "expected_overviews"),
    [
        (512 * 32 * np.dtype("float32").itemsize, None),
        (512 * 32 * np.dtype("float32").itemsize - 1, "NONE"),
    ],
)
def test_windowed_cog_selects_overviews_from_uncompressed_size(
    tmp_path, monkeypatch, overview_limit, expected_overviews
):
    target = tmp_path / f"gf6-{overview_limit}.tif"
    captured = {}

    def recording_copy(source, destination, *, driver, **options):
        captured.update(driver=driver, **options)
        shutil.copyfile(source, destination)

    monkeypatch.setattr(windowed_cog, "rasterio_copy", recording_copy)

    with open_windowed_cog(
        target,
        _profile(),
        overview_max_bytes=overview_limit,
    ):
        pass

    assert captured["NUM_THREADS"] == "2"
    assert captured.get("OVERVIEWS") == expected_overviews


def test_windowed_cog_configuration_is_bounded_and_can_disable_overviews(
    tmp_path, monkeypatch
):
    target = tmp_path / "gf6-configured.tif"
    captured = {}

    def recording_copy(source, destination, *, driver, **options):
        captured.update(driver=driver, **options)
        shutil.copyfile(source, destination)

    monkeypatch.setattr(windowed_cog, "rasterio_copy", recording_copy)
    monkeypatch.setenv("MULTISOURCE_COG_NUM_THREADS", "100")
    monkeypatch.setenv("MULTISOURCE_COG_OVERVIEW_MAX_BYTES", "0")

    with open_windowed_cog(target, _profile()):
        pass

    assert captured["NUM_THREADS"] == "8"
    assert captured["OVERVIEWS"] == "NONE"


def test_windowed_cog_without_overviews_remains_a_readable_cog(tmp_path):
    target = tmp_path / "gf6-large-policy.tif"

    with open_windowed_cog(target, _profile(), overview_max_bytes=0) as destination:
        destination.write(np.ones((1, 32, 512), dtype=np.float32))

    with rasterio.open(target) as result:
        assert result.tags(ns="IMAGE_STRUCTURE").get("LAYOUT") == "COG"
        assert result.overviews(1) == []
        assert np.all(result.read(1, window=Window(0, 0, 1, 1)) == 1.0)


def test_windowed_cog_removes_overview_temporaries_when_conversion_fails(
    tmp_path, monkeypatch
):
    target = tmp_path / "gf6-standard.tif"

    def failing_copy(source, destination, *, driver, **options):
        Path(f"{source}.ovr.tmp").write_bytes(b"partial source overview")
        Path(f"{destination}.ovr.tmp").write_bytes(b"partial destination overview")
        Path(f"{destination}.ovr.tmp_tmp_ovr.tif").write_bytes(
            b"partial nested destination overview"
        )
        raise RuntimeError("simulated COG conversion failure")

    monkeypatch.setattr(windowed_cog, "rasterio_copy", failing_copy)

    with pytest.raises(RuntimeError, match="simulated COG conversion failure"):
        with open_windowed_cog(target, _profile()):
            pass

    assert list(tmp_path.iterdir()) == []
