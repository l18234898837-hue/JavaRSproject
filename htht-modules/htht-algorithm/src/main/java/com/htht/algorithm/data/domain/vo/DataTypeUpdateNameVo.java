package com.htht.algorithm.data.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/8/27 16:19
 **/
@Data
public class DataTypeUpdateNameVo {
    @NotNull(message = "id不能为空")
    private long id;
    @NotBlank(message = "数据类型名称不能为空")
    private String name;
}
