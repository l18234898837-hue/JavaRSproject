package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * <p>
 *
 * </p>
 *
 * @author MSFY
 * @since 2025-07-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@Accessors(chain = true)
@TableName("st_instruments_info")
public class StInstrumentsInfo extends BaseEntity {

    @TableId
    private Long id;

    @NotBlank(message="仪器名称不能为空")
    private String instrumentName;

    @NotNull(message="图片ID不能为空")
    private Long imageId;

    private String manufacturer;

    private String distributor;

    private String parameters;
}
