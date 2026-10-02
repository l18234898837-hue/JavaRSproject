package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.TaskHistoryQueryUseCase;
import com.htht.multisource.application.usecase.model.TaskHistoryPage;
import com.htht.multisource.application.usecase.model.TaskHistoryQuery;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.TaskInputRetentionStatus;

import java.util.Objects;

public final class TaskHistoryQueryService implements TaskHistoryQueryUseCase {
    private final TaskHistoryRepository repository;
    private final com.htht.multisource.application.usecase.TaskInputRetentionUseCase inputRetention;

    public TaskHistoryQueryService(TaskHistoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.inputRetention = null;
    }

    public TaskHistoryQueryService(TaskHistoryRepository repository,
                                   com.htht.multisource.application.usecase.TaskInputRetentionUseCase inputRetention) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.inputRetention = Objects.requireNonNull(inputRetention, "inputRetention");
    }

    @Override public TaskHistoryPage query(TaskHistoryQuery query) {
        var page = repository.findPage(Objects.requireNonNull(query, "query"));
        if (inputRetention == null || page.items().isEmpty()) return page;
        var items = page.items().stream().map(item -> inputRetention.find(item.taskId())
            .map(retention -> new com.htht.multisource.application.usecase.model.TaskHistoryItem(
                item.taskId(), item.taskNo(), item.taskName(), item.productCodes(), item.completedAt(),
                item.lifecycleStatus(), item.artifactCount(), item.artifactSizeBytes(), item.retentionStatus(),
                retention.status()))
            .orElse(item)).toList();
        return new TaskHistoryPage(items, page.total(), page.page(), page.pageSize());
    }

    @Override public TaskHistoryRepository.StorageSummary storageSummary(AccessSubject subject) {
        return repository.summarize(Objects.requireNonNull(subject, "subject"));
    }
}
