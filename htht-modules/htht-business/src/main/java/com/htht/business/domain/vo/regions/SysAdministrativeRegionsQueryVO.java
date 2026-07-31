package com.htht.business.domain.vo.regions;

import com.htht.common.build.QueryType;
import com.htht.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 行政区域信息表
 * </p>
 *
 * @author MSFY
 * @since 2025-09-25
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysAdministrativeRegionsQueryVO extends PageQuery {

    /**
     * 行政区划代码
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String regionsCode;

    /**
     * 父级行政区划代码
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String parentCode;

    /**
     * 简称
     */
    @QueryType(QueryType.QueryMode.LIKE)
    private String prettyname;

    /**
     * 区域等级
     */
    @QueryType(QueryType.QueryMode.EQ)
    private Integer regionsLevel;


}
