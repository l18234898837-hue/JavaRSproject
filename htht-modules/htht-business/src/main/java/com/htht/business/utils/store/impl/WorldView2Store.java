package com.htht.business.utils.store.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.business.domain.StStoreData;
import com.htht.business.utils.store.BaseStore;
import com.htht.common.exception.GlobalException;
import com.htht.system.service.ISysFileService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/24 17:44
 **/
@Service
public class WorldView2Store extends BaseStore {
    public WorldView2Store
        (TileProperties tileProperties,
         ISysFileService sysFileService,
         IBusMenuInfoService busMenuInfoService,
         IBusCatalogInfoService busCatalogInfoService,
         IBusDataInfoService busDataInfoService
        ) {
        super(
            tileProperties,
            sysFileService,
            busMenuInfoService,
            busCatalogInfoService,
            busDataInfoService
        );
    }

    @Override
    protected List<Long> prepareHandle(StStoreData stStoreData, File unZipFile) {
        List<Long> fileIds = new ArrayList<>();
        LoopAdd(unZipFile, fileIds,stStoreData);
        return fileIds;
    }

    private void LoopAdd(File file,List<Long> fileIds,StStoreData stStoreData)  {
        File[] files = file.listFiles();
        if (files != null) {
            for (File tempFile : files) {
                if (!tempFile.isDirectory()) {
                    Long dataInfoId = processFile(tempFile, stStoreData);
                    if(!dataInfoId.equals(-1L)){
                        // 对文件进行操作
                        fileIds.add(dataInfoId);
                    }
                }else{
                    LoopAdd(tempFile, fileIds,stStoreData);
                }
            }
        }
    }

    private Long processFile(File file,StStoreData stStoreData)  {
        try {
            String fileName = file.getName();
            String fileNameLower = fileName.toLowerCase();

            if (fileNameLower.contains(".tif") || fileNameLower.contains(".tiff")) {
                BusDataInfo busDataInfo = dataInfoService.importTiffFile(file);
                return busDataInfo.getId();
            } else if (
                fileNameLower.contains(".jpeg") ||
                    fileNameLower.contains(".jpg") ||
                    fileNameLower.contains(".png")) {
                //后续如果说要记录略缩图，就在这里实现
                return -1L;
            }
        }catch (Exception e){
            throw new GlobalException("文件处理异常");
        }
        return -1L;
    }
}
