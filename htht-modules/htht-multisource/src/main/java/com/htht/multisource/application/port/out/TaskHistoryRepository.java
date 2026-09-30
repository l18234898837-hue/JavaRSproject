package com.htht.multisource.application.port.out;

import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskId;

import java.util.Optional;

public interface TaskHistoryRepository {
    TaskHistoryPage findPage(TaskHistoryQuery query);
    StorageSummary summarize(AccessSubject subject);
    default Optional<String> findTaskNo(TaskId taskId, AccessSubject subject) {
        return Optional.empty();
    }

    record StorageSummary(long availableArtifactCount, long recycleBinBytes, long totalArtifactBytes) { }
}
