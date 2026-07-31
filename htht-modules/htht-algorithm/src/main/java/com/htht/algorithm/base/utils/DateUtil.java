package com.htht.algorithm.base.utils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Author: MSFY
 * Description:
 * DateTime: 2026/7/22 17:29
 **/
public class DateUtil {
    /**
        * 计算两个LocalDateTime之间差值（毫秒）
        * @param start 起始时间
     * @param end   结束时间
     * @return end - start 的总毫秒；若end < start 返回负数
     * @throws IllegalArgumentException 时间入参为空抛出异常
     */
    public static long getDiffMilliseconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("起始时间、结束时间不能为null");
        }
        return Duration.between(start, end).toMillis();
    }

    /**
        * 获取绝对毫秒差值（永远返回非负数）
        * @param start 起始时间
     * @param end   结束时间
     * @return 绝对毫秒数
     */
    public static long getAbsDiffMilliseconds(LocalDateTime start, LocalDateTime end) {
        return Math.abs(getDiffMilliseconds(start, end));
    }

    /**
        * 将毫秒格式化为 秒.毫秒 样式，例如 22.025
        * @param ms 总毫秒数
     * @return 格式化字符串
     */
    public static String formatMsToSec(long ms) {
        long sec = ms / 1000;
        long millis = ms % 1000;
        return String.format("%d.%03d", sec, millis);
    }
}
