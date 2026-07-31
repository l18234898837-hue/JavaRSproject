package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * S3在线数据下载记录表，用于追踪数据下载全流程 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-08
 */
public interface StOnlineDownloadInfoService extends IService<StOnlineDownloadInfo> {

    void store(Long id);

    void storeAll();
}
