package com.htht.business.service;

import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.business.domain.StStoreData;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

/**
 * <p>
 * 存储遥感数据入库记录 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
public interface StStoreDataService extends IService<StStoreData> {
    TableDataInfo<StStoreData> page(StStoreData stStoreData, PageQuery pageQuery);

    boolean add(StStoreData stStoreData);

    void store(Long id);


}
