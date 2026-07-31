package com.htht.algorithm.download.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTreeVO {
    /** 文件/文件夹名称 */
    private String fileName;
    /** 类型 0=文件夹，1=文件 */
    private Integer type;
    /** 文件完整绝对路径 */
    private String absolutePath;
    /** 子节点集合（文件夹才有子节点，文件此字段为null/空） */
    private List<FileTreeVO> children;
}
