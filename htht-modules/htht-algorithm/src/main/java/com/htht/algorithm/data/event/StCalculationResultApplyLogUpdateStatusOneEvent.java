package com.htht.algorithm.data.event;

import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 14:40
 **/
@EqualsAndHashCode(callSuper = false)
@Data
@Accessors(chain = true)
public class StCalculationResultApplyLogUpdateStatusOneEvent  extends LogBaseEvent {
    /**
     * 请求组ID，用于同批次申请的归类管理
     */
    private Long applyGroupId;
    /**
     * 申请状态枚举：1=审核中，2=已通过，3=已驳回
     */
    private Short applyStatus;

    /**
     * 数据唯一ID，用于后续跟踪申请状态的变更
     */
    private Long dataId;

}
