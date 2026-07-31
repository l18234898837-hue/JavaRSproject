package com.htht.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.business.domain.StVoyages;
import com.htht.business.service.StVoyagesService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 *  航线表控制器
 *
 */

@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/StVoyages")
public class StVoyagesController {
    private final StVoyagesService stVoyagesService;

    @PostMapping()
    public R<Void> add(@Validated @RequestBody StVoyages stVoyages) {
        return stVoyagesService.save(stVoyages) ? R.ok() : R.fail();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return stVoyagesService.removeById(id) ? R.ok() : R.fail();
    }

    @PutMapping()
    public R<Void> update(@Validated @RequestBody StVoyages stVoyages) {
        return stVoyagesService.updateById(stVoyages) ? R.ok() : R.fail();
    }

    @GetMapping()
    public R<List<StVoyages>> list() {
        return R.ok(stVoyagesService.list());
    }

    @GetMapping("/{id}")
    public R<StVoyages> getById(@PathVariable Long  id) {
        return R.ok(stVoyagesService.getById(id));
    }

    @GetMapping("/codeOrName")
    public R<List<StVoyages>> getByCodeOrName(@RequestParam String name) {
        LambdaQueryWrapper<StVoyages> wrapper =new LambdaQueryWrapper<>();
        wrapper.like(StVoyages::getVoyageCode,name).or().like(StVoyages::getVoyageName,name);
        return R.ok(stVoyagesService.list(wrapper));
    }

    @GetMapping("/timeBetween")
    public R<List<StVoyages>> getByTimeBetween(@RequestParam String startTime, @RequestParam String endTime) {
        LocalDate start = LocalDate.parse(startTime);
        LocalDate end = LocalDate.parse(endTime);
        LambdaQueryWrapper<StVoyages> wrapper =new LambdaQueryWrapper<>();
        wrapper.between(StVoyages::getVoyageDate,start,end);
        return R.ok(stVoyagesService.list(wrapper));
    }



}
