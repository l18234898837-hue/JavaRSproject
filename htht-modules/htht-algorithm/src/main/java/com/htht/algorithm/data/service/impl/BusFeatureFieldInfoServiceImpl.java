package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.BusFeatureFieldInfo;
import com.htht.algorithm.data.mapper.BusFeatureFieldInfoMapper;
import com.htht.algorithm.data.service.IBusFeatureFieldInfoService;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-06-19
 */
@Service
@CacheConfig(
    cacheNames = "feature_field"
)
public class BusFeatureFieldInfoServiceImpl extends ServiceImpl<BusFeatureFieldInfoMapper, BusFeatureFieldInfo> implements IBusFeatureFieldInfoService {

    @Override
    @CacheEvict(key = "#dataId")
    public void removeFieldByDataId(Long dataId) {
        this.lambdaUpdate()
            .eq(BusFeatureFieldInfo::getDataId, dataId)
            .remove();
    }

    @Override
    @Cacheable(key = "#dataId")
    public List<BusFeatureFieldInfo> getFieldListByDataId(Long dataId) {
        return this.lambdaQuery()
            .eq(BusFeatureFieldInfo::getDataId, dataId)
            .orderByAsc(BusFeatureFieldInfo::getIdx)
            .list();
    }

    @Override
    @CachePut(key = "#fieldInfos[0].dataId")
    public List<BusFeatureFieldInfo> updateFieldList(List<BusFeatureFieldInfo> fieldInfos) {
        this.updateBatchById(fieldInfos);
        return fieldInfos;
    }
}
