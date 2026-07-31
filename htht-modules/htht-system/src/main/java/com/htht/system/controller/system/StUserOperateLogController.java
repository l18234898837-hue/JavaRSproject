package com.htht.system.controller.system;

import cn.hutool.core.bean.BeanUtil;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.utils.spring.SpringUtils;
import com.htht.system.domain.StUserOperateLog;
import com.htht.system.domain.vo.StUserOperateLogPageQueryVo;
import com.htht.system.event.StUserOperateLogEvent;
import com.htht.system.service.StUserOperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * <p>
 * 用户操作日志表，记录用户的操作行为及结果 前端控制器
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stUserOperateLog")
public class StUserOperateLogController {
    private final StUserOperateLogService stUserOperateLogService;
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return stUserOperateLogService.removeById(id)?R.ok():R.fail();
    }

    @PostMapping("/page")
    public R<TableDataInfo<StUserOperateLog>> page(@RequestBody StUserOperateLogPageQueryVo vo) {
        return R.ok(stUserOperateLogService.page(vo));
    }

    @GetMapping("/functionModule")
    public R<List<String>> functionModule() {
        return R.ok(stUserOperateLogService.functionModule());
    }

    @GetMapping("/operateType")
    public R<List<String>> operateType() {
        return R.ok(stUserOperateLogService.operateType());
    }

    @PostMapping()
    public R<Void>  add(@RequestBody StUserOperateLog stUserOperateLog) {
        StUserOperateLogEvent bean = BeanUtil.toBean(stUserOperateLog, StUserOperateLogEvent.class);
        SpringUtils.context().publishEvent(bean);
        return R.ok();
    }

}
