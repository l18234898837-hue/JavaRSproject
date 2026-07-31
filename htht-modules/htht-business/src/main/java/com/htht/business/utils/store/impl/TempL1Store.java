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
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/7/24 17:12
 **/
@Service
public class TempL1Store extends BaseStore {
    public TempL1Store
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
        throw new GlobalException("暂未实现这个类别转换器");
    }
}
