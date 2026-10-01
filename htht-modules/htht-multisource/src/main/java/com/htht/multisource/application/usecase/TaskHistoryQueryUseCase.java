package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;

public interface TaskHistoryQueryUseCase {
    TaskHistoryPage query(TaskHistoryQuery query);
    TaskHistoryRepository.StorageSummary storageSummary(AccessSubject subject);
}
