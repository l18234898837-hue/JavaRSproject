package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;
import org.apache.commons.jexl3.*;
import org.gdal.gdal.Dataset;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeComputed extends Computed {

    private final String regex = "B\\d*";

    private final Pattern pattern = Pattern.compile(regex);

    private final JexlEngine jexlEngine = new JexlBuilder().create();

    private Set<String> getMatcherList(String code) {
        Set<String> materList = new HashSet<>();
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            materList.add(matcher.group());
        }
        return materList;
    }

    public List<Double> compute(BusAlgorithmInfo algorithmInfo, String pm, List<String> tilePathList) {
        JSONObject params = JSONObject.parseObject(pm);
        String type = params.getString("type");
        String code = "";
        Dataset dataset = this.getDataset(tilePathList.get(0));
        Set<String> materList = getMatcherList(code);
        int bandCount = dataset.GetRasterCount();
        Map<String, double[]> bandDataMap = new HashMap<>();
        for (String mater : materList) {
            //得到波段索引
            int bandIdx = Integer.parseInt(mater.substring(1));
            if (bandIdx <= bandCount) {
                double[] bandData = this.getBandData(dataset, bandIdx);
                bandDataMap.put(mater, bandData);
            } else {
                bandDataMap.put(mater, new double[256 * 256]);
            }
        }

        List<Double> resultData = new ArrayList<>();

        if (!bandDataMap.keySet().isEmpty()) {
            double[] firstData = bandDataMap.values().stream().findFirst().get();
            for (int i = 0; i < firstData.length; i++) {
                JexlContext jc = new MapContext();
                jc.set("Math", Math.class);
                jc.set("type", type);
                for (String key : bandDataMap.keySet()) {
                    double[] bandData = bandDataMap.get(key);
                    double value = bandData[i];
                    jc.set(key, value);
                    System.out.print(value + "   ");
                }
                System.out.println();
                JexlScript e = jexlEngine.createScript(code);
                final Object result = e.execute(jc);
                resultData.add((Double) result);
            }
        }
        return resultData;
    }
}
