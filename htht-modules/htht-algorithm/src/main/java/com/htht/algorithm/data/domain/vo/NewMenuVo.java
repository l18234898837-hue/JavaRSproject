package com.htht.algorithm.data.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/5 16:35
 **/
@Data
@Accessors(chain = true)
public class NewMenuVo {
    private Long id;
    private String name;
}
