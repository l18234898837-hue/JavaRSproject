package com.htht.algorithm.data.utils.ncOrHdf.lib;

import java.io.File;

import com.basic.resultcreator.json.BaseJsonCreator;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper=true)
public class JsonCreator extends BaseJsonCreator {

	private float[][] datas;
    private JsonCreatorBorder border;

    public JsonCreator(float[][] datas, JsonCreatorBorder border) {
        this.datas = datas;
        this.border = border;
    }

	@Override
	public File create(String path) {
		JsonCreatorHeader header = JsonCreatorHeader.border2Header(border);
        JsonCreatorFlowBean flowBean = new JsonCreatorFlowBean();
        flowBean.setHeader(header);
        flowBean.setDatas(datas);
        boolean b = write2File(path, flowBean);
        if (b) {
            return new File(path);
        }
        return null;
    }

}
