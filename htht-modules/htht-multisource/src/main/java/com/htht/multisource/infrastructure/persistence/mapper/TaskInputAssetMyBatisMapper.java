package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputAssetEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaskInputAssetMyBatisMapper extends BaseMapper<TaskInputAssetEntity> {
    default List<TaskInputAssetEntity> selectByTaskIdAndWorkspace(@Param("taskId") long taskId,
                                                                   @Param("workspaceKey") String workspaceKey) {
        return selectList(new LambdaQueryWrapper<TaskInputAssetEntity>()
            .eq(TaskInputAssetEntity::getTaskId, taskId)
            .apply("EXISTS (SELECT 1 FROM rs_processing_task t "
                + "WHERE t.task_id = rs_task_input_asset.task_id "
                + "AND t.workspace_key = {0})", workspaceKey)
            .orderByAsc(TaskInputAssetEntity::getPackageId)
            .orderByAsc(TaskInputAssetEntity::getRole)
            .orderByAsc(TaskInputAssetEntity::getOrdinal)
            .orderByAsc(TaskInputAssetEntity::getInputAssetId));
    }

    default List<TaskInputAssetEntity> selectByTaskId(long taskId) {
        return selectList(new LambdaQueryWrapper<TaskInputAssetEntity>()
            .eq(TaskInputAssetEntity::getTaskId, taskId)
            .orderByAsc(TaskInputAssetEntity::getPackageId)
            .orderByAsc(TaskInputAssetEntity::getRole)
            .orderByAsc(TaskInputAssetEntity::getOrdinal)
            .orderByAsc(TaskInputAssetEntity::getInputAssetId));
    }
}
