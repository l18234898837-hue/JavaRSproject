package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.ProcessingTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProcessingTaskMyBatisMapper extends BaseMapper<ProcessingTaskEntity> {

    @Update("UPDATE rs_processing_task SET lifecycle_status = #{status} WHERE task_id = #{taskId}")
    int updateLifecycleStatus(@Param("taskId") long taskId, @Param("status") String status);
}
