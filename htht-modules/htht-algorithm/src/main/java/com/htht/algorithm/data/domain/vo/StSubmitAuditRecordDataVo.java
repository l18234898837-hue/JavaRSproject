package com.htht.algorithm.data.domain.vo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/30 17:28
 **/
@Data
@Accessors(chain = true)
public class StSubmitAuditRecordDataVo {


    /**
     * 审核记录id（关联 st_submit_audit_record.id）
     */
    private Long recordId;

    /**
     * 数据id
     */
    private Long dataId;

    /**
     * 数据名称
     */
    private String dataName;

    /**
     * 数据格式
     */
    private String dataType;

    /**
     * 卫星传感器
     */
    private String satelliteSensor;

    /**
     * 数据级别
     */
    private String dataLevel;
    /**
     * 区域
     */
    private String region;

    /**
     * 审核状态（1:待审批，2:已通过，3:以驳回）
     */
    private Short auditStatus;

}
