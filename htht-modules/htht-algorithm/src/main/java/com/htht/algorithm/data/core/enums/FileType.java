package com.htht.algorithm.data.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.htht.common.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileType {

    GEO_TIFF(1, "栅格"),
    SHP(2, "矢量"),
    WIND(3, "流场"),
    IMAGE(4, "贴图");

    @EnumValue
    private int type;
    private String name;

    public static FileType getByType(int type) {
        for (FileType fileType : FileType.values()) {
            if (fileType.getType() == type) {
                return fileType;
            }
        }
        throw new GlobalException("未知的数据类型：" + type);
    }
}
