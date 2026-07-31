package com.htht.algorithm.data.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StIslandInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.htht.algorithm.data.domain.vo.StIslandInfoQueryVO;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 存储群岛信息，含层级关系（parent_id和level） Mapper 接口
 * </p>
 *
 * @author MSFY
 * @since 2025-08-18
 */
public interface StIslandInfoMapper extends BaseMapper<StIslandInfo> {

    Page<StIslandInfo> pageStIslandInfo(@Param("page") Page<Object> page,@Param("queryVO") StIslandInfoQueryVO queryVO);

    StIslandInfo getStIslandInfo(Long id);
}
