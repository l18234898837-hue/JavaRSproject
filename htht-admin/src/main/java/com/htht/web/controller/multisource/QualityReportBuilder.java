package com.htht.web.controller.multisource;

import com.fasterxml.jackson.databind.JsonNode;
import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.web.controller.multisource.dto.QualityReportResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QualityReportBuilder {
    public static final String RULE_VERSION = "quality-check-v1";

    public QualityReportResponse build(TaskDetailView detail) {
        return build(detail, null, null);
    }

    public QualityReportResponse build(TaskDetailView detail, QualityArtifactInspector inspector,
                                       AccessSubject subject) {
        return build(detail, inspector, subject, null);
    }

    public QualityReportResponse build(TaskDetailView detail, QualityArtifactInspector inspector,
                                       AccessSubject subject, JsonNode evaluationReport) {
        List<QualityReportResponse.Check> checks = new ArrayList<>();
        List<String> functions = detail.plan().stages().stream()
            .map(stage -> stage.stageKey() + " · " + stage.stageCode())
            .toList();

        Map<String, List<TaskArtifactView>> artifactsByType = new LinkedHashMap<>();
        Map<String, String> observedEvaluationMetrics = new LinkedHashMap<>();
        for (TaskArtifactView artifact : detail.artifacts()) {
            artifactsByType.computeIfAbsent(artifact.type(), ignored -> new ArrayList<>()).add(artifact);
        }
        for (var stage : detail.plan().stages()) {
            for (StageOutput output : stage.outputs()) {
                if (artifactsByType.getOrDefault(output.artifactType(), List.of()).isEmpty()) {
                    checks.add(check("output-" + stage.stageKey() + "-" + output.artifactType(),
                        stage.stageKey(), null, output.artifactType(), "声明产物可用性", "FAIL",
                        "未生成", "至少 1 个", null, "声明的产物未生成，任务结果不完整", null, "ERROR",
                        Map.of("relativePath", output.relativePath(), "retention", output.retention().name()),
                        "CHECKED", "请检查对应阶段输出或重新执行任务"));
                }
            }
        }

        for (TaskArtifactView artifact : detail.artifacts()) {
            String format = format(artifact);
            boolean nonEmpty = artifact.sizeBytes() > 0;
            checks.add(check("artifact-availability-" + artifact.stageId().value() + "-" + artifact.type(),
                null, artifact.stageId().value(), artifact.type(), "产物文件可用性",
                nonEmpty ? "PASS" : "FAIL", Long.toString(artifact.sizeBytes()), "> 0", "字节",
                nonEmpty ? "文件可读取且大小大于 0" : "文件为空，无法进行质量检查",
                null, nonEmpty ? "INFO" : "ERROR", Map.of("displayName", displayName(artifact)),
                "CHECKED", nonEmpty ? null : "请重新生成该产物"));

            if ("BINARY".equals(format)) {
                checks.add(check("artifact-format-" + artifact.stageId().value() + "-" + artifact.type(),
                    null, artifact.stageId().value(), artifact.type(), "产物格式识别", "WARN",
                    format, "可识别格式", null, "该二进制格式暂未纳入在线质量检查", null, "WARNING",
                    Map.of("displayName", displayName(artifact)), "NOT_APPLICABLE",
                    "请下载文件后使用对应专业工具检查"));
                continue;
            }
            if (inspector == null || subject == null) {
                checks.add(notChecked(artifact, format, "尚未执行内容质量检查",
                    "终态质量报告会通过 Worker 读取该文件的真实内容"));
                continue;
            }
            try {
                JsonNode inspection = inspector.inspect(detail, subject, artifact, format);
                collectEvaluationMetrics(observedEvaluationMetrics, format, inspection);
                appendQualityChecks(checks, artifact, format, inspection);
            } catch (RuntimeException exception) {
                checks.add(notChecked(artifact, format, "无法读取产物内容，质量指标未检查",
                    "Worker 检查失败，请确认文件可读后重试"));
            }
        }

        int passed = (int) checks.stream().filter(check -> "PASS".equals(check.status())).count();
        int warnings = (int) checks.stream().filter(check -> "WARN".equals(check.status())).count();
        int failed = (int) checks.stream().filter(check -> "FAIL".equals(check.status())).count();
        int unchecked = (int) checks.stream().filter(check -> "NOT_CHECKED".equals(check.status())).count();
        String overall = failed > 0 ? "FAIL" : warnings > 0 ? "WARN"
            : unchecked > 0 ? "NOT_CHECKED" : checks.isEmpty() ? "NOT_CHECKED" : "PASS";
        List<QualityReportResponse.ArtifactReference> references = detail.artifacts().stream()
            .map(artifact -> new QualityReportResponse.ArtifactReference(
                artifact.stageId().value(), artifact.type(), displayName(artifact), format(artifact),
                artifact.sizeBytes(), artifact.checksum(), artifact.retention()))
            .toList();
        String profileCode = detail.candidates().stream()
            .findFirst()
            .map(candidate -> candidate.profileKey().productCode())
            .orElse("UNKNOWN");
        QualityReportResponse.AlgorithmEvaluation algorithmEvaluation =
            evaluationReport == null
                ? AlgorithmEvaluationCatalog.forProfile(profileCode, observedEvaluationMetrics)
                : AlgorithmEvaluationCatalog.fromReport(profileCode, evaluationReport, observedEvaluationMetrics);
        return new QualityReportResponse("task-" + detail.id().value() + "-quality", detail.id().value(),
            Instant.now(), overall, new QualityReportResponse.Summary(checks.size(), passed, warnings, failed),
            functions, checks, algorithmEvaluation,
            references, RULE_VERSION);
    }

    private static void collectEvaluationMetrics(Map<String, String> observed, String format, JsonNode inspection) {
        if (inspection == null || !inspection.isObject()) {
            return;
        }
        JsonNode quality = inspection.path("quality");
        if (!quality.isObject()) {
            quality = inspection;
        }
        observe(observed, quality, "bounds_valid", "boundsValid");
        observe(observed, quality, "valid_pixel_ratio", "validPixelRatio");
        observe(observed, quality, "finite_values", "hasNanOrInf", true);
        observe(observed, quality, "row_count", "rowCount");
        observe(observed, quality, "empty_value_ratio", "emptyValueRatio");
        if ("HY-2D".equals(format)) {
            observe(observed, quality, "track_valid_ratio", "validRatio");
        }
        if ("NETCDF".equals(format)) {
            observe(observed, quality, "track_valid_ratio", "validRatio");
        }
    }

    private static void observe(Map<String, String> observed, JsonNode quality, String key, String field) {
        observe(observed, quality, key, field, false);
    }

    private static void observe(Map<String, String> observed, JsonNode quality, String key,
                                String field, boolean invertBoolean) {
        JsonNode value = quality.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        if (invertBoolean && value.isBoolean()) {
            observed.put(key, Boolean.toString(!value.asBoolean()));
        } else if (value.isValueNode()) {
            observed.put(key, value.asText());
        }
    }

    private static void appendQualityChecks(List<QualityReportResponse.Check> checks, TaskArtifactView artifact,
                                            String format, JsonNode inspection) {
        if (inspection == null || !inspection.isObject()) {
            checks.add(notChecked(artifact, format, "检查结果缺少有效内容", "Worker 未返回可解释的质量指标"));
            return;
        }
        JsonNode quality = inspection.path("quality");
        if ("JSON".equals(format) && !quality.isObject()) {
            appendJson(checks, prefixFor(artifact), artifact, inspection, inspection);
            return;
        }
        if (!quality.isObject()) {
            checks.add(notChecked(artifact, format, "未返回该文件的质量指标", "当前文件缺少可用于判定的质量元数据"));
            return;
        }
        String prefix = "quality-" + artifact.stageId().value() + "-" + artifact.type() + "-";
        switch (format) {
            case "COG", "GEOTIFF" -> appendRaster(checks, prefix, artifact, quality);
            case "PNG", "JPEG" -> appendImage(checks, prefix, artifact, quality);
            case "GEOJSON" -> appendGeoJson(checks, prefix, artifact, quality);
            case "CSV", "PARQUET" -> appendTable(checks, prefix, artifact, quality);
            case "NETCDF" -> appendNetcdf(checks, prefix, artifact, quality);
            case "JSON" -> appendJson(checks, prefix, artifact, quality, inspection);
            default -> checks.add(notChecked(artifact, format, "该格式暂未定义质量规则", "下载文件后使用对应专业工具检查"));
        }
    }

    private static String prefixFor(TaskArtifactView artifact) {
        return "quality-" + artifact.stageId().value() + "-" + artifact.type() + "-";
    }

    private static void appendImage(List<QualityReportResponse.Check> checks, String prefix,
                                    TaskArtifactView artifact, JsonNode quality) {
        addPositive(checks, prefix + "width", artifact, "图片宽度", quality, "width", "width > 0",
            "图片宽度有效", "图片宽度无效");
        addPositive(checks, prefix + "height", artifact, "图片高度", quality, "height", "height > 0",
            "图片高度有效", "图片高度无效");
        addNumeric(checks, prefix + "valid-pixels", artifact, "有效像元比例", quality, "validPixelRatio",
            0.5, "比例 ≥ 0.5", "有效像元比例达到工程阈值", "图片可能包含大量透明或空数据");
        addBoolean(checks, prefix + "finite", artifact, "像元数值有限", quality, "hasNanOrInf", "false",
            "未发现 NaN/Inf", "发现 NaN/Inf，部分像元可能不可用");
    }

    private static void appendRaster(List<QualityReportResponse.Check> checks, String prefix,
                                     TaskArtifactView artifact, JsonNode quality) {
        addBoolean(checks, prefix + "bounds", artifact, "空间范围有效", quality, "boundsValid", "true",
            "地理范围有效，可用于地图展示", "地理范围无效，无法可靠定位到地图");
        addNumeric(checks, prefix + "valid-pixels", artifact, "有效像元比例", quality, "validPixelRatio",
            0.5, "比例 ≥ 0.5", "有效像元比例达到工程阈值", "有效像元较少，结果可能包含大量空数据");
        addBoolean(checks, prefix + "finite", artifact, "像元数值有限", quality, "hasNanOrInf", "false",
            "未发现 NaN/Inf", "发现 NaN/Inf，部分像元可能不可用");
        addPositive(checks, prefix + "width", artifact, "栅格宽度", quality, "width", "width > 0",
            "栅格宽度有效", "栅格宽度无效");
        addPositive(checks, prefix + "height", artifact, "栅格高度", quality, "height", "height > 0",
            "栅格高度有效", "栅格高度无效");
        addPositive(checks, prefix + "bands", artifact, "波段数量", quality, "bandCount", "bandCount > 0",
            "包含有效波段", "未发现有效波段");
        if (!quality.path("statistics").isObject() || quality.path("statistics").size() == 0) {
            checks.add(check(prefix + "statistics", null, artifact, "统计摘要", "NOT_CHECKED", "未提供",
                "min/max", null, "未返回有效像元的统计摘要", null, "WARNING", Map.of(),
                "NOT_CHECKED", "请确认产物包含可计算的有效像元"));
        }
    }

    private static void appendGeoJson(List<QualityReportResponse.Check> checks, String prefix,
                                      TaskArtifactView artifact, JsonNode quality) {
        addNumericExact(checks, prefix + "invalid-geometry", artifact, "非法几何数量", quality,
            "invalidGeometryCount", 0, "0", "所有几何和坐标均合法", "存在非法几何或越界坐标");
        addBoolean(checks, prefix + "coordinate-range", artifact, "坐标范围有效", quality,
            "coordinateRangeValid", "true", "经纬度在合法范围内", "存在越界或无效坐标");
        addPositive(checks, prefix + "geometry-count", artifact, "要素数量", quality, "geometryCount",
            "geometryCount > 0", "包含可用空间要素", "未发现空间要素");
    }

    private static void appendTable(List<QualityReportResponse.Check> checks, String prefix,
                                    TaskArtifactView artifact, JsonNode quality) {
        addPositive(checks, prefix + "rows", artifact, "记录数量", quality, "rowCount", "rowCount > 0",
            "包含数据记录", "没有可用数据记录");
        addPositive(checks, prefix + "columns", artifact, "字段数量", quality, "columnCount", "columnCount > 0",
            "包含数据字段", "没有可用字段");
        addNumericMax(checks, prefix + "empty-values", artifact, "空值比例", quality, "emptyValueRatio",
            0.2, "比例 ≤ 0.2", "空值比例在工程阈值内", "空值比例较高，使用前请检查数据完整性");
    }

    private static void appendNetcdf(List<QualityReportResponse.Check> checks, String prefix,
                                     TaskArtifactView artifact, JsonNode quality) {
        addPositive(checks, prefix + "dimensions", artifact, "维度数量", quality, "dimensionCount",
            "dimensionCount > 0", "包含有效维度", "未发现有效维度");
        addPositive(checks, prefix + "variables", artifact, "变量数量", quality, "variableCount",
            "variableCount > 0", "包含有效变量", "未发现有效变量");
        addPositive(checks, prefix + "observations", artifact, "观测数量", quality, "observationCount",
            "observationCount > 0", "包含观测数据", "未发现观测数据");
        addBoolean(checks, prefix + "time-range", artifact, "时间范围有效", quality, "timeRangeValid",
            "true", "时间范围可解析且起止顺序正确", "时间范围缺失或无效");
    }

    private static void appendJson(List<QualityReportResponse.Check> checks, String prefix,
                                   TaskArtifactView artifact, JsonNode quality, JsonNode inspection) {
        boolean found = false;
        for (String field : List.of("validPixelRatio", "validRatio", "passRate")) {
            if (quality.has(field) && quality.path(field).isNumber()) {
                found = true;
                addNumeric(checks, prefix + field, artifact, field, quality, field, 0.5,
                    "比例 ≥ 0.5", "质量比例达到工程阈值", "质量比例较低，请检查无效数据");
            }
        }
        if (quality.has("statistics") || inspection.has("statistics")) {
            found = true;
            JsonNode statistics = quality.has("statistics") ? quality.path("statistics") : inspection.path("statistics");
            boolean valid = statistics.isObject() && statistics.size() > 0;
            checks.add(check(prefix + "statistics", null, artifact, "统计摘要", valid ? "PASS" : "NOT_CHECKED",
                statistics.toString(), "包含统计字段", null, valid ? "已提取真实统计摘要" : "未返回统计摘要",
                null, valid ? "INFO" : "WARNING", Map.of(), valid ? "CHECKED" : "NOT_CHECKED",
                valid ? null : "请确认 JSON 中包含质量统计字段"));
        }
        if (!found) {
            checks.add(notChecked(artifact, "JSON", "JSON 中未发现质量指标字段",
                "仅完成结构解析，无法据此判定业务质量"));
        }
    }

    private static void addBoolean(List<QualityReportResponse.Check> checks, String id, TaskArtifactView artifact,
                                   String name, JsonNode quality, String field, String expected,
                                   String passMessage, String failMessage) {
        if (!quality.has(field) || !quality.path(field).isBoolean()) {
            checks.add(missing(id, artifact, name, field));
            return;
        }
        boolean actual = quality.path(field).asBoolean();
        boolean pass = Boolean.toString(actual).equals(expected);
        checks.add(check(id, null, artifact, name, pass ? "PASS" : "FAIL", Boolean.toString(actual), expected, null,
            pass ? passMessage : failMessage, null, pass ? "INFO" : "ERROR", Map.of(field, actual),
            "CHECKED", pass ? null : "请修复该质量问题后重新生成产物"));
    }

    private static void addNumeric(List<QualityReportResponse.Check> checks, String id, TaskArtifactView artifact,
                                   String name, JsonNode quality, String field, double threshold,
                                   String thresholdText, String passMessage, String warnMessage) {
        if (!quality.has(field) || !quality.path(field).isNumber()) {
            checks.add(missing(id, artifact, name, field));
            return;
        }
        double actual = quality.path(field).asDouble();
        String status = actual >= threshold ? "PASS" : actual > 0 ? "WARN" : "FAIL";
        checks.add(check(id, null, artifact, name, status, Double.toString(actual), thresholdText, "比例",
            "PASS".equals(status) ? passMessage : warnMessage, null,
            "PASS".equals(status) ? "INFO" : "WARN".equals(status) ? "WARNING" : "ERROR",
            Map.of(field, actual), "CHECKED", "PASS".equals(status) ? null : "请检查无效值、空数据或质量掩膜"));
    }

    private static void addNumericMax(List<QualityReportResponse.Check> checks, String id, TaskArtifactView artifact,
                                      String name, JsonNode quality, String field, double threshold,
                                      String thresholdText, String passMessage, String warnMessage) {
        if (!quality.has(field) || !quality.path(field).isNumber()) {
            checks.add(missing(id, artifact, name, field));
            return;
        }
        double actual = quality.path(field).asDouble();
        String status = actual <= threshold ? "PASS" : actual < 1 ? "WARN" : "FAIL";
        checks.add(check(id, null, artifact, name, status, Double.toString(actual), thresholdText, "比例",
            "PASS".equals(status) ? passMessage : warnMessage, null,
            "PASS".equals(status) ? "INFO" : "WARN".equals(status) ? "WARNING" : "ERROR",
            Map.of(field, actual), "CHECKED", "PASS".equals(status) ? null : "请检查空值来源并完善数据"));
    }

    private static void addNumericExact(List<QualityReportResponse.Check> checks, String id, TaskArtifactView artifact,
                                        String name, JsonNode quality, String field, long expected,
                                        String thresholdText, String passMessage, String failMessage) {
        if (!quality.has(field) || !quality.path(field).isNumber()) {
            checks.add(missing(id, artifact, name, field));
            return;
        }
        long actual = quality.path(field).asLong();
        boolean pass = actual == expected;
        checks.add(check(id, null, artifact, name, pass ? "PASS" : "FAIL", Long.toString(actual),
            thresholdText, "个", pass ? passMessage : failMessage, null, pass ? "INFO" : "ERROR",
            Map.of(field, actual), "CHECKED", pass ? null : "修复非法几何或坐标后重新生成文件"));
    }

    private static void addPositive(List<QualityReportResponse.Check> checks, String id, TaskArtifactView artifact,
                                    String name, JsonNode quality, String field, String threshold,
                                    String passMessage, String failMessage) {
        if (!quality.has(field) || !quality.path(field).isNumber()) {
            checks.add(missing(id, artifact, name, field));
            return;
        }
        long actual = quality.path(field).asLong();
        boolean pass = actual > 0;
        checks.add(check(id, null, artifact, name, pass ? "PASS" : "FAIL", Long.toString(actual), threshold, null,
            pass ? passMessage : failMessage, null, pass ? "INFO" : "ERROR", Map.of(field, actual),
            "CHECKED", pass ? null : "请重新生成该产物"));
    }

    private static QualityReportResponse.Check missing(String id, TaskArtifactView artifact, String name, String field) {
        return check(id, null, artifact, name, "NOT_CHECKED", "未提供", field, null,
            "缺少该检查项所需的质量字段", null, "WARNING", Map.of("field", field),
            "NOT_CHECKED", "请让产物生成流程写入该质量元数据");
    }

    private static QualityReportResponse.Check notChecked(TaskArtifactView artifact, String format,
                                                          String message, String recommendation) {
        return check("quality-unchecked-" + artifact.stageId().value() + "-" + artifact.type(),
            null, artifact, format + " 内容质量", "NOT_CHECKED", "未检查", "Worker 质量指标", null,
            message, null, "WARNING", Map.of("format", format), "NOT_CHECKED", recommendation);
    }

    private static QualityReportResponse.Check check(String id, String functionKey, TaskArtifactView artifact,
                                                     String name, String status, String actual, String threshold,
                                                     String unit, String userMessage, String technicalMessage,
                                                     String severity, Map<String, Object> details,
                                                     String availability, String recommendation) {
        return check(id, functionKey, artifact == null ? null : artifact.stageId().value(),
            artifact == null ? null : artifact.type(), name, status, actual, threshold, unit, userMessage,
            technicalMessage, severity, details, availability, recommendation);
    }

    private static QualityReportResponse.Check check(String id, String functionKey, Long stageId,
                                                     String artifactType, String name, String status, String actual,
                                                     String threshold, String unit, String userMessage,
                                                     String technicalMessage, String severity,
                                                     Map<String, Object> details, String availability,
                                                     String recommendation) {
        return new QualityReportResponse.Check(id, functionKey, stageId, artifactType, name, status,
            actual, threshold, unit, userMessage, technicalMessage, severity, details, availability, recommendation);
    }

    private static String displayName(TaskArtifactView artifact) {
        String value = artifact.uri().value();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static String format(TaskArtifactView artifact) {
        String name = displayName(artifact).toLowerCase(Locale.ROOT);
        String type = artifact.type().toUpperCase(Locale.ROOT);
        if (name.endsWith(".geojson") || type.contains("GEOJSON")) return "GEOJSON";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".csv")) return "CSV";
        if (name.endsWith(".nc") || name.endsWith(".netcdf")) return "NETCDF";
        if (name.endsWith(".parquet")) return "PARQUET";
        if (name.endsWith(".png")) return "PNG";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "JPEG";
        if (name.endsWith(".tif") || name.endsWith(".tiff")) return type.contains("COG") ? "COG" : "GEOTIFF";
        return "BINARY";
    }
}
