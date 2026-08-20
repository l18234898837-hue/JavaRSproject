package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskStageEntity;

import java.util.List;

public interface TaskStageMyBatisMapper extends BaseMapper<TaskStageEntity> {

    default List<TaskStageEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<TaskStageEntity>()
            .eq(TaskStageEntity::getTaskId, taskId)
            .orderByAsc(TaskStageEntity::getStageId));
    }
}
