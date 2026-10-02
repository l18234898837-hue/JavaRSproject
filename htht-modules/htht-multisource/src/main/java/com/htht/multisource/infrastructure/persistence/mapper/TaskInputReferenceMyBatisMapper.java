package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputReferenceEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.Instant;
import java.util.List;

public interface TaskInputReferenceMyBatisMapper extends BaseMapper<TaskInputReferenceEntity> {
    @Insert("INSERT INTO rs_task_input_reference(task_id,managed_file_id,source_type,source_id,checksum,reference_status) "
        + "VALUES(#{taskId},#{managedFileId},#{sourceType},#{sourceId},#{checksum},'ACTIVE') ON CONFLICT(task_id,managed_file_id) DO NOTHING")
    int insertActive(@Param("taskId") long taskId, @Param("managedFileId") long managedFileId,
                     @Param("sourceType") String sourceType, @Param("sourceId") String sourceId, @Param("checksum") String checksum);

    @Select("SELECT task_id FROM rs_task_input_reference WHERE managed_file_id=#{managedFileId} AND reference_status='ACTIVE'")
    List<Long> selectActiveTaskIds(@Param("managedFileId") long managedFileId);

    @Update("UPDATE rs_task_input_reference SET reference_status='RELEASED', released_at=#{now}, version=version+1 "
        + "WHERE task_id=#{taskId} AND reference_status='ACTIVE'")
    int releaseForTask(@Param("taskId") long taskId, @Param("operatorId") long operatorId, @Param("now") Instant now);
}
