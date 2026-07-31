package com.htht.algorithm.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.algorithm.data.domain.BusRegionInfo;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author wjx
 * @since 2023-05-29
 */
public interface BusRegionInfoMapper extends BaseMapper<BusRegionInfo> {

    String getGeometryByCode(String code);
}
