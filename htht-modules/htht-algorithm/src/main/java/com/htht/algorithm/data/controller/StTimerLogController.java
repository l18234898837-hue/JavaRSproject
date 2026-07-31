package com.htht.algorithm.data.controller;

import cn.hutool.core.bean.BeanUtil;
import com.htht.algorithm.data.domain.StTimerLog;
import com.htht.algorithm.data.event.StTimerLogEvent;
import com.htht.algorithm.data.service.StTimerLogService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时器运行日志表，记录定时任务每次执行的结果 前端控制器
 *
 * @author MSFY
 * @since 2025-09-20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stTimerLog")
public class StTimerLogController {
    private final StTimerLogService stTimerLogService;
    @RequestMapping("/page")
    public R<TableDataInfo<StTimerLog>> page(Long userId,Short taskType,String runDate,Integer taskStatus, PageQuery pageQuery) {
        if(userId==null){
            R.fail("输入要查看的userId");
        }
        return R.ok(stTimerLogService.page( userId, taskType, runDate, taskStatus,  pageQuery));
    }

    @PostMapping()
    public R<Void> add(@RequestBody StTimerLog stTimerLog) {
        StTimerLogEvent bean = BeanUtil.toBean(stTimerLog, StTimerLogEvent.class);
        bean.sendEvent();
        return R.ok();
    }

}
