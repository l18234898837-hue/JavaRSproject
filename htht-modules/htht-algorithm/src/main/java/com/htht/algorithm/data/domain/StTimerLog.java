package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 定时器运行日志表，记录定时任务每次执行的结果
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_timer_log")
public class StTimerLog extends BaseEntity {

    /**
     * 自增主键，唯一标识运行日志记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的用户ID，对应用户表主键
     */
    private Long userId;

    /**
     * 任务类型：1=下载任务，2=计算任务
     */
    private Short taskType;

    /**
     * 任务名称，记录本次运行的任务标识
     */
    private String taskName;

    /**
     * 任务开始运行的时间戳
     */
    private LocalDateTime runTime;

    /**
     * 任务运行时长（秒），成功完成时非空
     */
    private Integer runDuration;

    /**
     * 运行状态：1=成功，2=失败，3=运行时，4=超时
     */
    private Short runStatus;

    /**
     * 任务失败时的错误详情，成功时可为空
     */
    private String errorInfo;
}
