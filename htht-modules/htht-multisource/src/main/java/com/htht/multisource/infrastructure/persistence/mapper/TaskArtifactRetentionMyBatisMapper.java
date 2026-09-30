package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskArtifactRetentionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TaskArtifactRetentionMyBatisMapper extends BaseMapper<TaskArtifactRetentionEntity> {
    @Insert("INSERT INTO rs_task_artifact_retention(task_id, retention_status, pinned, version) "
        + "VALUES(#{taskId}, 'AVAILABLE', false, 0) ON CONFLICT (task_id) DO NOTHING")
    int insertAvailableIfAbsent(@Param("taskId") long taskId);

    @Insert("INSERT INTO rs_task_artifact_retention(task_id, retention_status, pinned, recycled_at, purge_started_at, "
        + "purged_at, operator_id, failure_reason, version) VALUES(#{retention.taskId}, #{retention.retentionStatus}, "
        + "#{retention.pinned}, #{retention.recycledAt}, #{retention.purgeStartedAt}, #{retention.purgedAt}, "
        + "#{retention.operatorId}, #{retention.failureReason}, #{retention.version}) ON CONFLICT (task_id) DO NOTHING")
    int insertIfAbsent(@Param("retention") TaskArtifactRetentionEntity retention);

    @Update("UPDATE rs_task_artifact_retention SET retention_status=#{next.retentionStatus}, pinned=#{next.pinned}, "
        + "recycled_at=#{next.recycledAt}, purge_started_at=#{next.purgeStartedAt}, purged_at=#{next.purgedAt}, "
        + "operator_id=#{next.operatorId}, failure_reason=#{next.failureReason}, version=#{next.version}, "
        + "updated_at=CURRENT_TIMESTAMP WHERE task_id=#{next.taskId} AND version=#{expectedVersion}")
    int updateIfVersionMatches(@Param("expectedVersion") long expectedVersion,
                               @Param("next") TaskArtifactRetentionEntity next);
}
