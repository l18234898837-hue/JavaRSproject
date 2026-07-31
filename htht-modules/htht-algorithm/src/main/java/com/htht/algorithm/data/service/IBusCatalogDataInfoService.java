package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusCatalogDataInfo;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 目录资源关联表 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-05-24
 */
public interface IBusCatalogDataInfoService extends IService<BusCatalogDataInfo> {

    /**
     * 根据资源目录ID删除目录和资源关联关系
     *
     * @param catalogId
     */
    void removeCatalogDataInfoByCatalogId(Long catalogId);

    /**
     * 根据资源目录ID得到资源列表
     *
     * @param catalogId
     * @return
     */
    List<BusDataInfo> getDataInfoListByCatalogId(Long catalogId);


    /**
     * 根据资源目录ID分页得到资源列表
     * @param catalogId
     * @return
     */
    TableDataInfo<BusDataInfo> getDataInfoListByCatalogIdPage(Long catalogId, PageQuery pageQuery);

    /**
     * 根据资源ID删除资源目录和资源的关联关系
     *
     * @param dataId
     */
    void removeCatalogDataInfoByDataId(Long dataId);

}
