package com.htht.business.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.htht.business.converter.SysSatelliteInfoConverter;
import com.htht.business.domain.SysSatelliteInfo;
import com.htht.business.domain.vo.DictVO;
import com.htht.business.domain.vo.regions.satellite.SatelliteTreeNode;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoEditVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoListVO;
import com.htht.business.domain.vo.regions.satellite.SysSatelliteInfoQueryVO;
import com.htht.business.mapper.SysSatelliteInfoMapper;
import com.htht.business.service.SysSatelliteInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.htht.common.build.QueryWrapperBuilder;
import com.htht.common.constant.DateFormatConstant;
import com.htht.common.core.domain.entity.SysDictData;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.enums.DelFlagEnums;
import com.htht.common.utils.IDUtil;
import com.htht.system.service.ISysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 卫星信息表 服务实现类
 * </p>
 *
 * @author MSFY
 * @since 2025-09-26
 */
@Service
@RequiredArgsConstructor
public class SysSatelliteInfoServiceImpl extends ServiceImpl<SysSatelliteInfoMapper, SysSatelliteInfo> implements SysSatelliteInfoService {

    private final SysSatelliteInfoConverter sysSatelliteInfoConverter;

    private final ISysDictDataService sysDictDataService;

    @Override
    public TableDataInfo<SysSatelliteInfoListVO> pageSysSatelliteInfo(SysSatelliteInfoQueryVO queryVO) {
        Map<String, Object> params = queryVO.getParams();
        LocalDateTime beginTime = null;
		LocalDateTime endTime = null;
        if (ObjectUtil.isNotNull(params.get("beginTime")) && ObjectUtil.isNotNull(params.get("endTime"))){
			beginTime = LocalDateTimeUtil.parse(params.get("beginTime").toString(), DateFormatConstant.NORMAL);
			endTime = LocalDateTimeUtil.parse(params.get("endTime").toString(), DateFormatConstant.NORMAL);
        }
        Page<SysSatelliteInfo> page = this.baseMapper.selectPage(queryVO.build(),
            QueryWrapperBuilder.build(queryVO, SysSatelliteInfo.class, false, null, null)
                .ge(beginTime != null, "launch_date", beginTime)
                .le(endTime != null, "launch_date", endTime));
        Page<SysSatelliteInfoListVO> pagevo = this.sysSatelliteInfoConverter.entity2page(page);
        return TableDataInfo.build(pagevo);
    }

    @Override
    public SysSatelliteInfoEditVO getSysSatelliteInfo(Long id) {
        SysSatelliteInfo sysSatelliteInfo = this.baseMapper.selectById(id);
        return this.sysSatelliteInfoConverter.entity2edit(sysSatelliteInfo);
    }

    @Override
    public int addSysSatelliteInfo(SysSatelliteInfoEditVO editVO) {
        SysSatelliteInfo sysSatelliteInfo = this.sysSatelliteInfoConverter.edit2entity(editVO);
        sysSatelliteInfo.setId(IDUtil.getLongID());
        return this.baseMapper.insert(sysSatelliteInfo);
    }

    @Override
    public int updateSysSatelliteInfo(SysSatelliteInfoEditVO editVO) {
        SysSatelliteInfo sysSatelliteInfo = this.sysSatelliteInfoConverter.edit2entity(editVO);
        return this.baseMapper.updateById(sysSatelliteInfo);
    }

    @Override
    public int updateDeleteStatusByIds(List<Long> ids) {
        return this.baseMapper.update(new SysSatelliteInfo(), new LambdaUpdateWrapper<SysSatelliteInfo>()
            .in(SysSatelliteInfo::getId, ids)
            .set(SysSatelliteInfo::getDelFlag, DelFlagEnums.DELETED.getCode()));
    }

