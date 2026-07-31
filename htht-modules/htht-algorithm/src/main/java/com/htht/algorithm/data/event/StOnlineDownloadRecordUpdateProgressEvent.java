package com.htht.algorithm.data.event;

import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/15 10:30
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class StOnlineDownloadRecordUpdateProgressEvent extends LogBaseEvent {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 下载进度
     */
    private Integer progress;


    public StOnlineDownloadRecordUpdateProgressEvent() {
    }

    public StOnlineDownloadRecordUpdateProgressEvent(Long id, Integer progress) {
        this.id = id;
        this.progress = progress;
    }

    public void sendEvent(Integer  progress) {
        if(!Objects.equals(getProgress(), progress)){
            setProgress(progress);
            sendEvent();
        }
    }
}
