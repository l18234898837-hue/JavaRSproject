package com.htht.algorithm.data.domain.vo;

import com.alibaba.fastjson2.JSON;
import com.htht.algorithm.data.domain.SdColorRamp;
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
@EqualsAndHashCode()
@Accessors(chain = true)
@Validated
public class ColorRampVo {

    private Long id;

    /**
     * 色带名称
     */
    @NotBlank(message = "色带名称不能为空")
    private String name;

    /**
     * 数据最小值
     */
    @NotNull(message = "数据最小值不能为空")
    private Double minValue;

    /**
     * 数据最大值
     */
    @NotNull(message = "数据最大值不能为空")
    private Double maxValue;

    /**
     * 色带RGB配置（JSONB格式）
     */
    @NotBlank(message = "色带配置不能为空")
    private List<int[]> colorRamp;

    public void checkColorRamp(){
        if(minValue >= maxValue){
            throw new GlobalException("数据最小值不能大于数据最大值");
        }
        if (colorRamp == null || colorRamp.isEmpty()) {
            throw new GlobalException("色带配置不能为空");
        }
        if(colorRamp.size() < 2){
            throw new GlobalException("色带配置至少需要两个颜色");
        }
        for (int[] color : colorRamp) {
            if(color.length != 3){
                throw new GlobalException("色带配置的每个颜色必须为3个元素");
            }
        }
    }

    public SdColorRamp toSdColorRamp(){
        return new SdColorRamp().setName(name)
            .setMinValue(minValue)
            .setMaxValue(maxValue)
            .setColorRamp(JSON.toJSONString(colorRamp));
    }

    public static ColorRampVo fromSdColorRamp(SdColorRamp sdColorRamp){
        return new ColorRampVo().setName(sdColorRamp.getName())
            .setMinValue(sdColorRamp.getMinValue())
            .setMaxValue(sdColorRamp.getMaxValue())
            .setColorRamp(JSON.parseObject(sdColorRamp.getColorRamp(), new com.alibaba.fastjson2.TypeReference<>() {
            }));
    }

}
