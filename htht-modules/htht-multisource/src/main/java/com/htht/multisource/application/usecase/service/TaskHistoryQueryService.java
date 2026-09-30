package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.TaskHistoryQueryUseCase;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;

import java.util.Objects;

public final class TaskHistoryQueryService implements TaskHistoryQueryUseCase {
    private final TaskHistoryRepository repository;

    public TaskHistoryQueryService(TaskHistoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override public TaskHistoryPage query(TaskHistoryQuery query) {
        return repository.findPage(Objects.requireNonNull(query, "query"));
    }

    @Override public TaskHistoryRepository.StorageSummary storageSummary(AccessSubject subject) {
        return repository.summarize(Objects.requireNonNull(subject, "subject"));
    }
}
