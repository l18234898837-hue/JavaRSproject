package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StOnlineDownloadRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 在线数据下载记录表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-17
 */
public interface StOnlineDownloadRecordService extends IService<StOnlineDownloadRecord> {
    void checkDownload();
    void consume(StOnlineDownloadRecord stOnlineDownloadRecord);
    void consumeLandsat(StOnlineDownloadRecord stOnlineDownloadRecord);
    void consumeSentinel2(StOnlineDownloadRecord stOnlineDownloadRecord);


    Map<String,String> downloadAreaLoopSentinel2(List<String> sentinel2TypeList, String wtk, String lastDate) throws Exception;

    Map<String,Map<String,String>> downloadAreaLoopLandsat(List<String> datasetNameList, String geoJsonStr, String lastDate);

    void add(StOnlineDownloadRecord stDataDownloadRecord);

    boolean pauseDownload(Long id);

    void updateStatus(Long id,Integer status);

    void updateStore(Long id,Integer store);

    void storeDate(Long id);

    R<Void> getPage(StOnlineDownloadRecord stOnlineDownloadRecord, PageQuery pageQuery);

    TableDataInfo<StOnlineDownloadRecord> getListByUserId(Long userId, PageQuery pageQuery);
}
