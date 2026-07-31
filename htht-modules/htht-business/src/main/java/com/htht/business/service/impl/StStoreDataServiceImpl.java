package com.htht.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.business.domain.StStoreData;
import com.htht.business.mapper.StStoreDataMapper;
import com.htht.business.service.StStoreDataService;
import com.htht.business.utils.store.StoreDataFactory;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 存储遥感数据入库记录 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
@RequiredArgsConstructor
@Service
public class StStoreDataServiceImpl extends ServiceImpl<StStoreDataMapper, StStoreData> implements StStoreDataService {
    private final StoreDataFactory storeDataFactory;
    @Override
    public TableDataInfo<StStoreData> page(StStoreData stStoreData, PageQuery pageQuery) {
        return TableDataInfo.build(this.lambdaQuery()
            .like(Strings.isNotBlank(stStoreData.getName()), StStoreData::getName, stStoreData.getName())
            .eq(ObjectUtils.isNotEmpty(stStoreData.getTypeId()), StStoreData::getTypeId, stStoreData.getTypeId())
            .eq(ObjectUtils.isNotEmpty(stStoreData.getStatus()), StStoreData::getStatus, stStoreData.getStatus())
            .eq(ObjectUtils.isNotEmpty(stStoreData.getFileId()), StStoreData::getFileId, stStoreData.getFileId())
            .page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()))
        );
    }

    @Override
    public boolean add(StStoreData stStoreData) {
        boolean save = save(stStoreData);
        if ( save)
        {
            store(stStoreData.getId());
        }
        return save;
    }

    @Override
    public void store(Long id) {
        StStoreData stStoreData = getById(id);
        if(stStoreData.getStatus()==(short)2){
            throw new GlobalException("数据已经处理过");
        }
        storeDataFactory.store(stStoreData);
        updateById(stStoreData);
    }
}
