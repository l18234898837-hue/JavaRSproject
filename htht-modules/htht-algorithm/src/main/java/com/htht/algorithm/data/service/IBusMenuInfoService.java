package com.htht.algorithm.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.htht.algorithm.data.domain.BusMenuInfo;

import java.util.List;

/**
 * <p>
 * 大屏业务菜单表 服务类
 * </p>
 *
 * @author wjx
 * @since 2023-05-25
 */
public interface IBusMenuInfoService extends IService<BusMenuInfo> {

    /**
     * 新增业务菜单
     *
     * @param menuInfo
     */
    void addMenu(BusMenuInfo menuInfo);

    /**
     * 删除业务菜单
     *
     * @param menuId
     */
    void removeMenu(Long menuId);

    /**
     * 更改业务菜单
     *
     * @param menuInfo
     */
    void updateMenu(BusMenuInfo menuInfo);

    /**
     * 查询菜单列表
     *
     * @return
     */
    List<BusMenuInfo> getMenuList(BusMenuInfo menuInfo);

    /**
     * 校验菜单名称是否唯一
     *
     * @param menu 菜单信息
     * @return 结果
     */
    boolean checkMenuNameUnique(BusMenuInfo menu);

    /**
     * 根据菜单名称获取对应id
     */
    Long getIdByMenuName(String menuName);

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果 true 存在 false 不存在
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * 根据菜单ID得到菜单
     *
     * @param id
     * @return
     */
    BusMenuInfo getMenuById(Long id);

    /**
     * 构建菜单树
     *
     * @return
     */
    List<BusMenuInfo> buildMenuTree();
}
