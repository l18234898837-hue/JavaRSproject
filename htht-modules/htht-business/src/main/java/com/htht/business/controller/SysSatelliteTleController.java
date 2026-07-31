package com.htht.business.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteTleQueryVO;
import com.htht.business.service.SysSatelliteTleService;
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
 * 轨道信息表 前端控制器
 *
 * @author MSFY
 * @since 2025-09-28
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/satellite/tle")
public class SysSatelliteTleController extends BaseController {

    private final SysSatelliteTleService sysSatelliteTleService;
    /**
     * 分页查询
     * @param queryVO 轨道信息表查询条件
     * @return
     */
    @GetMapping("/page")
    public TableDataInfo<SysSatelliteTleListVO> pageSysSatelliteTle(@ParameterObject SysSatelliteTleQueryVO queryVO) {
        return sysSatelliteTleService.pageSysSatelliteTle(queryVO);
    }

    /**
     * 根据卫星ID查轨道信息
     * @param satelliteInfoIds 轨道信息表查询条件
     * @return
     */
    @PostMapping("/list")
    @SaIgnore
    public TableDataInfo<SysSatelliteTleListVO> listSysSatelliteTle(@RequestBody List<Long> satelliteInfoIds) {
        return sysSatelliteTleService.listSysSatelliteTle(satelliteInfoIds);
    }

    /**
     * 根据ID获取数据
     * @param id 轨道信息表ID
     * @return
     */
    @GetMapping("/{id}")
    public R<SysSatelliteTleEditVO> getSysSatelliteTleById(@PathVariable Long id) {
        return R.ok(sysSatelliteTleService.getSysSatelliteTle(id));
    }

    /**
     * 新增轨道信息表
     * @param editVO 轨道信息表编辑框信息
     * @return
     */
    @PostMapping
    @Log(title = "轨道信息表", businessType = BusinessType.INSERT)
    public R<Void> addSysSatelliteTle(@RequestBody @Validated SysSatelliteTleEditVO editVO) {
        return toAjax(sysSatelliteTleService.addSysSatelliteTle(editVO));
    }

    /**
     * 修改轨道信息表
     * @param editVO 轨道信息表编辑框信息
     * @return
     */
    @PutMapping
    @Log(title = "轨道信息表", businessType = BusinessType.UPDATE)
    public R<Void> updateSysSatelliteTle(@RequestBody SysSatelliteTleEditVO editVO) {
        return toAjax(sysSatelliteTleService.updateSysSatelliteTle(editVO));
    }

    /**
     * 轨道信息表-逻辑删除
     * @param ids 轨道信息表ID集合
     * @return
     */
    @DeleteMapping
    @Log(title = "轨道信息表-逻辑删除", businessType = BusinessType.DELETE)
    public R<Void> updateDeleteStatusByIds(@RequestBody List<Long> ids) {
        return toAjax(sysSatelliteTleService.updateDeleteStatusByIds(ids));
    }

    /**
     * 轨道信息表-物理删除
     * @param ids 轨道信息表ID
     * @return
     */
    @DeleteMapping("/delete")
    @Log(title = "轨道信息表-物理删除", businessType = BusinessType.DELETE)
    public R<Void> deleteSysSatelliteTle(@RequestBody List<Long> ids) {
        return toAjax(sysSatelliteTleService.removeBatchByIds(ids));
    }

}
