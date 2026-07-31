package com.htht.business.utils.store.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.business.domain.StStoreData;
import com.htht.business.utils.store.BaseStore;
import com.htht.business.utils.store.util.GdalUtil;
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
public class GaoFen2 extends BaseStore {
    public GaoFen2
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
        File[] files = unZipFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) {
                    Long dataInfoId = processFile(file, stStoreData);
                    if(!dataInfoId.equals(-1L)){
                        // 对文件进行操作
                        fileIds.add(dataInfoId);
                    }

                }
            }
        }
        return fileIds;
    }


    private Long processFile(File file, StStoreData stStoreData) {
        try {
            String fileName = file.getName();
            String fileNameLower = fileName.toLowerCase();

            if (fileNameLower.contains(".tif") || fileNameLower.contains(".tiff")) {
                String geoTiff = GdalUtil.GaoFenToGeoTiff(file);
                BusDataInfo busDataInfo = dataInfoService.importTiffFile(new File(geoTiff));
                return busDataInfo.getId();
            } else  if (
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
