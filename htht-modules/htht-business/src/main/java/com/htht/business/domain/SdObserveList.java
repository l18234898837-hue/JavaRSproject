package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 卫星观测数据集合
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@TableName("sd_observe_list")
public class SdObserveList  {

    /**
     * 自增主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 观测集合的名称
     */
    private String name;
}
