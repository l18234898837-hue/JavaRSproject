package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.StDownloadLog;
import com.htht.algorithm.data.domain.StOnlineDownloadRecord;
import com.htht.algorithm.data.event.StOnlineDownloadRecordUpdateFilePathEvent;
import com.htht.algorithm.data.event.StOnlineDownloadRecordUpdateProgressEvent;
import com.htht.algorithm.data.mapper.StOnlineDownloadRecordMapper;
import com.htht.algorithm.data.service.StOnlineDownloadRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.dataDownload.UsgsDownload.UsgsDownloader;
import com.htht.algorithm.data.utils.dataDownload.UsgsDownload.UsgsSearch;
import com.htht.algorithm.data.utils.dataDownload.sentinelDownload.SentinelDataUtils;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import oshi.util.FileUtil;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 在线数据下载记录表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
public class StOnlineDownloadRecordServiceImpl extends ServiceImpl<StOnlineDownloadRecordMapper, StOnlineDownloadRecord> implements StOnlineDownloadRecordService {
    private final int concurrentlyDownloadCount=1;
    private final TileProperties tileProperties;

    private final LandsatScanStore landsatScanStore;
    private final Sentinel2ScanStore sentinel2ScanStore;

    @Override
    public void checkDownload() {
        List<StOnlineDownloadRecord> list = lambdaQuery().eq(StOnlineDownloadRecord::getStatus, 2).list();
//        list.forEach(record -> {
//            Instant run = record.getUpdateTime().toInstant();
//            Instant now = Instant.now();
//            long days = Duration.between(run, now).toDays();
//            if(days>=1){
//                record .setStatus(3);
//                updateById(record);
//            }
//        });
        int runCount =  list.size();
//        System.out.println(runCount);
        int planRunCount = concurrentlyDownloadCount-runCount;
        List<StOnlineDownloadRecord> canDownloadList = lambdaQuery().eq(StOnlineDownloadRecord::getStatus, 1)
            .or()
            .eq(StOnlineDownloadRecord::getStatus, 3)
            .orderByAsc(StOnlineDownloadRecord::getUpdateTime)
            .list();
        for(int i=1;i<=planRunCount;i++){
            if(i>canDownloadList.size()){
                break;
            }
            StOnlineDownloadRecord stOnlineDownloadRecord = canDownloadList.get(i - 1);
            consume(stOnlineDownloadRecord);
        }
    }

    @Override
    public void consume(StOnlineDownloadRecord stOnlineDownloadRecord) {
        if(stOnlineDownloadRecord.getType()==1){
            consumeLandsat(stOnlineDownloadRecord);
        } else if (stOnlineDownloadRecord.getType()==2) {
            consumeSentinel2(stOnlineDownloadRecord);
        }else{
            throw new GlobalException("未知卫星类型");
        }
    }

