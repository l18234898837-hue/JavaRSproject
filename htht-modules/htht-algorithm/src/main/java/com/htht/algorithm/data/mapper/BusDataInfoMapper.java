package com.htht.algorithm.data.mapper;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.htht.algorithm.data.domain.BusDataInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author wjx
 * @since 2023-04-24
 */
public interface BusDataInfoMapper extends BaseMapper<BusDataInfo> {

    /**
     * 分页查询目录绑定资源
     * @param page
     * @param bo
     * @return
     */
    IPage<BusDataInfo> getDataInfoListByCondition(IPage<BusDataInfo> page, @Param("bo") JSONObject bo);


    /**
     * 分页查询用户目录绑定资源
     */
    IPage<BusDataInfo> getDataInfoListByConditionAndUser(IPage<BusDataInfo> page, @Param("bo") JSONObject bo);

    /**
     * 分页查询全部资源（目录绑定资源+用户目录绑定资源）
     */
    IPage<BusDataInfo> getDataInfoListByConditionAndAll(IPage<BusDataInfo> page, @Param("bo") JSONObject bo);


    /**
     * 检查两景影像是否存在交集
     *
     * @param id1
     * @param id2
     * @return
     */
    Boolean checkDataIntersects(@Param("id1") Long id1, @Param("id2") Long id2);

    /**
     * 统计
     *
     * @param id
     * @param groupField
     * @param oneField
     * @param twoField
     * @return
     */
    List<Map<String, Object>> statistic(@Param(value = "id") Long id, @Param(value = "statsField") String statsField, @Param(value = "groupField") String groupField, @Param(value = "oneField") String oneField, @Param(value = "twoField") String twoField);
}
