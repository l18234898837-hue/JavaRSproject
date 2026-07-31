package com.htht.business.domain.vo;

import com.htht.common.core.domain.PageQuery;
import lombok.Data;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/12/29 14:00
 **/
@Data
public class StDocQueryVo {
    String type;
    String name;
    String fileType;
    PageQuery pageQuery;
}
