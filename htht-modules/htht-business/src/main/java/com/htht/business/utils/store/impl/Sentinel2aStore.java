package com.htht.business.utils.store.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.algorithm.data.utils.FileStrUtil;
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
 * DateTime: 2025/7/24 17:14
 **/
@Service
public class Sentinel2aStore extends BaseStore {
    public Sentinel2aStore
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
        List<Long> fileIds=new ArrayList<>();
        File GranuleFile = FileStrUtil.searchFolder(unZipFile,"GRANULE");
        File ImgDataFile = FileStrUtil.searchFolder(GranuleFile,"IMG_DATA");
        cycleLoad(ImgDataFile,fileIds);
        return fileIds;
    }

    private void cycleLoad(File file,List<Long> list){
        File[] files = file.listFiles();
        if(files!=null){
            for (File f : files) {
                if (f.isDirectory()) {
                    cycleLoad(f,list);
                }else{
                    Long l = processFile(f, null);
                    if(!l.equals(-1L)){
                        list.add(l);
                    }
                }
            }
        }
    }
    private Long processFile(File file,StStoreData stStoreData)  {
        try {
            String fileName = file.getName();
            String fileNameLower = fileName.toLowerCase();

            if (fileNameLower.contains(".jp2")) {
                String tiffFile = GdalUtil.Jp2ToGeoTiff(file);
                BusDataInfo busDataInfo = dataInfoService.importTiffFile(new File(tiffFile));
                return busDataInfo.getId();
            }  else if (
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
