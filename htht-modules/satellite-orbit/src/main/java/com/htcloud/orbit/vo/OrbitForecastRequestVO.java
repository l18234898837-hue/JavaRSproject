package com.htcloud.orbit.vo;

import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Data
public class OrbitForecastRequestVO {
    /**
     * 卫星轨道参数
     */
    @NotNull(message = "卫星轨道参数不能为空")
    private List<OrbitForecastSpecDto> orbitForecastSpecs;
    /**
     * 预测日期时间区间开始
     */
    @NotNull(message = "预测日期时间区间开始不能为空")
    private Date startTime;
    /**
     * 预测日期时间区间结束
     */
    @NotNull(message = "预测日期时间区间结束不能为空")
    private Date endTime;
    /**
     *  检测区域，WGS84坐标系
     */
    @NotNull(message = "检测区域不能为空")
    private Object zone;
}
