package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 算法运行记录表，用于追踪每次算法执行的状态、用户、日志及运行目录
 * </p>
 *
 * @author MSFY
 * @since 2025-09-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_algorithm_record")
public class StAlgorithmRecord extends BaseEntity {

    /**
     * 自增主键，唯一标识每条运行记录
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 运行状态编码：0=移动文件到计算文件夹，1=排队中，2=运行中，3=运行完成，4=运行报错
     */
    private Short status;

    /**
     * 关联的用户ID，对应系统用户表的主键
     */
    private Long userId;

    /**
     * 算法运行日志，存储执行过程中的输出信息和错误详情
     */
    private String log;

    /**
     * 算法运行时的工作目录绝对路径，用于定位相关文件资源
     */
    private String runFolder;

    /**
     * 运行排序顺序
     */
    private Long queueOrder;
}
