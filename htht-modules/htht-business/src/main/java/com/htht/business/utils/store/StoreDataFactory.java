package com.htht.business.utils.store;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.algorithm.data.utils.scanStore.BaseScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.LandsatScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.Sentinel2ScanStore;
import com.htht.algorithm.data.utils.scanStore.impl.WorldviewScanStore;
import com.htht.business.domain.StStoreData;
import com.htht.business.utils.store.impl.*;
import com.htht.common.exception.GlobalException;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.htht.business.utils.store.StoreDataType.*;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/24 16:37
 **/
@Service
@RequiredArgsConstructor
public class StoreDataFactory {

    private final TileProperties tileProperties;
    private final ISysFileService sysFileService;
    private final StDataTypeService stDataTypeService;

    private final LandsatScanStore landsatScanStore;
    private final Sentinel2ScanStore sentinel2ScanStore;
    private final WorldviewScanStore worldviewScanStore;

    public void store(StStoreData stStoreData){
        StDataType currentDataType = stDataTypeService.getById(stStoreData.getTypeId());
        StDataType parentDataType = stDataTypeService.getById(currentDataType.getPid());
        BaseScanStore scanStore = getScanStore(parentDataType.getName());
        int modeType = scanStore.getModeType(currentDataType.getName());
        if(modeType==-1){
            throw new GlobalException("暂不支持该数据类型入库，联系管理员");
        }
        try {
            scanStore.storeOlineDate(getZipDir(stStoreData.getFileId()),getUnZipDir(stStoreData.getId()),modeType);
            stStoreData.setStatus((short)2);

        } catch (Exception e) {
            stStoreData.setStatus((short)3);
            stStoreData.setError(e.getMessage());
        }
    }

    private BaseScanStore getScanStore(String dataTypeName){
        return switch (dataTypeName) {
            case "Landsat-9", "Landsat-8", "Landsat-7", "Landsat-5" -> landsatScanStore;
            case "Sentinel-2A", "Sentinel-2B", "Sentinel-2C" -> sentinel2ScanStore;
            case "WorldView-2", "WorldView-3" -> worldviewScanStore;
            default -> throw new GlobalException("暂不支持该数据类型入库，联系管理员");
        };
    }

    private String getZipDir(Long fileId){
        return sysFileService.getById(fileId).getFilePath();
    }

    private String getUnZipDir(Long storeDataId){
        return Paths.get(tileProperties.getVector().getStoreDir(),storeDataId.toString()).toString();
    }
}
