package com.htht.web.controller.multisource;

import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageExecutionStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QualityReportBuilderTest {
    @Test
    void reportsFailedStageAndMissingDeclaredOutput() {
        StageId stageId = new StageId(201L);
        ProcessingStage stage = new ProcessingStage(
            "READ", "READ_SOURCE", List.of(), List.of(), List.of(
                new StageOutput("SOURCE_JSON", "source.json", StageOutput.Retention.INTERMEDIATE)
            ), "{}"
        );
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.FAILED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(stage)), List.of(),
            List.of(new TaskStageView(stageId, "READ", StageExecutionStatus.FAILED, 1,
                "INVALID_REQUEST", "输入无效")), List.of()
        );

        var report = new QualityReportBuilder().build(detail);

        assertThat(report.overallStatus()).isEqualTo("FAIL");
        assertThat(report.summary().failedChecks()).isGreaterThanOrEqualTo(1);
        assertThat(report.checks()).extracting("status").contains("FAIL");
        assertThat(report.ruleVersion()).isEqualTo(QualityReportBuilder.RULE_VERSION);
        assertThat(report.algorithmEvaluation().catalogVersion()).isEqualTo(AlgorithmEvaluationCatalog.VERSION);
    }

    @Test
    void warnsForUnknownBinaryAndFailsForEmptyArtifact() {
        StageId stageId = new StageId(201L);
        ProcessingStage stage = new ProcessingStage(
            "READ", "READ_SOURCE", List.of(), List.of(), List.of(
                new StageOutput("RAW_OUTPUT", "raw.bin", StageOutput.Retention.INTERMEDIATE)
            ), "{}"
        );
        TaskArtifactView artifact = new TaskArtifactView(stageId, "RAW_OUTPUT",
            new StorageUri("rs://output/task/raw.bin"), "sha256:test", 0L, "INTERMEDIATE");
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(stage)), List.of(),
            List.of(new TaskStageView(stageId, "READ", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(artifact)
        );

        var report = new QualityReportBuilder().build(detail);

        assertThat(report.overallStatus()).isEqualTo("FAIL");
        assertThat(report.checks()).extracting("status").contains("WARN", "FAIL");
    }

    @Test
    void mergesRegisteredEvaluationReportArtifactIntoQualityReport() throws Exception {
        StageId stageId = new StageId(201L);
        TaskArtifactView artifact = new TaskArtifactView(stageId, "ALGORITHM_EVALUATION_REPORT",
            new StorageUri("rs://output/7/task/algorithm-evaluation-report.json"),
            "sha256:report", 128L, "FINAL");
        ProductCandidate candidate = new ProductCandidate(
            "pkg", new ProductProfileKey("GF-6-WFV-L1A", "v1"), ProductDataShape.RASTER_GRID,
            ProductSupportStatus.SUPPORTED,
            List.of(new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
                new StorageUri("rs://sys-file/42"), "sha256:input", true)), 0.9);
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))),
            List.of(candidate), List.of(new TaskStageView(stageId, "READ", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(artifact));

        var evaluation = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {
                  "reportVersion":"algorithm-evaluation-report-v1",
                  "profileCode":"GF-6-WFV-L1A",
                  "metrics":[
                    {"key":"rpc_geolocation_error","status":"OBSERVED","value":2.31,
                     "reason":"已根据参考数据计算"}
                  ]
                }
                """);
        var report = new QualityReportBuilder().build(detail, (ignoredDetail, ignoredSubject, ignoredArtifact, format) ->
            evaluation, new AccessSubject(7L));

        var metric = report.algorithmEvaluation().metrics().stream()
            .filter(item -> item.key().equals("rpc_geolocation_error")).findFirst().orElseThrow();
        assertThat(metric.status()).isEqualTo("OBSERVED");
        assertThat(metric.observedValue()).isEqualTo("2.31");
        assertThat(metric.message()).isEqualTo("已根据参考数据计算");
    }
}
