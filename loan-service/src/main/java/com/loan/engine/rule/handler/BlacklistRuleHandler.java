package com.loan.engine.rule.handler;

import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.rule.AdmissionRuleHandler;
import com.loan.engine.rule.RuleHandlerResult;
import com.loan.engine.rule.RuleStepConfig;
import org.springframework.stereotype.Component;

/**
 * 黑名单专用规则处理器（全局前置风控，命中直接 REJECT）。
 *
 * <p>阶段一影子执行：从 {@link AdmissionContext} 事实读 {@code blacklist} 字段（0/1）。
 * 正式匹配接入 {@code t_blacklist} 多维命中查询（手机号/身份证/信用代码/法人），此处保留扩展点。
 *
 * @author loan-platform
 */
@Component
public class BlacklistRuleHandler implements AdmissionRuleHandler {

    @Override
    public String ruleCode() {
        return RuleCatalog.BLACKLIST_REJECT.getRuleCode();
    }

    @Override
    public RuleHandlerResult handle(AdmissionContext context, RuleStepConfig config) {
        Object black = context.getFact("blacklist");
        if (black != null && ("1".equals(String.valueOf(black)) || "true".equalsIgnoreCase(String.valueOf(black)))) {
            return RuleHandlerResult.fail("命中本地黑名单");
        }
        return RuleHandlerResult.pass("{\"blacklist\":false}");
    }
}
