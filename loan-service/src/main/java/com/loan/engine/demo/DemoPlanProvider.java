package com.loan.engine.demo;

import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.execute.AdmissionPlan;
import com.loan.engine.execute.PlanModule;
import com.loan.engine.rule.RuleStepConfig;
import org.springframework.stereotype.Component;

/**
 * 示例准入计划提供器（调试中心影子执行用，阶段一内存构造）。
 *
 * <p>预置 1 个「武汉企业税贷」准入计划，结构与初始化数据（t_admission_execution_plan →
 * module → step → rule）一致；正式匹配时由计划加载服务从 DB 组装，本类仅作调试空跑与演示。
 *
 * <p>计划结构：
 * <pre>
 * 全局前置风控模块（AND，命中直接 REJECT）
 *   ├ blacklist_reject（专用 Handler：黑名单）
 *   ├ industry_forbid（not_in 敏感行业）
 *   └ region_allow（in 允许区域）
 * 经营能力模块（AND）
 *   ├ establish_years_min（成立年限 ≥2）
 *   ├ annual_tax_min（年纳税额 ≥3 万）
 *   ├ annual_invoice_min（年开票额 ≥50 万）
 *   ├ tax_grade_in（纳税等级 in A/B）
 *   └ debt_ratio_max（负债率 ≤70%）
 * 资质准入模块（AND）
 *   ├ biz_status_in（经营状态 in 存续/在营）
 *   └ registered_capital_min（注册资本 ≥50 万）
 * </pre>
 *
 * @author loan-platform
 */
@Component
public class DemoPlanProvider {

    /**
     * 构造示例企业税贷准入计划。
     *
     * @return 准入计划
     */
    public AdmissionPlan buildEnterpriseTaxPlan() {
        AdmissionPlan plan = new AdmissionPlan(1001L, "DEMO_WUHAN_TAX", "武汉企业税贷·示例计划", CustomerGroup.ENTERPRISE);

        // 模块 1：全局前置风控（AND，命中直接 REJECT）
        PlanModule riskModule = new PlanModule(2001L, "GLOBAL_RISK", "全局前置风控", "AND", true, 1, "AND");
        riskModule.addStep(step(3001L, riskModule, RuleCatalog.BLACKLIST_REJECT, "blacklist", "==", "0", "NUMBER"));
        riskModule.addStep(step(3002L, riskModule, RuleCatalog.INDUSTRY_FORBID, "industry", "not_in", "房地产,娱乐,两高一剩", "LIST"));
        riskModule.addStep(step(3003L, riskModule, RuleCatalog.REGION_ALLOW, "region", "in", "武汉,湖北", "LIST"));
        plan.addModule(riskModule);

        // 模块 2：经营能力（AND）
        PlanModule operationModule = new PlanModule(2002L, "OPERATION", "经营能力", "AND", false, 2, "AND");
        operationModule.addStep(step(3004L, operationModule, RuleCatalog.ESTABLISH_YEARS_MIN, "establish_years", ">=", "2", "NUMBER"));
        operationModule.addStep(step(3005L, operationModule, RuleCatalog.ANNUAL_TAX_MIN, "annual_tax", ">=", "30000", "NUMBER"));
        operationModule.addStep(step(3006L, operationModule, RuleCatalog.ANNUAL_INVOICE_MIN, "annual_invoice", ">=", "500000", "NUMBER"));
        operationModule.addStep(step(3007L, operationModule, RuleCatalog.TAX_GRADE_IN, "tax_grade", "in", "A,B", "LIST"));
        operationModule.addStep(step(3008L, operationModule, RuleCatalog.DEBT_RATIO_MAX, "debt_ratio", "<=", "70", "NUMBER"));
        plan.addModule(operationModule);

        // 模块 3：资质准入（AND）
        PlanModule qualificationModule = new PlanModule(2003L, "QUALIFICATION", "资质准入", "AND", false, 3, "AND");
        qualificationModule.addStep(step(3009L, qualificationModule, RuleCatalog.BIZ_STATUS_IN, "biz_status", "in", "存续,在营", "LIST"));
        qualificationModule.addStep(step(3010L, qualificationModule, RuleCatalog.REGISTERED_CAPITAL_MIN, "registered_capital", ">=", "500000", "NUMBER"));
        plan.addModule(qualificationModule);

        return plan;
    }

    /**
     * 构造单步配置。
     *
     * @param stepId    步骤 ID
     * @param module    所属模块
     * @param catalog   规则目录项
     * @param fieldCode 字段编码
     * @param operator  运算符
     * @param valueText 规则值
     * @param valueType 值类型
     * @return 步骤配置
     */
    private RuleStepConfig step(Long stepId, PlanModule module, RuleCatalog catalog,
                                String fieldCode, String operator, String valueText, String valueType) {
        return RuleStepConfig.builder()
                .planId(1001L)
                .moduleId(module.getModuleId())
                .moduleCode(module.getModuleCode())
                .moduleName(module.getModuleName())
                .logicType(module.getLogicType())
                .globalPre(module.isGlobalPre())
                .stepId(stepId)
                .ruleCode(catalog.getRuleCode())
                .fieldCode(fieldCode)
                .fieldName(catalog.getDisplayName())
                .operator(operator)
                .valueType(valueType)
                .valueText(valueText)
                .stepSort(stepId.intValue())
                .build();
    }
}
