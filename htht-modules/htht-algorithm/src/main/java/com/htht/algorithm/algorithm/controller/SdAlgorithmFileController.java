package com.htht.algorithm.algorithm.controller;

import com.htht.algorithm.algorithm.domain.dto.*;
import com.htht.algorithm.algorithm.service.ISdAlgorithmFileService;
import com.htht.algorithm.algorithm.domain.vo.SdAlgorithmFileVo;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* 算法文件表 接口
*
* @author MSFY
* @since 2026-07-21
*/
@RestController
@RequestMapping("/sd-algorithm-file")
@RequiredArgsConstructor
public class SdAlgorithmFileController {
    private final ISdAlgorithmFileService iSdAlgorithmFileService;

    /**
    * 添加算法文件表
    * @param addSdAlgorithmFile 添加对象
    * @return 返回状态
    */
    @PostMapping
    public R<Void> add(@Validated @RequestBody SdAlgorithmFileAdd addSdAlgorithmFile) {
        return iSdAlgorithmFileService.add(addSdAlgorithmFile) ? R.ok() : R.fail();
    }

    /**
    * 删除算法文件表
    * @param id 删除id
    * @return 状态
    */
    @DeleteMapping("/{id}")
    public R<Void> deleteById(@PathVariable("id") Long id) {
        return iSdAlgorithmFileService.removeById(id) ? R.ok() : R.fail();
    }

    /**
    * 更新算法文件表
    * @param upSdAlgorithmFile 修改对象
    * @return   状态
    */
    @PutMapping
    public R<Void> update(@Validated @RequestBody SdAlgorithmFileUp upSdAlgorithmFile) {
        return iSdAlgorithmFileService.up(upSdAlgorithmFile) ? R.ok() : R.fail();
    }

    /**
    * 根据参数获取所有算法文件表
    * @return 返回前端显示值
    */
    @GetMapping
    public R<List<SdAlgorithmFileVo>> list(SdAlgorithmFileQuery query) {
        return R.ok(iSdAlgorithmFileService.getList(query));
    }

    /**
    * 根据id获取信息
    * @param id 数据id
    * @return 返回前端显示值
    */
    @GetMapping("/{id}")
    public R<SdAlgorithmFileVo> getById(@PathVariable Long id) {
        return R.ok(iSdAlgorithmFileService.getVoById(id));
    }

    /**
    * 分页查询列表
    */
    @GetMapping("/page")
    public R<TableDataInfo<SdAlgorithmFileVo>> page(SdAlgorithmFileQuery query, PageQuery pageQuery) {
        return R.ok(iSdAlgorithmFileService.pageByQuery(query, pageQuery));
    }

    /**
     * 计算接口
     */
    @PostMapping("/algorithm")
    public R<Void> algorithm(@RequestBody SdAlgorithmStartByVo start){
        iSdAlgorithmFileService.run(start);
        return R.ok();

    }

    /**
     * 计算接口
     */
    @PostMapping("/algorithmById")
    public R<Void> algorithm(@RequestBody SdAlgorithmStartById start){
        iSdAlgorithmFileService.run(start);
        return R.ok();

    }

    /**
     * 获取算法对应输入文件夹接口
     */
    @GetMapping("/input/{id}")
    public R<FileTreeVO> getInput(@PathVariable Long id)  {
        return R.ok(iSdAlgorithmFileService.getInput(id));
    }

    /**
     * 获取算法对应输出文件夹接口
     */
    @GetMapping("/output/{id}")
    public R<FileTreeVO> getOutput(@PathVariable Long id){
        return R.ok(iSdAlgorithmFileService.getOutput(id));
    }
}
