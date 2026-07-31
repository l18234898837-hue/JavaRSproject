package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class ZeuComputed extends CommonComputed {
    @Override
    List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String type = params.getString("type");
        Dataset dataset = this.getDataset(tilePathList.get(0));
        List<Double> resultData = new ArrayList<>();
        if (type.equalsIgnoreCase("landsat-8")) {
            double[] bandData1 = this.getBandData(dataset, 1);
            double[] bandData2 = this.getBandData(dataset, 2);
            double[] bandData3 = this.getBandData(dataset, 3);
            for (int i = 0; i < bandData1.length; i++) {
                double B1 = bandData1[i];
                double B2 = bandData2[i];
                double B3 = bandData3[i];
                double value = Math.pow(0.935 - 119.747 * B1 + 123.008 * B2 - 4.822 * B3, -0.35);
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("sentinel-2")) {
            double[] bandData5 = this.getBandData(dataset, 5);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData5.length; i++) {
                double B5 = bandData5[i];
                double B4 = bandData4[i];
                double value = Math.pow(50.51 * ((B5 - B4) / (B5 + B4)) + 15.21, -0.35);
                resultData.add(value);
            }
        }
        dataset.delete();
        return resultData;
    }
}
