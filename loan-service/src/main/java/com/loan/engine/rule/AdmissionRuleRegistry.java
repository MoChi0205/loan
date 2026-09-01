package com.loan.engine.rule;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则处理器注册表（参考 mds AdmissionRuleRegistry）。
 *
 * <p>Spring 注入全部 {@link AdmissionRuleHandler} 实现，按 {@code ruleCode} 建立索引；
 * 行为/风控专用规则经本注册表定位 Handler，通用条件规则由 {@code RuleConditionEvaluator} 统一处理。
 *
 * @author loan-platform
 */
@Component
public class AdmissionRuleRegistry {

    /** 注入全部规则处理器实现 */
    @Resource
    private List<AdmissionRuleHandler> handlers;

    /** ruleCode → Handler 索引 */
    private final Map<String, AdmissionRuleHandler> handlerMap = new HashMap<>();

    /**
     * 初始化：构建 ruleCode → Handler 索引。
     */
    @PostConstruct
    public void init() {
        if (handlers != null) {
            for (AdmissionRuleHandler handler : handlers) {
                handlerMap.put(handler.ruleCode(), handler);
            }
        }
    }

    /**
     * 按规则编码获取处理器。
     *
     * @param ruleCode 规则编码
     * @return 处理器，未注册返回 null
     */
    public AdmissionRuleHandler getHandler(String ruleCode) {
        return handlerMap.get(ruleCode);
    }
}
