package com.htht.algorithm.data.controller;

import cn.hutool.core.bean.BeanUtil;
import com.htht.algorithm.data.domain.StCalculationResultApplyLog;
import com.htht.algorithm.data.event.StCalculationResultApplyLogEvent;
import com.htht.algorithm.data.service.StCalculationResultApplyLogService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 计算结果上传申请记录表，跟踪计算结果的上传申请及审核流程 前端控制器
 *
 * @author MSFY
 * @since 2025-09-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stCalculationResultApplyLog")
public class StCalculationResultApplyLogController {
    private final StCalculationResultApplyLogService stCalculationResultApplyLogService;
    @GetMapping("/page")
    public R<TableDataInfo<StCalculationResultApplyLog>> page(Long userId, PageQuery pageQuery) {
        return R.ok(stCalculationResultApplyLogService.page(userId, pageQuery));
    }

    @PostMapping()
    public R<Void> add(@RequestBody StCalculationResultApplyLog stCalculationResultApplyLog) {
        BeanUtil.toBean(stCalculationResultApplyLog, StCalculationResultApplyLogEvent.class).sendEvent();
        return R.ok();
    }
}
