package com.htht.multisource.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.type.JdbcType;
import com.htht.multisource.infrastructure.persistence.typehandler.PostgreSqlJsonbTypeHandler;

@TableName(value = "rs_batch_product", autoResultMap = true)
public class BatchProductEntity {
    @TableId(value = "batch_product_id", type = IdType.AUTO)
    private Long batchProductId;
    private Long batchId;
    private String packageId;
    private String productCode;
    private String profileVersion;
    private String dataShape;
    private String supportStatus;
    private Double confidence;
    @TableField(value = "input_assets_snapshot", jdbcType = JdbcType.OTHER, typeHandler = PostgreSqlJsonbTypeHandler.class)
    private String inputAssetsSnapshot;
    public Long getBatchProductId() { return batchProductId; }
    public void setBatchProductId(Long batchProductId) { this.batchProductId = batchProductId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
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
    public String getInputAssetsSnapshot() { return inputAssetsSnapshot; }
    public void setInputAssetsSnapshot(String inputAssetsSnapshot) { this.inputAssetsSnapshot = inputAssetsSnapshot; }
}
