package com.htht.algorithm.data.utils.ncOrHdf.lib;

import lombok.Data;

@Data
public class JsonCreatorFlowBean {

    private JsonCreatorHeader header;

    /**
     * 设置该值，则为图片数据
     */
    private float[][] datas;

    /**
     * u分量数组 eastward_wind
     */
    private float[][] uDatas;
    /**
     * v分量数组 northward_wind
     */
    private float[][] vDatas;

}
