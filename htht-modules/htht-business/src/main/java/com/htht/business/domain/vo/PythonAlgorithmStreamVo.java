package com.htht.business.domain.vo;

import com.htht.business.domain.StPythonAlgorithmStream;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/8/15 15:31
 **/
@Data
public class PythonAlgorithmStreamVo {
    @NotBlank(message="输入算法组名称")
    private String name;
    @NotNull(message="输入算法数组")
    private Map<Integer,Long> pythonInfoIds;

    public PythonAlgorithmStreamVo(){}

    public PythonAlgorithmStreamVo(List<StPythonAlgorithmStream> StPythonAlgorithmStreamList){
        name=StPythonAlgorithmStreamList.get(0).getAlgorithmGroup();
        pythonInfoIds=new HashMap<>();
        for (StPythonAlgorithmStream pythonAlgorithmStream : StPythonAlgorithmStreamList) {
            pythonInfoIds.put(pythonAlgorithmStream.getExecutionOrder(),pythonAlgorithmStream.getPythonInfoId());
        }
    }

    public List<StPythonAlgorithmStream> toStPythonAlgorithmStream() {
        List<StPythonAlgorithmStream> StPythonAlgorithmStreamList =new ArrayList<>();
        pythonInfoIds.forEach((key,value)->{
            StPythonAlgorithmStream StPythonAlgorithmStream=new StPythonAlgorithmStream();
            StPythonAlgorithmStream.setAlgorithmGroup(name);
            StPythonAlgorithmStream.setPythonInfoId(value);
            StPythonAlgorithmStream.setExecutionOrder(key);
            StPythonAlgorithmStreamList.add(StPythonAlgorithmStream);
        });
        return StPythonAlgorithmStreamList;
    }
}
