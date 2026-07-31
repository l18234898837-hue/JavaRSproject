package com.htht.algorithm.data.controller;


import com.htht.algorithm.data.domain.BusDataTypeInfo;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.algorithm.data.domain.BusAlgorithmInfo;
import com.htht.algorithm.data.service.IBusAlgorithmInfoService;
import com.htht.common.enums.BusinessType;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 算法管理
 *
 * @author wjx
 */
@Validated
@RestController
@RequestMapping("/algorithm/algorithm")
@RequiredArgsConstructor
public class BusAlgorithmInfoController extends BaseController {

    private final IBusAlgorithmInfoService algorithmInfoService;

    /**
     * 新增算法
     *
     * @param algorithmInfo
     * @return
     */
    @PostMapping
    public R<Void> addAlgorithm(@RequestBody BusAlgorithmInfo algorithmInfo) {
        algorithmInfoService.addAlgorithm(algorithmInfo);
        return R.ok();
    }

    /**
     * 删除算法
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R<Void> removeAlgorithm(@PathVariable(value = "id") Integer id) {
        algorithmInfoService.removeAlgorithm(id);
        return R.ok();
    }

    /**
     * 更新算法
     *
     * @param algorithmInfo
     * @return
     */
    @PutMapping
    public R<Void> updateAlgorithm(@Validated @RequestBody BusAlgorithmInfo algorithmInfo) {
        algorithmInfoService.updateAlgorithm(algorithmInfo);
        return R.ok();
    }

    /**
     * 分页查询算法列表
     *
     * @param algorithmInfo
     * @param query
     * @return
     */
    @GetMapping(value = "/page")
    public TableDataInfo<BusAlgorithmInfo> selectByPage(BusAlgorithmInfo algorithmInfo, PageQuery query) {
        return algorithmInfoService.selectByPage(algorithmInfo, query);
    }

    /**
     * 查询所有的算法列表
     *
     * @return
     */
    @GetMapping(value = "/list")
    public R<List<BusAlgorithmInfo>> selectList() {
        return R.ok(algorithmInfoService.selectList());
    }

    /**
     * 根据算法ID查询算法详情
     *
     * @param id
     * @return
     */
    @GetMapping(value = "/{id}")
    public R<BusAlgorithmInfo> selectById(@PathVariable Integer id) {
        return R.ok(algorithmInfoService.getById(id));
    }

    /**
     * 根据数据id查询所有支持的算法
     *
     * @return
     */
    @GetMapping(value = "/list/data")
    public R<List<BusAlgorithmInfo>> getAlgorithmListByDataId(Long dataId) {
        return R.ok(algorithmInfoService.getAlgorithmListByDataId(dataId));
    }

    /**
     * 导出资源类型
     */
    @Log(title = "资源管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BusAlgorithmInfo algorithmInfo, HttpServletResponse response) {
        algorithmInfoService.export(algorithmInfo, response);
    }
}
