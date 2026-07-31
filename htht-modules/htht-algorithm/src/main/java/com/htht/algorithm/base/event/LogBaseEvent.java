package com.htht.algorithm.base.event;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 14:50
 **/

import com.htht.common.utils.spring.SpringUtils;
import lombok.Data;

import java.io.Serializable;

@Data
public class LogBaseEvent implements Serializable {
    private static final long serialVersionUID = 1L;


    public void sendEvent(){
        SpringUtils.context().publishEvent(this);
    }
}
