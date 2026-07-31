package com.htht.business.controller;

import com.htht.business.domain.vo.DictVO;
import com.htht.business.domain.vo.regions.satellite.SatelliteTreeNode;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoQueryVO;
import com.htht.business.service.SysSatelliteInfoService;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.BusinessType;
import lombok.RequiredArgsConstructor;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 卫星信息表 前端控制器
 *
 * @author MSFY
 * @since 2025-09-26
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/satellite/info")
public class SysSatelliteInfoController extends BaseController {

    private final SysSatelliteInfoService sysSatelliteInfoService;
    /**
     * 分页查询
     * @param queryVO 卫星信息表查询条件
     * @return
     */
    @GetMapping("/page")
    public TableDataInfo<SysSatelliteInfoListVO> pageSysSatelliteInfo(@ParameterObject SysSatelliteInfoQueryVO queryVO) {
        return sysSatelliteInfoService.pageSysSatelliteInfo(queryVO);
    }

    /**
     * 根据ID获取数据
     * @param id 卫星信息表ID
     * @return
     */
    @GetMapping("/{id}")
    public R<SysSatelliteInfoEditVO> getSysSatelliteInfoById(@PathVariable Long id) {
        return R.ok(sysSatelliteInfoService.getSysSatelliteInfo(id));
    }

    /**
     * 新增卫星信息表
     * @param editVO 卫星信息表编辑框信息
     * @return
     */
    @PostMapping
    @Log(title = "卫星信息表", businessType = BusinessType.INSERT)
    public R<Void> addSysSatelliteInfo(@RequestBody @Validated SysSatelliteInfoEditVO editVO) {
        return toAjax(sysSatelliteInfoService.addSysSatelliteInfo(editVO));
    }

    /**
     * 修改卫星信息表
     * @param editVO 卫星信息表编辑框信息
     * @return
     */
    @PutMapping
    @Log(title = "卫星信息表", businessType = BusinessType.UPDATE)
    public R<Void> updateSysSatelliteInfo(@RequestBody SysSatelliteInfoEditVO editVO) {
        return toAjax(sysSatelliteInfoService.updateSysSatelliteInfo(editVO));
    }

    /**
     * 卫星信息表-逻辑删除
     * @param ids 卫星信息表ID集合
     * @return
     */
    @DeleteMapping
    @Log(title = "卫星信息表-逻辑删除", businessType = BusinessType.DELETE)
    public R<Void> updateDeleteStatusByIds(@RequestBody List<Long> ids) {
        return toAjax(sysSatelliteInfoService.updateDeleteStatusByIds(ids));
    }

    /**
     * 卫星信息表-物理删除
     * @param ids 卫星信息表ID
     * @return
     */
    @DeleteMapping("/delete")
    @Log(title = "卫星信息表-物理删除", businessType = BusinessType.DELETE)
    public R<Void> deleteSysSatelliteInfo(@RequestBody List<Long> ids) {
        return toAjax(sysSatelliteInfoService.removeBatchByIds(ids));
    }

    /**
     * 获取卫星树结构
     * @return
     */
    @GetMapping("/tree")
    public R<List<SatelliteTreeNode>> treeSysSatelliteInfo() {
        return R.ok(sysSatelliteInfoService.treeSysSatelliteInfo());
    }

    /**
     * 获取卫星字典
     * @return
     */
    @GetMapping("/dict")
    public TableDataInfo<DictVO> dictSysSatelliteInfo() {
        return sysSatelliteInfoService.dictSysSatelliteInfo();
    }

}
