package com.htht.multisource.application.port.out;

import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;

public interface TaskHistoryRepository {
    TaskHistoryPage findPage(TaskHistoryQuery query);
    StorageSummary summarize(AccessSubject subject);

    record StorageSummary(long availableArtifactCount, long recycleBinBytes, long totalArtifactBytes) { }
}
