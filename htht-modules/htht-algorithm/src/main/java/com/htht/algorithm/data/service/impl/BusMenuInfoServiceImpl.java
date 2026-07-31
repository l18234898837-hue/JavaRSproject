package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.BusMenuInfo;
import com.htht.algorithm.data.mapper.BusMenuInfoMapper;
import com.htht.algorithm.data.service.IBusMenuInfoService;
import com.htht.common.exception.GlobalException;
import org.apache.logging.log4j.util.Strings;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 大屏业务菜单表 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-05-25
 */
@Service

public class BusMenuInfoServiceImpl extends ServiceImpl<BusMenuInfoMapper, BusMenuInfo> implements IBusMenuInfoService {

    @Override
    @CacheEvict(value = {"ocean:menu:tree", "ocean:menu:list"}, allEntries = true)
    public void addMenu(BusMenuInfo menuInfo) {
        //判断菜单的父级类型是否为目录，不为目录则不能继续添加
        if (menuInfo.getParentId() == 0) {
            menuInfo.setLevel(0);
        } else {
            BusMenuInfo parentMenu = Optional.ofNullable(this.getById(menuInfo.getParentId())).orElseThrow(() -> new GlobalException("父级目录不存在"));
            if (parentMenu.getMenuType().equals("C")) {
                throw new GlobalException("菜单类型为资源，不能在添加菜单");
            }
            //前端要求，把所有目录都添加到这个表里去，所以必须去除菜单限制
//            if (parentMenu.getLevel() >= 3) {
//                throw new GlobalException("菜单最大层级只能到3层");
//            }
//            if (parentMenu.getLevel() == 2 && !menuInfo.getMenuType().equals("C")) {
//                throw new GlobalException("3级菜单类型只能为菜单");
//            }
            menuInfo.setLevel(parentMenu.getLevel() + 1);
        }
        this.save(menuInfo);
    }

    @Override
    @CacheEvict(value = {"ocean:menu:tree", "ocean:menu:list"}, allEntries = true)
    public void removeMenu(Long menuId) {
        this.removeById(menuId);
    }

    @Override
    @CacheEvict(value = {"ocean:menu:tree", "ocean:menu:list"}, allEntries = true)
    public void updateMenu(BusMenuInfo menuInfo) {
        this.updateById(menuInfo);
    }

    @Override
    @Cacheable(value = "ocean:menu:list")
    public List<BusMenuInfo> getMenuList(BusMenuInfo menuInfo) {
        return this.lambdaQuery()
            .like(Strings.isNotBlank(menuInfo.getMenuName()), BusMenuInfo::getMenuName, menuInfo.getMenuName())
            .eq(Strings.isNotBlank(menuInfo.getStatus()), BusMenuInfo::getStatus, menuInfo.getStatus())
            .orderByAsc(BusMenuInfo::getParentId)
            .orderByAsc(BusMenuInfo::getOrderNum)
            .list();
    }

    @Override
    public boolean checkMenuNameUnique(BusMenuInfo menu) {
        return !baseMapper.exists(
            new LambdaQueryWrapper<BusMenuInfo>()
                .eq(BusMenuInfo::getMenuName, menu.getMenuName())
                .eq(BusMenuInfo::getParentId, menu.getParentId())
                .ne(Objects.nonNull(menu.getMenuId()), BusMenuInfo::getMenuId, menu.getMenuId())
        );
    }

    @Override
    public Long getIdByMenuName(String menuName) {
        BusMenuInfo one = this.getOne(new LambdaQueryWrapper<BusMenuInfo>().eq(BusMenuInfo::getMenuName, menuName));
        return one.getMenuId();
    }

    @Override
    public boolean hasChildByMenuId(Long menuId) {
        return baseMapper.exists(new LambdaQueryWrapper<BusMenuInfo>().eq(BusMenuInfo::getParentId, menuId));
    }

    @Override
    public BusMenuInfo getMenuById(Long id) {
        return this.getById(id);
    }

    @Override
    @Cacheable(value = "ocean:menu:tree")
    public List<BusMenuInfo> buildMenuTree() {
        List<BusMenuInfo> menuList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<Long, BusMenuInfo> menuMap = menuList.stream().collect(
            Collectors.toMap(BusMenuInfo::getMenuId, Function.identity(), (key1, key2) -> key2)
        );
        List<BusMenuInfo> menuTree = new ArrayList<>();
        menuMap.entrySet().stream().forEach(entry -> {
            if (entry.getValue().getParentId() != 0) {
                BusMenuInfo parentMenu = menuMap.get(entry.getValue().getParentId());
                List<BusMenuInfo> children = parentMenu.getChildren();
                if (Objects.isNull(children)) {
                    children = new ArrayList<>();
                    parentMenu.setChildren(children);
                }
                children.add(entry.getValue());
                children.sort(Comparator.comparing(BusMenuInfo::getOrderNum));
            } else {
                menuTree.add(entry.getValue());
            }
        });
        menuTree.sort(Comparator.comparing(BusMenuInfo::getOrderNum));
        return menuTree;
    }
}
