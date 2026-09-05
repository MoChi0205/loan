package com.loan.engine.aggregate;

import com.loan.engine.enums.Grade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 档位聚合单测（M3 L2 规则引擎结果态）：命中产品数 → 高/中/低。
 */
class GradeAggregatorTest {

    private final GradeAggregator aggregator = new GradeAggregator();

    @Test
    @DisplayName("默认阈值：0 命中 → 低")
    void aggregate_zero_low() {
        assertEquals(Grade.LOW, aggregator.aggregate(0));
    }

    @Test
    @DisplayName("默认阈值：1~2 命中 → 中")
    void aggregate_middle() {
        assertEquals(Grade.MIDDLE, aggregator.aggregate(1));
        assertEquals(Grade.MIDDLE, aggregator.aggregate(2));
    }

    @Test
    @DisplayName("默认阈值：≥3 命中 → 高")
    void aggregate_high() {
        assertEquals(Grade.HIGH, aggregator.aggregate(3));
        assertEquals(Grade.HIGH, aggregator.aggregate(10));
    }

    @Test
    @DisplayName("自定义阈值：以 2 为界，2 命中 → 高，1 → 中，0 → 低")
    void aggregate_customThreshold() {
        assertEquals(Grade.HIGH, aggregator.aggregate(2, 2));
        assertEquals(Grade.MIDDLE, aggregator.aggregate(1, 2));
        assertEquals(Grade.LOW, aggregator.aggregate(0, 2));
    }
}
