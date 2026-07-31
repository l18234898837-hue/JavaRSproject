package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * S3在线数据下载记录表，用于追踪数据下载全流程
 * </p>
 *
 * @author MSFY
 * @since 2025-08-08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_online_download_info")
public class StOnlineDownloadInfo extends BaseEntity {

    /**
     * 记录唯一标识
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 下载数据的名称（如卫星影像文件名）
     */
    private String name;

    /**
     * 下载状态枚举：0-初始化，1-下载中，2-成功，3-失败
     */
    private Short status;

    /**
     * 在线数据入库状态：0-未入库，1-已入库
     */
    private Short storeStatus;

    /**
     * 在线数据下载类别
     */
    private int dataType;

    /**
     * S3源文件路径（如s3://usgs-landsat/collection02/...）
     */
    private String s3Url;

    /**
     * 本地存储绝对路径（如D:/data/landsat/xxx.tif）
     */
    private String downloadUrl;

    /**
     * 下载失败时的错误详情（如Access Denied、网络超时）
     */
    private String errorMsg;

    /**
     * 扩展备注（如“定时任务触发”“用户手动下载”）
     */
    private String remark;

}
