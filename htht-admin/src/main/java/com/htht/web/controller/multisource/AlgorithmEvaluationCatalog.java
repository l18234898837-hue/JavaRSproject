package com.htht.web.controller.multisource;

import com.htht.web.controller.multisource.dto.QualityReportResponse;

import java.util.List;
import java.util.Map;

final class AlgorithmEvaluationCatalog {
    static final String VERSION = "algorithm-evaluation-v1";

    private AlgorithmEvaluationCatalog() {}

    static QualityReportResponse.AlgorithmEvaluation forProfile(String profileCode) {
        return forProfile(profileCode, Map.of());
    }

    static QualityReportResponse.AlgorithmEvaluation forProfile(String profileCode, Map<String, String> observed) {
        List<Metric> metrics = CATALOG.getOrDefault(profileCode, List.of());
        return new QualityReportResponse.AlgorithmEvaluation(
            VERSION,
            profileCode,
            metrics.stream().map(metric -> new QualityReportResponse.EvaluationMetric(
                metric.key(), metric.name(), metric.category(), metric.direction(), metric.unit(),
                observed.containsKey(metric.key()) ? "OBSERVED"
                    : metric.referenceRequired() ? "REQUIRES_REFERENCE" : "UNAVAILABLE",
                observed.get(metric.key()), metric.referenceRequired(), metric.source(),
                observed.containsKey(metric.key()) ? "已从产物检查结果中观测到"
                    : metric.referenceRequired()
                    ? "需要参考产品、控制点或人工标注后才能评价"
                    : "当前版本尚未接入该指标的自动观测"
            )).toList()
        );
    }

    private record Metric(String key, String name, String category, String direction,
                          String unit, boolean referenceRequired, String source) {}

    private static Metric m(String key, String name, String category, String direction,
                            String unit, boolean referenceRequired, String source) {
        return new Metric(key, name, category, direction, unit, referenceRequired, source);
    }

    private static final List<Metric> RASTER = List.of(
        m("bounds_valid", "空间范围有效", "CORRECTNESS", "PASS_FAIL", null, false, "artifact.quality.boundsValid"),
        m("valid_pixel_ratio", "有效像元比例", "DATA_QUALITY", "HIGHER_BETTER", "ratio", false, "artifact.quality.validPixelRatio"),
        m("finite_values", "像元值有限", "CORRECTNESS", "PASS_FAIL", null, false, "artifact.quality.hasNanOrInf")
    );

    private static final Map<String, List<Metric>> CATALOG = Map.of(
        "FY-3H-MERSI", concat(RASTER, List.of(
            m("navigation_error", "导航误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", true, "reference.control_points"),
            m("grid_coverage_ratio", "网格覆盖率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", false, "artifact.statistics")
        )),
        "FY-4B-AGRI", concat(RASTER, List.of(
            m("navigation_valid_ratio", "导航有效率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", false, "artifact.statistics"),
            m("reprojection_error", "重投影误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", true, "reference.control_points")
        )),
        "GF-6-WFV-L1A", concat(RASTER, List.of(
            m("mosaic_gap_ratio", "拼接空洞比例", "DATA_QUALITY", "LOWER_BETTER", "ratio", false, "artifact.statistics"),
            m("rpc_geolocation_error", "RPC 定位误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", true, "reference.control_points"),
            m("projection_error", "投影误差", "DATA_QUALITY", "LOWER_BETTER", "meter", true, "reference.control_points")
        )),
        "GF-6-WFV-BROWSE", List.of(
            m("bounds_valid", "空间范围有效", "CORRECTNESS", "PASS_FAIL", null, false, "artifact.metadata"),
            m("georeference_offset", "地理参考偏移", "DATA_QUALITY", "LOWER_BETTER", "degree", true, "reference.footprint")
        ),
        "HY-1C-CZI", concat(RASTER, List.of(
            m("quality_mask_valid_ratio", "质量掩膜后有效比例", "DATA_QUALITY", "HIGHER_BETTER", "ratio", false, "artifact.quality.validPixelRatio"),
            m("geolocation_error", "地理定位误差", "DATA_QUALITY", "LOWER_BETTER", "pixel", true, "reference.control_points"),
            m("band_mapping_consistency", "波段映射一致性", "CORRECTNESS", "PASS_FAIL", null, false, "artifact.metadata")
        )),
        "HY-2D-ALTIMETER", List.of(
            m("track_valid_ratio", "轨迹保留率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", false, "artifact.quality.validRatio"),
            m("track_continuity", "轨迹连续性", "DATA_QUALITY", "HIGHER_BETTER", "ratio", true, "reference.track_geometry"),
            m("baseline_residual", "基线拟合残差", "DATA_QUALITY", "LOWER_BETTER", "meter", true, "reference.baseline_residual"),
            m("anomaly_precision", "异常检测准确率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", true, "reference.labels"),
            m("anomaly_recall", "异常检测召回率", "DATA_QUALITY", "HIGHER_BETTER", "ratio", true, "reference.labels")
        )
    );

    private static List<Metric> concat(List<Metric> first, List<Metric> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
