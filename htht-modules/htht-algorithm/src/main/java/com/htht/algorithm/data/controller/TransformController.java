package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.bo.GenerateCircleBo;
import com.htht.algorithm.data.domain.vo.TransformVo;
import com.htht.algorithm.data.service.TransformService;
import com.htht.algorithm.data.transform.CoordinateTransform;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * 坐标转化工具包
 *
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/22 15:00
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/transform")
public class TransformController {

    private final TransformService transformService;

    @PostMapping("WGS84ToGCJ02")
    public R<TransformVo> WGS84ToGCJ02(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.wgs84ToGcj02(transformVo.getLng(), transformVo.getLat())));
    }

    @PostMapping("GCJ02ToWGS84")
    public R<TransformVo> GCJ02ToWGS84(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.gcj02ToWgs84(transformVo.getLng(), transformVo.getLat())));
    }

    @PostMapping("GCJ02ToBD09")
    public R<TransformVo> GCJ02ToBD09(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.gcj02ToBd09(transformVo.getLng(), transformVo.getLat())));
    }

    @PostMapping("BD09ToGCJ02")
    public R<TransformVo> BD09ToGCJ02(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.bd09ToGcj02(transformVo.getLng(), transformVo.getLat())));
    }

    @PostMapping("WGS84ToBD09")
    public R<TransformVo> WGS84ToBD09(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.wgs84ToBd09(transformVo.getLng(), transformVo.getLat())));
    }

    @PostMapping("BD09ToWGS84")
    public R<TransformVo> BD09ToWGS84(@RequestBody TransformVo transformVo){
        return R.ok(TransformVo.saveArray(CoordinateTransform.bd09ToWgs84(transformVo.getLng(), transformVo.getLat())));
    }


    @PostMapping("ToCircleWkt3857")
    public R<String> ToCircleWkt3857(@RequestBody GenerateCircleBo bo){
        return R.ok("转化成功",transformService.generateCircleWkt3857(bo));
    }

    @PostMapping("ToCircleWkt4326")
    public R<String> ToCircleWkt4326(@RequestBody  GenerateCircleBo bo){
        return R.ok("转化成功",transformService.generateCircleWkt4326(bo));
    }
}
