package com.loan.engine.evaluate;

import com.loan.engine.execute.AdmissionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件表达式求值器单测（M3 L2 规则引擎结果态）：运算符覆盖 is_null / not_null / == / != /
 * &gt; / &lt; / &gt;= / &lt;= / in / not_in / contains / not_contains / between，含非法值容错。
 */
class RuleConditionEvaluatorTest {

    private RuleConditionEvaluator evaluator;
    private AdmissionContext ctx;

    @BeforeEach
    void setUp() {
        evaluator = new RuleConditionEvaluator();
        java.util.Map<String, Object> facts = new java.util.HashMap<>();
        facts.put("age", 30);
        facts.put("name", "Tom");
        facts.put("city", "Beijing");
        facts.put("empty", "");
        ctx = AdmissionContext.builder().fieldValues(facts).build();
    }

    @Test
    @DisplayName("is_null / not_null")
    void nullChecks() {
        assertTrue(evaluator.evaluate(ctx, "missing", "is_null", null, null));
        assertFalse(evaluator.evaluate(ctx, "name", "is_null", null, null));
        assertTrue(evaluator.evaluate(ctx, "empty", "is_null", null, null)); // 空串视为空
        assertFalse(evaluator.evaluate(ctx, "missing", "not_null", null, null));
        assertTrue(evaluator.evaluate(ctx, "name", "not_null", null, null));
    }

    @Test
    @DisplayName("== 与 !=")
    void equality() {
        assertTrue(evaluator.evaluate(ctx, "age", "==", "30", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "==", "31", "NUMBER"));
        assertTrue(evaluator.evaluate(ctx, "name", "==", "Tom", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "name", "==", "Jerry", "STRING"));
        assertTrue(evaluator.evaluate(ctx, "age", "!=", "31", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "!=", "30", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "missing", "==", "x", "STRING")); // null 实际值
    }

    @Test
    @DisplayName("> < >= <=")
    void comparisons() {
        assertTrue(evaluator.evaluate(ctx, "age", ">", "20", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", ">", "40", "NUMBER"));
        assertTrue(evaluator.evaluate(ctx, "age", "<", "40", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "<", "10", "NUMBER"));
        assertTrue(evaluator.evaluate(ctx, "age", ">=", "30", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", ">=", "31", "NUMBER"));
        assertTrue(evaluator.evaluate(ctx, "age", "<=", "30", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "<=", "29", "NUMBER"));
    }

    @Test
    @DisplayName("非数值比较 → 不命中（容错）")
    void nonNumericCompare() {
        assertFalse(evaluator.evaluate(ctx, "name", ">", "x", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", ">", "abc", "NUMBER"));
    }

    @Test
    @DisplayName("in / not_in")
    void inList() {
        assertTrue(evaluator.evaluate(ctx, "city", "in", "Shanghai,Beijing", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "city", "in", "Shanghai,Tianjin", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "city", "not_in", "Shanghai,Beijing", "STRING"));
        assertTrue(evaluator.evaluate(ctx, "city", "not_in", "Shanghai,Tianjin", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "missing", "in", "a,b", "STRING")); // null 实际值
    }

    @Test
    @DisplayName("contains / not_contains（忽略大小写）")
    void contains() {
        assertTrue(evaluator.evaluate(ctx, "city", "contains", "eiji", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "city", "contains", "xyz", "STRING"));
        assertFalse(evaluator.evaluate(ctx, "city", "not_contains", "eiji", "STRING"));
        assertTrue(evaluator.evaluate(ctx, "city", "not_contains", "xyz", "STRING"));
    }

    @Test
    @DisplayName("between（闭区间）")
    void between() {
        assertTrue(evaluator.evaluate(ctx, "age", "between", "1,100", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "between", "40,50", "NUMBER"));
        assertFalse(evaluator.evaluate(ctx, "age", "between", "bad", "NUMBER")); // 非法区间
        assertFalse(evaluator.evaluate(ctx, "missing", "between", "1,100", "NUMBER"));
    }

    @Test
    @DisplayName("未知运算符 → 不命中")
    void unknownOperator() {
        assertFalse(evaluator.evaluate(ctx, "age", "regex", ".*", "NUMBER"));
    }
}
