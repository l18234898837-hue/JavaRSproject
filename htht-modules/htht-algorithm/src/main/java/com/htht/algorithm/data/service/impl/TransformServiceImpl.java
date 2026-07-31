package com.htht.algorithm.data.service.impl;

import com.htht.algorithm.data.domain.bo.GenerateCircleBo;
import com.htht.algorithm.data.mapper.TransformMapper;
import com.htht.algorithm.data.service.TransformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/4 14:15
 **/
@Service
@RequiredArgsConstructor
public class TransformServiceImpl implements TransformService {

    private  final TransformMapper transformMapper;

    @Override
    public String generateCircleWkt3857(GenerateCircleBo bo) {
        return transformMapper.generateCircleWkt3857(bo);
    }

    @Override
    public String generateCircleWkt4326(GenerateCircleBo bo) {
        return transformMapper.generateCircleWkt4326(bo);
    }
}
