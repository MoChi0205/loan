package com.loan.engine.rule.handler;

import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.rule.AdmissionRuleHandler;
import com.loan.engine.rule.RuleHandlerResult;
import com.loan.engine.rule.RuleStepConfig;
import org.springframework.stereotype.Component;

/**
 * 司法诉讼专用规则处理器（全局前置风控，命中直接 REJECT）。
 *
 * <p>阶段一影子执行：从 {@link AdmissionContext} 事实读 {@code lawsuit} 字段（0/1）。
 * 正式匹配接入司法诉讼/被执行查询 API，此处保留扩展点。
 *
 * @author loan-platform
 */
@Component
public class LawsuitRuleHandler implements AdmissionRuleHandler {

    @Override
    public String ruleCode() {
        return RuleCatalog.LAWSUIT_REJECT.getRuleCode();
    }

    @Override
    public RuleHandlerResult handle(AdmissionContext context, RuleStepConfig config) {
        Object v = context.getFact("lawsuit");
        if (v != null && ("1".equals(String.valueOf(v)) || "true".equalsIgnoreCase(String.valueOf(v)))) {
            return RuleHandlerResult.fail("存在未结诉讼/被执行记录");
        }
        return RuleHandlerResult.pass("{\"lawsuit\":false}");
    }
}
