package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 下载记录表，记录数据下载的详细信息及状态变迁
 * </p>
 *
 * @author MSFY
 * @since 2025-09-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_download_log")
public class StDownloadLog extends BaseEntity {

    /**
     * 自增主键，唯一标识每条下载记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

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
     * 下载状态枚举：1=已执行，2=异常中断
     */
    private Short downloadStatus;

    /**
     * 跟踪ID，用于下载完成/失败时的自动状态关联
     */
    private Long recordId;
}
