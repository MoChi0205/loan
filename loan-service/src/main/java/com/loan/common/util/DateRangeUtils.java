package com.loan.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 统一日期查询边界，避免各业务重复拼接当天起止时间。 */
public final class DateRangeUtils {

    private DateRangeUtils() {
    }

    public static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    public static LocalDateTime endOf(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().minusNanos(1);
    }
}
