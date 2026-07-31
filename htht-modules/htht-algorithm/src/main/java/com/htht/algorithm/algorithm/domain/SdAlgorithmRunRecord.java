package com.htht.algorithm.algorithm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 算法运行记录表
 * </p>
 *
 * @author MSFY
 * @since 2026-07-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
@TableName("sd_algorithm_run_record")
public class SdAlgorithmRunRecord extends BaseEntity {

    /**
     * 自增主键
     */
    @TableId("id")
    private Long id;

    /**
     * 算法id（关联sd_algorithm_file.id）
     */
    @TableField("algorithm_id")
    private Long algorithmId;

    /**
     * 算法运行开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 算法结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 算法运行状态
     */
    @TableField("run_status")
    private String runStatus;

    /**
     * 算法日志
     */
    @TableField("log")
    private String log;

    /**
     * 运行总时间（单位：毫秒）
     */
    @TableField("run_duration")
    private Long runDuration;
}
