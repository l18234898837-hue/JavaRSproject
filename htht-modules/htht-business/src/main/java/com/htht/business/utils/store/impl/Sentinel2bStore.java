package com.htht.business.utils.store.impl;

import com.htht.algorithm.data.config.TileProperties;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.business.domain.StStoreData;
import com.htht.business.service.StStoreDataService;
import com.htht.business.utils.store.BaseStore;
import com.htht.common.exception.GlobalException;
import com.htht.system.service.ISysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/24 17:30
 **/
@Service
public class Sentinel2bStore extends BaseStore {

    private final Sentinel2aStore sentinel2aStore;

    @Autowired
    public Sentinel2bStore
        (TileProperties tileProperties,
         ISysFileService sysFileService,
         IBusMenuInfoService busMenuInfoService,
         IBusCatalogInfoService busCatalogInfoService,
         IBusDataInfoService busDataInfoService,
         Sentinel2aStore sentinel2aStore
        ) {
        super(
            tileProperties,
            sysFileService,
            busMenuInfoService,
            busCatalogInfoService,
            busDataInfoService
        );
        this.sentinel2aStore = sentinel2aStore;
    }


    @Override
    protected List<Long> prepareHandle(StStoreData stStoreData, File unZipFile) {
        return sentinel2aStore.prepareHandle(stStoreData,unZipFile);
    }
}
