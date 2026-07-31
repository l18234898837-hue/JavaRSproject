package com.htht.business.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/15 14:51
 **/
@Data
public class PythonAlgorithmFileInfoUpdateVo {
    @NotNull(message = "id不能为空")
    private Long id;


    /**
     * 算法名称，如“NDVI计算算法”
     */
    private String algorithmName;


    /**
     * 算法描述，说明该Python算法功能、输入输出要求等
     */
    private String description;

}
