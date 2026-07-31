package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * 定时器信息表，存储定时任务的配置信息
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_timer_info")
public class StTimerInfo extends BaseEntity {

    /**
     * 自增主键，唯一标识定时器配置记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 定时任务的名称
     */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /**
     * 关联的用户ID，对应用户表主键
     */
    private Long userId;

    /**
     * 任务状态：0=未启用，1=进行中
     */
    private Short status;

    /**
     * 任务类型：1=一次性定时任务，2=周期任务
     */
    @NotNull(message = "任务时间类型不能为空")
    private Short type;

    /**
     * 周期任务运行频率：1=每天，2=每周，3=每月
     */
    @NotNull(message = "任务频率不能为空")
    private Short frequency;

    /**
     * 具体任务类型：1=下载任务，2=计算任务
     */
    @NotNull(message = "任务类型不能为空")
    private Short taskType;

    /**
     * 任务描述
     */

    private String tastDescription;

    /**
     * 运行日期（字符串格式，多日期用逗号等分隔，需业务层解析）
     */
    @NotBlank(message = "运行日期不能为空")
    private String runDate;

    /**
     * 运行时间（字符串格式，多时间点用逗号等分隔，需业务层解析）
     */
    @NotBlank(message = "运行时间不能为空")
    private String runTime;

    /**
     * 运行参数字符串，存储任务所需的复杂参数
     */
    @NotBlank(message = "运行参数不能为空")
    private String runText;
}
