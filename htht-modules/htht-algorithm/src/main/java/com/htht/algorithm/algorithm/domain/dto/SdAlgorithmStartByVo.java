package com.htht.algorithm.algorithm.domain.dto;

import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmFileVo;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/21 12:03
 **/
@Data
@Validated
public class SdAlgorithmStartByVo {
    @NotNull(message = "确保携带算法实体")
    SdAlgorithmFileVo sdAlgorithmFileVo;
    String params;
}
