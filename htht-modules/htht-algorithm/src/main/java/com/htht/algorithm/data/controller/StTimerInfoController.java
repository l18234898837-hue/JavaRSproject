package com.htht.algorithm.data.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StTimerInfo;
import com.htht.algorithm.data.service.StTimerInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.domain.model.LoginUser;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.helper.LoginHelper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 定时器信息表，存储定时任务的配置信息 前端控制器
 */
@RestController
@RequestMapping("/stTimerInfo")
public class StTimerInfoController {
    @Resource
    private StTimerInfoService service;


    /**
     * 新增
     */
    @PostMapping
    public R<String> add(@RequestBody StTimerInfo stTimerInfo) {
        if (stTimerInfo.getTaskType() == 2 && stTimerInfo.getType() == 2)
            return R.fail("参数非法"); // 计算任务不能是周期任务
        LoginUser loginUser = null;
        try {
            loginUser = LoginHelper.getLoginUser();
        } catch (Exception ignored) {
        }
        if (loginUser == null)
            return R.fail("用户未登录");
        stTimerInfo.setUserId(loginUser.getUserId());
        // 定时任务必须手动开启，默认未启用
        stTimerInfo.setStatus((short) 0);
        service.checkAdd(stTimerInfo);
        if (service.save(stTimerInfo))
            return R.ok();
        return R.fail();
    }

    @PostMapping("test")
    public R<String> test(@RequestBody StTimerInfo stTimerInfo) {
        service.checkAdd(stTimerInfo);
        service.TestRunTask(stTimerInfo);
        return R.ok();
    }

    /**
     * 修改
     */
    @PutMapping
    public R<Void> update(@RequestBody StTimerInfo stTimerInfo) {
        if (stTimerInfo.getTaskType() == 2 && stTimerInfo.getType() == 2)
            return R.fail("参数非法"); // 计算任务不能是周期任务
        stTimerInfo.setUserId(null); // 不允许修改任务所属用户
        LoginUser loginUser = null;
        try {
            loginUser = LoginHelper.getLoginUser();
        } catch (Exception ignored) {
        }
        if (loginUser == null)
            return R.fail("用户未登录");

        if(!LoginHelper.isAdmin()) {
            if (stTimerInfo.getStatus() == 1) { // 如果当前任务是启用状态，则需要检查是否有其他任务处于启用状态
                List<StTimerInfo> list = service.list(Wrappers.lambdaQuery(StTimerInfo.class)
                    .eq(StTimerInfo::getTaskType, stTimerInfo.getTaskType()).eq(StTimerInfo::getType, stTimerInfo.getType())
                    .eq(StTimerInfo::getUserId, loginUser.getUserId()).eq(StTimerInfo::getStatus, 1));
                if (!list.isEmpty() && !list.get(0).getId().equals(stTimerInfo.getId()))
                    return R.fail("只能有一个定时任务处于启用状态");
            }
        }
        // 注意这个方法被重写过，状态变更会触发Quartz相关逻辑
        if (service.updateById(stTimerInfo))
            return R.ok();
        return R.fail();
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        // 注意这个方法被重写过，删除记录会同步删除Quartz相关记录
        if (service.removeById(id))
            return R.ok();
        return R.fail();
    }

    /**
     * 根据ID获取详情
     */
    @GetMapping("/{id}")
    public R<StTimerInfo> detail(@PathVariable("id") Long id) {
        return R.ok(service.getById(id));
    }

    /**
     * 分页查询列表
     */
    @GetMapping("/page")
    public R<TableDataInfo<StTimerInfo>> page(StTimerInfo stTimerInfo, PageQuery pageQuery) {
        return R.ok(
            service.page(stTimerInfo,pageQuery)
        );
    }
}
