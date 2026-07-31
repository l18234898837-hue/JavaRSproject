package com.htht.business.domain.vo.regions;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.htht.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 行政区域信息表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-25
 */
@Data
public class SysAdministrativeRegionsListVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 行政区划代码
     */
    private String regionsCode;

    /**
     * 父级行政区划代码
     */
    private String parentCode;

    /**
     * 区域名称
     */
    private String regionsName;

    /**
     * 简称
     */
    private String prettyname;

    /**
     * 区域等级
     */
    private Integer regionsLevel;

    /**
     * 中心点经度
     */
    private Float lon;

    /**
     * 中心点纬度
     */
    private Float lat;

    /**
     * 边界类型: Polygon或MultiPolygon
     */
    private String boundaryType;

    /**
     * 地理边界
     */
    private String boundary;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    private String delFlag;
}
