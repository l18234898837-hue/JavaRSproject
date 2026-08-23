package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskStageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

public interface TaskStageMyBatisMapper extends BaseMapper<TaskStageEntity> {

    default List<TaskStageEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<TaskStageEntity>()
            .eq(TaskStageEntity::getTaskId, taskId)
            .orderByAsc(TaskStageEntity::getStageId));
    }

    default List<TaskStageEntity> selectExpiredRunning(Instant now) {
        return selectList(new LambdaQueryWrapper<TaskStageEntity>()
            .eq(TaskStageEntity::getExecutionStatus, "RUNNING")
            .lt(TaskStageEntity::getLeaseExpireAt, now)
            .orderByAsc(TaskStageEntity::getLeaseExpireAt));
    }

    @Select("WITH candidate AS ("
        + " SELECT s.stage_id FROM rs_task_stage s"
        + " WHERE s.task_id = #{taskId}"
        + " AND s.execution_status IN ('PENDING', 'RETRYING')"
        + " AND NOT EXISTS ("
        + "   SELECT 1 FROM rs_task_stage_dependency d"
        + "   JOIN rs_task_stage prerequisite ON prerequisite.stage_id = d.depends_on_stage_id"
        + "   WHERE d.stage_id = s.stage_id"
        + "   AND prerequisite.execution_status <> 'SUCCEEDED'"
        + " )"
        + " ORDER BY s.stage_id"
        + " LIMIT 1"
        + " FOR UPDATE OF s SKIP LOCKED"
        + ")"
        + " UPDATE rs_task_stage s"
        + " SET execution_status = 'RUNNING', execution_id = #{executionId}, worker_id = #{workerId},"
        + "     lease_expire_at = #{leaseExpiresAt}, heartbeat_at = #{startedAt}, started_at = #{startedAt},"
        + "     finished_at = NULL, error_code = NULL, error_message = NULL"
        + " FROM candidate"
        + " WHERE s.stage_id = candidate.stage_id"
        + " RETURNING s.*")
    TaskStageEntity claimNextRunnable(
        @Param("taskId") long taskId,
        @Param("executionId") String executionId,
        @Param("workerId") String workerId,
        @Param("leaseExpiresAt") Instant leaseExpiresAt,
        @Param("startedAt") Instant startedAt
    );

    @Update("UPDATE rs_task_stage SET task_id = #{taskId}, stage_key = #{stageKey}, execution_status = #{executionStatus},"
        + " attempt = #{attempt}, execution_id = #{executionId}, worker_id = #{workerId},"
        + " lease_expire_at = #{leaseExpireAt}, heartbeat_at = #{heartbeatAt}, error_code = #{errorCode},"
        + " error_message = #{errorMessage}, log_uri = #{logUri}, started_at = #{startedAt}, finished_at = #{finishedAt}"
        + " WHERE stage_id = #{stageId}")
    int updateExecutionState(TaskStageEntity entity);
}
