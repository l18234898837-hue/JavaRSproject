package com.htht.multisource.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.multisource.infrastructure.persistence.entity.LegacyPlanArchiveEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LegacyPlanArchiveMyBatisMapper extends BaseMapper<LegacyPlanArchiveEntity> {
    @Select("SELECT task_id, plan_version, archived_at, reason FROM rs_legacy_plan_archive WHERE task_id = #{taskId}")
    LegacyPlanArchiveEntity selectByTaskId(@Param("taskId") long taskId);

    @Insert("INSERT INTO rs_legacy_plan_archive(task_id, plan_version, archived_at, reason) "
        + "VALUES(#{taskId}, #{planVersion}, #{archivedAt}, #{reason}) ON CONFLICT (task_id) DO NOTHING")
    int insertIfAbsent(@Param("taskId") long taskId, @Param("planVersion") String planVersion,
                       @Param("archivedAt") java.time.Instant archivedAt, @Param("reason") String reason);
}
