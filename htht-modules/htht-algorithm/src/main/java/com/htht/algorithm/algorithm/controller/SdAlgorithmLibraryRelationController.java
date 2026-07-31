package com.htht.algorithm.algorithm.controller;

import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationDel;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmTreeVo;
import com.htht.algorithm.algorithm.service.ISdAlgorithmLibraryRelationService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmLibraryRelationVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* 算法库与算法文件关联表 接口
*
* @author MSFY
* @since 2026-07-21
*/
@RestController
@RequestMapping("/sd-algorithm-library-relation")
@RequiredArgsConstructor
public class SdAlgorithmLibraryRelationController {
    private final ISdAlgorithmLibraryRelationService iSdAlgorithmLibraryRelationService;

    /**
    * 添加算法库与算法文件关联表
    * @param addSdAlgorithmLibraryRelation 添加对象
    * @return 返回状态
    */
    @PostMapping
    public R<Void> add(@Validated @RequestBody SdAlgorithmLibraryRelationAdd addSdAlgorithmLibraryRelation) {
        return iSdAlgorithmLibraryRelationService.add(addSdAlgorithmLibraryRelation) ? R.ok() : R.fail();
    }

    /**
    * 删除算法库与算法文件关联表
    * @param id 删除id
    * @return 状态
    */
    @DeleteMapping("/{id}")
    public R<Void> deleteById(@PathVariable("id") Long id) {
        return iSdAlgorithmLibraryRelationService.removeById(id) ? R.ok() : R.fail();
    }


    /**
     * 删除算法运行记录表根据算法id+库id
     */
    @DeleteMapping()
    public R<Void> deleteByParam(@RequestBody @Validated SdAlgorithmLibraryRelationDel del){
        return iSdAlgorithmLibraryRelationService.removeByDel(del)?R.ok():R.fail();
    }

    /**
    * 更新算法库与算法文件关联表
    * @param upSdAlgorithmLibraryRelation 修改对象
    * @return   状态
    */
    @PutMapping
    public R<Void> update(@Validated @RequestBody SdAlgorithmLibraryRelationUp upSdAlgorithmLibraryRelation) {
        return iSdAlgorithmLibraryRelationService.up(upSdAlgorithmLibraryRelation) ? R.ok() : R.fail();
    }

    /**
    * 根据参数获取所有算法库与算法文件关联表
    * @return 返回前端显示值
    */
    @GetMapping
    public R<List<SdAlgorithmLibraryRelationVo>> list(SdAlgorithmLibraryRelationQuery query) {
        return R.ok(iSdAlgorithmLibraryRelationService.getList(query));
    }

    /**
    * 根据id获取信息
    * @param id 数据id
    * @return 返回前端显示值
    */
    @GetMapping("/{id}")
    public R<SdAlgorithmLibraryRelationVo> getById(@PathVariable("id") Long id) {
        return R.ok(iSdAlgorithmLibraryRelationService.getVoById(id));
    }

    /**
    * 分页查询列表
    */
    @GetMapping("/page")
    public R<TableDataInfo<SdAlgorithmLibraryRelationVo>> page(SdAlgorithmLibraryRelationQuery query, PageQuery pageQuery) {
        return R.ok(iSdAlgorithmLibraryRelationService.pageByQuery(query, pageQuery));
    }

    /**
     * 获取算法树
     */
    @GetMapping("/tree")
    public R<List<SdAlgorithmTreeVo>> tree(){
        return R.ok(iSdAlgorithmLibraryRelationService.tree());
    }
}
