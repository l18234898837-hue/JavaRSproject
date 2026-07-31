package com.htht.system.event;

import lombok.Data;

import java.io.Serializable;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/20 17:46
 **/
@Data
public class StUserOperateLogEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    //用户名称
    String userName;
    //功能模块
    String functionModule;
    //操作类型
    String operateType;
    //操作描述
    String description ;
    //操作结果
    String result;
}
