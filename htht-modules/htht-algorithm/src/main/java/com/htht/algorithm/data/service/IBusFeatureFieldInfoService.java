package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusFeatureFieldInfo;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-06-19
 */
public interface IBusFeatureFieldInfoService extends IService<BusFeatureFieldInfo> {

    /**
     * 根据数据ID删除字段
     *
     * @param dataId
     */
    void removeFieldByDataId(Long dataId);

    /**
     * 根据数据ID查询字段列表
     *
     * @param dataId
     * @return
     */
    List<BusFeatureFieldInfo> getFieldListByDataId(Long dataId);

    /**
     * 更新字段
     *
     * @param fieldInfos
     */
    List<BusFeatureFieldInfo> updateFieldList(List<BusFeatureFieldInfo> fieldInfos);

}
