package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;

import java.util.List;

public interface TaskInputAssetMyBatisMapper extends BaseMapper<TaskInputAssetEntity> {
    default List<TaskInputAssetEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<TaskInputAssetEntity>()
            .eq(TaskInputAssetEntity::getTaskId, taskId)
            .orderByAsc(TaskInputAssetEntity::getPackageId)
            .orderByAsc(TaskInputAssetEntity::getRole)
            .orderByAsc(TaskInputAssetEntity::getOrdinal)
            .orderByAsc(TaskInputAssetEntity::getInputAssetId));
    }
}
