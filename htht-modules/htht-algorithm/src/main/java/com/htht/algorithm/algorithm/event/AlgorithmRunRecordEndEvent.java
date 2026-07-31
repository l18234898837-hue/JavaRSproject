package com.htht.algorithm.algorithm.event;

import com.htht.algorithm.base.event.LogBaseEvent;
import com.htht.algorithm.base.utils.DateUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/22 17:24
 **/
@EqualsAndHashCode(callSuper = false)
@Data
@Accessors(chain = true)
public class AlgorithmRunRecordEndEvent extends LogBaseEvent {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String runStatus;
    private String log;
    private Long runDuration;

    @Override
    public void sendEvent() {
        setRunDuration(DateUtil.getAbsDiffMilliseconds(startTime, endTime));
        super.sendEvent();
    }
}
