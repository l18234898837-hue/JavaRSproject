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
from rasterio.windows import Window, from_bounds as window_from_bounds


TILE_SIZE = 256
MAX_ZOOM = 22
MAX_PNG_BYTES = 512 * 1024
WEB_MERCATOR_HALF_WORLD = 20037508.342789244
QUALITY_SAMPLE_SIZE = 256


def inspect_raster_artifact(
    path: Path, artifact_format: str, artifact_type: str
) -> dict[str, Any]:
    """Return browser-safe raster metadata without exposing the artifact path."""
    del artifact_format
    with rasterio.open(path) as dataset:
        quality = _raster_quality(dataset)
        if dataset.crs is None:
            quality["boundsValid"] = False
            return _disabled("TIFF 未包含可用坐标系", quality)
        authority = dataset.crs.to_epsg()
        if authority is None:
            quality["boundsValid"] = False
            return _disabled("TIFF 坐标系暂不支持地图查看", quality)
        if (
            dataset.width <= 0
            or dataset.height <= 0
            or dataset.count <= 0
            or dataset.bounds.left >= dataset.bounds.right
            or dataset.bounds.bottom >= dataset.bounds.top
        ):
            quality["boundsValid"] = False
            return _disabled("TIFF 地理范围无效", quality)
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
            quality["boundsValid"] = False
            return _disabled("TIFF 地理范围无效", quality)
        if not _valid_wgs84_bounds(bounds):
            quality["boundsValid"] = False
            return _disabled("TIFF 地理范围无效", quality)
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
            "quality": quality,
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


def _disabled(reason: str, quality: dict[str, Any]) -> dict[str, Any]:
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
        "quality": quality,
    }


def _raster_quality(dataset: rasterio.io.DatasetReader) -> dict[str, Any]:
    """Summarize a fixed-size overview window without scanning the raster."""
    height = min(dataset.height, QUALITY_SAMPLE_SIZE)
    width = min(dataset.width, QUALITY_SAMPLE_SIZE)
    values = dataset.read(
        indexes=1,
        out_shape=(height, width),
        masked=True,
        resampling=Resampling.nearest,
    )
    raw = np.asanyarray(values.data)
    finite = (
        np.isfinite(raw)
        if np.issubdtype(raw.dtype, np.inexact)
        else np.ones(raw.shape, dtype=bool)
    )
    valid = finite & ~np.ma.getmaskarray(values)
    valid_values = raw[valid]
    # ``masked=True`` represents nodata/outside-coverage pixels in the mask.
    # Do not report those expected gaps as NaN/Inf corruption: inspect only
    # unmasked samples, which are the values users can actually interpret.
    has_nonfinite = bool(
        np.issubdtype(raw.dtype, np.inexact) and np.any(~np.isfinite(raw[~np.ma.getmaskarray(values)]))
    )
    masked_ratio = float(np.ma.getmaskarray(values).sum() / values.size) if values.size else 0.0
    statistics: dict[str, float | int] = {}
    if valid_values.size:
        statistics = {
            "min": _json_number(valid_values.min()),
            "max": _json_number(valid_values.max()),
        }
    return {
        "width": dataset.width,
        "height": dataset.height,
        "bandCount": dataset.count,
        "dtype": dataset.dtypes[0] if len(set(dataset.dtypes)) == 1 else list(dataset.dtypes),
        "boundsValid": True,
        "validPixelRatio": float(valid.sum() / valid.size) if valid.size else 0.0,
        "nodataPixelRatio": masked_ratio,
        "statistics": statistics,
        "hasNanOrInf": has_nonfinite,
    }


def _json_number(value: Any) -> float | int:
    numeric = value.item() if isinstance(value, np.generic) else value
    return int(numeric) if isinstance(numeric, (int, np.integer)) else float(numeric)


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
    # Sparse feature rasters can collapse to almost-transparent overview
    # tiles with bilinear interpolation. Max pooling keeps any valid source
    # signal represented at low zoom while detailed tiles retain smooth RGB.
    overview_resampling = Resampling.max if z <= 8 else Resampling.bilinear
    if kind == "rgb":
        bands = style.get("bands")
        if bands != [1, 2, 3] or dataset.count < 3:
            raise ValueError("raster RGB bands are invalid")
        rendered = [
            _render_band(dataset, band, z, x, y, overview_resampling)
            for band in bands
        ]
        # RGB COGs use 0 as a storage nodata sentinel, but 0 is also a valid
        # stretched colour value.  The renderer already receives NaN for
        # pixels outside the source footprint, so alpha must follow finiteness
        # rather than the numeric nodata value here.
        alpha = np.logical_and.reduce([np.isfinite(channel) for channel in rendered])
        rgb = np.stack([_stretch(channel) for channel in rendered], axis=-1)
    elif kind == "mask":
        values = _render_band(dataset, 1, z, x, y, Resampling.nearest)
        alpha = np.isfinite(values) & (values != 0)
        rgb = np.zeros((TILE_SIZE, TILE_SIZE, 3), dtype=np.uint8)
        rgb[..., 0] = 244
        rgb[..., 1] = 208
        rgb[..., 2] = 63
    elif kind == "continuous":
        values = _render_band(dataset, 1, z, x, y, overview_resampling)
        alpha = np.isfinite(values)
        rgb = _viridis(_stretch(values))
    elif kind == "grayscale":
        values = _render_band(dataset, 1, z, x, y, overview_resampling)
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

    # A task TIFF can be tens of thousands of pixels wide. Reading the full
    # source for every 256px web tile makes low zoom requests unnecessarily
    # expensive and can leave Cesium with an apparently empty layer. Restrict
    # the source read to the part that intersects this Web Mercator tile.
    source_window = _source_window_for_tile(dataset, west, south, east, north)
    if source_window is None:
        return destination
    source = dataset.read(band, window=source_window, out_dtype="float32")
    source_transform = dataset.window_transform(source_window)
    reproject(
        source=source,
        destination=destination,
        src_transform=source_transform,
        src_crs=dataset.crs,
        src_nodata=dataset.nodata,
        dst_transform=from_bounds(west, south, east, north, TILE_SIZE, TILE_SIZE),
        dst_crs="EPSG:3857",
        dst_nodata=np.nan,
        resampling=resampling,
    )
    return destination


def _source_window_for_tile(
    dataset: rasterio.io.DatasetReader,
    west: float,
    south: float,
    east: float,
    north: float,
) -> Window | None:
    """Return the clipped source pixel window intersecting a 3857 tile."""
    try:
        source_bounds = transform_bounds(
            "EPSG:3857", dataset.crs, west, south, east, north, densify_pts=21
        )
        requested = window_from_bounds(*source_bounds, transform=dataset.transform)
        full = Window(0, 0, dataset.width, dataset.height)
        clipped = requested.intersection(full)
    except (ValueError, TypeError, rasterio.errors.RasterioError):
        return None
    if clipped.width <= 0 or clipped.height <= 0:
        return None
    return clipped


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
