package com.htht.algorithm.data.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/5/13 14:31
 **/
public class PathUtil {
    /**
     * 去掉完整路径中与前缀路径相同的部分，返回剩下的相对路径
     *
     * @param prefixPath  公共前缀路径（要去掉的部分）
     * @param fullPath    完整路径
     * @return 去掉公共前缀后的路径
     */
    public static String removeCommonPrefix(String prefixPath, String fullPath) {
        Path prefix = Paths.get(prefixPath).normalize();
        Path full = Paths.get(fullPath).normalize();

        // 核心：自动计算相对路径（去掉相同前缀）
        Path relative = prefix.relativize(full);

        return relative.toString();
    }

    // 测试
    public static void main(String[] args) {
        String prefix = "/htht/python/SNR/";
        String full = "/htht/python/SNR/result/snr.xlsx";

        String result = removeCommonPrefix(prefix, full);
        System.out.println(result);
        // 输出：result/snr.xlsx
    }
}
