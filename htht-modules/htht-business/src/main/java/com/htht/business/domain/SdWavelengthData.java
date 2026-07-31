package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * <p>
 * 卫星波长数据
 * </p>
 *
 * @author MSFY
 * @since 2025-10-30
 */
@Data
@EqualsAndHashCode()
@Accessors(chain = true)
@TableName("sd_wavelength_data")
public class SdWavelengthData {

    /**
     * 卫星id或者站点
     */
    private String sdIdOrSite;

    /**
     * 波长
     */
    private Integer  wavelength;

    /**
     * 存储的观测数据
     */
    private BigDecimal data;


    public SdWavelengthData setData(String  data){
        if(data != null&&!data.isEmpty()){
            this.data=new BigDecimal(data);
        }
        return this;
    }


    public SdWavelengthData setWavelength(String  waveLength){
        if(waveLength != null&&!waveLength.isEmpty()){
            this.wavelength=Integer.parseInt(waveLength);
        }
        return this;
    }
}
