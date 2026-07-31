package com.htht.algorithm.data.timer;

import com.htht.algorithm.data.service.StAlgorithmRecordService;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.algorithm.data.service.StOnlineDownloadRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/19 10:05
 **/
@Component
@RequiredArgsConstructor
public class algorithmTask {
    private final StAlgorithmRecordService stAlgorithmRecordService;
    private final StOnlineDownloadRecordService stOnlineDownloadRecordService;
    //用于执行算法运行任务，控制算法执行步骤+检查算法运行过程+自动调用入库流程
    @Scheduled(cron = "0 0/1 * * * ?")
    public void executeEveryMinute(){
        // 这里编写需要每分钟执行的操作
        stAlgorithmRecordService.task();
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void executeEveryFiveMinute(){
        // 这里编写需要每分钟执行的操作
        stOnlineDownloadRecordService.checkDownload();
    }


}

