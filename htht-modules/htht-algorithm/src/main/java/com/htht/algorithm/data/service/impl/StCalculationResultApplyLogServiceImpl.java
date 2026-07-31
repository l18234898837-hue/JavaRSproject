package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StCalculationResultApplyLog;
import com.htht.algorithm.data.event.StCalculationResultApplyLogEvent;
import com.htht.algorithm.data.event.StCalculationResultApplyLogUpdateStatusAllEvent;
import com.htht.algorithm.data.event.StCalculationResultApplyLogUpdateStatusOneEvent;
import com.htht.algorithm.data.mapper.StCalculationResultApplyLogMapper;
import com.htht.algorithm.data.service.StCalculationResultApplyLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 计算结果上传申请记录表，跟踪计算结果的上传申请及审核流程 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
@Service
public class StCalculationResultApplyLogServiceImpl extends ServiceImpl<StCalculationResultApplyLogMapper, StCalculationResultApplyLog> implements StCalculationResultApplyLogService {
    @Async
    @EventListener
    public void recordCalculationResultApplyLog(StCalculationResultApplyLogEvent calculationResultApplyLogEvent) {
        StCalculationResultApplyLog bean = BeanUtil.toBean(calculationResultApplyLogEvent, StCalculationResultApplyLog.class);
        save(bean);
    }

    @Async
    @EventListener
    public void updateOneCalculationResultApplyLog(StCalculationResultApplyLogUpdateStatusOneEvent oneEvent) {
        this.lambdaUpdate()
            .eq(StCalculationResultApplyLog::getApplyGroupId, oneEvent.getApplyGroupId())
            .eq(StCalculationResultApplyLog::getDataId, oneEvent.getDataId())
            .set(StCalculationResultApplyLog::getApplyStatus, oneEvent.getApplyStatus())
            .update();
    }

    @Async
    @EventListener
    public void  updateAllCalculationResultApplyLog(StCalculationResultApplyLogUpdateStatusAllEvent allEvent) {
        this.lambdaUpdate()
            .eq(StCalculationResultApplyLog::getApplyGroupId, allEvent.getApplyGroupId())
            .set(StCalculationResultApplyLog::getApplyStatus, allEvent.getApplyStatus())
            .update();
    }

    @Override
    public TableDataInfo<StCalculationResultApplyLog> page(Long userId, PageQuery pageQuery) {
        return TableDataInfo.build(
            lambdaQuery()
                .eq(StCalculationResultApplyLog::getUserId, userId)
                .orderByDesc(StCalculationResultApplyLog::getCreateTime)
                .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))
        );
    }
}
