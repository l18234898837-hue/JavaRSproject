package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskEventEntity;
import org.apache.ibatis.annotations.Insert;

public interface TaskEventMyBatisMapper extends BaseMapper<TaskEventEntity> {

    @Insert("INSERT INTO rs_task_event (stage_id, execution_id, attempt, event_sequence, event_type, acceptance_status, payload, occurred_at) "
        + "VALUES (#{stageId}, #{executionId}, #{attempt}, #{eventSequence}, #{eventType}, #{acceptanceStatus}, "
        + "#{payload,typeHandler=com.htht.multisource.infrastructure.persistence.typehandler.PostgreSqlJsonbTypeHandler,jdbcType=OTHER}, #{occurredAt}) "
        + "ON CONFLICT (stage_id, attempt, event_sequence) DO NOTHING")
    int insertIfAbsent(TaskEventEntity entity);
}
