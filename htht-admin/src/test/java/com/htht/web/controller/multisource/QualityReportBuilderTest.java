package com.htht.web.controller.multisource;

import com.htht.multisource.application.port.out.model.StageOutput;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskStageView;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
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
}
