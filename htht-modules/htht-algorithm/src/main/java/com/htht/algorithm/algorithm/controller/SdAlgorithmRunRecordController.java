package com.htht.algorithm.algorithm.controller;

import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmLibraryRelationDel;
import com.htht.algorithm.algorithm.service.ISdAlgorithmRunRecordService;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordAdd;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordQuery;
import com.htht.algorithm.algorithm.domain.dto.SdAlgorithmRunRecordUp;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmRunRecordVo;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.hsqldb.Table;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* 算法运行记录表 接口
*
* @author MSFY
* @since 2026-07-21
*/
@RestController
@RequestMapping("/sd-algorithm-run-record")
@RequiredArgsConstructor
public class SdAlgorithmRunRecordController {
    private final ISdAlgorithmRunRecordService iSdAlgorithmRunRecordService;

    /**
    * 添加算法运行记录表
    * @param addSdAlgorithmRunRecord 添加对象
    * @return 返回状态
    */
    @PostMapping
    public R<Void> add(@Validated @RequestBody SdAlgorithmRunRecordAdd addSdAlgorithmRunRecord) {
        return iSdAlgorithmRunRecordService.add(addSdAlgorithmRunRecord) ? R.ok() : R.fail();
    }

    /**
    * 删除算法运行记录表
    * @param id 删除id
    * @return 状态
    */
    @DeleteMapping("/{id}")
    public R<Void> deleteById(@PathVariable("id") Long id) {
        return iSdAlgorithmRunRecordService.removeById(id) ? R.ok() : R.fail();
    }

    /**
    * 更新算法运行记录表
    * @param upSdAlgorithmRunRecord 修改对象
    * @return   状态
    */
    @PutMapping
    public R<Void> update(@Validated @RequestBody SdAlgorithmRunRecordUp upSdAlgorithmRunRecord) {
        return iSdAlgorithmRunRecordService.up(upSdAlgorithmRunRecord) ? R.ok() : R.fail();
    }

    /**
    * 根据参数获取所有算法运行记录表
    * @return 返回前端显示值
    */
    @GetMapping
    public R<List<SdAlgorithmRunRecordVo>> list(SdAlgorithmRunRecordQuery query) {
        return R.ok(iSdAlgorithmRunRecordService.getList(query));
    }

    /**
    * 根据id获取信息
    * @param id 数据id
    * @return 返回前端显示值
    */
    @GetMapping("/{id}")
    public R<SdAlgorithmRunRecordVo> getById(@PathVariable("id") Long id) {
        return R.ok(iSdAlgorithmRunRecordService.getVoById(id));
    }

    /**
    * 分页查询列表
    */
    @GetMapping("/page")
    public R<TableDataInfo<SdAlgorithmRunRecordVo>> page(SdAlgorithmRunRecordQuery query, PageQuery pageQuery) {
        return R.ok(iSdAlgorithmRunRecordService.pageByQuery(query, pageQuery));
    }

    /**
     * 根据算法id获取计算结果信息，分页
     */
    @GetMapping("/page/{id}")
    public R<TableDataInfo<SdAlgorithmRunRecordVo>> page(@PathVariable("id") Long id,PageQuery pageQuery){
        return R.ok(iSdAlgorithmRunRecordService.pageById(id,pageQuery));
    }

}
