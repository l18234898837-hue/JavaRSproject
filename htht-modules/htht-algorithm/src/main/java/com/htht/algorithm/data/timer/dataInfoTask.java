package com.htht.algorithm.data.timer;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StAlgorithmRecordCatalogInfoService;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/29 18:07
 **/
@Component
@RequiredArgsConstructor
public class dataInfoTask {
    private final IBusDataInfoService busDataInfoService;
    private final LandsatScanStore landsatScanStore;
    private final Sentinel2ScanStore sentinel2ScanStore;

    @Scheduled(cron = "0 0/1 * * * ?")
    public void storeAllDataInfo(){
//        String onlineDir = tileProperties.getVector().getOnlineDir();
//        scanOnlineZip(onlineDir);
        // 让他每分钟自动入库
        busDataInfoService.storageAll();
    }

    //入库在线文件夹中的压缩包
    private void scanOnlineZip(String path){
        File file = new File(path);
        File[] files = file.listFiles();
        if(files != null){
            for (File file1 : files) {
                boolean b = landsatScanStore.canL0Scan(file1.getName());
                if(b){
                    landsatScanStore.storeGridZip(file1);
                }
                b = sentinel2ScanStore.canL0Scan(file1.getName());
                if(b){
                    sentinel2ScanStore.storeGridZip(file1);
                }
            }
        }
    }
}
