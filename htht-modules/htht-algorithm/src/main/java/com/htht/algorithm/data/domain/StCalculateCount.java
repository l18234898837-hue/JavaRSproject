package com.htht.algorithm.data.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author MSFY
 * @since 2025-10-14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_calculate_count")
public class StCalculateCount extends BaseEntity {

    private Long userId;

    private Integer maxCount;

    private Integer currentCount;
}
