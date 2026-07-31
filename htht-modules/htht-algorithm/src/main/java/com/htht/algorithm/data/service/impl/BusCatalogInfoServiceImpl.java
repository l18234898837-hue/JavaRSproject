package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.BusDataInfo;
import com.htht.algorithm.data.domain.StUserCatalogDataInfo;
import com.htht.algorithm.data.service.StUserCatalogDataInfoService;
import com.htht.algorithm.data.utils.DateUtil;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.exception.GlobalException;
import com.htht.algorithm.data.constants.DataConstants;
import com.htht.algorithm.data.domain.BusCatalogDataInfo;
import com.htht.algorithm.data.domain.BusCatalogInfo;
import com.htht.algorithm.data.mapper.BusCatalogInfoMapper;
import com.htht.algorithm.data.service.IBusCatalogDataInfoService;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import com.htht.algorithm.data.service.IBusDataInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 服务实现类
 *
 * @author wjx
 * @since 2023-04-24
 */
@Service
@RequiredArgsConstructor
public class BusCatalogInfoServiceImpl extends ServiceImpl<BusCatalogInfoMapper, BusCatalogInfo> implements IBusCatalogInfoService {

    private final IBusDataInfoService dataInfoService;

    private final IBusCatalogDataInfoService catalogDataInfoService;

    private final StUserCatalogDataInfoService userCatalogDataInfoService;

    @Override
    @CacheEvict(value = "ocean:catalog:tree")
    public boolean addCatalog(BusCatalogInfo catalogInfo) {
        //如果父级ID为空，则设置为根目录
        if (catalogInfo.getPId() == null || catalogInfo.getPId().equals(DataConstants.CATALOG_ROOT_ID)) {
            catalogInfo.setPId(DataConstants.CATALOG_ROOT_ID);
            catalogInfo.setLevel(1);
            catalogInfo.setType(DataConstants.CATALOG_COMMON_TYPE);
        } else {
            //检查父级目录是否存在
            BusCatalogInfo parentCatalogInfo = Optional.ofNullable(this.getById(catalogInfo.getPId())).orElseThrow(() -> new GlobalException("父级目录不存在"));
            //父级目录类型是否为目录
            if (!Objects.equals(parentCatalogInfo.getType(), DataConstants.CATALOG_COMMON_TYPE)) {
                throw new GlobalException("资源目录中不能再新建目录");
            }
            catalogInfo.setLevel(parentCatalogInfo.getLevel() + 1);
            if (catalogInfo.getLevel() > 2) {
                throw new GlobalException("最多新建2级目录");
            }
            //若为第3级目录，直接设置为资源目录，其他为普通目录
            catalogInfo.setType(catalogInfo.getLevel() == 2 ? DataConstants.CATALOG_DATA_TYPE : DataConstants.CATALOG_COMMON_TYPE);
        }
        return this.save(catalogInfo);
    }

    @Override
    @CacheEvict(value = "ocean:catalog:tree")
    public boolean deleteCatalog(Long id) {
        Optional.ofNullable(this.getById(id)).orElseThrow(() -> new GlobalException("目录不存在"));
        //递归删除子目录及资源目录和资源的关联
        deleteCatalogByPid(id);
        return this.removeById(id);
    }

    /**
     * 根据父级ID删除子目录
     *
     * @param pid
     */
    private void deleteCatalogByPid(Long pid) {
        this.lambdaQuery()
            .eq(BusCatalogInfo::getPId, pid)
            .list().forEach(catalog -> {
                this.removeById(catalog.getId());
                if (Objects.equals(catalog.getType(), DataConstants.CATALOG_COMMON_TYPE)) {
                    deleteCatalogByPid(catalog.getId());
                } else if (Objects.equals(catalog.getType(), DataConstants.CATALOG_DATA_TYPE)) {
                    catalogDataInfoService.removeCatalogDataInfoByCatalogId(catalog.getId());
                    userCatalogDataInfoService.removeByUserIdAndCatalogId(catalog.getId());
                }
            });
    }

