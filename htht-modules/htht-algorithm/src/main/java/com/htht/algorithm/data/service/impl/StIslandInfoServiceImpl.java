package com.htht.algorithm.data.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.algorithm.data.domain.StIslandInfo;
import com.htht.algorithm.data.domain.vo.StIslandInfoQueryVO;
import com.htht.algorithm.data.mapper.StIslandInfoMapper;
import com.htht.algorithm.data.service.StIslandInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.DelFlagEnums;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 存储群岛信息，含层级关系（parent_id和level） 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-08-18
 */
@Service
public class StIslandInfoServiceImpl extends ServiceImpl<StIslandInfoMapper, StIslandInfo> implements StIslandInfoService {

    @Override
    public boolean add(StIslandInfo stIslandInfo) {
        stIslandInfo.setLongitude(convertDegreeMinuteToDecimal(stIslandInfo.getLongitude()));
        stIslandInfo.setLatitude(convertDegreeMinuteToDecimal(stIslandInfo.getLatitude()));
        return save(stIslandInfo);
    }

    @Override
    public boolean addBatch(List<StIslandInfo> islandList) {
        List<StIslandInfo> collect = islandList.stream().peek(
            island -> {
                island.setLongitude(convertDegreeMinuteToDecimal(island.getLongitude()));
                island.setLatitude(convertDegreeMinuteToDecimal(island.getLatitude()));
            }
        ).toList();
        for (StIslandInfo stIslandInfo : collect) {
            save(stIslandInfo);
        }
        return true;

    }

    @Override
    public List<StIslandInfo> buildTree() {
            List<StIslandInfo> islandInfoList = this.list(new LambdaQueryWrapper<StIslandInfo>()
                .eq(StIslandInfo::getDelFlag, DelFlagEnums.EXISTS.getCode())
                .select(StIslandInfo::getId,
                    StIslandInfo::getParentId,
                    StIslandInfo::getLevel,
                    StIslandInfo::getArchipelago,
                    StIslandInfo::getStandardName,
                    StIslandInfo::getChineseName,
                    StIslandInfo::getLongitude,
                    StIslandInfo::getLatitude));

            // 将群岛信息列表转换为Map，以id为key，群岛信息对象为value
            Map<Long, StIslandInfo> islandMap = islandInfoList.stream()
                .collect(Collectors.toMap(StIslandInfo::getId, Function.identity(), (key1, key2) -> key2));

            List<StIslandInfo> islandTree = new ArrayList<>();

            // 遍历所有群岛信息
            islandMap.forEach((id, island) -> {
                // 如果是顶级节点（parentId为null或0）
                if (island.getParentId() == null || island.getParentId() == 0) {
                    islandTree.add(island);
                } else {
                    // 找到父级节点并添加当前节点到其children列表中
                    StIslandInfo parentIsland = islandMap.get(island.getParentId());
                    if (parentIsland != null) {
                        if (parentIsland.getChildren() == null) {
                            parentIsland.setChildren(new ArrayList<>());
                        }
                        parentIsland.getChildren().add(island);
                    }
                }
            });

            // 对根节点进行排序
            islandTree.sort(Comparator.comparing(StIslandInfo::getArchipelago));

            // 对每个节点的子节点进行排序
            islandTree.forEach(root -> {
                if (root.getChildren() != null) {
                    root.getChildren().sort(Comparator.comparing(StIslandInfo::getArchipelago));
                }
            });

            return islandTree;
    }

    @Override
    public TableDataInfo<StIslandInfo> pageStIslandInfo(StIslandInfoQueryVO queryVO) {
//        Page<StIslandInfo> page = this.baseMapper.selectPage(queryVO.build(), QueryWrapperBuilder.build(queryVO, StIslandInfo.class, false, null, null));
        Page<StIslandInfo> page = this.baseMapper.pageStIslandInfo(queryVO.build(), queryVO);
        return TableDataInfo.build(page);
    }

    @Override
    public StIslandInfo getStIslandInfo(Long id) {
        return this.baseMapper.getStIslandInfo(id);
    }


    public static String convertDegreeMinuteToDecimal(String degreeMinute) {
        if (degreeMinute == null || degreeMinute.isEmpty()) {
            return null;
        }
        // 去除可能存在的空格
        degreeMinute = degreeMinute.trim();
        int degreeIndex = degreeMinute.indexOf('°');
        int minuteIndex = degreeMinute.indexOf('\'');
        if (degreeIndex == -1 || minuteIndex == -1) {
            return degreeMinute;
        }
        try {
            double degrees = Double.parseDouble(degreeMinute.substring(0, degreeIndex));
            double minutes = Double.parseDouble(degreeMinute.substring(degreeIndex + 1, minuteIndex));
            return degrees + minutes / 60.0 + "";
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("度或分部分无法正确转换为数字", e);
        }
    }

    public static void main(String[] args) {
        String input = "20°35'";
        String result = convertDegreeMinuteToDecimal(input);
        System.out.println("转换后的十进制度数: " + result);
    }
}
