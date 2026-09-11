"""Bounded metadata inspection and PNG tile rendering for task TIFF artifacts."""

from __future__ import annotations

import io
import math
import os
import sys
from pathlib import Path
from typing import Any

import numpy as np


def _configure_geospatial_environment() -> None:
    """Prefer the worker virtualenv's PROJ/GDAL data over host installations."""
    site_packages = Path(sys.prefix) / "Lib" / "site-packages"
    proj_candidates = (
        site_packages / "pyproj" / "proj_dir" / "share" / "proj",
        site_packages / "rasterio" / "proj_data",
    )
    gdal_candidates = (
        site_packages / "rasterio" / "gdal_data",
        site_packages / "pyproj" / "proj_dir" / "share" / "gdal",
    )
    for candidate in proj_candidates:
        if candidate.is_dir():
            os.environ["PROJ_LIB"] = str(candidate)
            break
    for candidate in gdal_candidates:
        if candidate.is_dir():
            os.environ["GDAL_DATA"] = str(candidate)
            break


_configure_geospatial_environment()

import rasterio
from PIL import Image
from rasterio.transform import from_bounds
from rasterio.warp import Resampling, reproject, transform_bounds


TILE_SIZE = 256
MAX_ZOOM = 22
MAX_PNG_BYTES = 512 * 1024
WEB_MERCATOR_HALF_WORLD = 20037508.342789244


def inspect_raster_artifact(
    path: Path, artifact_format: str, artifact_type: str
) -> dict[str, Any]:
    """Return browser-safe raster metadata without exposing the artifact path."""
    del artifact_format
    with rasterio.open(path) as dataset:
        if dataset.crs is None:
            return _disabled("TIFF 未包含可用坐标系")
        authority = dataset.crs.to_epsg()
        if authority is None:
            return _disabled("TIFF 坐标系暂不支持地图查看")
        if (
            dataset.width <= 0
            or dataset.height <= 0
            or dataset.count <= 0
            or dataset.bounds.left >= dataset.bounds.right
            or dataset.bounds.bottom >= dataset.bounds.top
        ):
            return _disabled("TIFF 地理范围无效")
        try:
            bounds = transform_bounds(
                dataset.crs,
                "EPSG:4326",
                dataset.bounds.left,
                dataset.bounds.bottom,
                dataset.bounds.right,
                dataset.bounds.top,
                densify_pts=21,
            )
        except Exception:
            return _disabled("TIFF 地理范围无效")
        if not _valid_wgs84_bounds(bounds):
            return _disabled("TIFF 地理范围无效")
        style, legend = _style_for(dataset.count, artifact_type)
        return {
            "kind": "raster",
            "renderable": True,
            "reason": None,
            "crs": f"EPSG:{authority}",
            "bounds": [float(value) for value in bounds],
            "minZoom": 0,
            "maxZoom": 16,
            "style": style,
            "legend": legend,
        }


def render_raster_tile(
    path: Path,
    *,
    z: int,
    x: int,
    y: int,
    style: dict[str, Any],
) -> bytes:
    """Render one EPSG:3857 web tile as an RGBA PNG."""
    _require_tile_coordinate(z, x, y)
    with rasterio.open(path) as dataset:
        if dataset.crs is None:
            raise ValueError("TIFF 未包含可用坐标系")
        if dataset.crs.to_epsg() is None:
            raise ValueError("TIFF 坐标系暂不支持地图查看")
        rgba = _render_rgba(dataset, z, x, y, style)
    encoded = _encode_png(rgba)
    if len(encoded) > MAX_PNG_BYTES:
        raise ValueError("rendered tile exceeds size limit")
    return encoded


def _disabled(reason: str) -> dict[str, Any]:
    return {
        "kind": "raster",
        "renderable": False,
        "reason": reason,
        "crs": None,
        "bounds": None,
        "minZoom": None,
        "maxZoom": None,
        "style": None,
        "legend": None,
    }


def _style_for(count: int, artifact_type: str) -> tuple[dict[str, Any], dict[str, Any] | None]:
    normalized = artifact_type.upper()
    if count >= 3:
        return {"kind": "rgb", "bands": [1, 2, 3]}, None
    if "MASK" in normalized:
        return (
            {"kind": "mask", "nodataAlpha": 0},
            {
                "kind": "mask",
                "items": [
                    {"value": 0, "color": "transparent"},
                    {"value": 1, "color": "#f4d03f"},
                ],
            },
        )
    if "SCORE" in normalized or "SALIENCY" in normalized:
        return (
            {"kind": "continuous", "palette": "viridis"},
            {"kind": "continuous", "palette": "viridis"},
        )
    return {"kind": "grayscale"}, None


