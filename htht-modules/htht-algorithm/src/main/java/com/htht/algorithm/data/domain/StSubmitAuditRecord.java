package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 提交数据审核表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_submit_audit_record")
public class StSubmitAuditRecord extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 提交用户id
     */
    private Long submitUserId;

    /**
     * 提交数据数量
     */
    private Integer submitCount;

    /**
     * 申请理由
     */
    private String auditReason;

    /**
     * 申请状态（1:待审批，2:已审批，3:已通过，4：已驳回）
     */
    private Short auditStatus;

    /**
     * 区域
     */
    private String region;

    /**
     * 记录所有的数据名称，用于模糊搜索
     */
    private String nameKey;
}
