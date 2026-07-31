package com.htht.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.common.utils.StringUtils;
import com.htht.system.domain.StUserOperateLog;
import com.htht.system.domain.vo.StUserOperateLogPageQueryVo;
import com.htht.system.mapper.StUserOperateLogMapper;
import com.htht.system.service.StUserOperateLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.system.event.StUserOperateLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 用户操作日志表，记录用户的操作行为及结果 服务实现类
 *
 * @author MSFY
 * @since 2025-09-20
 */
@Service
@RequiredArgsConstructor
public class StUserOperateLogServiceImpl extends ServiceImpl<StUserOperateLogMapper, StUserOperateLog> implements StUserOperateLogService {

    /**
     *  插入用户操作日志，通过事件机制驱动
     */
    @Async
    @EventListener
    public void recordUserOperateLog(StUserOperateLogEvent userOperateLogEvent){
        StUserOperateLog userOperateLog = BeanUtil.toBean(userOperateLogEvent, StUserOperateLog.class);
        save(userOperateLog);
    }

    @Override
    public TableDataInfo<StUserOperateLog> page(StUserOperateLogPageQueryVo vo) {
        Date startTime = null;
        Date endTime = null;
        if (StringUtils.isNotEmpty(vo.getStartTime()) && StringUtils.isNotEmpty(vo.getEndTime())) {
            try {
                startTime = DateUtil.parse(vo.getStartTime(), "yyyy-MM-dd");
                endTime = DateUtil.parse(vo.getEndTime(), "yyyy-MM-dd");
            } catch (Exception e) {
                throw new GlobalException("日期解析失败: "+ e.getMessage());
            }
        }
        Page<StUserOperateLog> page = this.lambdaQuery()
            .like(StringUtils.isNotEmpty(vo.getUserName()), StUserOperateLog::getUserName, vo.getUserName())
            .in(Objects.nonNull(vo.getFunctionModules()) && !vo.getFunctionModules().isEmpty(), StUserOperateLog::getFunctionModule, vo.getFunctionModules())
            .in(Objects.nonNull(vo.getOperateTypes()) && !vo.getOperateTypes().isEmpty(), StUserOperateLog::getOperateType, vo.getOperateTypes())
            .eq(StringUtils.isNotEmpty(vo.getResult()), StUserOperateLog::getResult, vo.getResult())
            .between(startTime != null,
               StUserOperateLog::getCreateTime,
               startTime,
               endTime
            )
            .page(new Page<>(vo.getPageQuery().getPageNum(), vo.getPageQuery().getPageSize()));
        return TableDataInfo.build(page);
    }

    @Override
    public List<String> functionModule() {
        return this.lambdaQuery()
            .select(StUserOperateLog::getFunctionModule)
            .groupBy(StUserOperateLog::getFunctionModule)
            .list().stream().map(StUserOperateLog::getFunctionModule).toList();
    }

    @Override
    public List<String> operateType() {
        return this.lambdaQuery()
            .select(StUserOperateLog::getOperateType)
            .groupBy(StUserOperateLog::getOperateType)
            .list().stream().map(StUserOperateLog::getOperateType).toList();
    }
}
