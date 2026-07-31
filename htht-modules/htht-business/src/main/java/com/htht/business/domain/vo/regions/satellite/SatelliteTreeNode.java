package com.htht.business.domain.vo.regions.satellite;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SatelliteTreeNode {

    private String id;
    private String name;
    private int level;
    private List<SatelliteTreeNode> children = new ArrayList<>();
    private Object data; // 存储原始数据（仅叶子节点）

    public SatelliteTreeNode(String id, String name, int level) {
        this.id = id;
        this.name = name;
        this.level = level;
    }

    // 添加子节点方法
    public void addChild(SatelliteTreeNode child) {
        children.add(child);
    }

}
