package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import org.gdal.gdal.Dataset;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class TsmComputed extends CommonComputed {
    @Override
    List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        String type = params.getString("type");
        Dataset dataset = this.getDataset(tilePathList.get(0));
        List<Double> resultData = new ArrayList<>();
        if (type.equalsIgnoreCase("landsat-8")) {
            double[] bandData1 = this.getBandData(dataset, 1);
            double[] bandData2 = this.getBandData(dataset, 2);
            double[] bandData4 = this.getBandData(dataset, 4);
            for (int i = 0; i < bandData1.length; i++) {
                double B1 = bandData1[i];
                double B2 = bandData2[i];
                double B4 = bandData4[i];
                double value = 30.089 - 4110.846 * B1 + 4221.346 * B2 - 30.711 * B4;
                resultData.add(value);
            }
        } else if (type.equalsIgnoreCase("sentinel-2")) {
            double[] bandData4 = this.getBandData(dataset, 4);
            for (double B4 : bandData4) {
                double value = 8146.03 * Math.pow(B4, 2) - 738.37 * B4 + 53.011;
                resultData.add(value);
            }
        }
        dataset.delete();
        return resultData;
    }
}
