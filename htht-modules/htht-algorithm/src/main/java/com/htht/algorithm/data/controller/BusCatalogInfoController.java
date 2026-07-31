package com.htht.algorithm.data.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.htht.common.annotation.Log;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.PageQuery;
import com.htht.common.core.domain.R;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.BusinessType;
import com.htht.algorithm.data.domain.BusCatalogInfo;
import com.htht.algorithm.data.service.IBusCatalogInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资源目录管理
 *
 * @author wjx
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/algorithm/catalog")
public class BusCatalogInfoController extends BaseController {

    private final IBusCatalogInfoService catalogInfoService;

    /**
     * 新增资源目录
     */
    @SaCheckPermission("ocean:catalog:add")
    @Log(title = "资源目录管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BusCatalogInfo catalogInfo) {
        return toAjax(catalogInfoService.addCatalog(catalogInfo));
    }

    /**
     * 删除资源目录
     *
     * @param id 资源目录ID
     */
    @SaCheckPermission("ocean:catalog:remove")
    @Log(title = "资源目录管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return toAjax(catalogInfoService.deleteCatalog(id));
    }

    /**
     * 修改资源目录
     */
    @SaCheckPermission("ocean:catalog:edit")
    @Log(title = "资源目录管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BusCatalogInfo catalogInfo) {
        return toAjax(catalogInfoService.updateCatalog(catalogInfo));
    }

    /**
     * 根据父级ID查询目录和资源列表
     */
    @GetMapping("/list")
    public R<List<?>> list(@RequestParam(value = "pid") Long pid) {
        return R.ok(catalogInfoService.getCatalogList(pid));
    }

    /**
     * 分页查询资源列表
     */
    @GetMapping("/page")
    public R<TableDataInfo<?>> page(Long pid, PageQuery pageQuery) {
        return R.ok(catalogInfoService.selectPage(pid, pageQuery));
    }

    /**
     * 获取用户绑定目录的资源列表
     * @param userId
     * @param catalogId
     * @param pageQuery
     * @return
     */
    @GetMapping("/user/page")
    public R<TableDataInfo<?>> page(Long userId, Long catalogId, PageQuery pageQuery){
        return R.ok(catalogInfoService.getDataInfoListByUserIdAndCatalogIdPage(userId, catalogId, pageQuery));
    }


    /**
     * 绑定资源到目录
     *
     * @param dataId
     * @param catalogId
     * @return
     */
    @SaCheckPermission("ocean:catalog:bind")
    @PutMapping(value = "/bind")
    public R<Void> bindDataToCatalog(@RequestParam(value = "dataId") Long dataId, @RequestParam(value = "catalogId") Long catalogId) {
        catalogInfoService.bindDataToCatalog(dataId, catalogId);
        return R.ok();
    }

    /**
     * 绑定资源到用户目录
     */
    @SaCheckPermission("ocean:catalog:bind")
    @PutMapping(value = "/user/bind")
    public R<Void> bindDatasToUser(@RequestParam(value = "dataId") Long dataId,@RequestParam(value="userId") Long userId,@RequestParam(value = "catalogId")Long catalogId ) {
            catalogInfoService.bindDataToCatalogAndUser(dataId,userId,catalogId);
        return R.ok();
    }

    /**
     * 批量绑定资源到目录
     *
     * @param dataIds
     * @param catalogId
     * @return
     */
    @SaCheckPermission("ocean:catalog:bind")
    @PutMapping(value = "/binds")
    public R<Void> bindDatasToCatalog(@RequestParam(value = "dataIds") String dataIds, @RequestParam(value = "catalogId") Long catalogId) {
        String[] s = dataIds.split(",");
        for (String dataId : s) {
            catalogInfoService.bindDataToCatalog(Long.parseLong(dataId), catalogId);
        }
        return R.ok();
    }


    /**
     * 解绑资源目录和资源关联
     *
     * @param dataId
     * @param catalogId
     * @return
     */
    @SaCheckPermission(value={"ocean:catalog:unbind","data:remove:system"},mode = SaMode.OR)
    @PutMapping(value = "/unbind")
    public R<Void> unbindDataToCatalog(@RequestParam(value = "dataId") Long dataId, @RequestParam(value = "catalogId") Long catalogId) {
        catalogInfoService.unBindDataToCatalog(dataId, catalogId);
        return R.ok();
    }

    /**
     * 解绑用户目录跟资源关联
     */
    @SaCheckPermission(value={"ocean:catalog:unbind","data:remove:system"},mode = SaMode.OR)
    @PutMapping(value="/user/unbind")
    public R<Void> unbindDataToUserAndCatalog(@RequestParam(value = "dataId") Long dataId,@RequestParam(value="userId") Long userId,@RequestParam(value = "catalogId")Long catalogId ) {
        catalogInfoService.unBindDataToUserAndCatalog(dataId,userId,catalogId);
        return R.ok();
    }

    /**
     * 查询根资源目录列表
     *
     * @return
     */
    @GetMapping(value = "/root/list")
    public R<List<BusCatalogInfo>> getRootCatalogList() {
        return R.ok(catalogInfoService.getRootCatalogList());
    }

    /**
     * 根据1级根目录构建目录树
     *
     * @param rootId
     * @return
     */
    @GetMapping(value = "/tree")
    public R<List<BusCatalogInfo>> buildTreeByRootId(@RequestParam Long rootId) {
        return R.ok(catalogInfoService.buildTreeByRootId(rootId));
    }

    /**
     * 构建目录树
     *
     * @return
     */
    @GetMapping(value = "/all/tree")
    public R<List<BusCatalogInfo>> buildTreeBy() {
        return R.ok(catalogInfoService.buildTree());
    }



    /**
     * 检查日期
     */
    @GetMapping(value = "/checkDate/{catalogId}")
    public R<Void> checkDate(@PathVariable Long catalogId) {
        catalogInfoService.checkDate(catalogId);
        return R.ok();
    }
}
