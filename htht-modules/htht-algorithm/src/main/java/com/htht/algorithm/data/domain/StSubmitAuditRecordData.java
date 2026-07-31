package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 提交数据审核管理数据表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-30
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("st_submit_audit_record_data")
public class StSubmitAuditRecordData {

    /**
     * 审核记录id（关联 st_submit_audit_record.id）
     */
    private Long recordId;

    /**
     * 数据id
     */
    private Long dataId;

    /**
     * 数据类型id
     */
    private Long dataTypeId;

    /**
     * 审核状态（1:待审批，2:已通过，3:以驳回）
     */
    private Short auditStatus;
}
