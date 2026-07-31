package com.htht.algorithm.data.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.htht.algorithm.data.domain.StDataType;
import com.htht.algorithm.data.domain.dto.SatelliteDto;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.algorithm.data.service.StDataTypeService;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 存储遥感数据类型定义 前端控制器
 *
 * @author MSFY
 * @since 2025-07-24
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/stDataType")
public class StDataTypeController {
    private final StDataTypeService stDataTypeService;
    @PostMapping
    public R<Void> add(@Validated @RequestBody StDataType stDataType){
        return stDataTypeService.add(stDataType)?R.ok():R.fail();
    };
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id){
        return stDataTypeService.rvById(id)?R.ok():R.fail();
    };
    @PutMapping
    public R<Void> update(@RequestBody StDataType stDataType){
        return stDataTypeService.updateById(stDataType)?R.ok():R.fail();
    };
    @GetMapping
    public R<List<StDataType>> list(){
        return R.ok(stDataTypeService.list());
    };
    @GetMapping("/{id}")
    public R<StDataType> getById( @PathVariable Long id){
        return R.ok(stDataTypeService.getById(id));
    };
    @GetMapping("/name")
    public R<List<StDataType>> getByName(@RequestParam String name){
        LambdaQueryWrapper<StDataType> wapper=new LambdaQueryWrapper<>();
        wapper.like(StDataType::getName,name);
    return R.ok(stDataTypeService.list(wapper));
    };
    @GetMapping("/tree")
    public R<List<StDataType>> getTree(){
        return R.ok(stDataTypeService.buildTree());
    };

    @GetMapping("/catalog")
    public R<SatelliteDto> getSatelliteDto(Long  catalogId){
        return R.ok(stDataTypeService.getSatelliteDto(catalogId));
    }

    /**
     * 检查数据类型绑定的目录是否正确
     * @return
     */
    @GetMapping("/check")
    public R<String> check(){
        return R.ok(stDataTypeService.check());
    }

    /**
     * 删除数据类型连带资源目录也一起删除
     */
    @DeleteMapping("/deleteDataTypeAndCatalog/{id}")
    public R<Void> deleteDataTypeAndCatalog(@PathVariable Long id){
        return stDataTypeService.deleteDataTypeAndCatalog(id)?R.ok():R.fail();
    }

    /**
     * 添加数据类型子类型
     * @param dataTypeChildVo
     * @return
     */
    @PostMapping("/addChildDataType")
    public R<Void> addChildDataType(@Validated @RequestBody DataTypeChildVo dataTypeChildVo){
        return stDataTypeService.addChild(dataTypeChildVo)?R.ok():R.fail();
    }

    /**
     * 添加数据类型父类型
     * @param aDTP
     * @return
     */
    @PostMapping("/addParentDataType")
    public R<Void> addParentDataType(@Validated @RequestBody AddDataTypeParentVo aDTP){
        return stDataTypeService.addParent(aDTP)?R.ok():R.fail();
    }

    /**
     * 修改数据类型名称，并修改关联的资源目录名称
     */
    @PutMapping("/updateNameAndCatalogName")
    public R<Void> updateNameAndCatalogName(@Validated @RequestBody DataTypeUpdateNameVo dataTypeUpdateNameVo){
        return stDataTypeService.updateNameAndCatalogName(dataTypeUpdateNameVo)?R.ok():R.fail();
    }

    /**
     * 通过1级名称+2级名称获取对应的资源目录id
     */
    @GetMapping("/getResourceCatalogId")
    public R<StDataType> getResourceCatalogId(@RequestParam String firstName,@RequestParam String secondName){
        return R.ok(stDataTypeService.getResourceCatalogId(firstName,secondName));
    }

    /**
     * 通过字符串要求，按照新的结构生成目录
     */
    @PostMapping("/TreeByString")
    public R<List<NewMenuVo>> TreeByString(@Validated @RequestBody NewMenuQueryVo queryVo){
        return R.ok(stDataTypeService.TreeByString(queryVo));
    }


}
