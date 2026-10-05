from __future__ import annotations

import base64
import io
import math

import numpy as np
import pytest
import rasterio
from PIL import Image
from rasterio.transform import from_origin

from rs_algorithm_worker.artifact_tiles import (
    inspect_raster_artifact,
    render_raster_tile,
)
from rs_algorithm_worker.contracts import validate_invocation
from rs_algorithm_worker.registry import dispatch


def write_raster(
    path,
    *,
    count: int = 3,
    crs: str | None = "EPSG:4326",
    nodata: int | None = 0,
):
    transform = from_origin(110, 31, 1 / 256, 1 / 256)
    if crs == "EPSG:32650":
        transform = from_origin(500000, 3500000, 100, 100)
    profile = {
        "driver": "GTiff",
        "width": 256,
        "height": 256,
        "count": count,
        "dtype": "uint8",
        "transform": transform,
        "nodata": nodata,
    }
    if crs is not None:
        profile["crs"] = crs
    with rasterio.open(path, "w", **profile) as target:
        values = np.full((count, 256, 256), 80, dtype=np.uint8)
        if count >= 3:
            values[1] = 140
            values[2] = 220
        target.write(values)
    return path


def test_inspect_raster_returns_wgs84_bounds_and_rgb_style(tmp_path):
    result = inspect_raster_artifact(
        write_raster(tmp_path / "rgb.tif"), "COG", "STANDARD_COG"
    )

    assert {key: value for key, value in result.items() if key != "quality"} == {
        "kind": "raster",
        "renderable": True,
        "reason": None,
        "crs": "EPSG:4326",
        "bounds": [110.0, 30.0, 111.0, 31.0],
        "minZoom": 0,
        "maxZoom": 16,
        "style": {"kind": "rgb", "bands": [1, 2, 3]},
        "legend": None,
    }
    assert result["quality"] == {
        "width": 256,
        "height": 256,
        "bandCount": 3,
        "dtype": "uint8",
        "boundsValid": True,
            "validPixelRatio": 1.0,
            "nodataPixelRatio": 0.0,
            "statistics": {"min": 80, "max": 80},
        "hasNanOrInf": False,
    }


def test_inspect_raster_reports_safe_reasons_for_missing_or_unsupported_crs(tmp_path):
    missing_crs = inspect_raster_artifact(
        write_raster(tmp_path / "missing.tif", crs=None), "GEOTIFF", "STANDARD_COG"
    )
    projected_crs = inspect_raster_artifact(
        write_raster(tmp_path / "utm.tif", crs="EPSG:32650"), "COG", "STANDARD_COG"
    )

    assert missing_crs["renderable"] is False
    assert missing_crs["reason"] == "TIFF 未包含可用坐标系"
    assert projected_crs["renderable"] is True
    assert projected_crs["crs"] == "EPSG:32650"
    west, south, east, north = projected_crs["bounds"]
    assert 116 < west < east < 118
    assert 30 < south < north < 33


def test_inspect_raster_accepts_a_geographic_extent_crossing_the_date_line(tmp_path):
    path = tmp_path / "date-line.tif"
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=80,
        height=20,
        count=1,
        dtype="uint8",
        crs="EPSG:4326",
        transform=from_origin(170, 10, 0.25, 1),
        nodata=0,
    ) as target:
        target.write(np.ones((1, 20, 80), dtype=np.uint8))

    result = inspect_raster_artifact(path, "COG", "TRACK_GRID_COG")

    assert result["renderable"] is True
    assert result["bounds"] == [170.0, -10.0, -170.0, 10.0]
    assert result["minZoom"] == 1


def test_render_raster_tile_wraps_date_line_source_data_to_the_western_hemisphere(tmp_path):
    path = tmp_path / "date-line.tif"
    with rasterio.open(
        path,
        "w",
        driver="GTiff",
        width=80,
        height=20,
        count=1,
        dtype="uint8",
        crs="EPSG:4326",
        transform=from_origin(170, 10, 0.25, 1),
        nodata=0,
    ) as target:
        target.write(np.ones((1, 20, 80), dtype=np.uint8))

    tile = render_raster_tile(
        path,
        z=2,
        x=0,
        y=1,
        style={"kind": "grayscale"},
    )

    image = Image.open(io.BytesIO(tile))
    assert image.getchannel("A").getextrema()[1] == 255


