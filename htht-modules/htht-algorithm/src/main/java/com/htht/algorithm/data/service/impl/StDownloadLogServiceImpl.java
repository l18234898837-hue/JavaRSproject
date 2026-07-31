package com.htht.algorithm.data.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StDownloadLog;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.algorithm.data.event.StDownloadLogUpdateStateEvent;
import com.htht.algorithm.data.mapper.StDownloadLogMapper;
import com.htht.algorithm.data.service.StDownloadLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 下载记录表，记录数据下载的详细信息及状态变迁 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
@Service
public class StDownloadLogServiceImpl extends ServiceImpl<StDownloadLogMapper, StDownloadLog> implements StDownloadLogService {
    @Async
    @EventListener
    public void recordDownloadLog(StDownloadLogEvent downloadLogEvent){
        StDownloadLog bean = BeanUtil.toBean(downloadLogEvent, StDownloadLog.class);
        save(bean);
    }

    @Async
    @EventListener
    public void updateDownloadLog(StDownloadLogUpdateStateEvent downloadLogUpdateStateEvent){
        this.lambdaUpdate()
            .eq(StDownloadLog::getRecordId, downloadLogUpdateStateEvent.getRecordId())
            .set(StDownloadLog::getDownloadStatus, downloadLogUpdateStateEvent.getDownloadStatus())
            .update();
    }

    @Override
    public TableDataInfo<StDownloadLog> page(Long userId, PageQuery pageQuery) {
        return TableDataInfo.build(
            this.lambdaQuery()
                .eq(StDownloadLog::getUserId, userId)
                .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))
        );
    }
}
