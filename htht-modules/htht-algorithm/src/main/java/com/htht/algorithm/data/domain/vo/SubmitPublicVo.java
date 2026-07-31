package com.htht.algorithm.data.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/30 15:42
 **/
@Data
public class SubmitPublicVo {
    @NotNull(message = "数据集不能为空")
    private List<Long> dataIds;
    @NotBlank(message="申请理由不能为空")
    private String reason;
    @NotBlank(message="申请区域不能为空")
    private String region;
}
