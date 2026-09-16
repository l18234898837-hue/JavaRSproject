package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("rs_legacy_plan_archive")
public class LegacyPlanArchiveEntity {
    @TableId private Long taskId;
    private String planVersion;
    private Instant archivedAt;
    private String reason;
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String value) { planVersion = value; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant value) { archivedAt = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
