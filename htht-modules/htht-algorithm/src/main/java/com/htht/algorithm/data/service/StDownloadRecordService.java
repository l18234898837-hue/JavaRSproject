package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StDownloadRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 数据下载记录表，记录用户提交的下载任务信息 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-11
 */
public interface StDownloadRecordService extends IService<StDownloadRecord> {
    boolean add(StDownloadRecord record);

    int getNewSerialNumberByUserId(Long userId);

    TableDataInfo<StDownloadRecord> listPage(StDownloadRecord stDownloadRecord, PageQuery pageQuery);
}
