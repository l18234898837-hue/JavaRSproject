package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.StOnlineDownloadInfo;
import com.htht.algorithm.data.mapper.StOnlineDownloadInfoMapper;
import com.htht.algorithm.data.service.StOnlineDownloadInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.utils.FileStrUtil;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * <p>
 * S3在线数据下载记录表，用于追踪数据下载全流程 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-08
 */
@Service
@RequiredArgsConstructor
public class StOnlineDownloadInfoServiceImpl extends ServiceImpl<StOnlineDownloadInfoMapper, StOnlineDownloadInfo> implements StOnlineDownloadInfoService {
    private final LandsatScanStore landsatScanStore;
    private final Sentinel2ScanStore sentinel2ScanStore;
    private final TileProperties tileProperties;


    @Override
    public void store(Long id) {
        StOnlineDownloadInfo byId = getById(id);
        switch (byId.getDataType()) {
            case 1:
                sentinel2ScanStore.storeOnlineDownloadData(byId.getDownloadUrl(),byId.getName());
                byId.setStoreStatus((short)1);
                updateById(byId);
                break;
            case 2:
                File file = new File(byId.getDownloadUrl());
                landsatScanStore.storeOlineDate(
                    byId.getDownloadUrl(),
                    Path.of(tileProperties.getVector().getOnlineDir(),
                    "unzip", FileStrUtil.getNameWithoutExtension(file)).toString(),
                    landsatScanStore.getModeType("L1"));
                byId.setStoreStatus((short)1);
                updateById(byId);
                break;
            default:
                throw new GlobalException("未知数据类型");
        }
    }

    @Override
    public void storeAll() {
        List<StOnlineDownloadInfo> list = list();
        for (StOnlineDownloadInfo stOnlineDownloadInfo : list) {
            if(stOnlineDownloadInfo.getStoreStatus()==0){
                store(stOnlineDownloadInfo.getId());
            }
        }
    }
}
