package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StDownloadLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

/**
 * <p>
 * 下载记录表，记录数据下载的详细信息及状态变迁 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
public interface StDownloadLogService extends IService<StDownloadLog> {
    TableDataInfo<StDownloadLog> page(Long userId, PageQuery pageQuery);

}
