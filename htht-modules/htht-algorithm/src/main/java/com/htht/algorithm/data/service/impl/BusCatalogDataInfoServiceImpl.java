package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.BusCatalogDataInfo;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.mapper.BusCatalogDataInfoMapper;
import com.htht.algorithm.data.service.IBusCatalogDataInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.utils.Lists;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 目录资源关联表 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-05-24
 */
@Service
@RequiredArgsConstructor
public class BusCatalogDataInfoServiceImpl extends ServiceImpl<BusCatalogDataInfoMapper, BusCatalogDataInfo> implements IBusCatalogDataInfoService {

    private final IBusDataInfoService dataInfoService;

    @Override
    public void removeCatalogDataInfoByCatalogId(Long catalogId) {
        this.lambdaUpdate()
            .eq(BusCatalogDataInfo::getCatalogId, catalogId)
            .remove();
    }

    @Override
    public List<BusDataInfo> getDataInfoListByCatalogId(Long catalogId) {
        List<Long> dataIds = this.lambdaQuery()
            .eq(BusCatalogDataInfo::getCatalogId, catalogId)
            .list().stream().map(catalogData -> {
                return catalogData.getDataId();
            }).collect(Collectors.toList());
        if (dataIds.size() > 0) {
            return dataInfoService.listByIds(dataIds);
        }
        return Lists.newArrayList();
    }

    @Override
    public TableDataInfo<BusDataInfo> getDataInfoListByCatalogIdPage(Long catalogId , PageQuery pageQuery) {
        List<Long> dataIds = this.lambdaQuery()
            .eq(BusCatalogDataInfo::getCatalogId, catalogId)
            .list().stream().map(catalogData -> {
                return catalogData.getDataId();
            }).collect(Collectors.toList());
        if (dataIds.size() > 0) {
            Page<BusDataInfo> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
            Page<BusDataInfo> resultPage=dataInfoService.pageByIds(page,dataIds);
            return TableDataInfo.build(resultPage);
        }
        return TableDataInfo.build(new Page<BusDataInfo>());
    }


    @Override
    public void removeCatalogDataInfoByDataId(Long dataId) {
        this.lambdaUpdate()
            .eq(BusCatalogDataInfo::getDataId, dataId)
            .remove();
    }
}
