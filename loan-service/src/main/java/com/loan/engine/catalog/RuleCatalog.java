package com.loan.engine.catalog;

import com.loan.engine.enums.CustomerGroup;
import lombok.Getter;

/**
 * 原子规则目录（第 4/7 章定稿：个人 14 条 + 企业 16 条，四分类树形管理）。
 *
 * <p>ruleCode 稳定不变；displayName / description 面向规则目录与编排界面。
 * 规则分两类执行方式：
 * <ul>
 *   <li><b>通用条件规则</b>（valueType ∈ STRING/NUMBER/LIST）：走 {@code RuleConditionEvaluator}
 *       按 {@code field_code + operator + value} 表达式后台可配（t_rule 表）；</li>
 *   <li><b>行为/风控专用规则</b>（valueType = API）：由专用 Handler 实现（黑名单/失信/欺诈等），
 *       命中直接 REJECT。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Getter
public enum RuleCatalog {

    // ==================== 全局前置风控（RISK，四分类之一，命中直接 REJECT） ====================

    /** 本地黑名单（手机号/身份证/企业统一信用代码/法人多维命中，t_blacklist） */
    BLACKLIST_REJECT("blacklist_reject", "黑名单·拒绝", "blacklist", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "命中本地黑名单（手机号/信用代码/法人）直接拒绝"),

    /** 失信名单（外部核验） */
    DISHONEST_REJECT("dishonest_reject", "失信名单·拒绝", "dishonest", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "命中失信被执行人名单直接拒绝"),

    /** 欺诈核验（外部核验） */
    FRAUD_REJECT("fraud_reject", "欺诈核验·拒绝", "fraud", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "欺诈风险核验命中直接拒绝"),

    /** 区域限制 */
    REGION_ALLOW("region_allow", "区域限制", "region", RuleValueType.LIST,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "企业注册地须在允许区域列表内（in）"),

    /** 敏感行业禁入 */
    INDUSTRY_FORBID("industry_forbid", "敏感行业禁入", "industry", RuleValueType.LIST,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "行业不在敏感禁入列表内（not_in）"),

    /** 司法诉讼（涉诉） */
    LAWSUIT_REJECT("lawsuit_reject", "司法诉讼·拒绝", "lawsuit", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.ENTERPRISE, "存在未结司法诉讼/被执行记录直接拒绝"),

    // ==================== 经营能力（OPERATION，企业） ====================

    /** 成立年限 */
    ESTABLISH_YEARS_MIN("establish_years_min", "成立年限", "establish_years", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "成立年限下限（>= N 年）"),

    /** 年纳税额 */
    ANNUAL_TAX_MIN("annual_tax_min", "年纳税额", "annual_tax", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "年纳税额下限（>= N 元）"),

    /** 年开票额 */
    ANNUAL_INVOICE_MIN("annual_invoice_min", "年开票额", "annual_invoice", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "年开票额下限（>= N 元）"),

    /** 纳税等级 */
    TAX_GRADE_IN("tax_grade_in", "纳税等级", "tax_grade", RuleValueType.LIST,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "纳税信用等级（in A/B/C/M）"),

    /** 资产负债率 */
    DEBT_RATIO_MAX("debt_ratio_max", "资产负债率", "debt_ratio", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "资产负债率上限（<= N%）"),

    /** 开票连续性 */
    INVOICE_CONTINUITY_MIN("invoice_continuity_min", "开票连续性", "invoice_continuity", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "连续开票月数下限（>= N 月）"),

    /** 纳税连续性 */
    TAX_CONTINUITY_MIN("tax_continuity_min", "纳税连续性", "tax_continuity", RuleValueType.NUMBER,
            RuleCategory.OPERATION, CustomerGroup.ENTERPRISE, "连续纳税月数下限（>= N 月）"),

    // ==================== 资质准入（QUALIFICATION，企业） ====================

    /** 经营状态 */
    BIZ_STATUS_IN("biz_status_in", "经营状态", "biz_status", RuleValueType.LIST,
            RuleCategory.QUALIFICATION, CustomerGroup.ENTERPRISE, "经营状态（in 存续/在营）"),

    /** 注册资本 */
    REGISTERED_CAPITAL_MIN("registered_capital_min", "注册资本", "registered_capital", RuleValueType.NUMBER,
            RuleCategory.QUALIFICATION, CustomerGroup.ENTERPRISE, "注册资本下限（>= N 元）"),

    /** 社保人数 */
    SOCIAL_COUNT_MIN("social_count_min", "社保人数", "social_security_count", RuleValueType.NUMBER,
            RuleCategory.QUALIFICATION, CustomerGroup.ENTERPRISE, "社保缴纳人数下限（>= N 人）"),

    // ==================== 个人基础（PERSONAL，个人客群骨架占位） ====================

    /** 个人黑名单 */
    P_BLACKLIST_REJECT("p_blacklist_reject", "个人黑名单·拒绝", "blacklist", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.PERSONAL, "个人命中黑名单直接拒绝"),

    /** 个人失信 */
    P_DISHONEST_REJECT("p_dishonest_reject", "个人失信·拒绝", "dishonest", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.PERSONAL, "个人命中失信名单直接拒绝"),

    /** 个人欺诈 */
    P_FRAUD_REJECT("p_fraud_reject", "个人欺诈·拒绝", "fraud", RuleValueType.API,
            RuleCategory.RISK, CustomerGroup.PERSONAL, "个人欺诈核验命中直接拒绝"),

    /** 年龄 */
    AGE_BETWEEN("age_between", "年龄", "age", RuleValueType.LIST,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "年龄区间（between 下限,上限）"),

    /** 月收入 */
    INCOME_MIN("income_min", "月收入", "monthly_income", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "月收入下限（>= N 元）"),

    /** 社保月数 */
    SOCIAL_MONTHS_MIN("social_months_min", "社保月数", "social_security_months", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "连续缴纳社保月数下限（>= N 月）"),

    /** 公积金月数 */
    FUND_MONTHS_MIN("fund_months_min", "公积金月数", "fund_months", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "连续缴纳公积金月数下限（>= N 月）"),

    /** 房产 */
    HOUSE_FLAG_EQ("house_flag_eq", "房产", "house_flag", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "是否有房产（== 1 有 / 0 无）"),

    /** 车辆 */
    CAR_FLAG_EQ("car_flag_eq", "车辆", "car_flag", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "是否有车辆（== 1 有 / 0 无）"),

    /** 个人负债率 */
    P_DEBT_RATIO_MAX("p_debt_ratio_max", "个人负债率", "debt_ratio", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "个人负债率上限（<= N%）"),

    /** 征信状态 */
    CREDIT_STATUS_IN("credit_status_in", "征信状态", "credit_status", RuleValueType.LIST,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "征信状态（in 正常/无逾期）"),

    /** 工作年限 */
    WORK_YEARS_MIN("work_years_min", "工作年限", "work_years", RuleValueType.NUMBER,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "当前单位工作年限下限（>= N 年）"),

    /** 城市 */
    CITY_IN("city_in", "城市", "city", RuleValueType.LIST,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "城市（in 允许城市列表）"),

    /** 学历 */
    EDUCATION_IN("education_in", "学历", "education", RuleValueType.LIST,
            RuleCategory.PERSONAL, CustomerGroup.PERSONAL, "学历（in 大专/本科及以上）");

    /** 规则编码（稳定，后台编排引用） */
    private final String ruleCode;

    /** 展示名称（面向规则目录与编排界面） */
    private final String displayName;

    /** 关联字段编码（通用条件规则：从客户事实取数；专用规则：预留） */
    private final String fieldCode;

    /** 值类型（NUMBER/LIST/API：API 走专用 Handler） */
    private final RuleValueType valueType;

    /** 规则分类（四分类） */
    private final RuleCategory category;

    /** 适用客群 */
    private final CustomerGroup customerGroup;

    /** 规则说明 */
    private final String description;

    RuleCatalog(String ruleCode, String displayName, String fieldCode, RuleValueType valueType,
                RuleCategory category, CustomerGroup customerGroup, String description) {
        this.ruleCode = ruleCode;
        this.displayName = displayName;
        this.fieldCode = fieldCode;
        this.valueType = valueType;
        this.category = category;
        this.customerGroup = customerGroup;
        this.description = description;
    }

    /**
     * 按编码解析（未命中返回 null）。
     *
     * @param ruleCode 规则编码
     * @return 规则目录项，未命中为 null
     */
    public static RuleCatalog fromCode(String ruleCode) {
        if (ruleCode == null) {
            return null;
        }
        for (RuleCatalog catalog : values()) {
            if (catalog.ruleCode.equals(ruleCode)) {
                return catalog;
            }
        }
        return null;
    }

    /**
     * 规则值类型（第 7 章定稿）。
     *
     * @author loan-platform
     */
    @Getter
    public enum RuleValueType {

        /** 数值比较（>= / <= / == / != 等） */
        NUMBER("NUMBER", "数值"),

        /** 多值列表（in / not_in / between） */
        LIST("LIST", "多值列表"),

        /** 接口型（专用 Handler：黑名单/失信/欺诈等行为风控） */
        API("API", "接口型"),

        /** 无需配置 */
        NONE("NONE", "无需配置");

        /** 类型编码 */
        private final String code;

        /** 类型名称 */
        private final String name;

        RuleValueType(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    /**
     * 规则分类（四分类树形管理）。
     *
     * @author loan-platform
     */
    @Getter
    public enum RuleCategory {

        /** 基础风控（全局前置，命中直接 REJECT） */
        RISK("RISK", "基础风控"),

        /** 经营能力 */
        OPERATION("OPERATION", "经营能力"),

        /** 资质准入 */
        QUALIFICATION("QUALIFICATION", "资质准入"),

        /** 个人基础 */
        PERSONAL("PERSONAL", "个人基础");

        /** 分类编码 */
        private final String code;

        /** 分类名称 */
        private final String name;

        RuleCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
