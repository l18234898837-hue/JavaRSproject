package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputReferenceEntity;
import com.htht.multisource.infrastructure.persistence.entity.TaskInputSummaryRow;
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

    @Select("SELECT managed_file_id FROM rs_task_input_reference WHERE task_id=#{taskId} AND reference_status='ACTIVE'")
    List<Long> selectActiveManagedFileIds(@Param("taskId") long taskId);

    @Select("SELECT COUNT(DISTINCT r.managed_file_id) inputCount,COALESCE(SUM(f.size_bytes),0) sizeBytes "
        + "FROM rs_task_input_reference r JOIN rs_managed_input_file f ON f.managed_file_id=r.managed_file_id "
        + "WHERE r.task_id=#{taskId} AND r.reference_status='ACTIVE'")
    TaskInputSummaryRow selectActiveInputSummary(@Param("taskId") long taskId);

    @Update("UPDATE rs_task_input_reference SET reference_status='RELEASED', released_at=#{now}, version=version+1 "
        + "WHERE task_id=#{taskId} AND reference_status='ACTIVE'")
    int releaseForTask(@Param("taskId") long taskId, @Param("operatorId") long operatorId, @Param("now") Instant now);
}
