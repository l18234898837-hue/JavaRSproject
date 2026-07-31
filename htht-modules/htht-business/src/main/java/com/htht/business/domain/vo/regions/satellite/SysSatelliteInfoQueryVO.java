package com.htht.business.domain.vo.regions.satellite;

import com.htht.common.build.QueryType;
import com.htht.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class SysSatelliteInfoQueryVO extends PageQuery implements Serializable {

    /**
     * 分辨率
     */
    @QueryType(QueryType.QueryMode.EQ)
    private Double resolution;

    /**
     * 所属国家
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String country;

    /**
     * NORAD编号
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String noradNumber;

    /**
     * 类型
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String satelliteCategory;
}
