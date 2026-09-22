package com.htht.web.controller.multisource;

import cn.dev33.satoken.annotation.SaIgnore;
import com.htht.common.core.domain.R;
import com.htht.config.multisource.LocalWorkerResultArchiveService;
import com.htht.config.multisource.ArtifactInspectionService;
import com.htht.config.multisource.ArtifactPreviewTicketService;
import com.htht.config.multisource.ArtifactRasterPreviewService;
import com.htht.config.multisource.ResultDownloadTicketService;
import com.htht.multisource.application.usecase.CancelProcessingTaskUseCase;
import com.htht.multisource.application.usecase.DispatchNextStageUseCase;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.RetryFailedStageUseCase;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.worker.WorkerFailurePresentation;
import com.htht.web.controller.multisource.dto.DispatchStageResponse;
import com.htht.web.controller.multisource.dto.ExecutionProgressResponse;
import com.htht.web.controller.multisource.dto.ArtifactTilePreviewResponse;
import com.htht.web.controller.multisource.dto.ResultDownloadTicketRequest;
import com.htht.web.controller.multisource.dto.ResultDownloadTicketResponse;
import com.htht.web.controller.multisource.dto.QualityReportResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource")
public class ProcessingExecutionController {
    private static final Logger log = LoggerFactory.getLogger(ProcessingExecutionController.class);
    private final Supplier<AccessSubject> currentSubject;
    private final GetTaskDetailUseCase detailUseCase;
    private final DispatchNextStageUseCase dispatchUseCase;
    private final CancelProcessingTaskUseCase cancelUseCase;
    private final RetryFailedStageUseCase retryUseCase;
    private final LocalWorkerResultArchiveService resultArchiveService;
    private final ResultDownloadTicketService downloadTicketService;
    private final ArtifactInspectionService artifactInspectionService;
    private final ArtifactPreviewTicketService artifactPreviewTicketService;
    private final ArtifactRasterPreviewService artifactRasterPreviewService;
    private final QualityReportBuilder qualityReportBuilder;

    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase,
                                         LocalWorkerResultArchiveService resultArchiveService,
                                         ResultDownloadTicketService downloadTicketService) {
        this(currentSubject, detailUseCase, dispatchUseCase, cancelUseCase, retryUseCase,
            resultArchiveService, downloadTicketService, null, null, null);
    }

    @Autowired
    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase,
                                         LocalWorkerResultArchiveService resultArchiveService,
                                         ResultDownloadTicketService downloadTicketService,
                                         ArtifactInspectionService artifactInspectionService,
                                         ArtifactPreviewTicketService artifactPreviewTicketService,
                                         ArtifactRasterPreviewService artifactRasterPreviewService) {
        this(currentSubject, detailUseCase, dispatchUseCase, cancelUseCase, retryUseCase,
            resultArchiveService, downloadTicketService, artifactInspectionService,
            artifactPreviewTicketService, artifactRasterPreviewService, new QualityReportBuilder());
    }

    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase,
                                         LocalWorkerResultArchiveService resultArchiveService,
                                         ResultDownloadTicketService downloadTicketService,
                                         ArtifactInspectionService artifactInspectionService,
                                         ArtifactPreviewTicketService artifactPreviewTicketService,
                                         ArtifactRasterPreviewService artifactRasterPreviewService,
                                         QualityReportBuilder qualityReportBuilder) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.dispatchUseCase = Objects.requireNonNull(dispatchUseCase, "dispatchUseCase");
        this.cancelUseCase = Objects.requireNonNull(cancelUseCase, "cancelUseCase");
        this.retryUseCase = Objects.requireNonNull(retryUseCase, "retryUseCase");
        this.resultArchiveService = Objects.requireNonNull(resultArchiveService, "resultArchiveService");
        this.downloadTicketService = Objects.requireNonNull(downloadTicketService, "downloadTicketService");
        this.artifactInspectionService = artifactInspectionService;
        this.artifactPreviewTicketService = artifactPreviewTicketService;
        this.artifactRasterPreviewService = artifactRasterPreviewService;
        this.qualityReportBuilder = Objects.requireNonNull(qualityReportBuilder, "qualityReportBuilder");
    }

    public ProcessingExecutionController(Supplier<AccessSubject> currentSubject, GetTaskDetailUseCase detailUseCase,
                                         DispatchNextStageUseCase dispatchUseCase,
                                         CancelProcessingTaskUseCase cancelUseCase,
                                         RetryFailedStageUseCase retryUseCase) {
        this.currentSubject = Objects.requireNonNull(currentSubject, "currentSubject");
        this.detailUseCase = Objects.requireNonNull(detailUseCase, "detailUseCase");
        this.dispatchUseCase = Objects.requireNonNull(dispatchUseCase, "dispatchUseCase");
        this.cancelUseCase = Objects.requireNonNull(cancelUseCase, "cancelUseCase");
        this.retryUseCase = Objects.requireNonNull(retryUseCase, "retryUseCase");
        this.resultArchiveService = null;
        this.downloadTicketService = null;
        this.artifactInspectionService = null;
        this.artifactPreviewTicketService = null;
        this.artifactRasterPreviewService = null;
        this.qualityReportBuilder = new QualityReportBuilder();
    }

    @PostMapping("/processing-tasks/{taskId}/dispatch")
    public ResponseEntity<R<DispatchStageResponse>> dispatch(@PathVariable long taskId) {
        TaskId id = TaskId.of(taskId);
        detailUseCase.get(id, currentSubject.get());
        var result = dispatchUseCase.dispatch(id);
        DispatchStageResponse response = result
            .map(value -> new DispatchStageResponse(true, value.stageId()))
            .orElseGet(() -> new DispatchStageResponse(false, null));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(R.ok(response));
    }

    @PostMapping("/processing-tasks/{taskId}/cancel")
    public ResponseEntity<R<Void>> cancel(@PathVariable long taskId) {
        cancelUseCase.cancel(TaskId.of(taskId), currentSubject.get());
        return ResponseEntity.ok(R.ok());
    }

    @PostMapping("/processing-stages/{stageId}/retry")
    public ResponseEntity<R<Void>> retry(@PathVariable long stageId) {
        retryUseCase.retry(new StageId(stageId), currentSubject.get());
        return ResponseEntity.ok(R.ok());
    }

    @GetMapping("/processing-tasks/{taskId}/progress")
    public ResponseEntity<R<ExecutionProgressResponse>> progress(@PathVariable long taskId) {
        var detail = detailUseCase.get(TaskId.of(taskId), currentSubject.get());
        QualityReportResponse report = qualityReportBuilder.build(detail);
        return ResponseEntity.ok(R.ok(new ExecutionProgressResponse(
            detail.id().value(), detail.lifecycleStatus().name(), detail.qualityStatus().name(),
            detail.stages().stream().map(stage -> {
                WorkerFailurePresentation.SafeFailure safeFailure = WorkerFailurePresentation.forClient(
                    stage.errorCode(), stage.errorMessage()
                );
                return new ExecutionProgressResponse.StageProgressResponse(
                    stage.id().value(), stage.stageKey(), stage.status().name(), stage.attempt(),
                    stage.errorCode() == null && stage.errorMessage() == null ? null : safeFailure.code(),
                    stage.errorCode() == null && stage.errorMessage() == null ? null : safeFailure.message(),
                    stage.executionId(), stage.startedAt(), stage.finishedAt(), durationMs(stage.startedAt(), stage.finishedAt()),
                    stage.stageKey(), declaredOutputs(detail.plan().stages(), stage.stageKey())
                );
            }).toList(),
            detail.artifacts().stream().map(artifact -> {
                String format = artifactFormat(artifact);
                return new ExecutionProgressResponse.ArtifactProgressResponse(
                    artifact.stageId().value(), artifact.type(), artifact.uri().value(), artifact.checksum(), artifact.sizeBytes(),
                    artifact.retention(), true, mediaType(format), previewable(format), displayName(artifact),
                    artifactVisualization(taskId, detail, artifact, format),
                    format, artifactView(taskId, artifact, format)
                );
            }).toList(),
            new ExecutionProgressResponse.QualityReportSummary(
                report.overallStatus(), report.summary().totalChecks(), report.summary().passedChecks(),
                report.summary().warningChecks(), report.summary().failedChecks())
        )));
    }

    @GetMapping("/processing-tasks/{taskId}/quality-report")
    public ResponseEntity<R<QualityReportResponse>> qualityReport(@PathVariable long taskId) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        requireTerminalForQualityReport(detail);
        return ResponseEntity.ok(R.ok(buildQualityReport(detail, subject)));
    }

    @GetMapping(value = "/processing-tasks/{taskId}/quality-report.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> qualityReportHtml(@PathVariable long taskId) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        requireTerminalForQualityReport(detail);
        QualityReportResponse report = buildQualityReport(detail, subject);
        String fileName = "task-" + taskId + "-quality-report.html";
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(renderQualityReportHtml(report));
    }

    private static void requireTerminalForQualityReport(
        com.htht.multisource.application.usecase.model.TaskDetailView detail) {
        String lifecycle = detail.lifecycleStatus().name();
        if (!"SUCCEEDED".equals(lifecycle) && !"FAILED".equals(lifecycle) && !"CANCELED".equals(lifecycle)) {
            throw new IllegalStateException("Processing task has not reached a terminal state.");
        }
    }

    private static String renderQualityReportHtml(QualityReportResponse report) {
        StringBuilder html = new StringBuilder("""
            <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>质量检测报告</title>
            <style>body{font-family:system-ui,sans-serif;max-width:1000px;margin:32px auto;padding:0 20px;color:#243746}
            .status{padding:8px 12px;border-radius:6px;background:#eef5f7;display:inline-block}
            table{width:100%;border-collapse:collapse;margin-top:20px}th,td{padding:9px;border-bottom:1px solid #dce8ed;text-align:left;vertical-align:top}
            th{background:#f6fafb}.PASS{color:#216b4c}.WARN{color:#9a6b20}.FAIL{color:#a13f3f}</style></head><body>
            """);
        html.append("<h1>质量检测报告</h1><p>任务 ").append(escapeHtml(Long.toString(report.taskId())))
            .append(" · 生成时间 ").append(escapeHtml(report.generatedAt().toString())).append("</p>");
        html.append("<div class=\"status ").append(escapeHtml(report.overallStatus())).append("\">总体结论：")
            .append(escapeHtml(report.overallStatus())).append(" · 检查 ")
            .append(report.summary().totalChecks()).append(" 项，")
            .append("通过 ").append(report.summary().passedChecks()).append("，")
            .append("警告 ").append(report.summary().warningChecks()).append("，")
            .append("失败 ").append(report.summary().failedChecks()).append("</div>");
        html.append("<table><thead><tr><th>检查项</th><th>状态</th><th>说明</th><th>实际值</th><th>规则/阈值</th><th>建议</th></tr></thead><tbody>");
        for (QualityReportResponse.Check check : report.checks()) {
            html.append("<tr><td>").append(escapeHtml(check.name())).append("</td><td class=\"")
                .append(escapeHtml(check.status())).append("\">").append(escapeHtml(check.status()))
                .append("</td><td>").append(escapeHtml(check.userMessage())).append("</td><td>")
                .append(escapeHtml(check.actualValue())).append("</td><td>")
                .append(escapeHtml(check.threshold())).append("</td><td>")
                .append(escapeHtml(check.recommendation())).append("</td></tr>");
        }
        html.append("</tbody></table>");
        html.append("<h2>算法质量分析</h2>");
        html.append("<p>指标目录版本：").append(escapeHtml(report.algorithmEvaluation().catalogVersion()))
            .append(" · 算法类型：").append(escapeHtml(report.algorithmEvaluation().profileCode()))
            .append("</p>");
        html.append("<table><thead><tr><th>指标</th><th>类别</th><th>状态</th><th>结果</th><th>说明</th></tr></thead><tbody>");
        for (QualityReportResponse.EvaluationMetric metric : report.algorithmEvaluation().metrics()) {
            html.append("<tr><td>").append(escapeHtml(metric.name())).append("</td><td>")
                .append(escapeHtml(metric.category())).append("</td><td class=\"")
                .append(escapeHtml(metric.status())).append("\">").append(escapeHtml(metric.status()))
                .append("</td><td>").append(escapeHtml(metric.observedValue()))
                .append(metric.unit() == null ? "" : " " + escapeHtml(metric.unit()))
                .append("</td><td>").append(escapeHtml(metric.message())).append("</td></tr>");
        }
        return html.append("</tbody></table></body></html>").toString();
    }

    private QualityReportResponse buildQualityReport(
        com.htht.multisource.application.usecase.model.TaskDetailView detail, AccessSubject subject) {
        if (artifactInspectionService == null) {
            return qualityReportBuilder.build(detail);
        }
        QualityArtifactInspector inspector = (task, owner, artifact, format) -> {
            if ("COG".equals(format) || "GEOTIFF".equals(format)) {
                return artifactInspectionService.inspectRaster(task, owner, artifact, format);
            }
            return artifactInspectionService.inspect(task, owner, artifact, format);
        };
        return qualityReportBuilder.build(detail, inspector, subject);
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static Long durationMs(java.time.Instant startedAt, java.time.Instant finishedAt) {
        return startedAt == null || finishedAt == null ? null : java.time.Duration.between(startedAt, finishedAt).toMillis();
    }

    private static List<String> declaredOutputs(java.util.List<com.htht.multisource.domain.model.ProcessingStage> stages, String key) {
        return stages.stream().filter(stage -> stage.stageKey().equals(key)).findFirst()
            .map(stage -> stage.outputs().stream().map(output -> output.artifactType()).toList()).orElse(List.of());
    }

    private static String displayName(com.htht.multisource.application.usecase.model.TaskArtifactView artifact) {
        String value = artifact.uri().value();
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : artifact.type();
    }

    private static String artifactFormat(com.htht.multisource.application.usecase.model.TaskArtifactView artifact) {
        String fileName = displayName(artifact).toLowerCase(java.util.Locale.ROOT);
        String type = artifact.type().toUpperCase(java.util.Locale.ROOT);
        if (fileName.endsWith(".geojson") || type.contains("GEOJSON")) return "GEOJSON";
        if (fileName.endsWith(".csv")) return "CSV";
        if (fileName.endsWith(".nc")) return "NETCDF";
        if (fileName.endsWith(".parquet")) return "PARQUET";
        if (fileName.endsWith(".json")) return "JSON";
        if (fileName.endsWith(".png")) return "PNG";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "JPEG";
        if (fileName.endsWith(".webp")) return "WEBP";
        if (fileName.endsWith(".gif")) return "GIF";
        if (fileName.endsWith(".xml")) return "XML";
        if (fileName.endsWith(".txt") || fileName.endsWith(".log")) return "TEXT";
        if (fileName.endsWith(".npz")) return "NPZ";
        if (fileName.endsWith(".tif") || fileName.endsWith(".tiff")) {
            return type.contains("COG") ? "COG" : "GEOTIFF";
        }
        return "BINARY";
    }

    private static String mediaType(String format) {
        if ("GEOJSON".equals(format)) return "application/geo+json";
        if ("JSON".equals(format)) return "application/json";
        if ("PNG".equals(format)) return "image/png";
        if ("JPEG".equals(format)) return "image/jpeg";
        if ("WEBP".equals(format)) return "image/webp";
        if ("GIF".equals(format)) return "image/gif";
        if ("CSV".equals(format)) return "text/csv";
        if ("XML".equals(format)) return "application/xml";
        if ("TEXT".equals(format)) return "text/plain";
        if ("COG".equals(format) || "GEOTIFF".equals(format)) return "image/tiff";
        if ("NETCDF".equals(format)) return "application/x-netcdf";
        if ("PARQUET".equals(format)) return "application/vnd.apache.parquet";
        return "application/octet-stream";
    }

    private static boolean previewable(String format) {
        String media = mediaType(format);
        return "image/png".equals(media) || "image/jpeg".equals(media)
            || "image/webp".equals(media) || "image/gif".equals(media)
            || "application/json".equals(media) || "application/geo+json".equals(media)
            || "text/csv".equals(media)
            || "application/xml".equals(media) || "text/plain".equals(media);
    }

    private ExecutionProgressResponse.ArtifactVisualizationResponse artifactVisualization(
        long taskId, com.htht.multisource.application.usecase.model.TaskDetailView detail,
        com.htht.multisource.application.usecase.model.TaskArtifactView artifact, String format
    ) {
        if (!isImageFormat(format) || artifactRasterPreviewService == null || artifactPreviewTicketService == null) {
            return null;
        }
        var companion = companionRaster(detail, artifact);
        if (companion == null) return null;
        try {
            String rasterFormat = artifactFormat(companion);
            AccessSubject subject = currentSubject.get();
            var preview = artifactRasterPreviewService.describe(detail, subject, companion, rasterFormat);
            if (!preview.renderable() || preview.bounds() == null) return null;
            // Use the companion raster only as georeferencing metadata. The
            // user clicked a PNG/JPEG artifact, so the map layer must show that
            // image rather than a derived score/mask COG that can be sparse or
            // visually empty. The client downsizes large images before handing
            // them to Cesium, keeping texture uploads bounded.
            String ticket = artifactPreviewTicketService.issue(taskId, subject, artifact.type(), artifact.checksum()).token();
            String encodedType = java.net.URLEncoder.encode(artifact.type(), java.nio.charset.StandardCharsets.UTF_8);
            String encodedTicket = java.net.URLEncoder.encode(ticket, java.nio.charset.StandardCharsets.UTF_8);
            String imageUrl = "/api/multisource/processing-tasks/" + taskId + "/artifacts/"
                + encodedType + "/image?ticket=" + encodedTicket;
            return new ExecutionProgressResponse.ArtifactVisualizationResponse(
                "bounds", null, imageUrl, preview.bounds(), "EPSG:4326", null, null, null);
        } catch (RuntimeException exception) {
            log.debug("Unable to describe georeferenced image artifact {}", artifact.type(), exception);
            return null;
        }
    }

    private static boolean isImageFormat(String format) {
        return "PNG".equals(format) || "JPEG".equals(format);
    }

    private static com.htht.multisource.application.usecase.model.TaskArtifactView companionRaster(
        com.htht.multisource.application.usecase.model.TaskDetailView detail,
        com.htht.multisource.application.usecase.model.TaskArtifactView image
    ) {
        String imageStem = stem(displayName(image));
        String expectedType = companionType(image.type());
        String expectedPrefix = expectedType.endsWith("_COG")
            ? expectedType.substring(0, expectedType.length() - "_COG".length()) : expectedType;
        return detail.artifacts().stream()
            .filter(candidate -> "COG".equals(artifactFormat(candidate)) || "GEOTIFF".equals(artifactFormat(candidate)))
            .filter(candidate -> stem(displayName(candidate)).equalsIgnoreCase(imageStem)
                || (!expectedType.isBlank() && expectedType.equalsIgnoreCase(candidate.type()))
                || (!expectedPrefix.isBlank() && candidate.type().toUpperCase(java.util.Locale.ROOT)
                    .startsWith(expectedPrefix + "_")))
            .findFirst().orElse(null);
    }

    private static String companionType(String imageType) {
        String normalized = imageType == null ? "" : imageType.toUpperCase(java.util.Locale.ROOT);
        if ("SALIENCY_PREVIEW_PNG".equals(normalized)
            || "SALIENCY_PREVIEW_JPEG".equals(normalized)) {
            return "SALIENCY_SCORE_COG";
        }
        for (String suffix : List.of("_PREVIEW_PNG", "_PREVIEW_JPEG")) {
            if (normalized.endsWith(suffix)) {
                return normalized.substring(0, normalized.length() - suffix.length()) + "_COG";
            }
        }
        return "";
    }

    private static String stem(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String extension : List.of(".png", ".jpg", ".jpeg", ".tif", ".tiff")) {
            if (lower.endsWith(extension)) return name.substring(0, name.length() - extension.length());
        }
        return name;
    }

    private static ExecutionProgressResponse.ArtifactViewResponse artifactView(
        long taskId, com.htht.multisource.application.usecase.model.TaskArtifactView artifact, String format
    ) {
        String mode = switch (format) {
            case "COG", "GEOTIFF", "GEOJSON" -> "map";
            case "PNG", "JPEG", "WEBP", "GIF" -> "image";
            case "JSON" -> "json";
            case "CSV" -> "table";
            case "NETCDF", "PARQUET" -> "dataset";
            case "XML", "TEXT" -> "text";
            default -> "disabled";
        };
        if ("NPZ".equals(format)) {
            return new ExecutionProgressResponse.ArtifactViewResponse(
                false, "disabled", "NPZ 二进制中间数据暂不支持在线查看", null
            );
        }
        if ("BINARY".equals(format)) {
            return new ExecutionProgressResponse.ArtifactViewResponse(
                false, "disabled", "该二进制产物暂不支持在线查看", null
            );
        }
        return new ExecutionProgressResponse.ArtifactViewResponse(
            true, mode, null,
            "/api/multisource/processing-tasks/" + taskId + "/artifacts/"
                + java.net.URLEncoder.encode(artifact.type(), java.nio.charset.StandardCharsets.UTF_8) + "/inspection"
        );
    }

    @GetMapping("/processing-tasks/{taskId}/artifacts/{type}/download")
    public void downloadArtifact(@PathVariable long taskId, @PathVariable String type,
                                 javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (resultArchiveService == null) {
            throw new IllegalStateException("Result artifact service is not configured.");
        }
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        resultArchiveService.writeArtifact(detail, subject, type, response);
    }

    @SaIgnore
    @GetMapping("/processing-tasks/{taskId}/artifacts/{type}/image")
    public void image(@PathVariable long taskId, @PathVariable String type,
                      @RequestParam("ticket") String ticket,
                      javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        requireImagePreviewService();
        var grant = artifactPreviewTicketService.resolve(taskId, type, ticket);
        var detail = detailUseCase.get(TaskId.of(taskId), grant.subject());
        var artifact = artifactByType(detail, type);
        if (!grant.checksum().equals(artifact.checksum())) {
            throw new SecurityException("Artifact preview ticket is invalid or expired.");
        }
        String format = artifactFormat(artifact);
        if (!isImageFormat(format)) {
            throw new IllegalArgumentException("Artifact is not a supported image preview.");
        }
        resultArchiveService.writeArtifactPreview(detail, grant.subject(), type, response);
    }

    private void requireImagePreviewService() {
        if (resultArchiveService == null || artifactPreviewTicketService == null) {
            throw new IllegalStateException("Image preview service is not configured.");
        }
    }

    @GetMapping("/processing-tasks/{taskId}/artifacts/{type}/inspection")
    public ResponseEntity<R<com.fasterxml.jackson.databind.JsonNode>> inspectArtifact(@PathVariable long taskId,
                                                                                       @PathVariable String type) {
        if (artifactInspectionService == null) {
            throw new IllegalStateException("Artifact inspection service is not configured.");
        }
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        var artifact = detail.artifacts().stream().filter(item -> type.equals(item.type())).reduce((first, second) -> {
            throw new IllegalArgumentException("Artifact type is ambiguous for this processing task.");
        }).orElseThrow(() -> new java.util.NoSuchElementException("Processing artifact not found."));
        String format = artifactFormat(artifact);
        if (!artifactView(taskId, artifact, format).enabled()) {
            throw new IllegalArgumentException("Artifact format does not support inspection.");
        }
        return ResponseEntity.ok(R.ok(artifactInspectionService.inspect(detail, subject, artifact, format)));
    }

    @PostMapping("/processing-tasks/{taskId}/artifacts/{type}/tile-preview")
    public ResponseEntity<R<ArtifactTilePreviewResponse>> tilePreview(@PathVariable long taskId,
                                                                        @PathVariable String type) {
        requireRasterPreviewService();
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        var artifact = artifactByType(detail, type);
        String format = artifactFormat(artifact);
        requireRasterFormat(format);
        var preview = artifactRasterPreviewService.describe(detail, subject, artifact, format);
        if (!preview.renderable()) {
            throw new IllegalArgumentException(preview.reason() == null ? "TIFF 暂不支持地图查看" : preview.reason());
        }
        String ticket = artifactPreviewTicketService.issue(taskId, subject, artifact.type(), artifact.checksum()).token();
        String encodedType = java.net.URLEncoder.encode(artifact.type(), java.nio.charset.StandardCharsets.UTF_8);
        String encodedTicket = java.net.URLEncoder.encode(ticket, java.nio.charset.StandardCharsets.UTF_8);
        String tileUrl = "/api/multisource/processing-tasks/" + taskId + "/artifacts/" + encodedType
            + "/tiles/{z}/{x}/{y}.png?ticket=" + encodedTicket;
        return ResponseEntity.ok(R.ok(new ArtifactTilePreviewResponse(tileUrl, preview.bounds(), preview.crs(),
            preview.minZoom(), preview.maxZoom(), preview.legend())));
    }

    @SaIgnore
    @GetMapping("/processing-tasks/{taskId}/artifacts/{type}/tiles/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> tile(@PathVariable long taskId, @PathVariable String type,
                                       @PathVariable int z, @PathVariable int x, @PathVariable int y,
                                       @RequestParam("ticket") String ticket) {
        requireRasterPreviewService();
        validateTileCoordinate(z, x, y);
        var grant = artifactPreviewTicketService.resolve(taskId, type, ticket);
        var detail = detailUseCase.get(TaskId.of(taskId), grant.subject());
        var artifact = artifactByType(detail, type);
        if (!grant.checksum().equals(artifact.checksum())) {
            throw new SecurityException("Artifact preview ticket is invalid or expired.");
        }
        String format = artifactFormat(artifact);
        requireRasterFormat(format);
        var preview = artifactRasterPreviewService.describe(detail, grant.subject(), artifact, format);
        byte[] png = artifactRasterPreviewService.renderTile(detail, grant.subject(), artifact, format, preview, z, x, y);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
            .header("X-Content-Type-Options", "nosniff")
            .body(png);
    }

    private void requireRasterPreviewService() {
        if (artifactPreviewTicketService == null || artifactRasterPreviewService == null) {
            throw new IllegalStateException("Raster preview service is not configured.");
        }
    }

    private static void requireRasterFormat(String format) {
        if (!"COG".equals(format) && !"GEOTIFF".equals(format)) {
            throw new IllegalArgumentException("Artifact is not a TIFF raster.");
        }
    }

    private static void validateTileCoordinate(int z, int x, int y) {
        if (z < 0 || z > 22) {
            throw new IllegalArgumentException("Raster tile coordinate is invalid.");
        }
        int dimension = 1 << z;
        if (x < 0 || y < 0 || x >= dimension || y >= dimension) {
            throw new IllegalArgumentException("Raster tile coordinate is invalid.");
        }
    }

    private static com.htht.multisource.application.usecase.model.TaskArtifactView artifactByType(
        com.htht.multisource.application.usecase.model.TaskDetailView detail, String type
    ) {
        return detail.artifacts().stream().filter(item -> type.equals(item.type())).reduce((first, second) -> {
            throw new IllegalArgumentException("Artifact type is ambiguous for this processing task.");
        }).orElseThrow(() -> new java.util.NoSuchElementException("Processing artifact not found."));
    }

    @PostMapping("/processing-tasks/{taskId}/result-download-ticket")
    public ResponseEntity<R<ResultDownloadTicketResponse>> createResultDownloadTicket(
        @PathVariable long taskId, @RequestBody ResultDownloadTicketRequest request) {
        AccessSubject subject = currentSubject.get();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        if (!"SUCCEEDED".equals(detail.lifecycleStatus().name())) {
            throw new IllegalStateException("Processing task has not completed successfully.");
        }
        if (downloadTicketService == null) {
            throw new IllegalStateException("Result download ticket service is not configured.");
        }
        var issued = downloadTicketService.issue(taskId, subject, request.fileName());
        return ResponseEntity.ok(R.ok(new ResultDownloadTicketResponse(issued.token(), issued.expiresAt())));
    }

    @SaIgnore
    @GetMapping("/processing-tasks/{taskId}/result.zip")
    public void download(@PathVariable long taskId, @RequestParam("ticket") String ticket,
                         javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        if (downloadTicketService == null) {
            throw new IllegalStateException("Result download ticket service is not configured.");
        }
        var grant = downloadTicketService.consume(taskId, ticket);
        AccessSubject subject = grant.subject();
        var detail = detailUseCase.get(TaskId.of(taskId), subject);
        if (!"SUCCEEDED".equals(detail.lifecycleStatus().name())) {
            throw new IllegalStateException("Processing task has not completed successfully.");
        }
        if (resultArchiveService == null) {
            throw new IllegalStateException("Result archive service is not configured.");
        }
        try {
            resultArchiveService.write(detail, subject, grant.fileName(), response);
        } catch (java.io.IOException exception) {
            if (response.isCommitted()) {
                // Once ZIP headers or bytes have been sent, a JSON error response is no
                // longer valid. Client cancellation is the common cause for this path.
                log.warn("Result ZIP stream ended early for task {}: {}", taskId, exception.getMessage());
                return;
            }
            throw exception;
        }
    }
}
