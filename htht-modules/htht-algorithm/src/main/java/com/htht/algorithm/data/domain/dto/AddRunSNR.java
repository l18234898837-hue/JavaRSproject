package com.htht.algorithm.data.domain.dto;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/12 17:39
 **/
@Data
@Validated
public class AddRunSNR {
    @NotBlank(message = "运算文件地址不能为空")
    private String runFileStr;
    @NotNull(message = "区域1不能为空")
    private Integer area1;
    @NotNull(message = "区域2不能为空")
    private Integer area2;
    @NotNull(message = "区域3不能为空")
    private Integer area3;
    @NotNull(message = "区域4不能为空")
    private Integer area4;
}
