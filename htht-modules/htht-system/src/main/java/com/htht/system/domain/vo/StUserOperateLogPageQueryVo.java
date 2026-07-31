package com.htht.system.domain.vo;

import com.htht.common.core.domain.PageQuery;
import lombok.Data;

import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 09:38
 **/
@Data
public class StUserOperateLogPageQueryVo {
    String userName;
    List<String> functionModules;
    List<String> operateTypes;
    String result;
    String startTime;
    String endTime;
    PageQuery pageQuery;
}
