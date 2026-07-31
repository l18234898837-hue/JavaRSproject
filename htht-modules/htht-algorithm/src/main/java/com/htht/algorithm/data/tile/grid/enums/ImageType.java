package com.htht.algorithm.data.tile.grid.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 切片类型
 *
 * @author wjx
 * @date 2023-4-19
 */
@Getter
@AllArgsConstructor
public enum ImageType {

    PNG(0, "png"),
    TIFF(1, "tiff");
    private int type;
    private String name;
}
