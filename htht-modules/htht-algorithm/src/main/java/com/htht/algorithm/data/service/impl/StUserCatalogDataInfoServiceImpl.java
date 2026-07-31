package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.api.client.util.Lists;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StUserCatalogDataInfo;
import com.htht.algorithm.data.mapper.StUserCatalogDataInfoMapper;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.algorithm.data.service.StUserCatalogDataInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户资源关联表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-10
 */
@Service
@RequiredArgsConstructor
public class StUserCatalogDataInfoServiceImpl extends ServiceImpl<StUserCatalogDataInfoMapper, StUserCatalogDataInfo> implements StUserCatalogDataInfoService {
    private final IBusDataInfoService dataInfoService;

    @Override
    public void bind(Long catalogId, Long userId, Long dataId) {
        StUserCatalogDataInfo stUserCatalogDataInfo = new StUserCatalogDataInfo()
            .setCatalogId(catalogId)
            .setUserId(userId)
            .setDataId(dataId);
        save(stUserCatalogDataInfo);
    }

    @Override
    public void removeByUserIdAndCatalogId(Long catalogId) {
        this.lambdaUpdate()
            .eq(StUserCatalogDataInfo::getCatalogId,catalogId)
            .remove();
    }

    @Override
    public List<BusDataInfo> getDataInfoListByUserIdAndCatalogId(Long userId, Long catalogId) {
        List<Long> dataIds = this.lambdaQuery()
            .eq(StUserCatalogDataInfo::getCatalogId, catalogId)
            .eq(StUserCatalogDataInfo::getUserId, userId)
            .list().stream().map(StUserCatalogDataInfo::getDataId).toList();
        if(!dataIds.isEmpty()){
            return dataInfoService.listByIds(dataIds);
        }
        return Lists.newArrayList();
    }

    @Override
    public TableDataInfo<BusDataInfo> getDataInfoListByUserIdAndCatalogIdPage(Long userId, Long catalogId, PageQuery pageQuery) {
        List<Long> dataIds = this.lambdaQuery()
            .eq(StUserCatalogDataInfo::getCatalogId, catalogId)
            .eq(StUserCatalogDataInfo::getUserId, userId)
            .list().stream().map(StUserCatalogDataInfo::getDataId).toList();
        if(!dataIds.isEmpty()){
            Page<BusDataInfo> page=new Page<>(pageQuery.getPageNum(),pageQuery.getPageSize());
            Page<BusDataInfo> resultPage = dataInfoService.pageByIds(page, dataIds);
            return TableDataInfo.build(resultPage);
        }
        return TableDataInfo.build(new Page<BusDataInfo>());
    }

    @Override
    public void removeByUserIdAndDataId( Long dataId) {
        this.lambdaUpdate()
            .eq(StUserCatalogDataInfo::getDataId,dataId)
            .remove();
    }
}
