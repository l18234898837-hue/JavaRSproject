package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskHistoryRepository;
import com.htht.multisource.application.usecase.model.*;
import com.htht.multisource.domain.model.*;
import com.htht.multisource.infrastructure.persistence.entity.*;
import com.htht.multisource.infrastructure.persistence.mapper.TaskHistoryMyBatisMapper;
import java.util.List;
import java.util.Objects;

public final class MyBatisTaskHistoryRepository implements TaskHistoryRepository {
    private final TaskHistoryMyBatisMapper mapper;
    public MyBatisTaskHistoryRepository(TaskHistoryMyBatisMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }
    @Override public TaskHistoryPage findPage(TaskHistoryQuery q) {
        int offset = (q.page() - 1) * q.pageSize();
        List<TaskHistoryItem> items = mapper.selectPage(q.subject().userId(), q.productCode(), name(q.lifecycle()), q.completedFrom(), q.completedTo(), name(q.retention()), name(q.inputRetention()), q.pageSize(), offset).stream().map(this::toItem).toList();
        return new TaskHistoryPage(items, mapper.countPage(q.subject().userId(), q.productCode(), name(q.lifecycle()), q.completedFrom(), q.completedTo(), name(q.retention()), name(q.inputRetention())), q.page(), q.pageSize());
    }
    @Override public StorageSummary summarize(AccessSubject subject) {
        ProcessingStorageSummaryRow row = mapper.selectStorageSummary(subject.userId());
        return new StorageSummary(row == null || row.getAvailableArtifactCount() == null ? 0 : row.getAvailableArtifactCount(), row == null || row.getRecycleBinBytes() == null ? 0 : row.getRecycleBinBytes(), row == null || row.getTotalArtifactBytes() == null ? 0 : row.getTotalArtifactBytes());
    }
    @Override public java.util.Optional<String> findTaskNo(TaskId taskId, AccessSubject subject) {
        return java.util.Optional.ofNullable(mapper.selectTaskNo(taskId.value(), subject.userId()));
    }
    private TaskHistoryItem toItem(TaskHistoryRow r) { return new TaskHistoryItem(TaskId.of(r.getTaskId()), r.getTaskNo(), r.getTaskName(), r.getProductCodes().isBlank() ? List.of() : List.of(r.getProductCodes().split(",")), r.getCompletedAt(), TaskLifecycleStatus.valueOf(r.getLifecycleStatus()), r.getArtifactCount().intValue(), r.getArtifactSizeBytes(), TaskArtifactRetentionStatus.valueOf(r.getRetentionStatus()), com.htht.multisource.domain.model.TaskInputRetentionStatus.valueOf(r.getSourceRetentionStatus() == null ? "AVAILABLE" : r.getSourceRetentionStatus())); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
