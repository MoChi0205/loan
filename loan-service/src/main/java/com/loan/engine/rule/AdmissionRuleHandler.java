package com.loan.engine.rule;

import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.execute.AdmissionContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 准入规则处理器（第 7 章定稿：数值/枚举通用 Handler + 行为风控专用 Handler）。
 *
 * <p>处理器分两类：
 * <ul>
 *   <li><b>通用条件规则</b>：由 {@link com.loan.engine.evaluate.RuleConditionEvaluator} 统一执行
 *       {@code field_code + operator + value} 表达式，不逐一注册 Handler；</li>
 *   <li><b>行为/风控专用规则</b>：实现本接口并声明 {@link #ruleCode()}，命中直接 REJECT。</li>
 * </ul>
 *
 * @author loan-platform
 */
public interface AdmissionRuleHandler {

    /**
     * 声明处理器对应的规则编码（专用规则才需要实现；通用条件规则由求值器统一处理）。
     *
     * @return 规则编码
     */
    String ruleCode();

    /**
     * 单号处理。
     *
     * @param context 执行上下文（含客户事实）
     * @param config  步骤配置
     * @return 处理结果
     */
    RuleHandlerResult handle(AdmissionContext context, RuleStepConfig config);

    /**
     * 批量处理（批量优先，默认按号循环 {@link #handle}）。
     *
     * <p>真批实现应覆盖本方法，取数/比较/回写一律批量；默认实现兜底循环。
     *
     * @param contexts   单号上下文列表
     * @param config     步骤配置
     * @return contextKey → 处理结果（contextKey 取 {@link AdmissionContext#contextKey()}）
     */
    default Map<String, RuleHandlerResult> handleBatch(List<AdmissionContext> contexts, RuleStepConfig config) {
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RuleHandlerResult> out = new java.util.HashMap<>(contexts.size());
        for (AdmissionContext context : contexts) {
            if (context == null) {
                continue;
            }
            out.put(context.contextKey(), handle(context, config));
        }
        return out;
    }
}
