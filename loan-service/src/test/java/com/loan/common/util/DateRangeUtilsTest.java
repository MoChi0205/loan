package com.loan.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateRangeUtilsTest {

    @Test
    void createsInclusiveDayBoundaries() {
        LocalDate date = LocalDate.of(2026, 10, 25);
        assertEquals(date.atStartOfDay(), DateRangeUtils.startOf(date));
        assertEquals(date.atTime(23, 59, 59, 999_999_999), DateRangeUtils.endOf(date));
        assertNull(DateRangeUtils.startOf(null));
        assertNull(DateRangeUtils.endOf(null));
    }
}
