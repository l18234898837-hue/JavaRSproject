package com.htht.business.utils.satellite;

import org.apache.commons.math3.util.FastMath;
import org.orekit.time.AbsoluteDate;

import java.time.*;
import java.time.temporal.ChronoField;

/**
 * 历元时间与 LocalDateTime 转换工具类
 * 支持特殊格式：年(2位)+年积日(3位整数+8位小数)，例如：25247.22279722
 */
public class EpochConverterUtil {

    public static void main(String[] args) {
        System.out.println(specialEpochToLocalDateTime("25247.22279722"));
        System.out.println(localDateTimeToSpecialEpoch(LocalDateTime.now()));
    }

    // 默认时区
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    /**
     * 将特殊格式的历元字符串转换为 LocalDateTime
     * 格式：年(2位)+年积日(3位整数+8位小数)，例如：25247.22279722
     *
     * @param epochStr 历元字符串
     * @return LocalDateTime 对象
     * @throws IllegalArgumentException 如果输入格式不正确
     */
    public static LocalDateTime specialEpochToLocalDateTime(String epochStr) {
        // 验证输入格式
        if (!epochStr.matches("\\d{5}\\.\\d{8}")) {
            throw new IllegalArgumentException("Invalid epoch format. Expected: ddddd.dddddddd (5 digits + '.' + 8 digits)");
        }

        // 分割整数和小数部分
        String[] parts = epochStr.split("\\.");
        String integerPart = parts[0];
        String fractionalPart = parts[1];

        // 提取年份（前两位）
        int shortYear = Integer.parseInt(integerPart.substring(0, 2));
        // 计算完整年份（假设20xx年）
        int fullYear = 2000 + shortYear;

        // 提取年积日（后三位）
        int dayOfYear = Integer.parseInt(integerPart.substring(2));

        // 验证年积日有效性
        Year year = Year.of(fullYear);
        if (dayOfYear < 1 || dayOfYear > year.length()) {
            throw new IllegalArgumentException("Day of year is invalid for the given year: " + dayOfYear);
        }

        // 将小数部分转换为一天中的时间（纳秒）
        double fractionOfDay = Double.parseDouble("0." + fractionalPart);
        long nanosecondsOfDay = (long) (fractionOfDay * 24 * 60 * 60 * 1_000_000_000L);

        // 创建 LocalDateTime
        return year.atDay(dayOfYear)
            .atTime(0, 0, 0, 0)
            .plusNanos(nanosecondsOfDay);
    }

    /**
     * 将 LocalDateTime 转换为特殊格式的历元字符串
     * 格式：年(2位)+年积日(3位整数+8位小数)，例如：25247.22279722
     *
     * @param localDateTime LocalDateTime 对象
     * @return 历元字符串
     */
    public static String localDateTimeToSpecialEpoch(LocalDateTime localDateTime) {
        // 提取两位年份
        int fullYear = localDateTime.getYear();
        int shortYear = fullYear % 100;

        // 提取年积日
        int dayOfYear = localDateTime.getDayOfYear();

        // 计算一天中的时间比例（0.0到1.0）
        long totalNanosInDay = 24 * 60 * 60 * 1_000_000_000L;
        long nanosOfDay = localDateTime.getLong(ChronoField.NANO_OF_DAY);
        double fractionOfDay = (double) nanosOfDay / totalNanosInDay;

        // 格式化为8位小数
        String fractionalStr = String.format("%.8f", fractionOfDay).substring(2); // 去掉"0."

        // 组合结果
        return String.format("%02d%03d.%s", shortYear, dayOfYear, fractionalStr);
    }

    /**
     * 将特殊格式的历元字符串转换为毫秒时间戳
     *
     * @param epochStr 历元字符串
     * @return 毫秒时间戳
     */
    public static long specialEpochToMillis(String epochStr) {
        LocalDateTime localDateTime = specialEpochToLocalDateTime(epochStr);
        return localDateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    /**
     * 将毫秒时间戳转换为特殊格式的历元字符串
     *
     * @param millis 毫秒时间戳
     * @return 历元字符串
     */
    public static String millisToSpecialEpoch(long millis) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis), DEFAULT_ZONE);
        return localDateTimeToSpecialEpoch(localDateTime);
    }

    /**
     * 验证历元字符串格式是否正确
     *
     * @param epochStr 历元字符串
     * @return 是否有效
     */
    public static boolean isValidSpecialEpoch(String epochStr) {
        if (!epochStr.matches("\\d{5}\\.\\d{8}")) {
            return false;
        }
        try {
            specialEpochToLocalDateTime(epochStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取当前时间的特殊格式历元字符串
     *
     * @return 历元字符串
     */
    public static String currentSpecialEpoch() {
        return localDateTimeToSpecialEpoch(LocalDateTime.now());
    }

    /**
     * 将AbsoluteDate转换为LocalDateTime（UTC时区）
     * @param absoluteDate Apache Commons Math的AbsoluteDate对象
     * @return 对应的LocalDateTime对象（UTC时区）
     */
    public static LocalDateTime toLocalDateTimeUTC(AbsoluteDate absoluteDate) {
        // 获取从AbsoluteDate纪元（2000-01-01T11:58:55.816 UTC）到指定日期的秒数
        double elapsedTime = absoluteDate.durationFrom(AbsoluteDate.J2000_EPOCH);
        // 计算从Java纪元（1970-01-01T00:00:00Z）到AbsoluteDate纪元的秒数偏移量
        // J2000纪元（2000-01-01T11:58:55.816 UTC）与Java纪元的时间差
        long j2000ToEpochMillis = 946684735816L; // 毫秒数

        // 计算总毫秒数（包括小数部分）
        double totalMillis = j2000ToEpochMillis + elapsedTime * 1000;

        // 转换为Instant然后转换为LocalDateTime
        long seconds = (long) FastMath.floor(totalMillis / 1000);
        int nanoAdjustment = (int) ((totalMillis % 1000) * 1_000_000);

        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanoAdjustment), ZoneOffset.UTC);
    }

    /**
     * 将AbsoluteDate转换为LocalDateTime（系统默认时区）
     * @param absoluteDate Apache Commons Math的AbsoluteDate对象
     * @return 对应的LocalDateTime对象（系统默认时区）
     */
    public static LocalDateTime toLocalDateTime(AbsoluteDate absoluteDate) {
        LocalDateTime utcDateTime = toLocalDateTimeUTC(absoluteDate);
        return utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneOffset.systemDefault()).toLocalDateTime();
    }

    /**
     * 将AbsoluteDate转换为LocalDateTime（指定时区）
     * @param absoluteDate Apache Commons Math的AbsoluteDate对象
     * @param zoneOffset 时区偏移量
     * @return 对应的LocalDateTime对象（指定时区）
     */
    public static LocalDateTime toLocalDateTime(AbsoluteDate absoluteDate, ZoneOffset zoneOffset) {
        LocalDateTime utcDateTime = toLocalDateTimeUTC(absoluteDate);
        return utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneOffset).toLocalDateTime();
    }
}
