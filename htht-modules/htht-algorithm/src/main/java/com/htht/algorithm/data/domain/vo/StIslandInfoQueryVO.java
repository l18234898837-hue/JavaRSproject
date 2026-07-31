package com.htht.algorithm.data.domain.vo;

import com.htht.common.build.QueryType;
import com.htht.common.core.domain.PageQuery;
import lombok.Data;

@Data
public class StIslandInfoQueryVO extends PageQuery {

    /**
     * 群岛名称（如东沙、西沙）
     */
    @QueryType(QueryType.QueryMode.EQ)
    private String archipelago;

    /**
     * 标准名称（拼音/混合格式）
     */
    @QueryType(QueryType.QueryMode.LIKE)
    private String standardName;

    /**
     * 中文地理名称
     */
    @QueryType(QueryType.QueryMode.LIKE)
    private String chineseName;

}
