package com.loan.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 分页边界归一化测试。 */
class PageParamsTest {

    @Test
    void pageShouldStartAtOne() {
        assertEquals(1, PageParams.page(0));
        assertEquals(1, PageParams.page(-3));
        assertEquals(7, PageParams.page(7));
    }

    @Test
    void sizeShouldUseDefaultAndUpperBound() {
        assertEquals(10, PageParams.size(0));
        assertEquals(10, PageParams.size(-1));
        assertEquals(100, PageParams.size(101));
        assertEquals(20, PageParams.size(20));
    }
}
