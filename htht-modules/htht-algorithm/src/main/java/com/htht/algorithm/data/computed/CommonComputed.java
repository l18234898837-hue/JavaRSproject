package com.htht.algorithm.data.computed;

import com.alibaba.fastjson2.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.List;

public abstract class CommonComputed extends Computed{
    abstract List<Double> compute(JSONObject params, List<String> tilePathList) throws UnsupportedEncodingException;
}
