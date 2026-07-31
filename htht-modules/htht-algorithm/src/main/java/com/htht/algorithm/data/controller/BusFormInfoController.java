package com.htht.algorithm.data.controller;


import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusFormInfo;
import com.htht.algorithm.data.service.IBusFormInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单管理
 *
 * @author wjx
 */
@Validated
@RestController
@RequestMapping("/algorithm/form")
@RequiredArgsConstructor
public class BusFormInfoController extends BaseController {

    private final IBusFormInfoService formInfoService;

    /**
     * 新增表单
     *
     * @param formInfo
     * @return
     */
    @PostMapping
    public R<Void> addForm(@RequestBody BusFormInfo formInfo) {
        return toAjax(formInfoService.addForm(formInfo));
    }

    /**
     * 删除表单
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R<Void> removeForm(@PathVariable(value = "id") Long id) {
        return toAjax(formInfoService.removeForm(id));
    }

    /**
     * 更新表单
     *
     * @param formInfo
     * @return
     */
    @PutMapping
    public R<Void> updateForm(@Validated @RequestBody BusFormInfo formInfo) {
        return toAjax(formInfoService.updateForm(formInfo));
    }

    /**
     * 分页查询表单列表
     *
     * @param formInfo
     * @param query
     * @return
     */
    @GetMapping(value = "/page")
    public TableDataInfo<BusFormInfo> selectByPage(BusFormInfo formInfo, PageQuery query) {
        return formInfoService.selectByPage(formInfo, query);
    }

    /**
     * 查询所有的表单列表
     *
     * @return
     */
    @GetMapping(value = "/list")
    public R<List<BusFormInfo>> selectList() {
        return R.ok(formInfoService.selectList());
    }

    /**
     * 根据表单ID查询表单详情
     *
     * @param id
     * @return
     */
    @GetMapping(value = "/{id}")
    public R<BusFormInfo> selectById(@PathVariable Long id) {
        return R.ok(formInfoService.getById(id));
    }
}
