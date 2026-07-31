package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StUserCatalogDataInfo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 用户资源关联表 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-10
 */
public interface StUserCatalogDataInfoService extends IService<StUserCatalogDataInfo> {

    void bind(Long catalogId,Long userId,Long dataId);

    /**
     * 根据资源目录ID和用户Id删除用户目录和资源关联关系
     *
     */
    void removeByUserIdAndCatalogId(Long catalogId);

    /**
     * 根据资源目录ID跟用户Id得到资源列表
     *
     */
    List<BusDataInfo> getDataInfoListByUserIdAndCatalogId(Long userId,Long catalogId);

    /**
     * 根据资源目录ID跟用户ID分页得到资源列表
     * @param catalogId
     * @return
     */
    TableDataInfo<BusDataInfo> getDataInfoListByUserIdAndCatalogIdPage(Long userId,Long catalogId, PageQuery pageQuery);


    /**
     * 根据用户Id跟资源ID删除用户目录和资源的关联关系
     *
     */
    void removeByUserIdAndDataId(Long dataId);
}
