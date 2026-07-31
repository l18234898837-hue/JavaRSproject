package com.htht.business.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.htht.business.domain.SdObserveData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.business.domain.dto.QuerySdObserveDataDto;
import com.htht.business.domain.vo.SdObserveDataVo;
import com.htht.business.domain.vo.SdObserveIntervalVo;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 卫星观测的详细数据记录表 Mapper 接口
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
public interface SdObserveDataMapper extends BaseMapper<SdObserveData> {

    /**
     * 分页搜索观测数据
     */
    IPage<SdObserveDataVo> getObserveListByCondition(IPage<SdObserveDataVo> page, @Param("dto") QuerySdObserveDataDto dto);

    // 查询主表最大最小值
    SdObserveIntervalVo getObserveDataMaxMin();

    // 查询波长最大最小值
    SdObserveIntervalVo getWavelengthMaxMin();
}
