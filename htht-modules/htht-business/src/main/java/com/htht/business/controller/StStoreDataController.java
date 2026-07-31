package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.business.domain.StStoreData;
import com.htht.business.service.StStoreDataService;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 存储遥感数据入库记录 前端控制器
 *
 * @author MSFY
 * @since 2025-07-24
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/stStoreData")
public class StStoreDataController {
    private final StStoreDataService stStoreDataService;

    @SaCheckPermission("data:upload:L1AndL2")
    @PostMapping()
    public R<Void> add(@Validated @RequestBody StStoreData stStoreData) {
        stStoreData.setStatus((short) 1);
        return stStoreDataService.add(stStoreData)?R.ok():R.fail();
    }

//    @PutMapping()
//    public R<Void> update( @RequestBody StStoreData stStoreData) {
//        return stStoreDataService.updateById(stStoreData)?R.ok():R.fail();
//    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return stStoreDataService.removeById(id)?R.ok():R.fail();
    }

    @GetMapping()
    public R<List<StStoreData>> list(){
        return R.ok(stStoreDataService.list());
    }

    @GetMapping("/page")
    public R<TableDataInfo<StStoreData>> page(StStoreData stStoreData, PageQuery pageQuery) {
        return R.ok(stStoreDataService.page(stStoreData,pageQuery));
    }

    /**
     * 入库id
     * @param id
     * @return
     */
    @PutMapping("/store/{id}")
    public R<Void> store(@PathVariable Long id) {
        stStoreDataService.store(id);
        return R.ok();

    }



}