    @Override
    @CacheEvict(value = "ocean:catalog:tree")
    public boolean updateCatalog(BusCatalogInfo catalogInfo) {
        BusCatalogInfo oldCatalog = Optional.ofNullable(this.getById(catalogInfo.getId())).orElseThrow(() -> new GlobalException("目录不存在"));
        if (catalogInfo.getType() != null && oldCatalog.getType() != catalogInfo.getType()) {
            throw new GlobalException("不能更改目录类型");
        }
        oldCatalog.setName(catalogInfo.getName());
        return this.updateById(catalogInfo);
    }

    @Override
    public List<?> getCatalogList(Long pid) {
        int type = 0;
        if (!Objects.equals(pid, DataConstants.CATALOG_ROOT_ID)) {
            BusCatalogInfo parentCatalog = Optional.ofNullable(this.getById(pid)).orElseThrow(() -> new GlobalException("目录不存在"));
            type = parentCatalog.getType();
        }
        if (type == DataConstants.CATALOG_COMMON_TYPE) {
            return this.lambdaQuery()
                .eq(BusCatalogInfo::getPId, pid)
                .list();
        } else {
            return catalogDataInfoService.getDataInfoListByCatalogId(pid);
        }
    }

    @Override
    public TableDataInfo<?> selectPage(Long pid, PageQuery page) {
        int type = 0;
        if (!Objects.equals(pid, DataConstants.CATALOG_ROOT_ID)) {
            BusCatalogInfo parentCatalog = Optional.ofNullable(this.getById(pid)).orElseThrow(() -> new GlobalException("目录不存在"));
            type = parentCatalog.getType();
        }
        if (type == DataConstants.CATALOG_COMMON_TYPE) {
            throw new GlobalException("这不是资源目录");
        } else {
            return catalogDataInfoService.getDataInfoListByCatalogIdPage(pid,page);
        }
    }

    @Override
    public TableDataInfo<?> getDataInfoListByUserIdAndCatalogIdPage(Long userId, Long catalogId, PageQuery pageQuery) {
        int type = 0;
        if (!Objects.equals(catalogId, DataConstants.CATALOG_ROOT_ID)) {
            BusCatalogInfo parentCatalog = Optional.ofNullable(this.getById(catalogId)).orElseThrow(() -> new GlobalException("目录不存在"));
            type = parentCatalog.getType();
        }
        if (type == DataConstants.CATALOG_COMMON_TYPE) {
            throw new GlobalException("这不是资源目录");
        } else {
            return userCatalogDataInfoService.getDataInfoListByUserIdAndCatalogIdPage(userId,catalogId,pageQuery);
        }
    }

    @Override
    public void bindDataToCatalog(Long dataId, Long catalogId) {
        BusCatalogInfo catalogInfo = Optional.ofNullable(this.getById(catalogId)).orElseThrow(() -> new GlobalException("资源目录不存在"));
        if (!Objects.equals(catalogInfo.getType(), DataConstants.CATALOG_DATA_TYPE)) {
            throw new GlobalException("目录不为资源目录，不能加入资源");
        }
        Optional.ofNullable(dataInfoService.getById(dataId)).orElseThrow(() -> new GlobalException("资源不存在"));
        BusCatalogDataInfo catalogDataInfo = new BusCatalogDataInfo();
        catalogDataInfo.setCatalogId(catalogId);
        catalogDataInfo.setDataId(dataId);
        catalogDataInfoService.save(catalogDataInfo);
    }

    @Override
    public void bindDataToCatalogAndUser(Long dataId, Long userId, Long catalogId) {
        BusCatalogInfo catalogInfo = Optional.ofNullable(this.getById(catalogId)).orElseThrow(() -> new GlobalException("资源目录不存在"));
        if (!Objects.equals(catalogInfo.getType(), DataConstants.CATALOG_DATA_TYPE)) {
            throw new GlobalException("目录不为资源目录，不能加入资源");
        }
        Optional.ofNullable(dataInfoService.getById(dataId)).orElseThrow(() -> new GlobalException("资源不存在"));
        StUserCatalogDataInfo userCatalogDataInfo = new StUserCatalogDataInfo();
        userCatalogDataInfo.setUserId(userId);
        userCatalogDataInfo.setCatalogId(catalogId);
        userCatalogDataInfo.setDataId(dataId);
        userCatalogDataInfoService.save(userCatalogDataInfo);
    }

