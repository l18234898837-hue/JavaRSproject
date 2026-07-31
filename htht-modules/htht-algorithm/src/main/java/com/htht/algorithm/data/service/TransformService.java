package com.htht.algorithm.data.service;

import com.htht.algorithm.data.domain.bo.GenerateCircleBo;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/4 14:15
 **/
public interface TransformService {
    String generateCircleWkt3857(GenerateCircleBo bo);
    String generateCircleWkt4326(GenerateCircleBo bo);
}
