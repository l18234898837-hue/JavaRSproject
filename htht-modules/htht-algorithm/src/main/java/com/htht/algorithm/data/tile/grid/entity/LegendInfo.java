package com.htht.algorithm.data.tile.grid.entity;

import com.alibaba.fastjson.JSONArray;
import lombok.Data;

import java.util.List;

/**
 * 栅格配色信息，和存储在数据库中的json一致，便于转换
 *
 * @author wjx
 * @since 2023-6-6
 */
@Data
public class LegendInfo {
    private Long id;

    //配色类型：1-波段组合，2-单波段唯一值，3-单波段分级
    private int type;
    /**
     * 波段组合数组
     */
    private int[] bands;
    private String field;
    private Boolean hasTwoField;
    private String twoField;

    /**
     * 单波段的分级或唯一值配色
     */
    private List<Grade> legendList;


    @Data
    public class Grade {
        //值
        private String value;
        //标签
        private String label;
        //颜色
        private String color;
        private JSONArray children;
    }

}
