package com.loan.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BizIdGenerator} 单元测试。
 *
 * @author loan-platform
 */
class BizIdGeneratorTest {

    /** 固定总长度业务编码应保留小写前缀并满足长度。 */
    @Test
    void generateFixedLength() {
        String first = BizIdGenerator.generate("CULIST", 16);
        String second = BizIdGenerator.generate("culist", 16);

        assertEquals(16, first.length());
        assertTrue(first.matches("culist[0-9a-z]{10}"));
        assertEquals(16, second.length());
        assertNotEquals(first, second);
    }

    /** 奇数长度随机部分也必须完整生成。 */
    @Test
    void generateFixedLengthWithOddRandomPart() {
        String value = BizIdGenerator.generate("pcity", 16);
        assertTrue(value.matches("pcity[0-9a-z]{11}"));
    }

    /** 总长度不足时拒绝生成。 */
    @Test
    void rejectInvalidTotalLength() {
        assertThrows(IllegalArgumentException.class, () -> BizIdGenerator.generate("culist", 6));
    }
}
