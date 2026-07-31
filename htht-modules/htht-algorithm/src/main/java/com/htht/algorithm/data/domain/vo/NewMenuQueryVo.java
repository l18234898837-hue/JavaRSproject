package com.htht.algorithm.data.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/5 16:46
 **/
@Data
public class NewMenuQueryVo {
    @NotBlank(message = "name不能为空")
    private String name;
    @NotNull(message = "type不能为空")
    private Integer type;
}