    @Override
    public List<SatelliteTreeNode> treeSysSatelliteInfo() {
        List<SysSatelliteInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<SysSatelliteInfo>()
            .eq(SysSatelliteInfo::getDelFlag, DelFlagEnums.EXISTS.getCode()));
        return buildTree(list);
    }

    @Override
    public TableDataInfo<DictVO> dictSysSatelliteInfo() {
        List<DictVO> list = this.baseMapper.dictSysSatelliteInfo();
        return TableDataInfo.build(list);
    }

    public List<SatelliteTreeNode> buildTree(List<SysSatelliteInfo> satelliteList) {
        List<String> typeList = new ArrayList<>();
        typeList.add("satellite_type");
        typeList.add("satellite_category");
        List<SysDictData> dictList = sysDictDataService.list(new LambdaQueryWrapper<SysDictData>()
            .in(SysDictData::getDictType, typeList));
        // 第一层：按卫星类型分组
        Map<String, SatelliteTreeNode> typeNodes = new HashMap<>();
        for (SysSatelliteInfo satellite : satelliteList) {
            // 处理多值卫星类型（如"光学,高光谱"）
            String[] types = satellite.getSatelliteType().split(",");
            for (String type : types) {
                String label = "";
                for (SysDictData dict : dictList) {
                    if(type.equals(dict.getDictValue())) {
                        label = dict.getDictLabel();
                        break;
                    }
                }
                final String currentType = label.trim(); // 创建final变量
                // 获取或创建类型节点
                SatelliteTreeNode typeNode = typeNodes.computeIfAbsent(type,
                    k -> new SatelliteTreeNode("type_" + currentType, currentType, 1));
                // 第二层：按分辨率分组
                String resolution = satellite.getResolution() != null ?
                    satellite.getResolution().toString() : "未知分辨率";
                String resolutionKey = type + "|" + resolution;
                SatelliteTreeNode resolutionNode = findOrCreateChild(
                    typeNode, resolutionKey, resolution+"米分辨率卫星", 2);
                // 第三层：按type分组
                String subType = satellite.getSatelliteCategory() != null ?
                    satellite.getSatelliteCategory() : "未知类型";
                for (SysDictData dict : dictList) {
                    if(subType.equals(dict.getDictValue())) {
                        subType = dict.getDictLabel();
                        break;
                    }
                }
                String subTypeKey = resolutionKey + "|" + subType;
                SatelliteTreeNode subTypeNode = findOrCreateChild(
                    resolutionNode, subTypeKey, subType, 3);
                // 第四层：卫星节点（叶子节点）
//                String satelliteKey = subTypeKey + "|" + satellite.getSatelliteCode();
                SatelliteTreeNode satelliteNode = new SatelliteTreeNode(
                    "sat_" + satellite.getId(),
                    satellite.getSatelliteCode(),
                    4
                );
                satelliteNode.setData(satellite); // 存储原始数据
                subTypeNode.addChild(satelliteNode);
            }
        }
        // 排序后返回
        List<SatelliteTreeNode> result = new ArrayList<>(typeNodes.values());
        // 第一层排序：按类型名称
        result.sort((o1, o2) -> {
            boolean isUncategorized1 = "未分类".equals(o1.getName());
            boolean isUncategorized2 = "未分类".equals(o2.getName());

            if (isUncategorized1 && !isUncategorized2) {
                return 1;  // 未分类排后面
            }
            if (!isUncategorized1 && isUncategorized2) {
                return -1; // 正常排前面
            }
            // 其余正常按名称排序
            return o1.getName().compareTo(o2.getName());
        });
        // 递归排序所有子节点
        for (SatelliteTreeNode typeNode : result) {
            sortChildrenRecursively(typeNode);
        }
        return result;
    }

    /**
     * 递归排序子节点
     */
    private void sortChildrenRecursively(SatelliteTreeNode node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        // 根据层级决定排序规则
        switch (node.getLevel()) {
            case 1: // 类型节点下的分辨率节点：按分辨率数值排序
                node.getChildren().sort((c1, c2) -> {
                    // 提取分辨率数值进行比较
                    Double res1 = extractResolutionValue(c1.getName());
                    Double res2 = extractResolutionValue(c2.getName());
                    return Double.compare(res1, res2);
                });
                break;
            case 2: // 分辨率节点下的子类型节点：按名称排序
                node.getChildren().sort(Comparator.comparing(SatelliteTreeNode::getName));
                break;
            case 3: // 子类型节点下的卫星节点：按卫星代码排序
                node.getChildren().sort(Comparator.comparing(SatelliteTreeNode::getName));
                break;
            default:
                node.getChildren().sort(Comparator.comparing(SatelliteTreeNode::getName));
        }

        // 递归排序孙子节点
        for (SatelliteTreeNode child : node.getChildren()) {
            sortChildrenRecursively(child);
        }
    }

    /**
     * 从分辨率字符串中提取数值（例如："0.5米" -> 0.5）
     */
    private Double extractResolutionValue(String resolutionStr) {
        if (resolutionStr == null || resolutionStr.equals("未知分辨率")) {
            return Double.MAX_VALUE; // 未知分辨率排在最后
        }

        try {
            // 提取数字部分
            String numStr = resolutionStr.replaceAll("[^0-9.]", "");
            if (!numStr.isEmpty()) {
                return Double.parseDouble(numStr);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            // 解析失败，返回最大值
        }

        return Double.MAX_VALUE;
    }

    private SatelliteTreeNode findOrCreateChild(SatelliteTreeNode parent,
                                                String key,
                                                String name,
                                                int level) {
        // 在父节点的子节点中查找
        for (SatelliteTreeNode child : parent.getChildren()) {
            if (child.getId().equals(key)) {
                return child;
            }
        }

        // 不存在则创建新节点
        SatelliteTreeNode newNode = new SatelliteTreeNode(key, name, level);
        parent.addChild(newNode);
        return newNode;
    }


}
