package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户、执行流程与数据集关联表，含名称和数字编码的执行状态，用于查询执行后的流程信息
 * </p>
 *
 * @author MSFY
 * @since 2025-08-15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_python_algorithm_user_stream")
public class StPythonAlgorithmUserStream extends BaseEntity {

    /**
     * 主键ID，唯一标识一条关联记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联记录的名称，用于直观描述该关联的用途或场景
     */
    private String name;

    /**
     * 用户唯一标识，关联用户表
     */
    private String userId;

    /**
     * 执行流程唯一标识，关联执行流程表（如python_execution_flows的id）
     */
    private Long pythonStreamId;

    /**
     * 数据集唯一标识，关联数据集表
     */
    private Long datasetId;

    /**
     * 执行状态（数字编码）：0-待执行，1-执行中，2-已完成，3-已失败
     */
    private Short executionStatus;

    /**
     * 执行错误信息
     */
    private String executionError;
}
