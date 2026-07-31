package com.htcloud.orbit.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 空间查询类型枚举类
 *
 * @author wjx
 * @date 2023-03-23
 */
@Getter
@AllArgsConstructor
public enum BoundType {

    REGION(1, "行政区划"),
    CIRCLE(2, "圆形"),
    GEOMETRY(3, "矢量");
    private Integer type;
    private String name;

}
