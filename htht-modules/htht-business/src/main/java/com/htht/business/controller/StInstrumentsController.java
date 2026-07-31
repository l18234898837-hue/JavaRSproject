package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dtflys.forest.annotation.Delete;
import com.dtflys.forest.annotation.Post;
import com.htht.business.domain.StInstruments;
import com.htht.business.domain.StInstrumentsInfo;
import com.htht.business.service.StInstrumentsService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Update;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;

/**
 *  仪器具体信息表 前端控制器
 *
 * @author MSFY
 * @since 2025-07-03
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/stInstruments")
public class StInstrumentsController {
    private final StInstrumentsService stInstrumentsService;
    @PostMapping
    public R<Void> add(@Validated @RequestBody StInstruments stInstruments){
        return stInstrumentsService.save(stInstruments)?R.ok():R.fail();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id){
        return stInstrumentsService.removeById(id)?R.ok():R.fail();
    }

    @PutMapping
    public R<Void> update(@Validated @RequestBody StInstruments stInstruments){
        return stInstrumentsService.updateById(stInstruments)?R.ok():R.fail();
    }
    @GetMapping
    public R<List<StInstruments>> list(){
        return R.ok(stInstrumentsService.list());
    }
    @GetMapping("/{id}")
    public R<StInstruments> getById(@PathVariable Long id){
        return R.ok(stInstrumentsService.getById(id));
    }
    @GetMapping("/code")
    public R<List<StInstruments>> getByCodeOrName(@RequestParam String code){
        LambdaQueryWrapper<StInstruments> wrapper=new LambdaQueryWrapper<>();
        wrapper.like(StInstruments::getInstrumentCode,code);
        return R.ok(stInstrumentsService.list(wrapper));
    }
    @GetMapping("/InfoId")
    public R<List<StInstruments>> getByInfoId(@RequestParam Long infoId){
        LambdaQueryWrapper<StInstruments> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(StInstruments::getInstrumentInfoId,infoId);
        return R.ok(stInstrumentsService.list(wrapper));
    }
    @GetMapping("/timeBetween")
    public R<List<StInstruments>> getByTimeBetween(@RequestParam String startTime, @RequestParam String endTime){
        LambdaQueryWrapper<StInstruments> wrapper=new LambdaQueryWrapper<>();
        LocalDate start = java.time.LocalDate.parse(startTime);
        LocalDate end = java.time.LocalDate.parse(endTime);
        wrapper.between(StInstruments::getCommissionDate,start,end);
        return R.ok(stInstrumentsService.list(wrapper));
    }
    @GetMapping("/useVoyage")
    public R<List<StInstruments>> getByUseVoyage(@RequestParam String voyage){
        LambdaQueryWrapper<StInstruments> wrapper=new LambdaQueryWrapper<>();
        wrapper.like(StInstruments::getUseVoyage,voyage);
        return R.ok(stInstrumentsService.list(wrapper));
    }
    @GetMapping("/manager")
    public R<List<StInstruments>> getByManager(@RequestParam String manager){
        LambdaQueryWrapper<StInstruments> wrapper=new LambdaQueryWrapper<>();
        wrapper.like(StInstruments::getManager,manager);
        return R.ok(stInstrumentsService.list(wrapper));
    }

}