    @Override
    public void consumeLandsat(StOnlineDownloadRecord stOnlineDownloadRecord) {
        try {
            stOnlineDownloadRecord.setStatus(2);
            updateById(stOnlineDownloadRecord);
            //添加事件类进工具类
            StOnlineDownloadRecordUpdateProgressEvent event = new StOnlineDownloadRecordUpdateProgressEvent(stOnlineDownloadRecord.getId(),0);
            boolean isBreak = UsgsDownloader.download(tileProperties.getVector().getOnlineDir(), stOnlineDownloadRecord.getParam1(), stOnlineDownloadRecord.getParam2(), event);
            if(isBreak){
                updateStatus(stOnlineDownloadRecord.getId(), 5);
            }else{
                updateStatus(stOnlineDownloadRecord.getId(), 4);
                storeDate(stOnlineDownloadRecord.getId());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            System.out.println("landsat download error");
            System.out.println(e.getMessage());
            e.printStackTrace();
            updateStatus(stOnlineDownloadRecord.getId(), 3);
        }
    }

    @Override
    public void consumeSentinel2(StOnlineDownloadRecord stOnlineDownloadRecord) {
        try {
            stOnlineDownloadRecord.setStatus(2);
            updateById(stOnlineDownloadRecord);
            //添加事件类进工具类
            StOnlineDownloadRecordUpdateProgressEvent event=new StOnlineDownloadRecordUpdateProgressEvent(stOnlineDownloadRecord.getId(),0);
            boolean isBreak = SentinelDataUtils.downloadProduct(tileProperties.getVector().getOnlineDir(), stOnlineDownloadRecord.getParam1(), stOnlineDownloadRecord.getParam2(), event);
            if(isBreak){
                updateStatus(stOnlineDownloadRecord.getId(), 5);
            }else{
                updateStatus(stOnlineDownloadRecord.getId(), 4);
                storeDate(stOnlineDownloadRecord.getId());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            updateStatus(stOnlineDownloadRecord.getId(), 3);
        }
    }

    @Override
    public Map<String,String> downloadAreaLoopSentinel2(List<String> sentinel2TypeList, String wtk, String lastDate) throws Exception {
        Map<String, String> downloadMap = SentinelDataUtils.periodSearch("SENTINEL-2", sentinel2TypeList, lastDate, DateUtil.getCurrentOnlineDate(), wtk);
        Set<String> names = downloadMap.keySet();
        List<String> list1 = lambdaQuery().in(StOnlineDownloadRecord::getName,names).list().stream().map(StOnlineDownloadRecord::getName).toList();
        for (String s : list1) {
            downloadMap.remove(s);
        }
        downloadMap.forEach((k, v) -> {
            StOnlineDownloadRecord record = new StOnlineDownloadRecord();
            record.setName(k).setParam1(k).setParam2(v).setStatus(1).setType(2);
            save(record);
        });
        return downloadMap;
    }

    @Override
    public Map<String,Map<String,String>>  downloadAreaLoopLandsat(List<String> datasetNameList, String geoJsonStr, String lastDate) {
        Map<String,Map<String,String>> returnValue=new HashMap<>();
        for (String datasetName : datasetNameList) {
            Map<String, String> downloadMap = UsgsSearch.periodSearch(datasetName, geoJsonStr, lastDate, DateUtil.getCurrentOnlineDate(),null,null);
            Set<String> names = downloadMap.keySet();
            if(!names.isEmpty()){
                List<StOnlineDownloadRecord> list = lambdaQuery().in(StOnlineDownloadRecord::getName, names).list();
                List<String> list1 = list.stream().map(StOnlineDownloadRecord::getName).toList();
                for (String s : list1) {
                    downloadMap.remove(s);
                }
            }
            returnValue.put(datasetName,downloadMap);
        }
        return returnValue;
    }

    @Override
    public void add(StOnlineDownloadRecord stDataDownloadRecord) {
        int size = lambdaQuery().eq(StOnlineDownloadRecord::getName, stDataDownloadRecord.getName()).list().size();
        if(size>0){
            throw new GlobalException("在线数据下载记录已存在");
        }
        save(stDataDownloadRecord);
    }

    @Override
    public boolean pauseDownload(Long id) {
        StOnlineDownloadRecord downloadRecord = getById(id);
        if(downloadRecord.getType()==1){
            UsgsDownloader.cancelDownloadById(id);
        }else{
            SentinelDataUtils.cancelDownloadById(id);
        }
        updateStatus(id, 5);
        return true;
    }


    @Async
    @EventListener
    public  void updateProgress(StOnlineDownloadRecordUpdateProgressEvent event){
        LambdaUpdateWrapper<StOnlineDownloadRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StOnlineDownloadRecord::getId, event.getId())
            .set(StOnlineDownloadRecord::getProgress, event.getProgress());
        update(updateWrapper);
    }

    @Async
    @EventListener
    public  void updateFilePath(StOnlineDownloadRecordUpdateFilePathEvent event){
        LambdaUpdateWrapper<StOnlineDownloadRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StOnlineDownloadRecord::getId, event.getId())
            .set(StOnlineDownloadRecord::getFilePath, event.getFilePath());
        update(updateWrapper);
    }

    @Override
    public void updateStatus(Long id,Integer status){
        StOnlineDownloadRecord downloadRecord = getById(id);
        if(downloadRecord.getStatus()==5&&status==3){
            return;
        }
        if(downloadRecord.getStatus().equals(status)){
            return;
        }
        LambdaUpdateWrapper<StOnlineDownloadRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StOnlineDownloadRecord::getId, id)
            .set(StOnlineDownloadRecord::getStatus, status);
        update(updateWrapper);
    }

    @Override
    public void updateStore(Long id,Integer store){
        LambdaUpdateWrapper<StOnlineDownloadRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(StOnlineDownloadRecord::getId, id)
            .set(StOnlineDownloadRecord::getStore, store);
        update(updateWrapper);
    }

    @Override
    public void storeDate(Long id){
        StOnlineDownloadRecord lastRecord = getById(id);
        if(lastRecord.getFilePath()!=null){
            File file = new File(lastRecord.getFilePath());
            if (lastRecord.getType()==1) {
                boolean b = landsatScanStore.canL0Scan(file.getName());
                if(b){
                    landsatScanStore.storeGridZip(file);
                    updateStore(lastRecord.getId(),1);
                }
            }else{
                boolean b = sentinel2ScanStore.canL0Scan(file.getName());
                if(b){
                    sentinel2ScanStore.storeGridZip(file);
                    updateStore(lastRecord.getId(),1);
                }
            }
        }
    }

    @Override
    public boolean removeById(Serializable id) {
        StOnlineDownloadRecord downloadRecord = getById(id);
        if(downloadRecord.getStatus()==2){
            throw new GlobalException("在线数据下载任务正在运行中，请勿删除");
        }
        pauseDownload(downloadRecord.getId());
        String filePath = downloadRecord.getFilePath();
        if(filePath!=null){
            File file = new File(filePath);
            if(file.exists()){
                file.delete();
            }
        }
        return super.removeById(id);
    }

    @Override
    public R<Void> getPage(StOnlineDownloadRecord stOnlineDownloadRecord, PageQuery pageQuery) {
        return null;
    }

    @Override
    public TableDataInfo<StOnlineDownloadRecord> getListByUserId(Long userId, PageQuery pageQuery) {
        return TableDataInfo.build(
            this.lambdaQuery()
                .eq(StOnlineDownloadRecord::getUserId, userId)
                .orderByAsc(StOnlineDownloadRecord::getCreateTime)
                .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))
        );
    }

}
