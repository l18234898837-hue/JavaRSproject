package com.htht.algorithm.data.mapper;

import com.htht.algorithm.data.domain.bo.GenerateCircleBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/4 13:46
 **/
@Mapper
public interface TransformMapper {
    String generateCircleWkt3857(@Param("bo") GenerateCircleBo bo);
    String generateCircleWkt4326(@Param("bo") GenerateCircleBo bo);
}
