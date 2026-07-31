package com.htht.business.domain.vo.regions.satellite;

import com.htht.common.build.QueryType;
import com.htht.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class SysSatelliteTleQueryVO extends PageQuery {

    /**
     * 卫星ID
     */
    @QueryType(QueryType.QueryMode.EQ)
    private Long satelliteInfoId;

}
