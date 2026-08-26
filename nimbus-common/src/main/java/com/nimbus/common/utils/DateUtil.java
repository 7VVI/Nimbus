package com.nimbus.common.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具
 */
public final class DateUtil {

    /** 标准日期时间格式 */
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    /** 标准日期格式 */
    public static final String PATTERN_DATE = "yyyy-MM-dd";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DATE);

    private DateUtil() {
    }

    /** 当前时间格式化为 yyyy-MM-dd HH:mm:ss */
    public static String now() {
        return format(LocalDateTime.now());
    }

    /** 格式化日期时间 */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : DATETIME_FORMATTER.format(dateTime);
    }

    /** 格式化日期 */
    public static String format(LocalDate date) {
        return date == null ? null : DATE_FORMATTER.format(date);
    }

    /** 解析 yyyy-MM-dd HH:mm:ss */
    public static LocalDateTime parseDateTime(String text) {
        return (text == null || text.isBlank()) ? null : LocalDateTime.parse(text, DATETIME_FORMATTER);
    }

    /** 解析 yyyy-MM-dd */
    public static LocalDate parseDate(String text) {
        return (text == null || text.isBlank()) ? null : LocalDate.parse(text, DATE_FORMATTER);
    }
}