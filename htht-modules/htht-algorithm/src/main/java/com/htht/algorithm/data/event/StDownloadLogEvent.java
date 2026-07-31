package com.htht.algorithm.data.event;


import com.htht.algorithm.base.event.LogBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/22 14:32
 **/
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Data
public class StDownloadLogEvent  extends LogBaseEvent {
    /**
     * 用户ID，关联系统用户表的主键
     */
    private Long userId;

    /**
     * 待下载的数据名称
     */
    private String dataName;

    /**
     * 数据格式（如栅格、矢量等类型）
     */
    private String dataType;

    /**
     * 卫星或传感器标识，区分不同数据源
     */
    private String satelliteSensor;

    /**
     * 数据产品级别（如原始数据、处理后数据）
     */
    private String dataLevel;

    /**
     * 下载文件的存储地址：0=本地服务器，1=电脑本地
     */
    private Short downloadLocation;

    /**
     * 下载状态枚举：1：已执行，2：异常中断
     */
    private Short downloadStatus;

    /**
     * 跟踪ID，用于下载完成/失败时的自动状态关联
     */
    private Long recordId;
}
