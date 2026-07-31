package com.htht.algorithm.data.service.impl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.data.domain.StOnlineDownloadRecord;
import com.htht.algorithm.data.domain.StUsgsInfo;
import com.htht.algorithm.data.domain.vo.UsgsSreachVo;
import com.htht.algorithm.data.event.StDownloadLogEvent;
import com.htht.algorithm.data.mapper.StUsgsInfoMapper;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.htht.algorithm.data.service.StOnlineDownloadRecordService;
import com.htht.algorithm.data.service.StUsgsInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.utils.dataDownload.UsgsDownload.UsgsInfo;
import com.htht.algorithm.data.utils.dataDownload.UsgsDownload.UsgsSearch;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.common.helper.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-29
 */
@Service
@RequiredArgsConstructor
public class StUsgsInfoServiceImpl extends ServiceImpl<StUsgsInfoMapper, StUsgsInfo> implements StUsgsInfoService {

    private final TileProperties tileProperties;
    private final StOnlineDownloadInfoService stOnlineDownloadInfoService;

    private final LandsatScanStore landsatScanStore;

    private final StOnlineDownloadRecordService stOnlineDownloadRecordService;

    @Override
    public List<StUsgsInfo> buildUsgsInfoTree() {
        List<StUsgsInfo> catalogInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, StUsgsInfo> catalogMap = catalogInfoList.stream().collect(
            Collectors.toMap(StUsgsInfo::getId, Function.identity(), (key1, key2) -> key2)
        );
        List<StUsgsInfo> regionTree = new ArrayList<>();
        catalogMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getLevel() > 1) {
                StUsgsInfo parentCatalog = catalogMap.get(entry.getValue().getPid());
                List<StUsgsInfo> children = parentCatalog.getChildren();
                children.add(entry.getValue());
                children.sort(Comparator.comparing(StUsgsInfo::getId));
            } else if (entry.getValue().getLevel() == 1) {
                regionTree.add(entry.getValue());
            }
        });
        regionTree.sort(Comparator.comparing(StUsgsInfo::getId));
        return regionTree;
    }

    @Override
    public boolean add(StUsgsInfo stUsgsInfo) {
        return save(stUsgsInfo);
    }

    @Override
    public String search(UsgsSreachVo usgsSreachVo) {
        return UsgsSearch.search(
            usgsSreachVo.getPageSize(),
            usgsSreachVo.getPageNum(),
            usgsSreachVo.getDatasetName(),
            usgsSreachVo.getGeoJsonStr(),
            usgsSreachVo.getStartDate(),
            usgsSreachVo.getEndDate(),
            usgsSreachVo.getCloudMin(),
            usgsSreachVo.getCloudMax()
        );
    }

    @Override
    @SaCheckPermission("data:download:online")
    public boolean downloadLandsatData(String dataName,String datasetId, String entityId) {
        return downloadLandsatData(dataName,datasetId,entityId,LoginHelper.getUserId());
    }

    @Override
    public boolean downloadLandsatData(String dataName, String datasetId, String entityId, Long userId) {
        String onlineDir = tileProperties.getVector().getOnlineDir();
        StOnlineDownloadInfo stOnlineDownloadInfo = new StOnlineDownloadInfo().
            setName(entityId).
            setStatus((short) 0).
            setS3Url(datasetId + "/" + entityId).
            setDownloadUrl(onlineDir)
            .setDataType(2);
        download(onlineDir, datasetId, entityId,stOnlineDownloadInfo,dataName,userId);
        return true;
    }

    @Override
    public boolean downloadLandsatData(String dataName, String datasetId, String entityId, Long userId,boolean isTry) {
        try{
            return downloadLandsatData(dataName,datasetId,entityId,userId);
        }catch (Exception e){
            log.error("下载失败",e);
        }
        return false;
    }

    @Override
    public void downloadAreaLoop(List<String> datasetNameList, List<String> sentinel2TypeList, String geoJsonStr, String wtk, String lastDate,Long userId) throws Exception {
        if(datasetNameList!=null){
            Map<String, Map<String, String>> landsatMap = stOnlineDownloadRecordService.downloadAreaLoopLandsat(datasetNameList, geoJsonStr, lastDate);
            landsatMap.forEach((key,value)->{
                value.forEach((k,v)->{
                    downloadLandsatData(k, UsgsInfo.getUsgsId(key),v,userId);
                });
            });
        }
        if(sentinel2TypeList!=null){
            Map<String, String> seintinelMap = stOnlineDownloadRecordService.downloadAreaLoopSentinel2(sentinel2TypeList, wtk, lastDate);
            seintinelMap.forEach((key,value)->{
                downloadSentinelData(key,value,userId);
            });
        }
    }

    @Override
    public boolean pauseDownload(Long id) {
        return stOnlineDownloadRecordService.pauseDownload(id);
    }


    @Override
    @SaCheckPermission("data:download:online")
    public boolean downloadSentinelData(String dataName,String downloadUrl) {
        downloadSentinelData(dataName,downloadUrl, LoginHelper.getUserId());
        return true;
    }

    @SaCheckPermission("data:download:online")
    public boolean downloadSentinelData(String dataName,String downloadUrl,Long userId) {
        StOnlineDownloadRecord stDataDownloadRecord = new StOnlineDownloadRecord()
            .setName(dataName).setType(2).setStatus(1)
            .setParam1(dataName).setParam2(downloadUrl)
            .setUserId(userId).setStore(0);
        stOnlineDownloadRecordService.add(stDataDownloadRecord);
        StDownloadLogEvent stDownloadLogEvent = new StDownloadLogEvent();
        stDownloadLogEvent.setRecordId(-1L);
        stDownloadLogEvent.setUserId(userId);
        stDownloadLogEvent.setDataName(dataName);
        stDownloadLogEvent.setDataType("zip");
        stDownloadLogEvent.setSatelliteSensor("Sentinel-"+dataName.split("_")[0]);
        stDownloadLogEvent.setDataLevel("L1");
        stDownloadLogEvent.setDownloadLocation((short)0);
        stDownloadLogEvent.setDownloadStatus((short)1);
        stDownloadLogEvent.sendEvent();
        return true;
    }

    @Override
    public boolean downloadSentinelData(String dataName, String downloadUrl, Long userId, boolean isTry) {
        try {
            downloadSentinelData(dataName,downloadUrl,userId);
        }catch (Exception e){
            log.error("下载失败",e);
        }
        return false;
    }

    private void download(String saveDir, String datasetId, String entityId,StOnlineDownloadInfo stOnlineDownloadInfo,String dataName,Long userId){
        stOnlineDownloadInfo.setStatus((short) 1);
        StUsgsInfo UssInfo = this.lambdaQuery().
            eq(StUsgsInfo::getDatasatId, datasetId)
            .one();

        StDownloadLogEvent stDownloadLogEvent = new StDownloadLogEvent();
        stDownloadLogEvent.setRecordId(-1L);
        stDownloadLogEvent.setUserId(userId);
        stDownloadLogEvent.setDataName(dataName);
        stDownloadLogEvent.setDataType("zip");
        stDownloadLogEvent.setSatelliteSensor(UssInfo.getName());
        stDownloadLogEvent.setDataLevel("L1");
        stDownloadLogEvent.setDownloadLocation((short)0);
        stDownloadLogEvent.setUserId(userId);


        try {
            StOnlineDownloadRecord stDataDownloadRecord = new StOnlineDownloadRecord()
                .setName(dataName).setType(1).setStatus(1)
                .setParam1(datasetId).setParam2(entityId).setUserId(userId).setStore(0);
            stOnlineDownloadRecordService.add(stDataDownloadRecord);
            stOnlineDownloadInfo.setStatus((short)2);
            stDownloadLogEvent.setDownloadStatus((short)1);
        } catch (Exception e) {
            stOnlineDownloadInfo.setStatus((short)3);
            stDownloadLogEvent.setDownloadStatus((short)2);
            stOnlineDownloadInfo.setErrorMsg(e.getMessage());
        }finally {
            stDownloadLogEvent.sendEvent();
        }

        stOnlineDownloadInfoService.updateById(stOnlineDownloadInfo);
    }
}
