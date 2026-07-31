package com.htht.algorithm.data.domain;

import aj.org.objectweb.asm.TypeReference;
import cn.hutool.json.ObjectMapper;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.htht.common.core.domain.BaseEntity;
import com.htht.common.exception.GlobalException;
import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 热力图色带配置表
 * </p>
 *
 * @author MSFY
 * @since 2026-03-05
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Validated
@TableName("sd_color_ramp")
public class SdColorRamp extends BaseEntity {

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 色带名称
     */
    private String name;

    /**
     * 数据最小值
     */
    private Double minValue;

    /**
     * 数据最大值
     */
    private Double maxValue;

    /**
     * 色带RGB配置（JSONB格式）
     */
    private String colorRamp;



}
