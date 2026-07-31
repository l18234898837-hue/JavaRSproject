package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusCatalogInfo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
public interface IBusCatalogInfoService extends IService<BusCatalogInfo> {


    /**
     * 新增目录
     *
     * @param catalogInfo
     */
    boolean addCatalog(BusCatalogInfo catalogInfo);

    /**
     * 删除目录
     *
     * @param id
     */
    boolean deleteCatalog(Long id);

    /**
     * 更新目录
     *
     * @param catalogInfo
     * @return
     */
    boolean updateCatalog(BusCatalogInfo catalogInfo);

    /**
     * 根据父级ID查询目录列表（包括资源）
     *
     * @param pid
     * @return
     */
    List<?> getCatalogList(Long pid);

    /**
     * 分页查询根资源目录列表
     *
     * */
    TableDataInfo<?> selectPage(Long pid, PageQuery page);

    /**
     * 分页查询用户目录下资源内容列表
     * @param userId
     * @param catalogId
     * @param pageQuery
     * @return
     */
    TableDataInfo<?> getDataInfoListByUserIdAndCatalogIdPage(Long userId, Long catalogId, PageQuery pageQuery);

    /**
     * 绑定资源到目录
     *
     * @param dataId    资源ID
     * @param catalogId 目录ID
     */
    void bindDataToCatalog(Long dataId, Long catalogId);


    /**
     * 绑定资源到用户目录
     * @param dataId
     * @param userId
     * @param catalogId
     */
    void bindDataToCatalogAndUser(Long dataId, Long userId, Long catalogId);

    /**
     * 批量绑定资源到目录
     */
    void bindsDataToCatalog(List<Long> dataIds,Long catalogId);

    /**
     * 解绑资源和资源目录关联
     *
     * @param dataId
     * @param catalogId
     */
    void unBindDataToCatalog(Long dataId, Long catalogId);


    void unBindDataToUserAndCatalog(Long dataId, Long userId, Long catalogId);

    /**
     * 查询根资源目录列表
     *
     * @return
     */
    List<BusCatalogInfo> getRootCatalogList();



    /**
     * 根据根目录构建目录树
     *
     * @param rootId
     * @return
     */
    List<BusCatalogInfo> buildTreeByRootId(Long rootId);

    /**
     * 构建目录树
     *
     * @return
     */
    List<BusCatalogInfo> buildTree();


    void checkDate(Long catalogId);
}
