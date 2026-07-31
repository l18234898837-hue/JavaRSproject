package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/20 10:30
 **/
@Data
@Validated
public class WDSDQuery {
    @NotNull(message = "风向数据id不能为空")
    private Long directionDataId;
    @NotNull(message = "风速数据id不能为空")
    private Long speedDataId;
}
