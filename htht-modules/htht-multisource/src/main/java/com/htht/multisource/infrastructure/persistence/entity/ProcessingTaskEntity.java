package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;
import com.htht.multisource.infrastructure.persistence.typehandler.PostgreSqlJsonbTypeHandler;

@TableName(value = "rs_processing_task", autoResultMap = true)
public class ProcessingTaskEntity {

    @TableId(value = "task_id", type = IdType.INPUT)
    private Long taskId;
    private Long batchId;
    private String taskNo;
    private String name;
    private Long creatorId;
    private String lifecycleStatus;
    private String qualityStatus;
    @TableField(value = "parameter_snapshot", jdbcType = JdbcType.OTHER, typeHandler = PostgreSqlJsonbTypeHandler.class)
    private String parameterSnapshot;
    private String outputUri;
    @TableField(value = "plan_snapshot", jdbcType = JdbcType.OTHER, typeHandler = PostgreSqlJsonbTypeHandler.class)
    private String planSnapshot;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
    public String getParameterSnapshot() { return parameterSnapshot; }
    public void setParameterSnapshot(String parameterSnapshot) { this.parameterSnapshot = parameterSnapshot; }
    public String getOutputUri() { return outputUri; }
    public void setOutputUri(String outputUri) { this.outputUri = outputUri; }
    public String getPlanSnapshot() { return planSnapshot; }
    public void setPlanSnapshot(String planSnapshot) { this.planSnapshot = planSnapshot; }
}
