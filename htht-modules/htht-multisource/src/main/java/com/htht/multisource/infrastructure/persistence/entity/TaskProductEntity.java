package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("rs_task_product")
public class TaskProductEntity {

    @TableId(value = "task_product_id", type = IdType.AUTO)
    private Long taskProductId;
    private Long taskId;
    private String packageId;
    private String productCode;
    private String profileVersion;
    private String dataShape;
    private String supportStatus;
    private Double confidence;

    public Long getTaskProductId() { return taskProductId; }
    public void setTaskProductId(Long taskProductId) { this.taskProductId = taskProductId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String profileVersion) { this.profileVersion = profileVersion; }
    public String getDataShape() { return dataShape; }
    public void setDataShape(String dataShape) { this.dataShape = dataShape; }
    public String getSupportStatus() { return supportStatus; }
    public void setSupportStatus(String supportStatus) { this.supportStatus = supportStatus; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}
