package com.htht.multisource.infrastructure.persistence.mapper;

import com.htht.multisource.infrastructure.persistence.entity.ProcessingStorageSummaryRow;
import com.htht.multisource.infrastructure.persistence.entity.TaskHistoryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.Instant;
import java.util.List;

public interface TaskHistoryMyBatisMapper {
    @Select("<script>SELECT t.task_id taskId,t.task_no taskNo,t.name taskName,COALESCE(p.product_codes,'') productCodes,t.updated_at completedAt,t.lifecycle_status lifecycleStatus,COALESCE(a.artifact_count,0) artifactCount,COALESCE(a.artifact_size_bytes,0) artifactSizeBytes,COALESCE(r.retention_status,'AVAILABLE') retentionStatus FROM rs_processing_task t LEFT JOIN (SELECT task_id,string_agg(DISTINCT product_code,',' ORDER BY product_code) product_codes FROM rs_task_product GROUP BY task_id) p ON p.task_id=t.task_id LEFT JOIN (SELECT task_id,count(*) artifact_count,COALESCE(sum(size_bytes),0) artifact_size_bytes FROM rs_artifact GROUP BY task_id) a ON a.task_id=t.task_id LEFT JOIN rs_task_artifact_retention r ON r.task_id=t.task_id WHERE t.creator_id=#{userId}<if test='productCode != null and productCode != &quot;&quot;'> AND EXISTS (SELECT 1 FROM rs_task_product fp WHERE fp.task_id=t.task_id AND fp.product_code=#{productCode})</if><if test='lifecycle != null'> AND t.lifecycle_status=#{lifecycle}</if><if test='from != null'> AND t.updated_at &gt;= #{from}</if><if test='to != null'> AND t.updated_at &lt;= #{to}</if><if test='retention != null'> AND COALESCE(r.retention_status,'AVAILABLE')=#{retention}</if> ORDER BY t.updated_at DESC,t.task_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<TaskHistoryRow> selectPage(@Param("userId") long userId, @Param("productCode") String productCode,
        @Param("lifecycle") String lifecycle, @Param("from") Instant from, @Param("to") Instant to,
        @Param("retention") String retention, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT count(*) FROM rs_processing_task t LEFT JOIN rs_task_artifact_retention r ON r.task_id=t.task_id WHERE t.creator_id=#{userId}<if test='productCode != null and productCode != &quot;&quot;'> AND EXISTS (SELECT 1 FROM rs_task_product fp WHERE fp.task_id=t.task_id AND fp.product_code=#{productCode})</if><if test='lifecycle != null'> AND t.lifecycle_status=#{lifecycle}</if><if test='from != null'> AND t.updated_at &gt;= #{from}</if><if test='to != null'> AND t.updated_at &lt;= #{to}</if><if test='retention != null'> AND COALESCE(r.retention_status,'AVAILABLE')=#{retention}</if></script>")
    long countPage(@Param("userId") long userId, @Param("productCode") String productCode, @Param("lifecycle") String lifecycle, @Param("from") Instant from, @Param("to") Instant to, @Param("retention") String retention);

    @Select("SELECT count(a.artifact_id) FILTER (WHERE COALESCE(r.retention_status,'AVAILABLE') IN ('AVAILABLE','PINNED')) availableArtifactCount,COALESCE(sum(a.size_bytes) FILTER (WHERE COALESCE(r.retention_status,'AVAILABLE')='RECYCLE_BIN'),0) recycleBinBytes,COALESCE(sum(a.size_bytes),0) totalArtifactBytes FROM rs_processing_task t LEFT JOIN rs_artifact a ON a.task_id=t.task_id LEFT JOIN rs_task_artifact_retention r ON r.task_id=t.task_id WHERE t.creator_id=#{userId}")
    ProcessingStorageSummaryRow selectStorageSummary(@Param("userId") long userId);

    @Select("SELECT task_no FROM rs_processing_task WHERE task_id=#{taskId} AND creator_id=#{userId}")
    String selectTaskNo(@Param("taskId") long taskId, @Param("userId") long userId);
}
