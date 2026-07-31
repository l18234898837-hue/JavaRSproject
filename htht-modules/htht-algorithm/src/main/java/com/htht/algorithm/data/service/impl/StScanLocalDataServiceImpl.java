package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.service.StScanLocalDataService;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.WorldviewScanStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/6 17:49
 **/
@Service
@RequiredArgsConstructor
public class StScanLocalDataServiceImpl implements StScanLocalDataService {
    private  final LandsatScanStore landsatScanStore;
    private  final Sentinel2ScanStore sentinel2ScanStore;
    private final WorldviewScanStore worldviewScanStore;

    @Override
    public void scanStoreLandsat() {
        //修改成L0压缩包入库，L1文件暂时不做处理，等后续考虑是直接删除该文件夹内容还是如何
        landsatScanStore.storeL0();
//        landsatScanStore.storeL1();
        landsatScanStore.storeL2A0();
        landsatScanStore.storeL2A1();
        landsatScanStore.storeL2A2();
        landsatScanStore.storeL3A0();
        landsatScanStore.storeL3A1();
        landsatScanStore.storeL3A2();
    }

    @Override
    public void scanStoreSentinel2() {
        //修改成L0压缩包入库，L1文件暂时不做处理，等后续考虑是直接删除该文件夹内容还是如何
        sentinel2ScanStore.storeL0();
//        sentinel2ScanStore.storeL1();
        sentinel2ScanStore.storeL2A0();
        sentinel2ScanStore.storeL2A1();
        sentinel2ScanStore.storeL2A2();
        sentinel2ScanStore.storeL3A0();
        sentinel2ScanStore.storeL3A1();
        sentinel2ScanStore.storeL3A2();

    }

    @Override
    public void scanStoreWorldView() {
        worldviewScanStore.storeL1();
        worldviewScanStore.storeL2A0();
        worldviewScanStore.storeL3A0();
        worldviewScanStore.storeL3A1();
    }

}
