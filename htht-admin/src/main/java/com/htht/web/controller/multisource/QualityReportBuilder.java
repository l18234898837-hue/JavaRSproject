package com.htht.web.controller.multisource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Set;
import java.util.Comparator;

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
                                       AccessSubject subject, JsonNode suppliedEvaluationReport) {
        List<QualityReportResponse.Check> checks = new ArrayList<>();
        List<String> functions = detail.plan().stages().stream()
            .map(stage -> stage.stageKey() + " · " + stage.stageCode())
            .toList();

        Map<String, List<TaskArtifactView>> artifactsByType = new LinkedHashMap<>();
        Map<String, String> observedEvaluationMetrics = new LinkedHashMap<>();
        JsonNode evaluationReport = suppliedEvaluationReport;
        List<ExplainabilityCandidate> explainabilityCandidates = new ArrayList<>();
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
                if (isEvaluationReportArtifact(artifact) && inspection != null && inspection.isObject()) {
                    evaluationReport = inspection;
                }
                if (inspection != null && inspection.isObject()) {
                    JsonNode candidate = inspection.path("explainability");
                    if (!candidate.isObject()) {
                        candidate = inspection.path("summary").path("explainability");
                    }
                    if (candidate.isObject()) {
                        explainabilityCandidates.add(new ExplainabilityCandidate(
                            artifact.type(), displayName(artifact), candidate));
                    }
                }
                appendQualityChecks(checks, artifact, format, inspection);
            } catch (RuntimeException exception) {
                checks.add(notChecked(artifact, format, "无法读取产物内容，质量指标未检查",
                    "Worker 检查失败，请确认文件可读后重试"));
            }
        }
        checks = deduplicateRepeatedChecks(checks);

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
        JsonNode explainability = aggregateExplainability(profileCode, explainabilityCandidates);
        if (explainability != null && (algorithmEvaluation.explainability() == null
            || algorithmEvaluation.explainability().isMissingNode()
            || algorithmEvaluation.explainability().isNull())) {
            algorithmEvaluation = AlgorithmEvaluationCatalog.withExplainability(
                algorithmEvaluation, explainability);
        }
        return new QualityReportResponse("task-" + detail.id().value() + "-quality", detail.id().value(),
            Instant.now(), overall, new QualityReportResponse.Summary(checks.size(), passed, warnings, failed),
            functions, checks, algorithmEvaluation,
            references, RULE_VERSION);
    }

    private static JsonNode aggregateExplainability(
        String profileCode, List<ExplainabilityCandidate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, ExplainabilityCandidate> unique = new LinkedHashMap<>();
        for (ExplainabilityCandidate candidate : candidates) {
            JsonNode value = candidate.value();
            String key = value.path("version").asText("") + "\u0001"
                + value.path("algorithm").asText("") + "\u0001" + value.toString();
            unique.putIfAbsent(key, candidate);
        }
        List<ExplainabilityCandidate> ordered = unique.values().stream()
            .sorted(Comparator.comparingInt(QualityReportBuilder::explainabilityPriority)
                .thenComparing(ExplainabilityCandidate::artifactType)
                .thenComparing(ExplainabilityCandidate::displayName))
            .toList();
        if (ordered.size() == 1) {
            return ordered.get(0).value();
        }
        ObjectNode aggregate = JsonNodeFactory.instance.objectNode();
        aggregate.put("version", "task-explainability-aggregate-v1");
        aggregate.put("algorithm", profileCode + " 任务处理链");
        aggregate.put("purpose", "汇总本任务各处理阶段的算法用途、结果含义和使用限制。");
        ArrayNode components = aggregate.putArray("components");
        for (ExplainabilityCandidate candidate : ordered) {
            ObjectNode component = components.addObject();
            component.put("artifactType", candidate.artifactType());
            component.put("displayName", candidate.displayName());
            component.set("explainability", candidate.value());
        }
        return aggregate;
    }

    private static int explainabilityPriority(ExplainabilityCandidate candidate) {
        String version = candidate.value().path("version").asText("").toLowerCase(Locale.ROOT);
        String algorithm = candidate.value().path("algorithm").asText("").toLowerCase(Locale.ROOT);
        String value = version + " " + algorithm + " " + candidate.artifactType().toLowerCase(Locale.ROOT);
        if (value.contains("processing") || value.contains("standard-track")
            || value.contains("standard_metadata")) return 10;
        if (value.contains("validation")) return 20;
        if (value.contains("navigation") || value.contains("calibration")
            || value.contains("quality")) return 30;
        if (value.contains("feature")) return 40;
        if (value.contains("fusion")) return 50;
        if (value.contains("saliency") || value.contains("anomaly")) return 60;
        if (value.contains("grid")) return 70;
        return 80;
    }

    private record ExplainabilityCandidate(
        String artifactType, String displayName, JsonNode value) {}

    private static List<QualityReportResponse.Check> deduplicateRepeatedChecks(
        List<QualityReportResponse.Check> checks) {
        Map<String, QualityReportResponse.Check> unique = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QualityReportResponse.Check check : checks) {
            if (!isDeduplicableGenericCheck(check)) {
                unique.put(check.checkId() + "#" + unique.size(), check);
                continue;
            }
            String key = String.join("\u0001",
                check.name(), check.status(), nullToEmpty(check.actualValue()),
                nullToEmpty(check.threshold()), nullToEmpty(check.unit()),
                nullToEmpty(check.userMessage()), nullToEmpty(check.recommendation()));
            if (!unique.containsKey(key)) {
                unique.put(key, check);
                counts.put(key, 1);
            } else {
                counts.computeIfPresent(key, (ignored, value) -> value + 1);
            }
        }
        List<QualityReportResponse.Check> result = new ArrayList<>();
        for (Map.Entry<String, QualityReportResponse.Check> entry : unique.entrySet()) {
            QualityReportResponse.Check check = entry.getValue();
            Integer count = counts.get(entry.getKey());
            if (count == null || count <= 1) {
                result.add(check);
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>(
                check.details() == null ? Map.of() : check.details());
            details.put("deduplicatedCount", count);
            details.put("deduplicatedMessage", "多个产物结论一致，已合并展示");
            result.add(new QualityReportResponse.Check(
                check.checkId(), check.functionKey(), check.stageId(), check.artifactType(),
                check.name(), check.status(), check.actualValue(), check.threshold(), check.unit(),
                nullToEmpty(check.userMessage()) + "（已合并 " + count + " 个产物）",
                check.technicalMessage(), check.severity(), details,
                check.availability(), check.recommendation()));
        }
        return result;
    }

    private static boolean isDeduplicableGenericCheck(QualityReportResponse.Check check) {
        return Set.of("空间范围有效", "有效覆盖率", "候选区域比例", "像元数值有限",
                "栅格宽度", "栅格高度", "波段数量").contains(check.name());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private static boolean isEvaluationReportArtifact(TaskArtifactView artifact) {
        String type = artifact.type().toUpperCase(Locale.ROOT);
        String name = displayName(artifact).toLowerCase(Locale.ROOT);
        return (type.contains("EVALUATION") && type.contains("REPORT"))
            || name.equals("algorithm-evaluation-report.json")
            || name.equals("algorithm-evaluation.json");
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
        if (isCandidateMaskArtifact(artifact)) {
            addCandidateRatio(checks, prefix + "candidate-ratio", artifact, quality);
        } else {
            addCoverageRatio(checks, prefix + "valid-pixels", artifact, quality);
        }
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

    private static void addCoverageRatio(List<QualityReportResponse.Check> checks, String id,
                                         TaskArtifactView artifact, JsonNode quality) {
        if (!quality.has("validPixelRatio") || !quality.path("validPixelRatio").isNumber()) {
            checks.add(missing(id, artifact, "有效覆盖率", "validPixelRatio"));
            return;
        }
        double actual = quality.path("validPixelRatio").asDouble();
        String status = actual > 0 ? "PASS" : "WARN";
        checks.add(check(id, null, artifact, "有效覆盖率", status, Double.toString(actual), "> 0", "比例",
            actual > 0 ? "产物包含可用于查看和统计的有效观测像元" : "当前抽样范围内未发现有效观测像元",
            null, actual > 0 ? "INFO" : "WARNING", Map.of("validPixelRatio", actual),
            "CHECKED", actual > 0 ? null : "请确认 ROI、质量掩膜或源数据覆盖范围"));
    }

    private static void addCandidateRatio(List<QualityReportResponse.Check> checks, String id,
                                          TaskArtifactView artifact, JsonNode quality) {
        if (!quality.has("validPixelRatio") || !quality.path("validPixelRatio").isNumber()) {
            checks.add(missing(id, artifact, "候选区域比例", "validPixelRatio"));
            return;
        }
        double actual = quality.path("validPixelRatio").asDouble();
        checks.add(check(id, null, artifact, "候选区域比例", "PASS", Double.toString(actual), "描述性指标", "比例",
            actual > 0 ? "检测到显著性候选区域" : "未检测到超过阈值的显著性候选区域，不代表文件损坏",
            null, "INFO", Map.of("candidatePixelRatio", actual), "CHECKED", null));
    }

    private static boolean isCandidateMaskArtifact(TaskArtifactView artifact) {
        String type = artifact.type().toUpperCase(Locale.ROOT);
        return type.contains("SALIENCY") && type.contains("MASK");
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
