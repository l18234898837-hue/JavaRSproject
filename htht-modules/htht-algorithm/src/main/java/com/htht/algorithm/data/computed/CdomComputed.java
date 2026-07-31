package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class CdomComputed extends CommonComputed {
    @Override
    List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String type = params.getString("type");
        Dataset dataset = this.getDataset(tilePathList.get(0));
        List<Double> resultData = new ArrayList<>();
        if (type.equalsIgnoreCase("landsat-8")) {
            double[] bandData2 = this.getBandData(dataset, 2);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData2.length; i++) {
                double B2 = bandData2[i];
                double B4 = bandData4[i];
                double value = 0.0723 * Math.exp(0.0723 * (B4 / B2));
                resultData.add(value);
            }
        }
        dataset.delete();
        return resultData;
    }
}
