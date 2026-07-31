package com.htht.algorithm.data.utils;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2025/9/6 15:27
 **/
public class DateUtil {
    public static Date getFormatterDate(String dateStr, String formatterStr)  {
        SimpleDateFormat sdf = new SimpleDateFormat(formatterStr);
        try {
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            System.out.println("时间转化异常"+e.getMessage());
        }
        return null;
    }

    /**
     * Date 对象 → 自定义格式字符串（Java8+ 线程安全）
     * @param date 待格式化的 Date
     * @param formatterStr 格式串，如 yyyy-MM-dd、yyyy-MM-dd HH:mm:ss
     * @return 格式化后的字符串，date为null返回null
     */
    public static String formatDate(Date date, String formatterStr) {
        if (date == null) {
            return null;
        }
        // Date → Instant → LocalDateTime（本地时区）
        LocalDateTime localDateTime = date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        return localDateTime.format(DateTimeFormatter.ofPattern(formatterStr));
    }

    public static LocalDate getFormatterLocalDate(String dateStr, String formatterStr)  {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(formatterStr));
        } catch (Exception e) {
            System.out.println("时间转化异常"+e.getMessage());
        }
        return null;
    }

    public static LocalTime getFormatterLocalTime(String timeStr, String formatterStr)  {
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern(formatterStr));
        } catch (Exception e) {
            System.out.println("时间转化异常"+e.getMessage());
        }
        return null;
    }

    public static String getCurrentOnlineDate() {
        // 1. 定义目标格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
        // 其中 'Z' 作为字面量（单引号包裹），表示 UTC 时区
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC); // 强制使用 UTC 时区

        // 2. 获取当前 UTC 时间（Instant 本身就是 UTC 时间的"瞬间"）
        Instant now = Instant.now();

        // 3. 格式化时间为目标字符串

        return formatter.format(now);
        // 输出示例：2025-10-18T12:34:56.789Z（实际为当前 UTC 时间）
    }
    public static String getNowFormatterStr(String formatterStr){
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(formatterStr));
    }

    public static void main(String[] args) {
        LocalDate date = getFormatterLocalDate("2025/10/18", "yyyy/MM/dd");
        System.out.println("日期解析结果：" + date); // 2025-10-18
        LocalTime formatterLocalTime1 = getFormatterLocalTime("10:42:01", "HH:mm:ss");
        System.out.println(formatterLocalTime1);
        formatterLocalTime1 = getFormatterLocalTime("10:42", "HH:mm:ss");
        System.out.println(formatterLocalTime1);
        formatterLocalTime1 = getFormatterLocalTime("10:42", "HH:mm");
        System.out.println(formatterLocalTime1);
    }
}
