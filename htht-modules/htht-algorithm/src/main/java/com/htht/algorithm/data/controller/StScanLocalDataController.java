package com.htht.algorithm.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.service.StScanLocalDataService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扫描本机文件夹夹控制器
 * Author: MSFY
 * Description: TODO 扫描本机文件夹夹控制器，不然太多循环依赖了，所以单独拿出来
 * DateTime: 2025/9/6 17:46
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/stScanLocalData")
public class StScanLocalDataController {

    private final StScanLocalDataService stScanLocalDataService;

    /**
     * Landsat本地数据扫描入库
     */
    @SaCheckPermission("data:scan:store")
    @PostMapping(value = "/scan/Landsat")
    public R<Void> scanStoreLandsat() {
        stScanLocalDataService.scanStoreLandsat();
        return R.ok();
    }

    /**
     * Sentinel2本地数据扫描入库
     */
    @SaCheckPermission("data:scan:store")
    @PostMapping(value="/scan/Sentinel2")
    public R<Void> scanStoreSentinel2() {
        stScanLocalDataService.scanStoreSentinel2();
        return R.ok();
    }

    /**
     * WorldView本地数据扫描入库
     */
    @SaCheckPermission("data:scan:store")
    @PostMapping(value="/scan/WorldView")
    public R<Void> scanStoreWorldView() {
        stScanLocalDataService.scanStoreWorldView();
        return R.ok();
    }
}
