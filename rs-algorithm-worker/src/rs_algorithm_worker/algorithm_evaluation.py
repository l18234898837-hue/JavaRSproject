"""Versioned algorithm-evaluation metric contracts.

This module deliberately separates metric definition from scoring.  The current
worker can observe structural/output metrics, but it must not claim scientific
accuracy without reference data or labels.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any, Mapping


CATALOG_VERSION = "algorithm-evaluation-v1"


@dataclass(frozen=True)
class MetricDefinition:
    key: str
    label: str
    category: str
    direction: str
    unit: str | None
    source: str
    reference_required: bool
    threshold: float | int | None = None

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def metric_catalog(profile_code: str) -> tuple[MetricDefinition, ...]:
    """Return the approved first-version metric set for one product profile."""
    try:
        return _CATALOGS[profile_code]
    except KeyError as error:
        raise ValueError(f"unsupported evaluation profile: {profile_code}") from error


def build_evaluation_contract(profile_code: str) -> dict[str, Any]:
    """Build a JSON-safe metric contract without evaluating a product."""
    metrics = metric_catalog(profile_code)
    return {
        "catalogVersion": CATALOG_VERSION,
        "profileCode": profile_code,
        "metrics": [metric.as_dict() for metric in metrics],
        "referenceDataRequired": any(metric.reference_required for metric in metrics),
        "scoringStatus": "METRICS_DEFINED_ONLY",
        "limitations": [
            "没有参考产品、控制点或人工标注时，不输出科学精度结论。",
            "threshold 当前只保留硬性结构检查；业务阈值需要基准数据确认。",
        ],
    }


def observe_artifact_metrics(
    profile_code: str,
    inspection: Mapping[str, Any],
) -> dict[str, Any]:
    """Map existing bounded artifact inspection fields to catalog metrics.

    Missing fields remain ``UNAVAILABLE``.  The function never converts an
    observed file-health metric into an accuracy score.
    """
    definitions = metric_catalog(profile_code)
    values = _flatten_observation(inspection)
    observations = []
    for metric in definitions:
        if metric.key not in values:
            observations.append({
                "key": metric.key,
                "status": "UNAVAILABLE",
                "value": None,
                "source": metric.source,
            })
            continue
        observations.append({
            "key": metric.key,
            "status": "OBSERVED",
            "value": values[metric.key],
            "source": metric.source,
        })
    return {
        "catalogVersion": CATALOG_VERSION,
        "profileCode": profile_code,
        "scoringStatus": "OBSERVED_OUTPUT_METRICS_ONLY",
        "metrics": observations,
    }


def _flatten_observation(inspection: Mapping[str, Any]) -> dict[str, Any]:
    quality = inspection.get("quality")
    quality = quality if isinstance(quality, Mapping) else {}
    values: dict[str, Any] = {}
    aliases = {
        "valid_pixel_ratio": ("validPixelRatio",),
        "quality_mask_valid_ratio": ("validPixelRatio",),
        "track_valid_ratio": ("validRatio", "validPixelRatio"),
        "bounds_valid": ("boundsValid",),
        "finite_values": ("hasNanOrInf",),
        "width": ("width",),
        "height": ("height",),
        "band_count": ("bandCount",),
        "row_count": ("rowCount",),
        "column_count": ("columnCount",),
        "empty_value_ratio": ("emptyValueRatio",),
        "geometry_count": ("geometryCount",),
        "invalid_geometry_count": ("invalidGeometryCount",),
        "coordinate_range_valid": ("coordinateRangeValid",),
        "dimension_count": ("dimensionCount",),
        "variable_count": ("variableCount",),
        "observation_count": ("observationCount",),
        "time_range_valid": ("timeRangeValid",),
    }
    for key, candidates in aliases.items():
        for candidate in candidates:
            if candidate in quality:
                value = quality[candidate]
                if key == "finite_values":
                    value = not bool(value)
                values[key] = value
                break
    return values


def _m(
    key: str,
    label: str,
    category: str,
    direction: str,
    unit: str | None,
    source: str,
    *,
    reference_required: bool = False,
    threshold: float | int | None = None,
) -> MetricDefinition:
    return MetricDefinition(
        key=key,
        label=label,
        category=category,
        direction=direction,
        unit=unit,
        source=source,
        reference_required=reference_required,
        threshold=threshold,
    )


_RASTER_OUTPUT = (
    _m("bounds_valid", "空间范围有效", "CORRECTNESS", "PASS_FAIL", None, "artifact.quality.boundsValid"),
    _m("valid_pixel_ratio", "有效像元比例", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.quality.validPixelRatio"),
    _m("finite_values", "像元值有限", "CORRECTNESS", "PASS_FAIL", None, "artifact.quality.hasNanOrInf"),
    _m("width", "栅格宽度", "CORRECTNESS", "DESCRIPTIVE", "pixel", "artifact.quality.width"),
    _m("height", "栅格高度", "CORRECTNESS", "DESCRIPTIVE", "pixel", "artifact.quality.height"),
    _m("band_count", "波段数量", "CORRECTNESS", "DESCRIPTIVE", "band", "artifact.quality.bandCount"),
)

_CATALOGS: dict[str, tuple[MetricDefinition, ...]] = {
    "FY-3H-MERSI": _RASTER_OUTPUT + (
        _m("navigation_error", "导航误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", "reference.control_points", reference_required=True),
        _m("grid_coverage_ratio", "网格覆盖率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.statistics"),
    ),
    "FY-4B-AGRI": _RASTER_OUTPUT + (
        _m("navigation_valid_ratio", "导航有效率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.statistics"),
        _m("reprojection_error", "重投影误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", "reference.control_points", reference_required=True),
        _m("resolution_coverage_ratio", "分辨率覆盖率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.statistics"),
    ),
    "GF-6-WFV-L1A": _RASTER_OUTPUT + (
        _m("mosaic_gap_ratio", "拼接空洞比例", "DATA_QUALITY", "LOWER_BETTER", "ratio", "artifact.statistics"),
        _m("rpc_geolocation_error", "RPC 定位误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", "reference.control_points", reference_required=True),
        _m("projection_error", "投影误差", "DATA_QUALITY", "LOWER_BETTER", "meter", "reference.control_points", reference_required=True),
    ),
    "GF-6-WFV-BROWSE": (
        _m("bounds_valid", "空间范围有效", "CORRECTNESS", "PASS_FAIL", None, "artifact.metadata"),
        _m("width", "图片宽度", "CORRECTNESS", "DESCRIPTIVE", "pixel", "artifact.quality.width"),
        _m("height", "图片高度", "CORRECTNESS", "DESCRIPTIVE", "pixel", "artifact.quality.height"),
        _m("georeference_offset", "地理参考偏移", "DATA_QUALITY", "LOWER_BETTER", "degree", "reference.footprint", reference_required=True),
    ),
    "HY-1C-CZI": _RASTER_OUTPUT + (
        _m("quality_mask_valid_ratio", "质量掩膜后有效比例", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.quality.validPixelRatio"),
        _m("geolocation_error", "地理定位误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", "reference.control_points", reference_required=True),
        _m("band_mapping_consistency", "波段映射一致性", "CORRECTNESS", "PASS_FAIL", None, "artifact.metadata"),
    ),
    "HY-2D-ALTIMETER": (
        _m("track_valid_ratio", "轨迹保留率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "artifact.quality.validRatio"),
        _m("track_continuity", "轨迹连续性", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "reference.track_geometry", reference_required=True),
        _m("baseline_residual", "基线拟合残差", "DATA_QUALITY", "LOWER_BETTER", "meter", "reference.baseline_residual", reference_required=True),
        _m("anomaly_precision", "异常检测准确率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "reference.labels", reference_required=True),
        _m("anomaly_recall", "异常检测召回率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", "reference.labels", reference_required=True),
        _m("row_count", "轨迹记录数", "CORRECTNESS", "DESCRIPTIVE", "row", "artifact.quality.rowCount"),
        _m("empty_value_ratio", "空值比例", "CORRECTNESS", "LOWER_BETTER", "ratio", "artifact.quality.emptyValueRatio"),
    ),
}
