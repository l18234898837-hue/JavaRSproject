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

    @Test
    void treatsSparseHy1cRasterCoverageAsDescriptiveQualityNotHardFailure() throws Exception {
        StageId stageId = new StageId(301L);
        TaskArtifactView standard = new TaskArtifactView(stageId, "STANDARD_COG",
            new StorageUri("rs://output/101/standard/czi-l2a.tif"), "sha256:standard", 1024L, "FINAL");
        TaskArtifactView saliencyMask = new TaskArtifactView(stageId, "SALIENCY_MASK_COG",
            new StorageUri("rs://output/101/saliency/mask.tif"), "sha256:mask", 1024L, "FINAL");
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-v3", List.of(new ProcessingStage("WRITE", "WRITE_STANDARD_COG", List.of()))),
            List.of(new ProductCandidate("pkg", new ProductProfileKey("HY-1C-CZI", "L2A-v1"),
                ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED,
                List.of(new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
                    new StorageUri("rs://sys-file/42"), "sha256:input", true)), 1.0)),
            List.of(new TaskStageView(stageId, "WRITE", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(standard, saliencyMask));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var maskInspection = mapper.readTree("""
            {"quality":{"boundsValid":true,"validPixelRatio":0.0,"hasNanOrInf":false,
            "width":10,"height":10,"bandCount":1,"statistics":{}}}
            """);
        var standardInspection = mapper.readTree("""
            {"quality":{"boundsValid":true,"validPixelRatio":0.01,"hasNanOrInf":false,
            "width":10,"height":10,"bandCount":1,"statistics":{"min":1,"max":2}}}
            """);
        var report = new QualityReportBuilder().build(detail, (ignoredDetail, ignoredSubject, artifact, format) -> {
            if ("SALIENCY_MASK_COG".equals(artifact.type())) {
                return maskInspection;
            }
            return standardInspection;
        }, new AccessSubject(7L));

        assertThat(report.checks())
            .filteredOn(check -> "有效覆盖率".equals(check.name()))
            .extracting("status")
            .containsExactly("PASS");
        assertThat(report.checks())
            .filteredOn(check -> "候选区域比例".equals(check.name()))
            .extracting("status")
            .containsExactly("PASS");
    }

    @Test
    void merges_identical_generic_raster_checks_but_keeps_per_artifact_availability() throws Exception {
        StageId stageId = new StageId(401L);
        TaskArtifactView first = new TaskArtifactView(stageId, "FIRST_COG",
            new StorageUri("rs://output/101/first.tif"), "sha256:first", 1024L, "FINAL");
        TaskArtifactView second = new TaskArtifactView(stageId, "SECOND_COG",
            new StorageUri("rs://output/101/second.tif"), "sha256:second", 2048L, "FINAL");
        ProcessingStage stage = new ProcessingStage("WRITE", "WRITE", List.of());
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-v3", List.of(stage)), List.of(),
            List.of(new TaskStageView(stageId, "WRITE", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(first, second));
        var inspection = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"quality":{"boundsValid":true,"validPixelRatio":0.8,"hasNanOrInf":false,
            "width":77,"height":41,"bandCount":1,"statistics":{"min":1,"max":2}}}
            """);

        var report = new QualityReportBuilder().build(detail,
            (ignoredDetail, ignoredSubject, ignoredArtifact, format) -> inspection,
            new AccessSubject(7L));

        assertThat(report.checks()).filteredOn(check -> "栅格宽度".equals(check.name())).hasSize(1);
        assertThat(report.checks()).filteredOn(check -> "有效覆盖率".equals(check.name())).hasSize(1);
        assertThat(report.checks()).filteredOn(check -> "产物文件可用性".equals(check.name())).hasSize(2);
        assertThat(report.checks()).filteredOn(check -> "栅格宽度".equals(check.name()))
            .first().extracting(check -> check.details().get("deduplicatedCount"))
            .isEqualTo(2);
    }

    @Test
    void aggregatesAllArtifactExplainabilityInsteadOfKeepingTheLastArtifactOnly() throws Exception {
        StageId stageId = new StageId(501L);
        TaskArtifactView standard = new TaskArtifactView(stageId, "STANDARD_METADATA_JSON",
            new StorageUri("rs://output/101/standard/summary.json"), "sha256:standard", 1024L, "FINAL");
        TaskArtifactView saliency = new TaskArtifactView(stageId, "SALIENCY_METADATA_JSON",
            new StorageUri("rs://output/101/saliency/summary.json"), "sha256:saliency", 1024L, "FINAL");
        TaskDetailView detail = new TaskDetailView(
            TaskId.of(101L), 33L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-v3", List.of(new ProcessingStage("WRITE", "WRITE", List.of()))),
            List.of(new ProductCandidate("pkg", new ProductProfileKey("HY-1C-CZI", "L2A-v1"),
                ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED,
                List.of(new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"),
                    new StorageUri("rs://sys-file/42"), "sha256:input", true)), 1.0)),
            List.of(new TaskStageView(stageId, "WRITE", StageExecutionStatus.SUCCEEDED, 1)),
            List.of(saliency, standard));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var standardInspection = mapper.readTree("""
            {"explainability":{"version":"hy1c-processing-explainability-v1",
            "algorithm":"标准化处理","purpose":"生成标准产物"}}
            """);
        var saliencyInspection = mapper.readTree("""
            {"explainability":{"version":"hy1c-saliency-explainability-v1",
            "algorithm":"显著性检测","purpose":"生成候选区域"}}
            """);

        var report = new QualityReportBuilder().build(detail,
            (ignoredDetail, ignoredSubject, artifact, format) ->
                "STANDARD_METADATA_JSON".equals(artifact.type())
                    ? standardInspection : saliencyInspection,
            new AccessSubject(7L));

        var explainability = report.algorithmEvaluation().explainability();
        assertThat(explainability.path("version").asText())
            .isEqualTo("task-explainability-aggregate-v1");
        assertThat(explainability.path("components")).hasSize(2);
        assertThat(explainability.path("components").get(0).path("explainability")
            .path("algorithm").asText()).isEqualTo("标准化处理");
        assertThat(explainability.path("components").get(1).path("explainability")
            .path("algorithm").asText()).isEqualTo("显著性检测");
    }
}