    @Override
    public void bindsDataToCatalog(List<Long> dataIds, Long catalogId) {
        for (Long dataId : dataIds) {
            bindDataToCatalog(dataId, catalogId);
        }
    }

    @Override
    public void unBindDataToCatalog(Long dataId, Long catalogId) {
        Optional.ofNullable(this.getById(catalogId)).orElseThrow(() -> new GlobalException("资源目录不存在"));
        Optional.ofNullable(dataInfoService.getById(dataId)).orElseThrow(() -> new GlobalException("资源不存在"));
        catalogDataInfoService.lambdaUpdate()
            .eq(BusCatalogDataInfo::getCatalogId, catalogId)
            .eq(BusCatalogDataInfo::getDataId, dataId)
            .remove();
    }

    @Override
    public void unBindDataToUserAndCatalog(Long dataId, Long userId, Long catalogId) {
        Optional.ofNullable(this.getById(catalogId)).orElseThrow(() -> new GlobalException("资源目录不存在"));
        Optional.ofNullable(dataInfoService.getById(dataId)).orElseThrow(() -> new GlobalException("资源不存在"));
        userCatalogDataInfoService.lambdaUpdate()
            .eq(StUserCatalogDataInfo::getUserId, userId)
            .eq(StUserCatalogDataInfo::getCatalogId, catalogId)
            .eq(StUserCatalogDataInfo::getDataId, dataId)
            .remove();
    }

    @Override
    public List<BusCatalogInfo> getRootCatalogList() {
        return this.lambdaQuery()
            .eq(BusCatalogInfo::getPId, 0)
            .list();
    }

    @Override
//    @Cacheable(value = "ocean:catalog:tree")
    public List<BusCatalogInfo> buildTreeByRootId(Long rootId) {
        List<BusCatalogInfo> catalogInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, BusCatalogInfo> catalogMap = catalogInfoList.stream().collect(
            Collectors.toMap(BusCatalogInfo::getId, Function.identity(), (key1, key2) -> key2)
        );
        List<BusCatalogInfo> regionTree = new ArrayList<>();
        catalogMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getLevel() > 2) {
                BusCatalogInfo parentCatalog = catalogMap.get(entry.getValue().getPId());
                List<BusCatalogInfo> children = parentCatalog.getChildren();
                children.add(entry.getValue());
                children.sort(Comparator.comparing(BusCatalogInfo::getId));
            } else if (entry.getValue().getLevel() == 2) {
                if (entry.getValue().getPId().longValue() == rootId.longValue()) {
                    regionTree.add(entry.getValue());
                }
            }
        });
        regionTree.sort(Comparator.comparing(BusCatalogInfo::getId));
        return regionTree;
    }

    @Override
    public List<BusCatalogInfo> buildTree() {
        List<BusCatalogInfo> catalogInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, BusCatalogInfo> catalogMap = catalogInfoList.stream().collect(
            Collectors.toMap(BusCatalogInfo::getId, Function.identity(), (key1, key2) -> key2)
        );
        List<BusCatalogInfo> regionTree = new ArrayList<>();
        catalogMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getLevel() > 1) {
                BusCatalogInfo parentCatalog = catalogMap.get(entry.getValue().getPId());
                List<BusCatalogInfo> children = parentCatalog.getChildren();
                children.add(entry.getValue());
                children.sort(Comparator.comparing(BusCatalogInfo::getId));
            } else if (entry.getValue().getLevel() == 1) {
                regionTree.add(entry.getValue());
            }
        });
        regionTree.sort(Comparator.comparing(BusCatalogInfo::getId));
        return regionTree;
    }

    @Override
    public void checkDate(Long catalogId) {
        List<BusDataInfo> dataInfoListByCatalogId = catalogDataInfoService.getDataInfoListByCatalogId(catalogId);
        for (BusDataInfo dataInfo : dataInfoListByCatalogId) {
            String[] s = dataInfo.getName().split("_");
            Date date = DateUtil.getFormatterDate(s[2] + s[3] + s[4], "yyyyMMdd");
            dataInfo.setActualTime(date);
            dataInfo.setProductTime(date);
        }
        dataInfoService.updateBatchById(dataInfoListByCatalogId) ;
    }


}
