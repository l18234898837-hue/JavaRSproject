package com.htht.algorithm.data.utils.ncOrHdf.lib;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.basic.resultcreator.json.model.Header;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper=true)
@AllArgsConstructor
@NoArgsConstructor
public class JsonCreatorHeader extends Header {

    /**
     * 类别名称
     */
    private String parameterCategoryName;
    /**
     * 维度名称
     */
    private String parameterNumberName;

    /**
     * 存储NC文件的全部变量信息
     */
    private List<Map<String, Object>> attributes ;

    private Map<String, Object> metadata ;

    /**
     * 带NC属性信息的header创建方法
     */
    public static JsonCreatorHeader border2Header(JsonCreatorBorder border, List<Map<String, Object>> attributes) {
        JsonCreatorHeader header = border2Header(border);
        if (attributes != null) {
            header.setAttributes(attributes);
        }
        return header;
    }

    public static JsonCreatorHeader border2Header(JsonCreatorBorder border) {
    	JsonCreatorHeader header = new JsonCreatorHeader();
        header.setDy(border.getLatOffset());
        header.setDx(border.getLngOffset());
        header.setLo1(border.getMinLng());
        header.setLo2(border.getMaxLng());
        header.setLa2(border.getMinLat());
        header.setLa1(border.getMaxLat());
        header.setNx(border.getSizeX());
        header.setNy(border.getSizeY());
        header.setRefTime(new Date());
        header.parameterCategoryName = border.getParameterCategoryName();
        header.parameterNumberName = border.getParameterNumberName();
        header.attributes = border.getAttributes();
        header.metadata = border.getMetadata();
        return header;
    }
}
