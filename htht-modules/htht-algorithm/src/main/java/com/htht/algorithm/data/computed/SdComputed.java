package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class SdComputed extends CommonComputed {
    @Override
    List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String type = params.getString("type");
        Dataset dataset = this.getDataset(tilePathList.get(0));
        List<Double> resultData = new ArrayList<>();
        if (type.equalsIgnoreCase("sentinel-2")) {
            double[] bandData4 = this.getBandData(dataset, 4);
            for (double B4 : bandData4) {
                double value = 0.0126 * B4 + 9.295;
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("landsat-8")) {
            double[] bandData1 = this.getBandData(dataset, 1);
            double[] bandData3 = this.getBandData(dataset, 3);
            for (int i = 0; i < bandData1.length; i++) {
                double B1 = bandData1[i];
                double B3 = bandData3[i];
                double value = 110.0 - 107.138 * (B3 / B1) - 38.11 * (B1 + B3) / 2;
                resultData.add(value);
            }
        }
        dataset.delete();
        return resultData;
    }
}
