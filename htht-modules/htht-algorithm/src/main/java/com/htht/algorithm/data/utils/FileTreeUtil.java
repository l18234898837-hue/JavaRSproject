package com.htht.algorithm.data.utils;

import com.htht.algorithm.download.domain.vo.FileTreeVO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileTreeUtil {

    /**
     * 生成完整目录树形结构
     * @param rootPath 根目录绝对路径
     * @return 根节点树形VO
     */
    public static FileTreeVO buildFileTree(String rootPath) {
        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return null;
        }
        return buildNode(root);
    }

    /**
     * 递归构建单个节点
     */
    private static FileTreeVO buildNode(File file) {
        FileTreeVO vo = new FileTreeVO();
        vo.setFileName(file.getName());
        vo.setAbsolutePath(file.getAbsolutePath());
        vo.setType(file.isDirectory() ? 0 : 1);

        // 如果是文件夹，递归构建子节点
        if (file.isDirectory()) {
            List<FileTreeVO> childList = new ArrayList<>();
            File[] children = file.listFiles();
            // 防止无权限读取返回null
            if (children != null) {
                for (File child : children) {
                    childList.add(buildNode(child));
                }
            }
            vo.setChildren(childList);
        } else {
            // 文件无子节点，置null
            vo.setChildren(null);
        }
        return vo;
    }

    /**
     * 只遍历一级子节点（不递归生成深层树形，平铺一级列表）
     */
    public static List<FileTreeVO> listSingleLevelChildren(String rootPath) {
        List<FileTreeVO> res = new ArrayList<>();
        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return res;
        }
        File[] children = root.listFiles();
        if (children == null) return res;
        for (File child : children) {
            FileTreeVO vo = new FileTreeVO();
            vo.setFileName(child.getName());
            vo.setAbsolutePath(child.getAbsolutePath());
            vo.setType(child.isDirectory() ? 0 : 1);
            // 一级列表不需要加载子节点，children留空
            vo.setChildren(null);
            res.add(vo);
        }
        return res;
    }
}
