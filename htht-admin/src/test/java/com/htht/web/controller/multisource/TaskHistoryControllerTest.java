package com.htht.web.controller.multisource;

import com.htht.multisource.application.port.out.TaskArtifactRetentionRepository;
import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository.SourceInputSummary;
import com.htht.multisource.application.usecase.GetTaskDetailUseCase;
import com.htht.multisource.application.usecase.TaskArtifactPurgeUseCase;
import com.htht.multisource.application.usecase.TaskArtifactRetentionUseCase;
import com.htht.multisource.application.usecase.TaskInputPurgeUseCase;
import com.htht.multisource.application.usecase.TaskInputRetentionUseCase;
import com.htht.multisource.application.usecase.TaskHistoryQueryUseCase;
import com.htht.multisource.application.usecase.model.ArtifactRetentionOperation;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.ProcessingPlan;
import com.htht.multisource.domain.model.ProcessingStage;
import com.htht.multisource.domain.model.QualityStatus;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskArtifactRetention;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;
import com.htht.web.controller.multisource.dto.ArtifactPurgeRequest;
import com.htht.web.controller.multisource.dto.ArtifactRetentionRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class TaskHistoryControllerTest {
    private static final AccessSubject OWNER = new AccessSubject(7L);

    @Test
    void rejectsBlankRetentionOperationBeforeInvokingApplicationUseCase() {
        TaskArtifactRetentionUseCase retentionUseCase = mock(TaskArtifactRetentionUseCase.class);
        TaskHistoryController controller = controller(retentionUseCase);

        assertThatThrownBy(() -> controller.changeRetention(12L, new ArtifactRetentionRequest("  ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("operation");

        verifyNoInteractions(retentionUseCase);
    }

    @Test
    void forwardsNormalizedRetentionOperationWithTheAuthenticatedSubject() {
        TaskArtifactRetentionUseCase retentionUseCase = mock(TaskArtifactRetentionUseCase.class);
        TaskArtifactRetention recycled = TaskArtifactRetention.available(TaskId.of(12L))
            .recycle(OWNER, Instant.parse("2026-09-30T08:00:00Z"));
        when(retentionUseCase.change(TaskId.of(12L), OWNER, ArtifactRetentionOperation.RECYCLE)).thenReturn(recycled);
        TaskHistoryController controller = controller(retentionUseCase);

        var response = controller.changeRetention(12L, new ArtifactRetentionRequest("recycle"));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().status()).isEqualTo(TaskArtifactRetentionStatus.RECYCLE_BIN);
        verify(retentionUseCase).change(TaskId.of(12L), OWNER, ArtifactRetentionOperation.RECYCLE);
    }

    @Test
    void parsesHistoryFiltersAndUsesTheAuthenticatedSubject() {
        TaskHistoryQueryUseCase historyQuery = mock(TaskHistoryQueryUseCase.class);
        when(historyQuery.query(org.mockito.ArgumentMatchers.any(TaskHistoryQuery.class)))
            .thenReturn(new TaskHistoryPage(List.of(), 0L, 2, 50));
        TaskHistoryController controller = controller(historyQuery);

        var response = controller.history("  GF-6-WFV-L1A  ", "succeeded", "2026-09-01T00:00:00Z",
            "2026-09-30T00:00:00Z", "pinned", 2, 50);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().page()).isEqualTo(2);
        verify(historyQuery).query(new TaskHistoryQuery(OWNER, "GF-6-WFV-L1A", TaskLifecycleStatus.SUCCEEDED,
            Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"),
            TaskArtifactRetentionStatus.PINNED, 2, 50));
    }

    @Test
    void forwardsSourceRetentionFilterSeparatelyFromArtifactRetention() {
        TaskHistoryQueryUseCase historyQuery = mock(TaskHistoryQueryUseCase.class);
        when(historyQuery.query(org.mockito.ArgumentMatchers.any(TaskHistoryQuery.class)))
            .thenReturn(new TaskHistoryPage(List.of(), 0L, 1, 10));
        TaskHistoryController controller = controller(historyQuery);

        controller.history(null, null, null, null, "RECYCLE_BIN", "PURGED", 1, 10);

        verify(historyQuery).query(new TaskHistoryQuery(OWNER, null, null, null, null,
            TaskArtifactRetentionStatus.RECYCLE_BIN, 1, 10, TaskInputRetentionStatus.PURGED));
    }

    @Test
    void forwardsTrimmedHistoryKeywordForFuzzySearch() {
        TaskHistoryQueryUseCase historyQuery = mock(TaskHistoryQueryUseCase.class);
        when(historyQuery.query(org.mockito.ArgumentMatchers.any(TaskHistoryQuery.class)))
            .thenReturn(new TaskHistoryPage(List.of(), 0L, 1, 10));
        TaskHistoryController controller = controller(historyQuery);

        controller.history(null, "  H2D_OPER  ", null, null, null, null, null, 1, 10);

        verify(historyQuery).query(new TaskHistoryQuery(OWNER, null, "H2D_OPER", null, null,
            null, null, 1, 10, null));
    }

    @Test
    void rejectsMissingPurgeTaskNumberWithoutCallingPurgeUseCase() {
        TaskArtifactPurgeUseCase purgeUseCase = mock(TaskArtifactPurgeUseCase.class);
        TaskHistoryController controller = controller(purgeUseCase);

        assertThatThrownBy(() -> controller.purge(12L, new ArtifactPurgeRequest("  ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Task number confirmation");

        verifyNoInteractions(purgeUseCase);
    }

    @Test
    void assemblesHistoryDetailForTheAuthenticatedTaskAndSumsArtifactSize() {
        GetTaskDetailUseCase detailUseCase = mock(GetTaskDetailUseCase.class);
        TaskArtifactRetentionRepository retentionRepository = mock(TaskArtifactRetentionRepository.class);
        TaskDetailView detail = detail(TaskId.of(12L));
        when(detailUseCase.get(TaskId.of(12L), OWNER)).thenReturn(detail);
        when(retentionRepository.findOrCreate(TaskId.of(12L))).thenReturn(TaskArtifactRetention.available(TaskId.of(12L)));
        TaskHistoryController controller = controller(detailUseCase, retentionRepository);

        var response = controller.detail(12L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().detail()).isSameAs(detail);
        assertThat(response.getBody().getData().artifactSizeBytes()).isEqualTo(64L);
        verify(detailUseCase).get(TaskId.of(12L), OWNER);
        verify(retentionRepository).findOrCreate(TaskId.of(12L));
    }

    @Test
    void includesActiveSourceInputMetricsSeparatelyFromArtifactMetrics() {
        GetTaskDetailUseCase detailUseCase = mock(GetTaskDetailUseCase.class);
        TaskArtifactRetentionRepository retentionRepository = mock(TaskArtifactRetentionRepository.class);
        TaskInputReferenceRepository inputReferences = mock(TaskInputReferenceRepository.class);
        TaskDetailView detail = detail(TaskId.of(12L));
        when(detailUseCase.get(TaskId.of(12L), OWNER)).thenReturn(detail);
        when(retentionRepository.findOrCreate(TaskId.of(12L))).thenReturn(TaskArtifactRetention.available(TaskId.of(12L)));
        when(inputReferences.summarizeActiveInputs(TaskId.of(12L))).thenReturn(new SourceInputSummary(3L, 123L));
        TaskHistoryController controller = new TaskHistoryController(
            () -> OWNER,
            mock(TaskHistoryQueryUseCase.class),
            detailUseCase,
            retentionRepository,
            mock(TaskArtifactRetentionUseCase.class),
            mock(TaskArtifactPurgeUseCase.class),
            mock(TaskInputRetentionUseCase.class),
            mock(TaskInputPurgeUseCase.class),
            mock(com.htht.multisource.application.port.out.ProcessingTaskRepository.class),
            mock(TaskHistoryRepository.class),
            inputReferences
        );

        var response = controller.detail(12L);

        assertThat(response.getBody().getData().artifactSizeBytes()).isEqualTo(64L);
        assertThat(response.getBody().getData().sourceInputCount()).isEqualTo(3L);
        assertThat(response.getBody().getData().sourceInputSizeBytes()).isEqualTo(123L);
        verify(detailUseCase).get(TaskId.of(12L), OWNER);
        verify(inputReferences).summarizeActiveInputs(TaskId.of(12L));
    }

    @Test
    void returnsStorageSummaryForTheAuthenticatedSubject() {
        TaskHistoryQueryUseCase historyQuery = mock(TaskHistoryQueryUseCase.class);
        when(historyQuery.storageSummary(OWNER)).thenReturn(new TaskHistoryRepository.StorageSummary(3L, 40L, 100L));
        TaskHistoryController controller = controller(historyQuery);

        var response = controller.storageSummary();

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().availableArtifactCount()).isEqualTo(3L);
        assertThat(response.getBody().getData().recycleBinBytes()).isEqualTo(40L);
        assertThat(response.getBody().getData().totalArtifactBytes()).isEqualTo(100L);
        verify(historyQuery).storageSummary(OWNER);
    }

    private TaskHistoryController controller(TaskArtifactRetentionUseCase retentionUseCase) {
        return new TaskHistoryController(
            () -> OWNER,
            mock(TaskHistoryQueryUseCase.class),
            mock(GetTaskDetailUseCase.class),
            mock(TaskArtifactRetentionRepository.class),
            retentionUseCase,
            mock(TaskArtifactPurgeUseCase.class)
        );
    }

    private TaskHistoryController controller(TaskHistoryQueryUseCase historyQuery) {
        return new TaskHistoryController(
            () -> OWNER,
            historyQuery,
            mock(GetTaskDetailUseCase.class),
            mock(TaskArtifactRetentionRepository.class),
            mock(TaskArtifactRetentionUseCase.class),
            mock(TaskArtifactPurgeUseCase.class)
        );
    }

    private TaskHistoryController controller(TaskArtifactPurgeUseCase purgeUseCase) {
        return new TaskHistoryController(
            () -> OWNER,
            mock(TaskHistoryQueryUseCase.class),
            mock(GetTaskDetailUseCase.class),
            mock(TaskArtifactRetentionRepository.class),
            mock(TaskArtifactRetentionUseCase.class),
            purgeUseCase
        );
    }

    private TaskHistoryController controller(GetTaskDetailUseCase detailUseCase,
                                              TaskArtifactRetentionRepository retentionRepository) {
        return new TaskHistoryController(
            () -> OWNER,
            mock(TaskHistoryQueryUseCase.class),
            detailUseCase,
            retentionRepository,
            mock(TaskArtifactRetentionUseCase.class),
            mock(TaskArtifactPurgeUseCase.class)
        );
    }

    private TaskDetailView detail(TaskId taskId) {
        return new TaskDetailView(taskId, 1L, TaskLifecycleStatus.SUCCEEDED, QualityStatus.NOT_CHECKED,
            new ProcessingPlan("plan-1", List.of(new ProcessingStage("READ", "READ_SOURCE", List.of()))),
            List.of(), List.of(), List.of(
                new TaskArtifactView(new StageId(1L), "STANDARD_COG", new StorageUri("rs://output/7/task-12/standard.tif"),
                    "sha256:one", 32L, "FINAL"),
                new TaskArtifactView(new StageId(2L), "PREVIEW_PNG", new StorageUri("rs://output/7/task-12/preview.png"),
                    "sha256:two", 32L, "INTERMEDIATE")
            ));
    }
}
