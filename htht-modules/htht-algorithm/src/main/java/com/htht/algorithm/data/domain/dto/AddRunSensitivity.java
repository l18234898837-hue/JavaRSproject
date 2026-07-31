package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/12 17:39
 **/
@Data
@Validated
public class AddRunSensitivity {
    @NotBlank(message = "运算文件地址不能为空")
    private String runFileStr;
}
