package com.htht.algorithm.data.event;

import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 17:28
 **/
@EqualsAndHashCode(callSuper = false)
@Data
public class StTimerLogEvent extends LogBaseEvent {
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
     * 运行状态：1=成功，2=失败，3=运行中，4=超时
     */
    private Short runStatus;

    /**
     * 任务失败时的错误详情，成功时可为空
     */
    private String errorInfo;
}
