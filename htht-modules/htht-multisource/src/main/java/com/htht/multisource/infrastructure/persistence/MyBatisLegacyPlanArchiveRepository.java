package com.htht.multisource.infrastructure.persistence;

import com.htht.multisource.application.port.out.LegacyPlanArchiveRepository;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.infrastructure.persistence.entity.LegacyPlanArchiveEntity;
import com.htht.multisource.infrastructure.persistence.mapper.LegacyPlanArchiveMyBatisMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

public final class MyBatisLegacyPlanArchiveRepository implements LegacyPlanArchiveRepository {
    private final LegacyPlanArchiveMyBatisMapper mapper;
    public MyBatisLegacyPlanArchiveRepository(LegacyPlanArchiveMyBatisMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }
    @Override public Optional<ArchiveRecord> findByTaskId(TaskId taskId) {
        LegacyPlanArchiveEntity entity = mapper.selectByTaskId(taskId.value());
        return entity == null ? Optional.empty() : Optional.of(new ArchiveRecord(
            TaskId.of(entity.getTaskId()), entity.getPlanVersion(), entity.getArchivedAt(), entity.getReason()));
    }
    @Override public boolean insertIfAbsent(TaskId taskId, String planVersion, String reason, Instant archivedAt) {
        return mapper.insertIfAbsent(taskId.value(), planVersion, archivedAt, reason) == 1;
    }
}
