package com.loan.engine.rule;

import com.loan.engine.execute.AdmissionContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 步骤前置条件求值器（对齐 mds v2 RuleConditionEvaluator）。
 *
 * <p>与 {@code com.loan.engine.evaluate.RuleConditionEvaluator}（规则表达式求值，5 参）区分：
 * 本类承载「步骤级前置条件」（conditionField/Operator/Value），判定本步是否执行。
 * 条件仅在 {@code conditionOperator} 非空时生效：{@code conditionValue} 对多数规则是业务参数
 * （天数、机构列表、阈值等），此时不应按条件语义解释，因此「配置了运算符」是启用条件判定的唯一开关。
 * 字段取值来自 {@link AdmissionContext#getFact(String)}（t_client_business_fact 事实仓）；
 * 未知字段或运算符抛 {@link IllegalArgumentException}，由调用方按「配置错误」处理
 * （保存时拒绝、运行时告警并跳过本步），绝不静默通过。
 */
public final class StepConditionEvaluator {

    /** 等于 */
    public static final String OP_EQ = "EQ";
    /** 不等于 */
    public static final String OP_NE = "NE";
    /** 字段值 ∈ 逗号/分号分隔列表 */
    public static final String OP_IN = "IN";
    /** 字段值 ∉ 逗号/分号分隔列表 */
    public static final String OP_NOT_IN = "NOT_IN";
    /** 字段值为空/空白 */
    public static final String OP_IS_BLANK = "IS_BLANK";
    /** 字段值非空 */
    public static final String OP_IS_NOT_BLANK = "IS_NOT_BLANK";

    private static final Set<String> OPERATORS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            OP_EQ, OP_NE, OP_IN, OP_NOT_IN, OP_IS_BLANK, OP_IS_NOT_BLANK)));

    /** 运算符别名：= / != / EMPTY / NOT_EMPTY。 */
    private static final Map<String, String> OPERATOR_ALIASES = new HashMap<>();

    static {
        OPERATOR_ALIASES.put("=", OP_EQ);
        OPERATOR_ALIASES.put("==", OP_EQ);
        OPERATOR_ALIASES.put("!=", OP_NE);
        OPERATOR_ALIASES.put("EMPTY", OP_IS_BLANK);
        OPERATOR_ALIASES.put("NOT_EMPTY", OP_IS_NOT_BLANK);
    }

    /**
     * 事实字段白名单（对齐 mds v2 RuleConditionEvaluator#FIELDS）。
     * <p>
     * 与 mds 不同，loan-main 的事实来自 {@code t_client_business_fact}（DB 驱动、开放式），
     * 故运行期 {@link #evaluate} 对未知字段保持宽容（取不到即视为空白，与 mds「未知字段抛错」语义不同）；
     * 但保存期须用本白名单拦截非法字段（「配置错误、保存拒绝」，对齐 mds）。
     */
    private static final Set<String> FACT_FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "channelCode", "customerGroup", "strategyCode", "submissionId", "clientProfileId",
            "blacklist", "dishonest", "fraud", "lawsuit")));

    private StepConditionEvaluator() {
    }

    /** 条件仅在运算符非空时生效（conditionValue 对多数规则是业务参数，不按条件解释）。 */
    public static boolean hasCondition(RuleStepConfig config) {
        return config != null && StringUtils.isNotBlank(config.getConditionOperator());
    }

    /**
     * 求值步骤条件；未配置条件时恒 true。
     *
     * @throws IllegalArgumentException 运算符或字段不在白名单内（配置错误，调用方按错误处理）
     */
    public static boolean evaluate(RuleStepConfig config, AdmissionContext context) {
        if (!hasCondition(config)) {
            return true;
        }
        String operator = normalizeOperator(config.getConditionOperator());
        String field = StringUtils.trimToNull(config.getConditionField());
        if (field == null) {
            throw new IllegalArgumentException("配置条件运算符时必须指定条件字段 conditionField");
        }
        Object fact = context != null ? context.getFact(field) : null;
        String actual = fact == null ? "" : StringUtils.trimToEmpty(String.valueOf(fact));
        switch (operator) {
            case OP_EQ:
                return actual.equalsIgnoreCase(StringUtils.trimToEmpty(config.getConditionValue()));
            case OP_NE:
                return !actual.equalsIgnoreCase(StringUtils.trimToEmpty(config.getConditionValue()));
            case OP_IN:
                return containsValue(config.getConditionValue(), actual);
            case OP_NOT_IN:
                return !containsValue(config.getConditionValue(), actual);
            case OP_IS_BLANK:
                return StringUtils.isBlank(actual);
            case OP_IS_NOT_BLANK:
                return StringUtils.isNotBlank(actual);
            default:
                throw new IllegalArgumentException("未知条件运算符: " + config.getConditionOperator()
                        + "，支持: " + knownOperatorText());
        }
    }

    /** 运算符是否属于白名单（含别名归一化）。 */
    public static boolean isKnownOperator(String operator) {
        return operator != null && OPERATORS.contains(normalizeOperator(operator));
    }

    /** 字段是否属于事实白名单（保存期校验用）。 */
    public static boolean isKnownField(String field) {
        return field != null && FACT_FIELDS.contains(field.trim());
    }

    /** 该运算符是否需要 conditionValue（IS_BLANK / IS_NOT_BLANK 不需要）。 */
    public static boolean needsValue(String operator) {
        String op = normalizeOperator(operator);
        return !OP_IS_BLANK.equals(op) && !OP_IS_NOT_BLANK.equals(op);
    }

    /** 白名单字段文本（用于保存校验报错提示）。 */
    public static String knownFieldText() {
        return String.join(", ", FACT_FIELDS);
    }

    /** 运算符是否属于白名单（含别名归一化）。 */
    public static String normalizeOperator(String operator) {
        String trimmed = StringUtils.trimToNull(operator);
        if (trimmed == null) {
            return null;
        }
        String upper = trimmed.toUpperCase();
        String alias = OPERATOR_ALIASES.get(upper);
        return alias != null ? alias : upper;
    }

    private static boolean containsValue(String valueList, String actual) {
        if (StringUtils.isBlank(valueList)) {
            return false;
        }
        for (String token : valueList.split("[,;，；]")) {
            if (StringUtils.trimToEmpty(token).equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    /** 白名单运算符文本（用于保存校验报错提示）。 */
    public static String knownOperatorText() {
        return String.join("/", OPERATORS);
    }
}
