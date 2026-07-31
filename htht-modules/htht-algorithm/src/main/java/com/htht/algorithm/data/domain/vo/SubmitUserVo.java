package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/30 15:42
 **/
@Data
public class SubmitUserVo {
    @NotNull(message = "数据集不能为空")
    private Long[] dataIds;
}
