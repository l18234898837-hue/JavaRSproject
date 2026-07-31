package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;
import com.htht.common.exception.UtilException;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ComputedUtil {

    private static final Map<Integer, CommonComputed> computedMap = new ConcurrentHashMap<>();
    private static final CodeComputed codeComputed = new CodeComputed();

    private static CommonComputed getComputed(Integer id) {
        CommonComputed computed = computedMap.get(id);
        if (computed == null) {
            if (id == 1) {
                computed = new ChlComputed();
            } else if (id == 2) {
                computed = new ZeuComputed();
            } else if (id == 3) {
                computed = new TsmComputed();
            } else if (id == 4) {
                computed = new CdomComputed();
            } else if (id == 5) {
                computed = new Co2Computed();
            } else if (id == 6) {
                computed = new NDVIComputed();
            } else if (id == 7) {
                computed = new SdComputed();
            } else if (id == 8) {
                computed = new DoComputed();
            } else {
                throw new UtilException("未找到算法");
            }
            computedMap.put(id, computed);
        }
        return computed;
    }

    public static List<Double> start(Integer id, JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException {
        CommonComputed computed = getComputed(id);
        return computed.compute(params, tilePathList);
    }

    public static List<Double> startCode(BusAlgorithmInfo algorithmInfo, String pm, List<String> tilePathList) {
        return codeComputed.compute(algorithmInfo, pm, tilePathList);
    }
}
