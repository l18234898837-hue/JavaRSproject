package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.TaskArtifactRetentionScanSource;
import com.htht.multisource.application.usecase.model.TaskArtifactRetentionScanCandidate;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.persistence.mapper.TaskArtifactRetentionScanMyBatisMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MyBatisTaskArtifactRetentionScanSource implements TaskArtifactRetentionScanSource {
    private final TaskArtifactRetentionScanMyBatisMapper mapper;

    public MyBatisTaskArtifactRetentionScanSource(TaskArtifactRetentionScanMyBatisMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<TaskArtifactRetentionScanCandidate> findWithoutRetention() {
        Map<TaskId, List<String>> storageKeysByTask = new LinkedHashMap<>();
        mapper.selectWithoutRetention().forEach(row -> {
            if (row.getTaskId() == null || row.getTaskId() <= 0) {
                throw new IllegalStateException("Historical task scan row has no valid task id.");
            }
            List<String> storageKeys = storageKeysByTask.computeIfAbsent(TaskId.of(row.getTaskId()), ignored -> new ArrayList<>());
            if (row.getStorageKey() != null) {
                storageKeys.add(row.getStorageKey());
            }
        });
        return storageKeysByTask.entrySet().stream()
            .map(entry -> new TaskArtifactRetentionScanCandidate(entry.getKey(), entry.getValue()))
            .toList();
    }
}
