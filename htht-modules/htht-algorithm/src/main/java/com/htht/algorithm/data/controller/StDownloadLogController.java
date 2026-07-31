package com.htht.algorithm.data.controller;

import cn.hutool.core.bean.BeanUtil;
import com.htht.algorithm.data.domain.StDownloadLog;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.algorithm.data.service.StDownloadLogService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 下载记录表，记录数据下载的详细信息及状态变迁 前端控制器
 *
 * @author MSFY
 * @since 2025-09-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stDownloadLog")
public class StDownloadLogController {
    private final StDownloadLogService stDownloadLogService;

    @GetMapping("/page")
    public R<TableDataInfo<StDownloadLog>> page(Long userId, PageQuery pageQuery){
        return R.ok(stDownloadLogService.page(userId, pageQuery));
    }

    @PostMapping("")
    public R<Void> add(@RequestBody StDownloadLog stDownloadLog){
        BeanUtil.toBean(stDownloadLog, StDownloadLogEvent.class).sendEvent();
        return R.ok();
    }

}
