package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class DoComputed extends CommonComputed {
    @Override
    List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String type = params.getString("type");
        Dataset dataset = this.getDataset(tilePathList.get(0));
        List<Double> resultData = new ArrayList<>();
        if (type.equalsIgnoreCase("sentinel-2")) {
            double[] bandData4 = this.getBandData(dataset, 4);
            double[] bandData7 = this.getBandData(dataset, 7);
            for (int i = 0; i < bandData4.length; i++) {
                double B4 = bandData4[i];
                double B7 = bandData7[i];
                double value = 5.87 * ((B4 - B7) / (B4 + B7)) + 3.28;
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("sentinel-8")) {
            double[] bandData4 = this.getBandData(dataset, 4);
            double[] bandData6 = this.getBandData(dataset, 6);
            for (int i = 0; i < bandData4.length; i++) {
                double B4 = bandData4[i];
                double B6 = bandData6[i];
                double value = 39.199 - 49.553 * (B4 / B6) + 24.849 * Math.pow(B4 / B6, 2) - 3.936 * Math.pow(B4 / B6, 3);
                resultData.add(value);
            }

        }
        dataset.delete();
        return resultData;
    }
}
