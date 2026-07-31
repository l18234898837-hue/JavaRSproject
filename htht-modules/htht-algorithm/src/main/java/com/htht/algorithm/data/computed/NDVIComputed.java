package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Component
public class NDVIComputed extends CommonComputed {

    @Override
    public List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String tilePath = tilePathList.get(0);
        String type = params.getString("type");
        List<Double> resultData = new ArrayList<>();
        Dataset dataset = this.getDataset(tilePath);
        if (dataset == null) return resultData;
        if (type.equalsIgnoreCase("GF1") || type.equalsIgnoreCase("GF2")) {
            double[] bandData3 = this.getBandData(dataset, 3);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData3.length; i++) {
                double B3 = bandData3[i];
                double B4 = bandData4[i];
                double value = (B4 - B3) / (B4 + B3);
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("landsat-8")) {
            double[] bandData5 = this.getBandData(dataset, 5);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData5.length; i++) {
                double B5 = bandData5[i];
                double B4 = bandData4[i];
                if (B5 == B4 && B5 == 0) {
                    System.out.println(B5);
                }
                double value = (B5 - B4) / (B5 + B4);
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("sentinel-2")) {
            double[] bandData8 = this.getBandData(dataset, 5);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData8.length; i++) {
                double B8 = bandData8[i];
                double B4 = bandData4[i];
                double value = (B8 - B4) / (B8 + B4);
                resultData.add(value);
            }
        }
        dataset.delete();
        return resultData;
    }
}

