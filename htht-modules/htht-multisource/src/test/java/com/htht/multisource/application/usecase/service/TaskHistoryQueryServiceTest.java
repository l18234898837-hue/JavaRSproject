package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.model.TaskHistoryItem;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskArtifactRetentionStatus;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.domain.model.TaskLifecycleStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskHistoryQueryServiceTest {

    @Test
    void queryPassesTheAuthenticatedOwnerToRepository() {
        RecordingRepository repository = new RecordingRepository();
        TaskHistoryQueryService service = new TaskHistoryQueryService(repository);

        TaskHistoryPage page = service.query(new TaskHistoryQuery(
            new AccessSubject(7L), null, null, null, null, null, 1, 20
        ));

        assertThat(page.items()).extracting(TaskHistoryItem::taskId).containsExactly(TaskId.of(101L));
        assertThat(repository.received.subject().userId()).isEqualTo(7L);
    }

    private static final class RecordingRepository implements TaskHistoryRepository {
        private TaskHistoryQuery received;

        @Override
        public TaskHistoryPage findPage(TaskHistoryQuery query) {
            received = query;
            return new TaskHistoryPage(List.of(new TaskHistoryItem(
                TaskId.of(101L), "TASK-101", "Owned task", List.of("GF-6-WFV-L1A"),
                Instant.parse("2026-09-30T08:00:00Z"), TaskLifecycleStatus.SUCCEEDED,
                2, 123L, TaskArtifactRetentionStatus.AVAILABLE
            )), 1, 1, 20);
        }

        @Override
        public StorageSummary summarize(AccessSubject subject) {
            return new StorageSummary(0, 0, 0);
        }
    }
}
