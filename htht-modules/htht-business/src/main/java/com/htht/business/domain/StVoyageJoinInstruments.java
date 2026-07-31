package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;

/**
 * <p>
 *
 * </p>
 *
 * @author MSFY
 * @since 2025-07-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("st_voyage_join_instruments")
public class StVoyageJoinInstruments extends BaseEntity {

    /**
     * 航线主键ID，关联st_voyages表
     */
    @TableId(value = "voyage_id", type = IdType.NONE)
    @NotNull(message = "航线ID不能为空")
    private Long voyageId;

    /**
     * 仪器主键ID，关联st_instruments表
     */
    @NotNull(message = "仪器ID不能为空")
    private Long instrumentId;

    /**
     * 关联数据集ID（当同一航线的同款仪器有多个数据集时使用）
     */
    @NotNull(message = "数据集ID不能为空")
    private Long datasetId;
}
