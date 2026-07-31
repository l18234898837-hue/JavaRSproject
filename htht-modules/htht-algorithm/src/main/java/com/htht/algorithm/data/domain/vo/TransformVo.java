package com.htht.algorithm.data.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/22 15:27
 **/
@Data
public class TransformVo {
    @NotNull(message = "经度不能为空")
    double lng;
    @NotNull(message = "纬度不能为空")
    double lat;

    public static TransformVo saveArray(double[] array){
        TransformVo transformVo = new TransformVo();
        transformVo.lng=array[0];
        transformVo.lat=array[1];
        return transformVo;
    }
}
