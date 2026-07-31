package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * S3在线数据下载记录表，用于追踪数据下载全流程 前端控制器
 *
 * @author MSFY
 * @since 2025-08-08
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stOnlineDownloadInfo")
public class StOnlineDownloadInfoController {
    private final StOnlineDownloadInfoService stOnlineDownloadInfoService;
    @GetMapping()
    public R<List<StOnlineDownloadInfo>> list() {
        return R.ok(stOnlineDownloadInfoService.list());
    }

    @PostMapping("/store/{id}")
    public R<Void> store(@PathVariable Long id) {
        stOnlineDownloadInfoService.store(id);
        return R.ok();
    }
    @PostMapping("/storeAll")
    public R<Void> storeAll() {
        stOnlineDownloadInfoService.storeAll();
        return R.ok();
    }


}
