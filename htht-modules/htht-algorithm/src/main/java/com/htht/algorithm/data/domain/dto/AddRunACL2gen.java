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
public class AddRunACL2gen {
    @NotBlank(message = "h1d运算地址不能为空")
    private String runFileStr;
    @NotBlank(message = "myd03运算地址不能为空")
    private String mod03FileStr;
}