def _valid_wgs84_bounds(bounds: tuple[float, float, float, float]) -> bool:
    west, south, east, north = bounds
    return (
        all(math.isfinite(value) for value in bounds)
        and -180 <= west < east <= 180
        and -90 <= south < north <= 90
    )


def _require_tile_coordinate(z: int, x: int, y: int) -> None:
    if (
        isinstance(z, bool)
        or isinstance(x, bool)
        or isinstance(y, bool)
        or not all(isinstance(value, int) for value in (z, x, y))
        or z < 0
        or z > MAX_ZOOM
        or x < 0
        or y < 0
        or x >= 2**z
        or y >= 2**z
    ):
        raise ValueError("tile coordinate is invalid")


def _render_rgba(
    dataset: rasterio.io.DatasetReader,
    z: int,
    x: int,
    y: int,
    style: dict[str, Any],
) -> np.ndarray:
    kind = style.get("kind")
    if kind == "rgb":
        bands = style.get("bands")
        if bands != [1, 2, 3] or dataset.count < 3:
            raise ValueError("raster RGB bands are invalid")
        rendered = [_render_band(dataset, band, z, x, y, Resampling.bilinear) for band in bands]
        alpha = np.isfinite(rendered[0])
        rgb = np.stack([_stretch(channel) for channel in rendered], axis=-1)
    elif kind == "mask":
        values = _render_band(dataset, 1, z, x, y, Resampling.nearest)
        alpha = np.isfinite(values) & (values != 0)
        rgb = np.zeros((TILE_SIZE, TILE_SIZE, 3), dtype=np.uint8)
        rgb[..., 0] = 244
        rgb[..., 1] = 208
        rgb[..., 2] = 63
    elif kind == "continuous":
        values = _render_band(dataset, 1, z, x, y, Resampling.bilinear)
        alpha = np.isfinite(values)
        rgb = _viridis(_stretch(values))
    elif kind == "grayscale":
        values = _render_band(dataset, 1, z, x, y, Resampling.bilinear)
        alpha = np.isfinite(values)
        channel = _stretch(values)
        rgb = np.stack((channel, channel, channel), axis=-1)
    else:
        raise ValueError("raster style is invalid")
    return np.dstack((rgb, np.where(alpha, 255, 0).astype(np.uint8)))


def _render_band(
    dataset: rasterio.io.DatasetReader,
    band: int,
    z: int,
    x: int,
    y: int,
    resampling: Resampling,
) -> np.ndarray:
    destination = np.full((TILE_SIZE, TILE_SIZE), np.nan, dtype=np.float32)
    west, south, east, north = _mercator_tile_bounds(z, x, y)
    reproject(
        source=rasterio.band(dataset, band),
        destination=destination,
        src_transform=dataset.transform,
        src_crs=dataset.crs,
        src_nodata=dataset.nodata,
        dst_transform=from_bounds(west, south, east, north, TILE_SIZE, TILE_SIZE),
        dst_crs="EPSG:3857",
        dst_nodata=np.nan,
        resampling=resampling,
    )
    return destination


def _mercator_tile_bounds(z: int, x: int, y: int) -> tuple[float, float, float, float]:
    tile_width = (WEB_MERCATOR_HALF_WORLD * 2) / (2**z)
    west = -WEB_MERCATOR_HALF_WORLD + x * tile_width
    east = west + tile_width
    north = WEB_MERCATOR_HALF_WORLD - y * tile_width
    south = north - tile_width
    return west, south, east, north


def _stretch(values: np.ndarray) -> np.ndarray:
    valid = values[np.isfinite(values)]
    if not valid.size:
        return np.zeros(values.shape, dtype=np.uint8)
    low, high = np.percentile(valid, [2, 98])
    if high <= low:
        return np.where(np.isfinite(values), np.clip(values, 0, 255), 0).astype(np.uint8)
    return np.where(
        np.isfinite(values), np.clip((values - low) * 255 / (high - low), 0, 255), 0
    ).astype(np.uint8)


def _viridis(values: np.ndarray) -> np.ndarray:
    anchors = np.array(
        [[68, 1, 84], [59, 82, 139], [33, 145, 140], [94, 201, 98], [253, 231, 37]],
        dtype=np.float32,
    )
    positions = np.linspace(0, 255, len(anchors))
    return np.stack(
        [np.interp(values, positions, anchors[:, index]) for index in range(3)], axis=-1
    ).astype(np.uint8)


def _encode_png(rgba: np.ndarray) -> bytes:
    output = io.BytesIO()
    Image.fromarray(rgba, "RGBA").save(output, format="PNG", optimize=True)
    return output.getvalue()
