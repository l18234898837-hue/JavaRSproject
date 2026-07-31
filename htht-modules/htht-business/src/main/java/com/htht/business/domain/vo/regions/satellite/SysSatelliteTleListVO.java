package com.htht.business.domain.vo.regions.satellite;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysSatelliteTleListVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 卫星ID
     */
    private Long satelliteInfoId;

    /**
     * line1
     */
    private String tleLine1;

    /**
     * line2
     */
    private String tleLine2;

    /**
     * 历元时间
     */
    private LocalDateTime epochTime;

//    /**
//     * 轨道倾角(度)
//     */
//    private String inclination;
//
//    /**
//     * 平均运动的一阶时间导数(rev/day²)
//     */
//    private String meanMotionDot;
//
//    /**
//     * 平均运动的二阶时间导数(rev/day³)
//     */
//    private String meanMotionDdot;
//
//    /**
//     * BSTAR阻力系数(/地球半径)
//     */
//    private String bstarDragCoefficient;
//
//    /**
//     * 星历类型
//     */
//    private String ephemerisType;
//
//    /**
//     * 元素编号
//     */
//    private String elementNumber;
//
//    /**
//     * 升交点赤经(度)
//     */
//    private String rightAscension;
//
//    /**
//     * 偏心率
//     */
//    private String eccentricity;
//
//    /**
//     * 近地点幅角(度)
//     */
//    private String argumentOfPerigee;
//
//    /**
//     * 平近点角(度)
//     */
//    private String meanAnomaly;
//
//    /**
//     * 平均运动(rev/day)
//     */
//    private String meanMotion;
//
//    /**
//     * 轨道周期(分钟)
//     */
//    private Integer revolutionNumber;
//
//    /**
//     * 删除标志（0代表存在 2代表删除）
//     */
//    private String delFlag;

    private String noradNumber;
}
