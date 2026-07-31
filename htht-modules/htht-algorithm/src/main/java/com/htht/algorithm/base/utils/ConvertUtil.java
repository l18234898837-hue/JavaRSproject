package com.htht.algorithm.base.utils;

import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/6/9 15:18
 **/
public class ConvertUtil {
    /**
     * 单个对象属性复制通用方法
     * @param source 源对象
     * @param targetClass 目标对象类
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 复制后的目标对象
     */
    public static <S, T> T convert(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象属性复制失败", e);
        }
    }

    /**
     * 批量对象属性复制通用方法
     * @param sourceList 源对象列表
     * @param targetClass 目标对象类
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 复制后的目标对象列表
     */
    public static <S, T> List<T> convertList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null || sourceList.isEmpty()) {
            return Collections.emptyList();
        }
        return sourceList.stream()
            .map(source -> convert(source, targetClass))
            .collect(Collectors.toList());
    }
}