def test_inspect_raster_assigns_mask_and_score_styles(tmp_path):
    mask = inspect_raster_artifact(
        write_raster(tmp_path / "mask.tif", count=1), "GEOTIFF", "SALIENCY_MASK_COG"
    )
    score = inspect_raster_artifact(
        write_raster(tmp_path / "score.tif", count=1), "GEOTIFF", "SALIENCY_SCORE_COG"
    )

    assert mask["style"] == {"kind": "mask", "nodataAlpha": 0}
    assert mask["legend"] == {"kind": "mask", "items": [{"value": 0, "color": "transparent"}, {"value": 1, "color": "#f4d03f"}]}
    assert score["style"] == {"kind": "continuous", "palette": "viridis"}
    assert score["legend"] == {"kind": "continuous", "palette": "viridis"}


def test_render_raster_tile_returns_a_256_pixel_rgba_png(tmp_path):
    tile = render_raster_tile(
        write_raster(tmp_path / "rgb.tif"),
        z=8,
        x=206,
        y=104,
        style={"kind": "rgb", "bands": [1, 2, 3]},
    )

    image = Image.open(io.BytesIO(tile))
    assert image.size == (256, 256)
    assert image.mode == "RGBA"
    assert image.getchannel("A").getextrema()[1] == 255


def test_render_raster_tile_reads_only_intersecting_window_for_utm_raster(tmp_path):
    path = write_raster(tmp_path / "utm.tif", crs="EPSG:32650")
    metadata = inspect_raster_artifact(path, "COG", "STANDARD_COG")
    west, south, east, north = metadata["bounds"]
    lon = (west + east) / 2
    lat = (south + north) / 2
    zoom = 8
    dimension = 2**zoom
    x = int((lon + 180) / 360 * dimension)
    y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * dimension)

    tile = render_raster_tile(path, z=zoom, x=x, y=y, style=metadata["style"])

    image = Image.open(io.BytesIO(tile))
    assert image.getchannel("A").getextrema()[1] == 255


def test_render_raster_tile_rejects_invalid_web_tile_coordinates(tmp_path):
    with pytest.raises(ValueError, match="tile coordinate is invalid"):
        render_raster_tile(
            write_raster(tmp_path / "rgb.tif"),
            z=2,
            x=4,
            y=0,
            style={"kind": "rgb", "bands": [1, 2, 3]},
        )


def test_raster_operations_use_the_single_authorized_manifest_artifact(tmp_path):
    source = write_raster(tmp_path / "rgb.tif")
    request, manifest = raster_invocation(source, tmp_path)

    validate_invocation(request, manifest)
    metadata = dispatch(request, manifest)

    assert metadata["renderable"] is True
    tile_request = {
        **request,
        "operation": "render-artifact-tile",
        "payload": {
            **request["payload"],
            "z": 8,
            "x": 206,
            "y": 104,
            "style": metadata["style"],
        },
    }
    validate_invocation(tile_request, manifest)
    tile = dispatch(tile_request, manifest)

    assert tile["contentType"] == "image/png"
    assert Image.open(io.BytesIO(base64.b64decode(tile["pngBase64"]))).size == (256, 256)


def raster_invocation(source, tmp_path):
    uri = "rs://output/7/task-01/output/rgb.tif"
    request = {
        "schemaVersion": "1.0",
        "operation": "inspect-raster-artifact",
        "requestId": "raster-preview-01",
        "payload": {
            "format": "COG",
            "artifactType": "STANDARD_COG",
            "inputs": [{"storageUriSnapshot": {"value": uri}}],
        },
    }
    manifest = {
        "manifestVersion": "1.0",
        "requestId": "raster-preview-01",
        "grantReference": "grant-01",
        "expiresAt": "2099-01-01T00:00:00+00:00",
        "inputs": [{
            "storageUri": uri,
            "role": "ARTIFACT",
            "ordinal": 0,
            "path": str(source),
        }],
        "output": {"path": str(tmp_path / "output")},
    }
    return request, manifest
