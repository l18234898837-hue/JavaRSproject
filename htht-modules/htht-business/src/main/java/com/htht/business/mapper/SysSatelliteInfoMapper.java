package com.htht.business.mapper;

import com.htht.business.domain.SysSatelliteInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.business.domain.vo.DictVO;

import java.util.List;

/**
 * <p>
 * 卫星信息表 Mapper 接口
 * </p>
 *
 * @author MSFY
 * @since 2025-09-26
 */
public interface SysSatelliteInfoMapper extends BaseMapper<SysSatelliteInfo> {

    List<DictVO> dictSysSatelliteInfo();
}
