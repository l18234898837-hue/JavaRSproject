package com.htht.algorithm.data.utils.ncOrHdf.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.basic.resultcreator.json.BaseJsonCreator;

public class JsonArrayCreator extends BaseJsonCreator {

	private List<JsonCreatorFlowBean> flowBeanList;

    /**
     * 构造函数 - 直接传入FlowBean列表
     */
    public JsonArrayCreator(List<JsonCreatorFlowBean> flowBeanList) {
        this.flowBeanList = flowBeanList;
    }

    /**
     * 构造函数 - 传入数据列表和对应的边界信息列表
     */
    public JsonArrayCreator(List<float[][]> dataList, List<JsonCreatorBorder> borderList) {
        if (dataList.size() != borderList.size()) {
            throw new IllegalArgumentException("数据列表和边界信息列表长度必须一致");
        }

        this.flowBeanList = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            JsonCreatorHeader header = JsonCreatorHeader.border2Header(borderList.get(i));
            JsonCreatorFlowBean flowBean = new JsonCreatorFlowBean();
            flowBean.setHeader(header);
            flowBean.setDatas(dataList.get(i));
            flowBeanList.add(flowBean);
        }
    }

    @Override
    public File create(String path) {
        boolean b = write2File(path, flowBeanList);
        if (b) {
            return new File(path);
        }
        return null;
    }

}
