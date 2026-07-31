package com.htht.algorithm.data.event;


import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 14:31
 **/
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Data
public class StCalculationResultApplyLogEvent extends LogBaseEvent {
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
