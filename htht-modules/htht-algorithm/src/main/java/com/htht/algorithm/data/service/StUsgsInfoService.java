package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StUsgsInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.vo.UsgsSreachVo;
import com.htht.algorithm.data.utils.dataDownload.sentinelDownload.SentinelDataUtils;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-29
 */
public interface StUsgsInfoService extends IService<StUsgsInfo> {

    List<StUsgsInfo> buildUsgsInfoTree();

    boolean add(StUsgsInfo stUsgsInfo);

    String search(UsgsSreachVo usgsSreachVo);

    boolean downloadSentinelData(String dataName,String downloadUrl);
    boolean downloadSentinelData(String dataName,String downloadUrl,Long userId);
    boolean downloadSentinelData(String dataName,String downloadUrl,Long userId,boolean isTry);


    boolean downloadLandsatData(String dataName,String datasetId, String entityId);
    boolean downloadLandsatData(String dataName,String datasetId, String entityId,Long userId);
    boolean downloadLandsatData(String dataName,String datasetId, String entityId,Long userId,boolean isTry);

    void downloadAreaLoop(List<String> datasetNameList, List<String> sentinel2TypeList, String geoJsonStr, String wtk, String lastDate, Long userId) throws Exception;

    boolean pauseDownload(Long id);
}
