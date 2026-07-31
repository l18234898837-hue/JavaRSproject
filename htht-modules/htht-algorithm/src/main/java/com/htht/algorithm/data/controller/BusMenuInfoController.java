package com.htht.algorithm.data.controller;


import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 业务菜单管理
 *
 * @author wjx
 * @since 2023-05-25
 */
@RestController
@RequestMapping("/algorithm/menu")
@Validated
@RequiredArgsConstructor
public class BusMenuInfoController extends BaseController {

    private final IBusMenuInfoService menuInfoService;

    /**
     * 新增业务菜单
     *
     * @param menuInfo
     * @return
     */
    @PostMapping
    public R<Void> addMenu(@Validated @RequestBody BusMenuInfo menuInfo) {
        if (!menuInfoService.checkMenuNameUnique(menuInfo)) {
            return R.fail("新增菜单" + menuInfo.getMenuName() + "失败，菜单名称已经存在");
        }
        menuInfoService.addMenu(menuInfo);
        return R.ok();
    }

    /**
     * 删除业务菜单
     *
     * @param menuId
     * @return
     */
    @DeleteMapping("/{menuId}")
    public R<Void> removeMenu(@PathVariable(value = "menuId") Long menuId) {
        if (menuInfoService.hasChildByMenuId(menuId)) {
            return R.fail("存在子菜单，不允许删除");
        }
        menuInfoService.removeMenu(menuId);
        return R.ok();
    }

    /**
     * 更改业务菜单
     *
     * @param menuInfo
     * @return
     */
    @PutMapping
    public R<Void> updateMenu(@Validated @RequestBody BusMenuInfo menuInfo) {
        if (!menuInfoService.checkMenuNameUnique(menuInfo)) {
            return R.fail("修改菜单" + menuInfo.getMenuName() + "失败，菜单名已经存在");
        } else if (menuInfo.getMenuId().equals(menuInfo.getParentId())) {
            return R.fail("修改菜单'" + menuInfo.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        menuInfoService.updateMenu(menuInfo);
        return R.ok();
    }

    /**
     * 查询菜单列表
     *
     * @param menuInfo
     * @return
     */
    @GetMapping(value = "/list")
    public R<List<BusMenuInfo>> list(BusMenuInfo menuInfo) {
        return R.ok(menuInfoService.getMenuList(menuInfo));
    }

    /**
     * 根据菜单ID得到菜单信息
     *
     * @param menuId
     * @return
     */
    @GetMapping(value = "/{menuId}")
    public R<BusMenuInfo> getMenuById(@PathVariable Long menuId) {
        return R.ok(menuInfoService.getMenuById(menuId));
    }

    /**
     * 构建菜单树
     *
     * @return
     */
    @GetMapping(value = "/tree")
    public R<List<BusMenuInfo>> buildTree() {
        return R.ok(menuInfoService.buildMenuTree());
    }
}
