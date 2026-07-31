package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.StDataType;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.dto.SatelliteDto;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.common.core.domain.R;

import java.util.List;

/**
 * <p>
 * 存储遥感数据类型定义 服务类
 * </p>
 *
 * @author MSFY
 * @since 2025-07-24
 */
public interface StDataTypeService extends IService<StDataType> {


    List<StDataType> buildTree();

    String check();

    boolean add(StDataType stDataType);

    boolean addChild(DataTypeChildVo dataTypeChildVo);

    boolean addParent(AddDataTypeParentVo aDTP);

    boolean updateNameAndCatalogName(DataTypeUpdateNameVo dataTypeUpdateNameVo);

    StDataType getResourceCatalogId(String firstName, String secondName);

    boolean deleteDataTypeAndCatalog(Long id);

    List<StDataType> getDataTypeByproductCatalogIdOrCalculateCatalogId(Long id);

    StDataType getParent(Long parentId);

    SatelliteDto getSatelliteDto(Long catalogId);

    List<NewMenuVo> TreeByString(NewMenuQueryVo queryVo);

    boolean rvById(Long id);
}
