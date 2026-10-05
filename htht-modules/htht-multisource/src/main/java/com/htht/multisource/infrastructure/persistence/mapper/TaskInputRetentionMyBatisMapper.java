package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputRetentionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TaskInputRetentionMyBatisMapper extends BaseMapper<TaskInputRetentionEntity> {
    @Insert("INSERT INTO rs_task_input_retention(task_id,workspace_key,retention_status,expires_at,scheduled_at,"
        + "purge_started_at,purged_at,recycled_at,recycle_expires_at,updated_at,operator_id,failure_reason,version) VALUES(#{value.taskId},"
        + "#{value.workspaceKey},#{value.retentionStatus},#{value.expiresAt},#{value.scheduledAt},"
        + "#{value.purgeStartedAt},#{value.purgedAt},#{value.recycledAt},#{value.recycleExpiresAt},#{value.updatedAt},#{value.operatorId},"
        + "#{value.failureReason},#{value.version}) ON CONFLICT (task_id) DO NOTHING")
    int insertIfAbsent(@Param("value") TaskInputRetentionEntity value);

    @Update("UPDATE rs_task_input_retention SET workspace_key=#{next.workspaceKey},retention_status=#{next.retentionStatus},"
        + "expires_at=#{next.expiresAt},scheduled_at=#{next.scheduledAt},purge_started_at=#{next.purgeStartedAt},"
        + "purged_at=#{next.purgedAt},recycled_at=#{next.recycledAt},recycle_expires_at=#{next.recycleExpiresAt},"
        + "updated_at=#{next.updatedAt},operator_id=#{next.operatorId},"
        + "failure_reason=#{next.failureReason},version=#{next.version} WHERE task_id=#{next.taskId} "
        + "AND version=#{expectedVersion}")
    int updateIfVersionMatches(@Param("expectedVersion") long expectedVersion,
                               @Param("next") TaskInputRetentionEntity next);
}
