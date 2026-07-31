package com.htcloud.orbit.vo;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Author: MSFY
 * Description: TODO
 * DateTime: 2026/4/10 14:37
 **/
@Data
@Validated
public class OrbitForecastPeriod {
    @NotNull(message = "周期开始时间不能为空")
    private LocalTime periodStartTime;

    @NotNull(message = "周期结束时间不能为空")
    private LocalTime periodEndTime;

    /**
     * 按【每日周期时分秒】切割时间，生成多个预测请求
     * @param original 原始请求（含 startTime, endTime）
     * @param period 周期（periodStartTime, periodEndTime）
     * @return 切割后的每日预测请求列表
     */
    public static List<OrbitForecastRequestVO> splitForecastByDailyPeriodOnZone(
        OrbitForecastRequestVO original,
        OrbitForecastPeriod period) {

        List<OrbitForecastRequestVO> resultList = new ArrayList<>();

        // 原始时间
        Date overallStart = original.getStartTime();
        Date overallEnd = original.getEndTime();

        // 周期时间（时分秒）
        LocalTime periodStart = period.getPeriodStartTime();
        LocalTime periodEnd = period.getPeriodEndTime();

        // 转 LocalDateTime 方便处理
        LocalDateTime startLdt = overallStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endLdt = overallEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        LocalDate currentDate = startLdt.toLocalDate();
        LocalDate endDate = endLdt.toLocalDate();

        // 循环每一天
        while (!currentDate.isAfter(endDate)) {
            // 构建当天的开始、结束时间
            LocalDateTime currentStart = LocalDateTime.of(currentDate, periodStart);
            LocalDateTime currentEnd = LocalDateTime.of(currentDate, periodEnd);

            // 处理边界：
            // 第一天：如果原始开始时间 > 当天周期开始 → 用原始时间
            if (currentDate.isEqual(startLdt.toLocalDate()) && startLdt.isAfter(currentStart)) {
                currentStart = startLdt;
            }
            // 最后一天：如果原始结束时间 < 当天周期结束 → 用原始时间
            if (currentDate.isEqual(endLdt.toLocalDate()) && endLdt.isBefore(currentEnd)) {
                currentEnd = endLdt;
            }

            if (currentStart.isAfter(currentEnd)) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // 复制原始VO，只替换时间
            OrbitForecastRequestVO copy = new OrbitForecastRequestVO();
            copy.setStartTime(convertToDate(currentStart));
            copy.setEndTime(convertToDate(currentEnd));
            // 其他字段原样复制
            copy.setZone(original.getZone());
            copy.setOrbitForecastSpecs(original.getOrbitForecastSpecs());

            resultList.add(copy);

            // 下一天
            currentDate = currentDate.plusDays(1);
        }

        return resultList;
    }

    /**
     * 按【每日周期时分秒】切割时间，生成多个预测请求
     * @param original 原始请求（含 startTime, endTime）
     * @param period 周期（periodStartTime, periodEndTime）
     * @return 切割后的每日预测请求列表
     */
    public static List<OrbitForecastCircleRequestVO> splitForecastByDailyPeriodOnCircle(
        OrbitForecastCircleRequestVO original,
        OrbitForecastPeriod period) {

        List<OrbitForecastCircleRequestVO> resultList = new ArrayList<>();

        // 原始时间
        Date overallStart = original.getStartTime();
        Date overallEnd = original.getEndTime();

        // 周期时间（时分秒）
        LocalTime periodStart = period.getPeriodStartTime();
        LocalTime periodEnd = period.getPeriodEndTime();

        // 转 LocalDateTime 方便处理
        LocalDateTime startLdt = overallStart.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endLdt = overallEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        LocalDate currentDate = startLdt.toLocalDate();
        LocalDate endDate = endLdt.toLocalDate();

        // 循环每一天
        while (!currentDate.isAfter(endDate)) {
            // 构建当天的开始、结束时间
            LocalDateTime currentStart = LocalDateTime.of(currentDate, periodStart);
            LocalDateTime currentEnd = LocalDateTime.of(currentDate, periodEnd);

            // 处理边界：
            // 第一天：如果原始开始时间 > 当天周期开始 → 用原始时间
            if (currentDate.isEqual(startLdt.toLocalDate()) && startLdt.isAfter(currentStart)) {
                currentStart = startLdt;
            }
            // 最后一天：如果原始结束时间 < 当天周期结束 → 用原始时间
            if (currentDate.isEqual(endLdt.toLocalDate()) && endLdt.isBefore(currentEnd)) {
                currentEnd = endLdt;
            }

            if (currentStart.isAfter(currentEnd)) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // 复制原始VO，只替换时间
            OrbitForecastCircleRequestVO copy = new OrbitForecastCircleRequestVO();
            copy.setStartTime(convertToDate(currentStart));
            copy.setEndTime(convertToDate(currentEnd));
            // 其他字段原样复制
            copy.setOrbitForecastSpecs(original.getOrbitForecastSpecs());
            copy.setCenterLon(original.getCenterLon());
            copy.setCenterLat(original.getCenterLat());
            copy.setRadiusKm(original.getRadiusKm());
            copy.setSegments(original.getSegments());


            resultList.add(copy);

            // 下一天
            currentDate = currentDate.plusDays(1);
        }

        return resultList;
    }

    // 工具：LocalDateTime → Date
    private static Date convertToDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
