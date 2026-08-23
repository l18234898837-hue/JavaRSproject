package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskProductEntity;

import java.util.List;

public interface TaskProductMyBatisMapper extends BaseMapper<TaskProductEntity> {
    default List<TaskProductEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<TaskProductEntity>()
            .eq(TaskProductEntity::getTaskId, taskId).orderByAsc(TaskProductEntity::getTaskProductId));
    }
}
