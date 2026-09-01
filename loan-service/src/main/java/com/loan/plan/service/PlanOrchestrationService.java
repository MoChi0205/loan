package com.loan.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.engine.rule.StepConditionEvaluator;
import com.loan.exception.BusinessException;
import com.loan.plan.entity.AdmissionExecutionPlan;
import com.loan.plan.entity.AdmissionPlanModule;
import com.loan.plan.entity.AdmissionPlanStep;
import com.loan.plan.entity.StrategyTemplate;
import com.loan.plan.entity.StrategyTemplateModule;
import com.loan.plan.entity.StrategyTemplateStep;
import com.loan.plan.mapper.AdmissionExecutionPlanMapper;
import com.loan.plan.mapper.AdmissionPlanModuleMapper;
import com.loan.plan.mapper.AdmissionPlanStepMapper;
import com.loan.plan.mapper.StrategyTemplateMapper;
import com.loan.plan.mapper.StrategyTemplateModuleMapper;
import com.loan.plan.mapper.StrategyTemplateStepMapper;
import com.loan.rule.entity.Rule;
import com.loan.rule.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计划编排服务：计划 + 模块 + 步骤 的 CRUD 与详情组装（对齐 mds Step4 PlanWorkflowEditor）。
 *
 * <p>计划结构：计划 → 模块（按 sort）→ 步骤（按 stepSort，单条规则）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class PlanOrchestrationService {

    private final AdmissionExecutionPlanMapper planMapper;
    private final AdmissionPlanModuleMapper moduleMapper;
    private final AdmissionPlanStepMapper stepMapper;
    private final RuleMapper ruleMapper;
    private final StrategyTemplateMapper templateMapper;
    private final StrategyTemplateModuleMapper templateModuleMapper;
    private final StrategyTemplateStepMapper templateStepMapper;

    /**
     * 计划详情：计划 + 模块 + 步骤（含规则编码/名称），按 sort/stepSort 升序。
     *
     * @param planId 计划 ID
     * @return { plan, modules: [{...module, steps: [{...step, ruleCode, ruleName}]}] }
     */
    public Map<String, Object> detail(Long planId) {
        AdmissionExecutionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "计划不存在");
        }
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>()
                        .eq(AdmissionPlanModule::getPlanId, planId)
                        .orderByAsc(AdmissionPlanModule::getSort));
        List<Long> moduleIds = modules.stream().map(AdmissionPlanModule::getId).collect(Collectors.toList());
        Map<Long, List<AdmissionPlanStep>> moduleSteps = new LinkedHashMap<>();
        List<Long> ruleIds = new ArrayList<>();
        if (!moduleIds.isEmpty()) {
            List<AdmissionPlanStep> steps = stepMapper.selectList(
                    new LambdaQueryWrapper<AdmissionPlanStep>()
                            .in(AdmissionPlanStep::getModuleId, moduleIds)
                            .orderByAsc(AdmissionPlanStep::getStepSort));
            for (AdmissionPlanStep step : steps) {
                moduleSteps.computeIfAbsent(step.getModuleId(), k -> new ArrayList<>()).add(step);
                ruleIds.add(step.getRuleId());
            }
        }
        Map<Long, Rule> ruleMap = ruleIds.isEmpty() ? Collections.emptyMap()
                : ruleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(Rule::getId, Function.identity()));

        List<Map<String, Object>> moduleVOs = new ArrayList<>();
        for (AdmissionPlanModule module : modules) {
            Map<String, Object> mvo = new LinkedHashMap<>();
            mvo.put("id", module.getId());
            mvo.put("moduleCode", module.getModuleCode());
            mvo.put("moduleName", module.getModuleName());
            mvo.put("logicType", module.getLogicType());
            mvo.put("isGlobalPre", module.getIsGlobalPre());
            mvo.put("sort", module.getSort());
            mvo.put("joinWithNextModule", module.getJoinWithNextModule());
            List<Map<String, Object>> stepVOs = new ArrayList<>();
            for (AdmissionPlanStep step : moduleSteps.getOrDefault(module.getId(), Collections.emptyList())) {
                Rule rule = ruleMap.get(step.getRuleId());
                Map<String, Object> svo = new LinkedHashMap<>();
                svo.put("id", step.getId());
                svo.put("ruleId", step.getRuleId());
                svo.put("ruleCode", rule != null ? rule.getRuleCode() : null);
                svo.put("ruleName", rule != null ? rule.getRuleName() : null);
                svo.put("stepSort", step.getStepSort());
                svo.put("joinWithNext", step.getJoinWithNext());
                svo.put("isDryRun", step.getIsDryRun());
                svo.put("stepConfigJson", step.getStepConfigJson());
                svo.put("conditionField", step.getConditionField());
                svo.put("conditionOperator", step.getConditionOperator());
                svo.put("conditionValue", step.getConditionValue());
                stepVOs.add(svo);
            }
            mvo.put("steps", stepVOs);
            moduleVOs.add(mvo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan);
        result.put("modules", moduleVOs);
        return result;
    }

    /**
     * 新建计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPlan(AdmissionExecutionPlan plan, String operator) {
        plan.setId(null);
        if (plan.getVersion() == null) {
            plan.setVersion(1);
        }
        if (plan.getStatus() == null) {
            plan.setStatus("0");
        }
        plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return plan.getId();
    }

    /**
     * 更新计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(AdmissionExecutionPlan plan, String operator) {
        plan.setUpdatedBy(operator);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    /**
     * 删除计划（级联删除模块/步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId) {
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>().eq(AdmissionPlanModule::getPlanId, planId));
        for (AdmissionPlanModule module : modules) {
            stepMapper.delete(new LambdaQueryWrapper<AdmissionPlanStep>()
                    .eq(AdmissionPlanStep::getModuleId, module.getId()));
        }
        moduleMapper.delete(new LambdaQueryWrapper<AdmissionPlanModule>()
                .eq(AdmissionPlanModule::getPlanId, planId));
        planMapper.deleteById(planId);
    }

    /**
     * 新建模块。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createModule(AdmissionPlanModule module, String operator) {
        module.setId(null);
        if (module.getSort() == null) {
            module.setSort(0);
        }
        if (module.getLogicType() == null) {
            module.setLogicType("AND");
        }
        if (module.getJoinWithNextModule() == null) {
            module.setJoinWithNextModule("AND");
        }
        module.setCreatedBy(operator);
        module.setCreatedAt(LocalDateTime.now());
        moduleMapper.insert(module);
        return module.getId();
    }

    /**
     * 更新模块。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(AdmissionPlanModule module, String operator) {
        moduleMapper.updateById(module);
    }

    /**
     * 删除模块（级联删除步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(Long moduleId) {
        stepMapper.delete(new LambdaQueryWrapper<AdmissionPlanStep>()
                .eq(AdmissionPlanStep::getModuleId, moduleId));
        moduleMapper.deleteById(moduleId);
    }

    /**
     * 新建步骤（单条规则）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createStep(AdmissionPlanStep step, String operator) {
        step.setId(null);
        if (step.getStepSort() == null) {
            step.setStepSort(0);
        }
        if (step.getJoinWithNext() == null) {
            step.setJoinWithNext("AND");
        }
        if (step.getIsDryRun() == null) {
            step.setIsDryRun(0);
        }
        // 步骤无显式版本时按规则当前版本解析（t_admission_plan_step.rule_version_id 非空无默认值）
        if (step.getRuleVersionId() == null) {
            step.setRuleVersionId(resolveRuleVersionId(step.getRuleId()));
        }
        validateStepCondition(step);
        step.setCreatedBy(operator);
        step.setCreatedAt(LocalDateTime.now());
        stepMapper.insert(step);
        return step.getId();
    }

    /**
     * 更新步骤。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(AdmissionPlanStep step, String operator) {
        validateStepCondition(step);
        stepMapper.updateById(step);
    }

    /** 步骤前置条件校验：运算符/字段须在白名单内；配置运算符时须同时配置字段与值（对齐 mds v2）。 */
    private void validateStepCondition(AdmissionPlanStep step) {
        if (step.getConditionOperator() == null) {
            return;
        }
        String operator = StepConditionEvaluator.normalizeOperator(step.getConditionOperator());
        if (!StepConditionEvaluator.isKnownOperator(step.getConditionOperator())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "步骤条件运算符不合法: " + step.getConditionOperator()
                    + "，支持: " + StepConditionEvaluator.knownOperatorText());
        }
        if (step.getConditionField() == null || step.getConditionField().trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置条件运算符时必须指定条件字段 conditionField");
        }
        if (!StepConditionEvaluator.isKnownField(step.getConditionField())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "步骤条件字段不在事实白名单内: " + step.getConditionField()
                    + "，支持: " + StepConditionEvaluator.knownFieldText());
        }
        if (StepConditionEvaluator.needsValue(step.getConditionOperator())
                && (step.getConditionValue() == null || step.getConditionValue().trim().isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "运算符 " + step.getConditionOperator() + " 必须配置条件值 conditionValue");
        }
        step.setConditionOperator(operator);
    }

    /**
     * 删除步骤。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(Long stepId) {
        stepMapper.deleteById(stepId);
    }

    /**
     * 策略模版应用为执行计划（对齐 mds v2 apply-template）。
     *
     * <p>把已上线策略模版（模块 + 步骤）实例化为草稿态执行计划，供策略绑定。
     *
     * @param templateId 策略模版 ID
     * @param planCode   新计划编码（可选，缺省用模版编码 + 时间戳）
     * @param planName   新计划名称（可选，缺省用模版名）
     * @param operator   操作人
     * @return 新计划 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applyTemplate(Long templateId, String planCode, String planName, String operator) {
        StrategyTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "策略模版不存在");
        }
        AdmissionExecutionPlan plan = new AdmissionExecutionPlan();
        plan.setPlanCode(StringUtils.hasText(planCode) ? planCode
                : template.getTemplateCode() + "_" + System.currentTimeMillis());
        plan.setPlanName(StringUtils.hasText(planName) ? planName : template.getTemplateName());
        plan.setCustomerGroup(template.getCustomerGroup());
        plan.setVersion(1);
        plan.setStatus("0");
        plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        List<StrategyTemplateModule> modules = templateModuleMapper.selectList(
                new LambdaQueryWrapper<StrategyTemplateModule>()
                        .eq(StrategyTemplateModule::getTemplateId, templateId)
                        .orderByAsc(StrategyTemplateModule::getSort));
        for (StrategyTemplateModule module : modules) {
            AdmissionPlanModule newModule = new AdmissionPlanModule();
            newModule.setPlanId(plan.getId());
            newModule.setModuleCode(module.getModuleCode());
            newModule.setModuleName(module.getModuleName());
            newModule.setLogicType(module.getLogicType());
            newModule.setIsGlobalPre(0);
            newModule.setSort(module.getSort());
            newModule.setJoinWithNextModule(module.getJoinWithNextModule());
            newModule.setCreatedBy(operator);
            newModule.setCreatedAt(LocalDateTime.now());
            moduleMapper.insert(newModule);

            List<StrategyTemplateStep> steps = templateStepMapper.selectList(
                    new LambdaQueryWrapper<StrategyTemplateStep>()
                            .eq(StrategyTemplateStep::getTemplateModuleId, module.getId())
                            .orderByAsc(StrategyTemplateStep::getStepSort));
            for (StrategyTemplateStep step : steps) {
                AdmissionPlanStep newStep = new AdmissionPlanStep();
                newStep.setModuleId(newModule.getId());
                newStep.setRuleId(step.getRuleId());
                newStep.setRuleVersionId(resolveRuleVersionId(step.getRuleId()));
                newStep.setStepSort(step.getStepSort());
                newStep.setJoinWithNext(step.getJoinWithNext());
                newStep.setIsDryRun(step.getIsDryRun());
                newStep.setStepConfigJson(step.getStepConfigJson());
                newStep.setConditionField(step.getConditionField());
                newStep.setConditionOperator(step.getConditionOperator());
                newStep.setConditionValue(step.getConditionValue());
                newStep.setCreatedBy(operator);
                newStep.setCreatedAt(LocalDateTime.now());
                stepMapper.insert(newStep);
            }
        }
        return plan.getId();
    }

    /**
     * 解析规则当前生效版本 id（t_rule.current_version → t_rule_version.id），
     * 供「模版步骤 → 计划步骤」实例化使用（模版步骤无版本概念，只存 ruleId）。
     * 版本记录缺失时回退 ruleId（兼容现有单版本数据形态）。
     *
     * @param ruleId 规则 ID
     * @return 版本记录 id 或回退的规则 id
     */
    private Long resolveRuleVersionId(Long ruleId) {
        if (ruleId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版步骤缺少规则 ruleId");
        }
        Rule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版引用的规则不存在: " + ruleId);
        }
        Long versionId = ruleMapper.selectCurrentVersionId(ruleId);
        return versionId != null ? versionId : ruleId;
    }

    /**
     * 复制执行计划（对齐 mds v2 copy）：计划 + 模块 + 步骤 深拷贝为新草稿计划。
     *
     * @param planId   源计划 ID
     * @param operator 操作人
     * @return 新计划 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long copyPlan(Long planId, String operator) {
        AdmissionExecutionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "计划不存在");
        }
        AdmissionExecutionPlan newPlan = new AdmissionExecutionPlan();
        newPlan.setPlanCode(plan.getPlanCode() + "_copy_" + System.currentTimeMillis());
        newPlan.setPlanName(plan.getPlanName() + "(副本)");
        newPlan.setCustomerGroup(plan.getCustomerGroup());
        newPlan.setVersion(1);
        newPlan.setStatus("0");
        newPlan.setCreatedBy(operator);
        newPlan.setUpdatedBy(operator);
        newPlan.setCreatedAt(LocalDateTime.now());
        newPlan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(newPlan);

        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>()
                        .eq(AdmissionPlanModule::getPlanId, planId)
                        .orderByAsc(AdmissionPlanModule::getSort));
        for (AdmissionPlanModule module : modules) {
            AdmissionPlanModule newModule = new AdmissionPlanModule();
            newModule.setPlanId(newPlan.getId());
            newModule.setModuleCode(module.getModuleCode());
            newModule.setModuleName(module.getModuleName());
            newModule.setLogicType(module.getLogicType());
            newModule.setIsGlobalPre(module.getIsGlobalPre());
            newModule.setSort(module.getSort());
            newModule.setJoinWithNextModule(module.getJoinWithNextModule());
            newModule.setCreatedBy(operator);
            newModule.setCreatedAt(LocalDateTime.now());
            moduleMapper.insert(newModule);

            List<AdmissionPlanStep> steps = stepMapper.selectList(
                    new LambdaQueryWrapper<AdmissionPlanStep>()
                            .eq(AdmissionPlanStep::getModuleId, module.getId())
                            .orderByAsc(AdmissionPlanStep::getStepSort));
            for (AdmissionPlanStep step : steps) {
                AdmissionPlanStep newStep = new AdmissionPlanStep();
                newStep.setModuleId(newModule.getId());
                newStep.setRuleId(step.getRuleId());
                newStep.setRuleVersionId(step.getRuleVersionId());
                newStep.setStepSort(step.getStepSort());
                newStep.setJoinWithNext(step.getJoinWithNext());
                newStep.setIsDryRun(step.getIsDryRun());
                newStep.setStepConfigJson(step.getStepConfigJson());
                newStep.setConditionField(step.getConditionField());
                newStep.setConditionOperator(step.getConditionOperator());
                newStep.setConditionValue(step.getConditionValue());
                newStep.setCreatedBy(operator);
                newStep.setCreatedAt(LocalDateTime.now());
                stepMapper.insert(newStep);
            }
        }
        return newPlan.getId();
    }
}
