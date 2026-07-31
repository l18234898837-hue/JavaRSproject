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
 * 算法库表
 * </p>
 *
 * @author MSFY
 * @since 2026-07-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
@TableName("sd_algorithm_library")
public class SdAlgorithmLibrary extends BaseEntity {

    /**
     * 自增主键
     */
    @TableId("id")
    private Long id;

    /**
     * 库名称
     */
    @TableField("library_name")
    private String libraryName;

    /**
     * 排序序号（数值越小越靠前）
     */
    @TableField("sort_order")
    private Integer sortOrder;
}
