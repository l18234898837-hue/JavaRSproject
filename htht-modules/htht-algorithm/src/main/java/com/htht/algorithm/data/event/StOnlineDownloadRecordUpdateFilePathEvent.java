package com.htht.algorithm.data.event;

import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/16 16:16
 **/
@Data
@EqualsAndHashCode(callSuper = false)
public class StOnlineDownloadRecordUpdateFilePathEvent extends LogBaseEvent {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 下载地址
     */
    private String filePath;


    public StOnlineDownloadRecordUpdateFilePathEvent() {
    }

    public StOnlineDownloadRecordUpdateFilePathEvent(Long id) {
        this.id = id;
    }

    public StOnlineDownloadRecordUpdateFilePathEvent(Long id, String filePath) {
        this.id = id;
        this.filePath = filePath;
    }
}
