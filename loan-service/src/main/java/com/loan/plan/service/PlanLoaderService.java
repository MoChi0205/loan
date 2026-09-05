package com.loan.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.engine.catalog.RuleCatalog;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.execute.AdmissionPlan;
import com.loan.engine.execute.PlanModule;
import com.loan.engine.execute.ProductPlan;
import com.loan.engine.rule.RuleStepConfig;
import com.loan.plan.entity.AdmissionExecutionPlan;
import com.loan.plan.entity.AdmissionPlanModule;
import com.loan.plan.entity.AdmissionPlanStep;
import com.loan.plan.entity.ProductStrategy;
import com.loan.plan.mapper.AdmissionExecutionPlanMapper;
import com.loan.plan.mapper.AdmissionPlanModuleMapper;
import com.loan.plan.mapper.AdmissionPlanStepMapper;
import com.loan.plan.mapper.ProductStrategyMapper;
import com.loan.product.entity.BankProduct;
import com.loan.product.entity.BankProductCity;
import com.loan.product.mapper.BankProductMapper;
import com.loan.product.mapper.BankProductCityMapper;
import com.loan.rule.entity.Rule;
import com.loan.rule.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 准入计划加载服务：从 DB（t_product_strategy → t_admission_execution_plan → module → step → rule）
 * 组装计划，供规则引擎执行「渠道 × 产品 → 策略 → 计划」真实主链路。
 *
 * <p>对齐 mds v2 渠道→策略→计划：按渠道编码 + 客群加载策略，策略 1:1 绑定计划。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class PlanLoaderService {

    private final AdmissionExecutionPlanMapper planMapper;
    private final AdmissionPlanModuleMapper moduleMapper;
    private final AdmissionPlanStepMapper stepMapper;
    private final ProductStrategyMapper strategyMapper;
    private final RuleMapper ruleMapper;
    private final BankProductMapper bankProductMapper;
    private final BankProductCityMapper bankProductCityMapper;

    /**
     * 按渠道编码 + 客群 + 申请城市加载产品-计划绑定（策略 1:1 绑计划）。
     *
     * @param channelCode 渠道编码（合作渠道）
     * @param group       客群
     * @param applyCity   申请城市（市一级，非空时按产品服务城市精确筛选）
     * @return 产品-计划绑定列表
     */
    public List<ProductPlan> loadProductPlans(String channelCode, CustomerGroup group, String applyCity) {
        // 1. 查激活的策略（渠道编码为空则查全渠道）
        List<ProductStrategy> strategies = strategyMapper.selectList(
                new LambdaQueryWrapper<ProductStrategy>()
                        .eq(channelCode != null && !channelCode.isEmpty(),
                                ProductStrategy::getChannelCode, channelCode)
                        .eq(ProductStrategy::getStatus, "ACTIVE"));
        if (strategies.isEmpty()) {
            return Collections.emptyList();
        }
        // 客群过滤：策略客群为 null/COMMON（通用）放行；否则须与请求客群一致
        strategies = strategies.stream().filter(s -> {
            CustomerGroup sg = CustomerGroup.fromCode(s.getCustomerGroup());
            return group == null || sg == null || sg.equals(group);
        }).collect(Collectors.toList());
        if (strategies.isEmpty()) {
            return Collections.emptyList();
        }

        // 申请城市筛选：精确匹配产品服务城市（市一级）
        Set<String> cityProductCodes = null;
        if (StringUtils.hasText(applyCity)) {
            cityProductCodes = new HashSet<>(bankProductCityMapper.selectList(
                    new LambdaQueryWrapper<BankProductCity>().eq(BankProductCity::getCity, applyCity))
                    .stream().map(BankProductCity::getProductCode).collect(Collectors.toSet()));
            if (cityProductCodes.isEmpty()) {
                return Collections.emptyList();
            }
        }

        // 2. 批量查产品 + 计划（业务 key 字符串关联）
        List<String> productCodes = strategies.stream().map(ProductStrategy::getBankProductCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        List<String> planCodes = strategies.stream().map(ProductStrategy::getExecutionPlanCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, BankProduct> productMap = productCodes.isEmpty() ? Collections.emptyMap()
                : bankProductMapper.selectList(new LambdaQueryWrapper<BankProduct>()
                        .in(BankProduct::getProductCode, productCodes)).stream()
                        .collect(Collectors.toMap(BankProduct::getProductCode, Function.identity()));
        Map<String, AdmissionPlan> planMap = planCodes.isEmpty() ? Collections.emptyMap()
                : loadPlansByCodes(planCodes);

        // 3. 组装产品-计划绑定（含申请城市筛选）
        List<ProductPlan> result = new ArrayList<>();
        for (ProductStrategy strategy : strategies) {
            BankProduct product = productMap.get(strategy.getBankProductCode());
            AdmissionPlan plan = planMap.get(strategy.getExecutionPlanCode());
            if (product == null || plan == null) {
                continue;
            }
            if (cityProductCodes != null && !cityProductCodes.contains(product.getProductCode())) {
                continue;
            }
            result.add(new ProductPlan(product.getId(), product.getProductCode(),
                    product.getProductName(), plan));
        }
        return result;
    }

    /**
     * 按计划编码列表批量组装计划（含模块 + 步骤 + 规则）。
     *
     * @param planCodes 计划编码列表
     * @return planCode → AdmissionPlan
     */
    private Map<String, AdmissionPlan> loadPlansByCodes(List<String> planCodes) {
        List<AdmissionExecutionPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<AdmissionExecutionPlan>().in(AdmissionExecutionPlan::getPlanCode, planCodes));
        Map<Long, AdmissionExecutionPlan> planById = plans.stream().collect(
                Collectors.toMap(AdmissionExecutionPlan::getId, Function.identity()));
        List<Long> planIds = new ArrayList<>(planById.keySet());
        Map<Long, List<AdmissionPlanModule>> planModules = plans.stream().collect(Collectors.toMap(
                AdmissionExecutionPlan::getId, p -> new ArrayList<>()));

        // 查模块（按 plan_id + sort）
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>()
                        .in(AdmissionPlanModule::getPlanId, planIds)
                        .orderByAsc(AdmissionPlanModule::getSort));
        List<Long> moduleIds = new ArrayList<>();
        for (AdmissionPlanModule module : modules) {
            planModules.get(module.getPlanId()).add(module);
            moduleIds.add(module.getId());
        }

        // 查步骤（按 module_id + step_sort），收集 rule_id
        List<AdmissionPlanStep> steps = moduleIds.isEmpty() ? Collections.emptyList()
                : stepMapper.selectList(new LambdaQueryWrapper<AdmissionPlanStep>()
                        .in(AdmissionPlanStep::getModuleId, moduleIds)
                        .orderByAsc(AdmissionPlanStep::getStepSort));
        Map<Long, List<AdmissionPlanStep>> moduleSteps = new java.util.HashMap<>();
        List<Long> ruleIds = new ArrayList<>();
        for (AdmissionPlanStep step : steps) {
            moduleSteps.computeIfAbsent(step.getModuleId(), k -> new ArrayList<>()).add(step);
            ruleIds.add(step.getRuleId());
        }

        // 批量查规则
        Map<Long, Rule> ruleMap = ruleIds.isEmpty() ? Collections.emptyMap()
                : ruleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(Rule::getId, Function.identity()));

        // 组装
        Map<String, AdmissionPlan> result = new java.util.LinkedHashMap<>();
        for (AdmissionExecutionPlan plan : plans) {
            AdmissionPlan ap = new AdmissionPlan(plan.getId(), plan.getPlanCode(), plan.getPlanName(),
                    CustomerGroup.fromCode(plan.getCustomerGroup()));
            for (AdmissionPlanModule module : planModules.get(plan.getId())) {
                PlanModule pm = new PlanModule(module.getId(), module.getModuleCode(), module.getModuleName(),
                        module.getLogicType(), module.getIsGlobalPre() != null && module.getIsGlobalPre() == 1,
                        module.getSort(), module.getJoinWithNextModule());
                for (AdmissionPlanStep step : moduleSteps.getOrDefault(module.getId(), Collections.emptyList())) {
                    Rule rule = ruleMap.get(step.getRuleId());
                    if (rule == null) {
                        continue;
                    }
                    pm.addStep(buildStep(plan.getId(), module, step, rule));
                }
                ap.addModule(pm);
            }
            result.put(plan.getPlanCode(), ap);
        }
        return result;
    }

    /**
     * 组装单步配置（规则元信息来自 t_rule，展示名优先取目录）。
     *
     * @param planId 计划 ID
     * @param module 模块
     * @param step   步骤
     * @param rule   规则
     * @return 步骤配置
     */
    private RuleStepConfig buildStep(Long planId, AdmissionPlanModule module, AdmissionPlanStep step, Rule rule) {
        RuleCatalog catalog = RuleCatalog.fromCode(rule.getRuleCode());
        String fieldName = rule.getFieldName();
        if (fieldName == null && catalog != null) {
            fieldName = catalog.getDisplayName();
        }
        return RuleStepConfig.builder()
                .planId(planId)
                .moduleId(module.getId())
                .moduleCode(module.getModuleCode())
                .moduleName(module.getModuleName())
                .logicType(module.getLogicType())
                .globalPre(module.getIsGlobalPre() != null && module.getIsGlobalPre() == 1)
                .stepId(step.getId())
                .ruleCode(rule.getRuleCode())
                .fieldCode(rule.getFieldCode())
                .fieldName(fieldName)
                .operator(rule.getOperator())
                .valueType(rule.getValueType())
                .valueText(rule.getValueText())
                .stepSort(step.getStepSort())
                .joinWithNext(step.getJoinWithNext())
                .isDryRun(step.getIsDryRun())
                .stepConfigJson(step.getStepConfigJson())
                .conditionField(step.getConditionField())
                .conditionOperator(step.getConditionOperator())
                .conditionValue(step.getConditionValue())
                .build();
    }
}
