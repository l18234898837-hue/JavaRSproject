package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StPythonAlgorithmFileInfo;
import com.htht.business.domain.StPythonAlgorithmStream;
import com.htht.business.domain.vo.PythonAlgorithmStreamVo;
import com.htht.business.service.StPythonAlgorithmFileInfoService;
import com.htht.business.service.StPythonAlgorithmStreamService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Python算法执行流程表，记录算法组、执行顺序及关联的Python文件 前端控制器
 *
 * @author MSFY
 * @since 2025-08-15
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/stPythonAlgorithmStream")
public class StPythonAlgorithmStreamController {
    private final StPythonAlgorithmStreamService stPythonAlgorithmStreamService;
    private final StPythonAlgorithmFileInfoService stPythonAlgorithmFileInfoService;

    @PostMapping()
    public R<Void> add(@Validated @RequestBody PythonAlgorithmStreamVo pythonAlgorithmStreamVo){
        if(!stPythonAlgorithmStreamService.groupIsOnly(pythonAlgorithmStreamVo.getName())){
            return R.fail("该算法组已存在");
        }
        List<Long> values = pythonAlgorithmStreamVo.getPythonInfoIds().values().stream().toList();
        for (Long value : values) {
            StPythonAlgorithmFileInfo byId = stPythonAlgorithmFileInfoService.getById(value);
            if(byId==null){
                return R.fail("确认关联的算法是否存在");
            }
        }
        List<StPythonAlgorithmStream> stPythonAlgorithmStream = pythonAlgorithmStreamVo.toStPythonAlgorithmStream();
        for (StPythonAlgorithmStream pythonAlgorithmStream : stPythonAlgorithmStream) {
            stPythonAlgorithmStreamService.save(pythonAlgorithmStream);
        }
        return R.ok("添加成功");

    }

    @GetMapping()
    public R<List<PythonAlgorithmStreamVo>> list(){
        return R.ok( stPythonAlgorithmStreamService.getVoList());
    }


    @GetMapping("/group")
    public R<String[]> getAlgorithmGroup(){
        return R.ok( stPythonAlgorithmStreamService.getAlgorithmGroup());
    }

    @GetMapping("/name")
    public R<List<StPythonAlgorithmStream>> name(String name){
        return R.ok(stPythonAlgorithmStreamService.list(
            new LambdaQueryWrapper<StPythonAlgorithmStream>()
                .eq(StPythonAlgorithmStream::getAlgorithmGroup, name)
        ));

    }

    @DeleteMapping("/name")
    public R<String> delete(String name){
        return R.ok(
            stPythonAlgorithmStreamService.remove(
                new LambdaQueryWrapper<StPythonAlgorithmStream>()
                    .eq(StPythonAlgorithmStream::getAlgorithmGroup, name)
            )?"删除成功":"删除失败"
        );
    }


}
