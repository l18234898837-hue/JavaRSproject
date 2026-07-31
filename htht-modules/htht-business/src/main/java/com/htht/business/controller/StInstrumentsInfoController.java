package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StInstrumentsInfo;
import com.htht.business.service.StInstrumentsInfoService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 *  设备信息表 前端控制器
 *
 * @author MSFY
 * @since 2025-07-03
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/stInstrumentsInfo")
public class StInstrumentsInfoController {
    private final StInstrumentsInfoService stInstrumentsInfoService;
    @PostMapping
    public R<Void> add(@Validated @RequestBody StInstrumentsInfo stInstrumentsInfo) {
        return stInstrumentsInfoService.save(stInstrumentsInfo)?R.ok():R.fail();
    }
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return stInstrumentsInfoService.removeById(id)?R.ok():R.fail();
    }
    @PutMapping
    public R<Void> update(@Validated @RequestBody StInstrumentsInfo stInstrumentsInfo) {
        return stInstrumentsInfoService.updateById(stInstrumentsInfo)?R.ok():R.fail();
    }
    @GetMapping
    public R<List<StInstrumentsInfo>> list() {
        return R.ok(stInstrumentsInfoService.list());
    }
    @GetMapping("/{id}")
    public R<StInstrumentsInfo> getById( @PathVariable Long id) {
        return R.ok(stInstrumentsInfoService.getById(id));
    }
    @GetMapping("/name")
    public R<List<StInstrumentsInfo>> getByName(@RequestParam String name) {
        LambdaQueryWrapper<StInstrumentsInfo> wrapper =new LambdaQueryWrapper<>();
        wrapper.like(StInstrumentsInfo::getInstrumentName,name);
        List<StInstrumentsInfo> stInstrumentsList = stInstrumentsInfoService.list(wrapper);
        return R.ok(stInstrumentsList);
    }
    @GetMapping("/company")
    public R<List<StInstrumentsInfo>> getByCompany(@RequestParam String company) {
        LambdaQueryWrapper<StInstrumentsInfo> wrapper =new LambdaQueryWrapper<>();
        wrapper.like(StInstrumentsInfo::getDistributor,company).or().like(StInstrumentsInfo::getManufacturer,company);
        return R.ok(stInstrumentsInfoService.list(wrapper));
    }

}
