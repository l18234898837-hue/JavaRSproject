package com.htht.algorithm.algorithm.controller;

import com.htht.algorithm.algorithm.service.ISdAlgorithmLibraryService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* 算法库表 接口
*
* @author MSFY
* @since 2026-07-21
*/
@RestController
@RequestMapping("/sd-algorithm-library")
@RequiredArgsConstructor
public class SdAlgorithmLibraryController {
    private final ISdAlgorithmLibraryService iSdAlgorithmLibraryService;

    /**
    * 添加算法库表
    * @param addSdAlgorithmLibrary 添加对象
    * @return 返回状态
    */
    @PostMapping
    public R<Void> add(@Validated @RequestBody SdAlgorithmLibraryAdd addSdAlgorithmLibrary) {
        return iSdAlgorithmLibraryService.add(addSdAlgorithmLibrary) ? R.ok() : R.fail();
    }

    /**
    * 删除算法库表
    * @param id 删除id
    * @return 状态
    */
    @DeleteMapping("/{id}")
    public R<Void> deleteById(@PathVariable("id") Long id) {
        return iSdAlgorithmLibraryService.removeById(id) ? R.ok() : R.fail();
    }

    /**
    * 更新算法库表
    * @param upSdAlgorithmLibrary 修改对象
    * @return   状态
    */
    @PutMapping
    public R<Void> update(@Validated @RequestBody SdAlgorithmLibraryUp upSdAlgorithmLibrary) {
        return iSdAlgorithmLibraryService.up(upSdAlgorithmLibrary) ? R.ok() : R.fail();
    }

    /**
    * 根据参数获取所有算法库表
    * @return 返回前端显示值
    */
    @GetMapping
    public R<List<SdAlgorithmLibraryVo>> list(SdAlgorithmLibraryQuery query) {
        return R.ok(iSdAlgorithmLibraryService.getList(query));
    }

    /**
    * 根据id获取信息
    * @param id 数据id
    * @return 返回前端显示值
    */
    @GetMapping("/{id}")
    public R<SdAlgorithmLibraryVo> getById(@PathVariable("id") Long id) {
        return R.ok(iSdAlgorithmLibraryService.getVoById(id));
    }

    /**
    * 分页查询列表
    */
    @GetMapping("/page")
    public R<TableDataInfo<SdAlgorithmLibraryVo>> page(SdAlgorithmLibraryQuery query, PageQuery pageQuery) {
        return R.ok(iSdAlgorithmLibraryService.pageByQuery(query, pageQuery));
    }
}
