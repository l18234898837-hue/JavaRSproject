package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@TableName("st_instruments")
public class StInstruments extends BaseEntity {

    @TableId
    private Long id;

    @NotBlank(message="仪器编号不能为空")
    private String instrumentCode;

    @NotNull(message = "仪器信息id不能为空")
    private Long instrumentInfoId;

    private LocalDate commissionDate;

    private String useVoyage;

    private String manager;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
