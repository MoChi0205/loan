package com.loan.engine.evaluate;

import com.loan.engine.execute.AdmissionContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 条件表达式求值器（第 15 章定稿：{@code {field_code} {operator} {value}}）。
 *
 * <p>支持运算符：== / != / &gt; / &lt; / &gt;= / &lt;= / in / not_in / contains / not_contains /
 * between / is_null / not_null。从 {@link AdmissionContext} 取事实值，按值类型转换后比较。
 *
 * @author loan-platform
 */
@Component
public class RuleConditionEvaluator {

    /**
     * 求值：判断客户事实是否命中条件表达式。
     *
     * @param context   执行上下文（取事实值）
     * @param fieldCode 字段编码
     * @param operator  运算符
     * @param valueText 规则值（原始文本）
     * @param valueType 值类型（STRING/NUMBER/DATE/LIST）
     * @return true 命中（规则通过），false 未命中
     */
    public boolean evaluate(AdmissionContext context, String fieldCode, String operator,
                            String valueText, String valueType) {
        Object actual = context.getFact(fieldCode);
        String op = operator == null ? "" : operator.trim();
        switch (op) {
            case "is_null":
                return actual == null || "".equals(String.valueOf(actual).trim());
            case "not_null":
                return actual != null && !"".equals(String.valueOf(actual).trim());
            case "==":
                return compareEqual(actual, valueText, valueType);
            case "!=":
                return !compareEqual(actual, valueText, valueType);
            case ">":
                return compareNumber(actual, valueText) > 0;
            case "<":
                return compareNumber(actual, valueText) < 0;
            case ">=":
                return compareNumber(actual, valueText) >= 0;
            case "<=":
                return compareNumber(actual, valueText) <= 0;
            case "in":
                return inList(actual, valueText);
            case "not_in":
                return !inList(actual, valueText);
            case "contains":
                return contains(actual, valueText, true);
            case "not_contains":
                return !contains(actual, valueText, true);
            case "between":
                return between(actual, valueText);
            default:
                return false;
        }
    }

    /**
     * 等值比较（数值按 BigDecimal，其余按字符串）。
     *
     * @param actual    实际值
     * @param expected  期望值
     * @param valueType 值类型
     * @return true 相等
     */
    private boolean compareEqual(Object actual, String expected, String valueType) {
        if (actual == null) {
            return false;
        }
        if ("NUMBER".equalsIgnoreCase(valueType)) {
            return compareNumber(actual, expected) == 0;
        }
        return String.valueOf(actual).trim().equals(expected == null ? "" : expected.trim());
    }

    /**
     * 数值比较（实际值 - 期望值），非法数值返回负无穷（视为不命中）。
     *
     * @param actual   实际值
     * @param expected 期望值
     * @return 差值符号（正/零/负）
     */
    private int compareNumber(Object actual, String expected) {
        if (actual == null || expected == null) {
            return Integer.MIN_VALUE;
        }
        try {
            BigDecimal a = new BigDecimal(String.valueOf(actual).trim());
            BigDecimal e = new BigDecimal(expected.trim());
            return a.compareTo(e);
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * in 列表判断（逗号分隔，含空格容忍）。
     *
     * @param actual  实际值
     * @param listStr 逗号分隔列表
     * @return true 在列表内
     */
    private boolean inList(Object actual, String listStr) {
        if (actual == null || listStr == null) {
            return false;
        }
        Set<String> set = new HashSet<>(Arrays.asList(listStr.split(",")));
        return set.contains(String.valueOf(actual).trim());
    }

    /**
     * 字符串包含判断。
     *
     * @param actual 实际值
     * @param sub    子串
     * @param ignoreCase 是否忽略大小写（预留）
     * @return true 包含
     */
    private boolean contains(Object actual, String sub, boolean ignoreCase) {
        if (actual == null || sub == null) {
            return false;
        }
        String a = String.valueOf(actual);
        if (ignoreCase) {
            return a.toLowerCase().contains(sub.trim().toLowerCase());
        }
        return a.contains(sub.trim());
    }

    /**
     * 区间判断（valueText 形如 "min,max"，闭区间）。
     *
     * @param actual   实际值
     * @param rangeStr 区间 "min,max"
     * @return true 在闭区间内
     */
    private boolean between(Object actual, String rangeStr) {
        if (actual == null || rangeStr == null) {
            return false;
        }
        String[] parts = rangeStr.split(",");
        if (parts.length < 2) {
            return false;
        }
        try {
            BigDecimal a = new BigDecimal(String.valueOf(actual).trim());
            BigDecimal min = new BigDecimal(parts[0].trim());
            BigDecimal max = new BigDecimal(parts[1].trim());
            return a.compareTo(min) >= 0 && a.compareTo(max) <= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
