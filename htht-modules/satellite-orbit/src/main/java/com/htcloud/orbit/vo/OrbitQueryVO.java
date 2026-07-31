package com.htcloud.orbit.vo;

import lombok.Data;
import org.locationtech.jts.geom.Geometry;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class OrbitQueryVO {

    /**
     *  tle line 1
     */
    @NotBlank(message = "tleLine1不能为空")
    private String tleLine1;
    /**
     *  tle line 2
     */
    @NotBlank(message = "tleLine2不能为空")
    private String tleLine2;
    /**
     *  预测日期时间区间开始
     */
    @NotNull(message = "预测日期时间区间开始不能为空")
    private Date startTime;
    /**
     *  预测日期时间区间结束
     */
    @NotNull(message = "预测日期时间区间结束不能为空")
    private Date endTime;

    /**
     *  检测区域，WGS84坐标系
     */
    @NotNull(message = "检测区域不能为空")
    private Object zone;

    /**
     * 卫星ID
     */
    private Long satelliteInfoId;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * NORAD编号
     */
    private String noradNumber;

}
