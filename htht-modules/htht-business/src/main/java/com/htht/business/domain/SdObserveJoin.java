package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 卫星观测数据与集合的关联表
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@TableName("sd_observe_join")
public class SdObserveJoin {

    /**
     * 关联的观测集合ID（对应sd_observe_list表的id）
     */
    private Long observeListId;

    /**
     * 关联的观测数据ID（对应sd_observe_data表的id）
     */
    private Long observeDataId;
}
