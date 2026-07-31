package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 在线数据下载记录表
 * </p>
 *
 * @author MSFY
 * @since 2025-10-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_online_download_record")
public class StOnlineDownloadRecord extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 数据名称
     */
    private String name;

    /**
     * 在线类别：1，Landsat，2，Sentinel
     */
    private Integer type;

    /**
     * 参数1
     */
    private String param1;

    /**
     * 参数2
     */
    private String param2;

    /**
     * 下载状态：1，等待下载，2，正在下载，3，下载失败，4，下载完成，5，暂停下载
     */
    private Integer status;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 下载进度
     */
    private Integer progress;

    /**
     * 入库状态：0，未入库，1，已入库，2，入库失败
     */
    private Integer store;

    /**
     * 下载文件地址
     */
    private String filePath;
}
