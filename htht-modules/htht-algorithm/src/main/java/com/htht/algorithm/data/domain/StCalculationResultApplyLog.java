package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 计算结果上传申请记录表，跟踪计算结果的上传申请及审核流程
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_calculation_result_apply_log")
public class StCalculationResultApplyLog extends BaseEntity {

    /**
     * 自增主键，唯一标识每条申请记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID，关联系统用户表的主键
     */
    private Long userId;

    /**
     * 计算结果的数据名称
     */
    private String dataName;

    /**
     * 计算结果的数据格式
     */
    private String dataType;

    /**
     * 关联的卫星或传感器标识
     */
    private String satelliteSensor;

    /**
     * 计算结果的数据级别
     */
    private String dataLevel;

    /**
     * 计算结果覆盖的地理区域（如省份、经纬度范围等）
     */
    private String region;

    /**
     * 申请状态枚举：1=审核中，2=已通过，3=已驳回
     */
    private Short applyStatus;

    /**
     * 请求组ID，用于同批次申请的归类管理
     */
    private Long applyGroupId;

    /**
     * 数据唯一ID，用于后续跟踪申请状态的变更
     */
    private Long dataId;
}
