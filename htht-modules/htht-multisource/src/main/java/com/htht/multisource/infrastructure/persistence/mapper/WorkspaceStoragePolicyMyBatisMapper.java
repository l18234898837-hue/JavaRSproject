package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.WorkspaceStoragePolicyEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface WorkspaceStoragePolicyMyBatisMapper extends BaseMapper<WorkspaceStoragePolicyEntity> {
    @Update("UPDATE rs_workspace_storage_policy SET input_retention_days=#{inputRetentionDays}, "
        + "orphan_upload_ttl_hours=#{orphanUploadTtlHours}, worker_directory_ttl_hours=#{workerDirectoryTtlHours}, "
        + "auto_cleanup_enabled=#{autoCleanupEnabled}, version=version+1, updated_at=CURRENT_TIMESTAMP "
        + "WHERE workspace_key=#{workspaceKey} AND version=#{expectedVersion}")
    int updateIfVersion(@Param("workspaceKey") String workspaceKey,
                        @Param("inputRetentionDays") int inputRetentionDays,
                        @Param("orphanUploadTtlHours") int orphanUploadTtlHours,
                        @Param("workerDirectoryTtlHours") int workerDirectoryTtlHours,
                        @Param("autoCleanupEnabled") boolean autoCleanupEnabled,
                        @Param("expectedVersion") long expectedVersion);
}
