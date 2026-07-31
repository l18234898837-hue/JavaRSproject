package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StTimerLog;
import com.htht.algorithm.data.event.StTimerLogEvent;
import com.htht.algorithm.data.mapper.StTimerLogMapper;
import com.htht.algorithm.data.service.StTimerLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import jodd.util.StringUtil;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <p>
 * 定时器运行日志表，记录定时任务每次执行的结果 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Service
public class StTimerLogServiceImpl extends ServiceImpl<StTimerLogMapper, StTimerLog> implements StTimerLogService {
    @Async
    @EventListener
    public void recordTimerLog(StTimerLogEvent timerLogEvent) {
        StTimerLog bean = BeanUtil.toBean(timerLogEvent, this.entityClass);
        save(bean);
    }


    @Override
    public TableDataInfo<StTimerLog> page(Long userId, Short taskType, String runDate, Integer taskStatus, PageQuery pageQuery) {
        DateTime startTime=null;
        DateTime endTime=null;
        if(StringUtil.isNotBlank(runDate)){
            DateTime parse = DateUtil.parse(runDate, "yyyy-MM-dd");
            startTime = DateUtil.offsetDay(parse, -1);
            endTime = DateUtil.offsetDay(parse, 1);
        }
        return TableDataInfo.build( this.lambdaQuery()
            .eq(StTimerLog::getUserId, userId)
            .eq(Objects.nonNull(taskType),StTimerLog::getTaskType, taskType)
            .eq(Objects.nonNull(taskStatus), StTimerLog::getRunStatus, taskStatus)
            .between(StringUtil.isNotBlank(runDate), StTimerLog::getRunTime, startTime, endTime)
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize())))
            ;
    }
}
