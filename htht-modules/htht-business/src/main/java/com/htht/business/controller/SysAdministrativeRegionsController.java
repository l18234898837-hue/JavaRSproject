package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.htht.business.domain.SysAdministrativeRegions;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsEditVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsListVO;
import com.htht.business.domain.vo.regions.SysAdministrativeRegionsQueryVO;
import com.htht.business.service.SysAdministrativeRegionsService;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.BusinessType;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * 行政区域信息表 前端控制器
 *
 * @author MSFY
 * @since 2025-09-25
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/regions")
public class SysAdministrativeRegionsController extends BaseController {

    private final SysAdministrativeRegionsService sysAdministrativeRegionsService;
    /**
     * 分页查询
     * @param queryVO 行政区域信息表查询条件
     * @return
     */
    @GetMapping("/page")
    public TableDataInfo<SysAdministrativeRegionsListVO> pageSysAdministrativeRegions(@ParameterObject SysAdministrativeRegionsQueryVO queryVO) {
        return sysAdministrativeRegionsService.pageSysAdministrativeRegions(queryVO);
    }

    /**
     * 根据ID获取数据
     * @param id 行政区域信息表ID
     * @return
     */
    @GetMapping("/{id}")
    public R<SysAdministrativeRegionsEditVO> getSysAdministrativeRegionsById(@PathVariable  Long id) {
        return R.ok(sysAdministrativeRegionsService.getSysAdministrativeRegions(id));
    }

    /**
     * 新增行政区域信息表
     * @param editVO 行政区域信息表编辑框信息
     * @return
     */
    @PostMapping
    @Log(title = "行政区域信息表", businessType = BusinessType.INSERT)
    public R<Void> addSysAdministrativeRegions(@RequestBody @Validated SysAdministrativeRegionsEditVO editVO) {
        return toAjax(sysAdministrativeRegionsService.addSysAdministrativeRegions(editVO));
    }

    /**
     * 修改行政区域信息表
     * @param editVO 行政区域信息表编辑框信息
     * @return
     */
    @PutMapping
    @Log(title = "行政区域信息表", businessType = BusinessType.UPDATE)
    public R<Void> updateSysAdministrativeRegions(@RequestBody SysAdministrativeRegionsEditVO editVO) {
        return toAjax(sysAdministrativeRegionsService.updateSysAdministrativeRegions(editVO));
    }

    /**
     * 行政区域信息表-逻辑删除
     * @param ids 行政区域信息表ID集合
     * @return
     */
    @DeleteMapping
    @Log(title = "行政区域信息表-逻辑删除", businessType = BusinessType.DELETE)
    public R<Void> updateDeleteStatusByIds(@RequestBody List<Long> ids) {
        return toAjax(sysAdministrativeRegionsService.updateDeleteStatusByIds(ids));
    }

    /**
     * 行政区域信息表-物理删除
     * @param ids 行政区域信息表ID
     * @return
     */
    @DeleteMapping("/delete")
    @Log(title = "行政区域信息表-物理删除", businessType = BusinessType.DELETE)
    public R<Void> deleteSysAdministrativeRegions(@RequestBody List<Long> ids) {
        return toAjax(sysAdministrativeRegionsService.removeBatchByIds(ids));
    }

}
