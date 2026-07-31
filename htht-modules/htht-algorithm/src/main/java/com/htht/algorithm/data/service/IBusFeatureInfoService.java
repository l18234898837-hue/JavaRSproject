package com.htht.algorithm.data.service;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusFeatureInfo;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
public interface IBusFeatureInfoService extends IService<BusFeatureInfo> {


    /**
     * 新增要素信息
     *
     * @param featureInfo
     */
    void addFeatureInfo(BusFeatureInfo featureInfo);

    /**
     * 批量新增要素信息
     *
     * @param resultList
     * @param batchSize
     */
    void addFeatureInfoBatch(Long dataId, List<Map<String, Object>> resultList, int batchSize);

    /**
     * 根据资源ID和要素ID查询要素属性信息
     *
     * @param dataId
     * @param fId
     * @return
     */
    JSONObject getFeatureByDataIdAndFid(Long dataId, Long fId);
}
