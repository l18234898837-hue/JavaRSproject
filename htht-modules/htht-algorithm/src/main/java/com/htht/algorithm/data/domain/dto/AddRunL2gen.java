package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/25 10:46
 **/
@Data
@Validated
public class AddRunL2gen {
    @NotBlank(message = "运算地址不能为空")
    private String runFileStr;

}
