package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/18 16:30
 **/
@Data
@Accessors(chain = true)
public class AddAlgorithmTaskVo {
    @NotNull(message = "数据集不能为空")
    private Long[] dataIds;
    private Long[] spaceDataIds;
    private Long[] settingDataIds;

}
