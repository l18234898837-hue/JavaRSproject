package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 数据下载记录表，记录用户提交的下载任务信息
 * </p>
 *
 * @author MSFY
 * @since 2025-08-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_download_record")
public class StDownloadRecord extends BaseEntity {

    /**
     * 记录唯一标识（自增）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 下载任务序号（可用于业务层面排序或编号）
     */
    private Integer serialNumber;

    /**
     * 下载任务执行时间（带时区）
     */
    private LocalDateTime downloadTime;

    /**
     * 下载的具体数据名称
     */
    private String dataName;

    /**
     * 数据级别（如L1级、L2级、标准级等）
     */
    private String dataLevel;

    /**
     * 提交下载任务的用户ID（关联用户表主键）
     */
    private Long submitUserId;
}
