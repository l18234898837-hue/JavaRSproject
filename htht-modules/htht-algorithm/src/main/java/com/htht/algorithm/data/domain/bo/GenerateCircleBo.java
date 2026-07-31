package com.htht.algorithm.data.domain.bo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/1/4 14:09
 **/
@Data
@Validated
public class GenerateCircleBo {
    @NotBlank(message = "中心点不能为空")
    private String centerWkt;
    @NotBlank(message = "半径不能为空")
    private Integer radius;
}
