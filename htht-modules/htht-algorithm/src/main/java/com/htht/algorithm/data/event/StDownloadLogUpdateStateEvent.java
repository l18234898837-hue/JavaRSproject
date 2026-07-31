package com.htht.algorithm.data.event;


import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 14:40
 **/
@EqualsAndHashCode(callSuper = false)
@Data
public class StDownloadLogUpdateStateEvent extends LogBaseEvent {
    /**
     * 下载状态枚举：1=进行中，2=已完成，3=异常中断
     */
    private Short downloadStatus;

    /**
     * 跟踪ID，用于下载完成/失败时的自动状态关联
     */
    private Long recordId;
}
