package com.htht.business.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.htht.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * <p>
 * 卫星信息表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sys_satellite_info")
public class SysSatelliteInfo extends BaseEntity {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 卫星名称
     */
    private String satelliteName;

    /**
     * 卫星代码
     */
    private String satelliteCode;

    /**
     * 分辨率
     */
    private Double resolution;

    /**
     * 所属国家
     */
    private String country;

    /**
     * NORAD编号
     */
    private String noradNumber;

    /**
     * 布幅宽度
     */
    private Long clothWidth;

    /**
     * 倾斜角度
     */
    private String sideAngle;

    /**
     * 卫星类型
     */
    private String satelliteType;

    /**
     * 轨道高度
     */
    private Integer orbitalAltitude;

    /**
     * 当前高度
     */
    private Integer altitude;

    /**
     * 卫星标识行
     */
    private String satelliteIdentityLine;

    /**
     * 卫星来源
     */
    private String satelliteSource;

    /**
     * 卫星系列
     */
    private String satelliteSeries;

    /**
     * 发射日期
     */
    private LocalDateTime launchDate;

    /**
     * 卫星状态
     */
    private String satelliteStatus;

    /**
     * 描述
     */
    private String remarks;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;

    /**
     * 类型
     */
    private String satelliteCategory;
}
