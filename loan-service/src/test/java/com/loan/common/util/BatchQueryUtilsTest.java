package com.loan.common.util;

import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 批量查询参数边界测试。 */
class BatchQueryUtilsTest {

    @Test
    void shouldTrimDeduplicateAndKeepOrder() {
        assertEquals(Arrays.asList("tpl2", "tpl1"),
                BatchQueryUtils.normalizeCodes(Arrays.asList(" tpl2 ", "tpl1", "tpl2", " ")));
    }

    @Test
    void shouldRejectEmptyAndOversizedBatch() {
        assertThrows(BusinessException.class, () -> BatchQueryUtils.normalizeCodes(Arrays.asList("", " ")));
        List<String> codes = new ArrayList<>();
        for (int i = 0; i <= BatchQueryUtils.MAX_BATCH_SIZE; i++) {
            codes.add("tpl" + i);
        }
        assertThrows(BusinessException.class, () -> BatchQueryUtils.normalizeCodes(codes));
    }
}
