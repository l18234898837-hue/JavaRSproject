package com.htht.multisource.infrastructure.persistence.mapper;

import com.htht.multisource.infrastructure.persistence.entity.TaskArtifactRetentionScanRow;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TaskArtifactRetentionScanMyBatisMapper {
    @Select("SELECT t.task_id taskId, a.storage_key storageKey FROM rs_processing_task t "
        + "LEFT JOIN rs_task_artifact_retention r ON r.task_id=t.task_id "
        + "LEFT JOIN rs_artifact a ON a.task_id=t.task_id "
        + "WHERE r.task_id IS NULL AND t.lifecycle_status IN ('SUCCEEDED','FAILED','CANCELED') "
        + "ORDER BY t.task_id ASC, a.artifact_id ASC")
    List<TaskArtifactRetentionScanRow> selectWithoutRetention();
}
