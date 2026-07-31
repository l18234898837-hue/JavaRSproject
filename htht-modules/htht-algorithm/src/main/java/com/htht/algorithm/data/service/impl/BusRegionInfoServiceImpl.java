package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.algorithm.data.domain.BusRegionInfo;
import com.htht.algorithm.data.mapper.BusRegionInfoMapper;
import com.htht.algorithm.data.service.IBusRegionInfoService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author wjx
 * @since 2023-05-29
 */
@Service
public class BusRegionInfoServiceImpl extends ServiceImpl<BusRegionInfoMapper, BusRegionInfo> implements IBusRegionInfoService {

    @Override
    @Cacheable(value = "algorithm:region:tree")
    public List<BusRegionInfo> buildTree() {
        List<BusRegionInfo> regionInfoList = this.list();
        //将菜单List转Map，菜单ID为key，菜单对象为value
        Map<String, BusRegionInfo> regionMap = regionInfoList.stream().collect(
            Collectors.toMap(BusRegionInfo::getCode, Function.identity(), (key1, key2) -> key2)
        );
        List<BusRegionInfo> regionTree = new ArrayList<>();
        regionMap.forEach((key, value) -> {
            if (value.getLevel() != 1) {
                BusRegionInfo parentRegion = regionMap.get(value.getPCode());
                List<BusRegionInfo> children = parentRegion.getChildren();
                children.add(value);
                children.sort(Comparator.comparing(BusRegionInfo::getCode));
            } else {
                regionTree.add(value);
            }
        });
        regionTree.sort(Comparator.comparing(BusRegionInfo::getCode));
        return regionTree;
    }
}
