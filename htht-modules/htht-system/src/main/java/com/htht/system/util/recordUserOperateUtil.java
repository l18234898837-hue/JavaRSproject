package com.htht.system.util;

import com.htht.common.helper.LoginHelper;
import com.htht.common.utils.spring.SpringUtils;
import com.htht.system.event.StUserOperateLogEvent;

import java.util.Objects;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/20 18:36
 **/
public class recordUserOperateUtil {
    /**
     * 扩展原本的登录信息，添加到新的用户操作日志表中
     */
    public static void recordUserOperateLogin(String functionModule,String operateType,String description,String result){
        StUserOperateLogEvent stUserOperateLogEvent = new StUserOperateLogEvent();
        stUserOperateLogEvent.setUserName( Objects.requireNonNull(LoginHelper.getUsername()));
        stUserOperateLogEvent.setFunctionModule(functionModule);
        stUserOperateLogEvent.setOperateType(operateType);
        stUserOperateLogEvent.setDescription(description);
        stUserOperateLogEvent.setResult(result);
        SpringUtils.context().publishEvent(stUserOperateLogEvent);
    }
    /**
     * 扩展原本的登录信息，添加到新的用户操作日志表中
     */
    public static void recordUserOperateLogin(String functionModule,String operateType,String description,String result,String userName){
        StUserOperateLogEvent stUserOperateLogEvent = new StUserOperateLogEvent();
        stUserOperateLogEvent.setUserName( userName);
        stUserOperateLogEvent.setFunctionModule(functionModule);
        stUserOperateLogEvent.setOperateType(operateType);
        stUserOperateLogEvent.setDescription(description);
        stUserOperateLogEvent.setResult(result);
        SpringUtils.context().publishEvent(stUserOperateLogEvent);
    }
}
