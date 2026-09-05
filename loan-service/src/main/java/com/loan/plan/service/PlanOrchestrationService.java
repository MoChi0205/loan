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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

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

    /** FR-03 连接符合法值：仅 AND / OR（大小写不敏感，入库归一为大写）。 */
    private static final Set<String> VALID_JOIN =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("AND", "OR")));

    /**
     * 归一化连接符：trim + 大写；非法值（非 AND / OR）拒绝；null 视为 AND（默认）。
     * 用于模块 / 步骤保存时保证存储值仅含 AND / OR，规避执行器误读。
     *
     * @param raw       原始连接符
     * @param fieldName 字段名（用于错误提示）
     * @return 归一后的 AND / OR
     */
    private String normalizeJoin(String raw, String fieldName) {
        if (raw == null) {
            return "AND";
        }
        String v = raw.trim().toUpperCase();
        if (!VALID_JOIN.contains(v)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    fieldName + " 非法连接符: " + raw + "，仅支持 AND / OR");
        }
        return v;
    }

    /**
     * 计划详情：计划 + 模块 + 步骤（含规则编码/名称），按 sort/stepSort 升序。
     *
     * @param planId 计划 ID
     * @return { plan, modules: [{...module, steps: [{...step, ruleCode, ruleName}]}] }
     */
    public Map<String, Object> detail(String planCode) {
        AdmissionExecutionPlan plan = findByCode(planCode);
        Long planId = plan.getId();
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
            mvo.put("moduleBizCode", module.getModuleBizCode());
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
                svo.put("stepCode", step.getStepCode());
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
        plan.setId(null);
        result.put("plan", plan);
        result.put("modules", moduleVOs);
        return result;
    }

    private AdmissionExecutionPlan findByCode(String planCode) {
        if (!StringUtils.hasText(planCode)) throw new BusinessException(ResultCode.PARAM_ERROR, "计划编码必填");
        AdmissionExecutionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                .eq(AdmissionExecutionPlan::getPlanCode, planCode));
        if (plan == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "计划不存在");
        return plan;
    }

    /**
     * 新建计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createPlan(AdmissionExecutionPlan plan, String operator) {
        plan.setId(null);
        if (plan.getVersion() == null) {
            plan.setVersion(1);
        }
        if (plan.getStatus() == null) {
            plan.setStatus("0");
        }
        if (!StringUtils.hasText(plan.getPlanCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "计划编码必填");
        }
        plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        return plan.getPlanCode();
    }

    /**
     * 更新计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(String planCode, AdmissionExecutionPlan plan, String operator) {
        AdmissionExecutionPlan existing = findByCode(planCode);
        plan.setId(existing.getId());
        plan.setPlanCode(null);
        plan.setUpdatedBy(operator);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    /**
     * 删除计划（级联删除模块/步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(String planCode) {
        Long planId = findByCode(planCode).getId();
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
    public String createModule(AdmissionPlanModule module, String operator) {
        if (module.getPlanId() == null && StringUtils.hasText(module.getPlanCode())) {
            module.setPlanId(findByCode(module.getPlanCode()).getId());
        }
        if (module.getPlanId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "计划编码必填");
        module.setId(null);
        module.setModuleBizCode(StringUtils.hasText(module.getModuleBizCode()) ? module.getModuleBizCode() : "module_" + UUID.randomUUID().toString().replace("-", ""));
        if (module.getSort() == null) {
            module.setSort(0);
        }
        if (module.getLogicType() == null) {
            module.setLogicType("AND");
        }
        if (module.getJoinWithNextModule() == null) {
            module.setJoinWithNextModule("AND");
        }
        module.setJoinWithNextModule(normalizeJoin(module.getJoinWithNextModule(), "joinWithNextModule"));
        module.setCreatedBy(operator);
        module.setCreatedAt(LocalDateTime.now());
        moduleMapper.insert(module);
        return module.getModuleBizCode();
    }

    /**
     * 更新模块。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(String moduleBizCode, AdmissionPlanModule module, String operator) {
        AdmissionPlanModule current = moduleMapper.selectOne(new LambdaQueryWrapper<AdmissionPlanModule>()
                .eq(AdmissionPlanModule::getModuleBizCode, moduleBizCode));
        if (current == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模块不存在");
        module.setId(current.getId());
        module.setModuleBizCode(null);
        module.setPlanId(null);
        if (module.getJoinWithNextModule() != null) {
            module.setJoinWithNextModule(normalizeJoin(module.getJoinWithNextModule(), "joinWithNextModule"));
        }
        moduleMapper.updateById(module);
    }

    /**
     * 删除模块（级联删除步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(String moduleBizCode) {
        AdmissionPlanModule current = moduleMapper.selectOne(new LambdaQueryWrapper<AdmissionPlanModule>()
                .eq(AdmissionPlanModule::getModuleBizCode, moduleBizCode));
        if (current == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模块不存在");
        Long moduleId = current.getId();
        stepMapper.delete(new LambdaQueryWrapper<AdmissionPlanStep>()
                .eq(AdmissionPlanStep::getModuleId, moduleId));
        moduleMapper.deleteById(moduleId);
    }

    /**
     * 新建步骤（单条规则）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createStep(AdmissionPlanStep step, String operator) {
        if (step.getModuleId() == null && StringUtils.hasText(step.getModuleBizCode())) {
            AdmissionPlanModule module = moduleMapper.selectOne(new LambdaQueryWrapper<AdmissionPlanModule>()
                    .eq(AdmissionPlanModule::getModuleBizCode, step.getModuleBizCode()));
            if (module != null) step.setModuleId(module.getId());
        }
        if (step.getModuleId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "父模块编码必填");
        step.setId(null);
        step.setStepCode(StringUtils.hasText(step.getStepCode()) ? step.getStepCode() : "step_" + UUID.randomUUID().toString().replace("-", ""));
        if (step.getStepSort() == null) {
            step.setStepSort(0);
        }
        if (step.getJoinWithNext() == null) {
            step.setJoinWithNext("AND");
        }
        if (step.getIsDryRun() == null) {
            step.setIsDryRun(0);
        }
        step.setJoinWithNext(normalizeJoin(step.getJoinWithNext(), "joinWithNext"));
        // 步骤无显式版本时按规则当前版本解析（t_admission_plan_step.rule_version_id 非空无默认值）
        if (step.getRuleVersionId() == null) {
            step.setRuleVersionId(resolveRuleVersionId(step.getRuleId()));
        }
        validateStepCondition(step);
        step.setCreatedBy(operator);
        step.setCreatedAt(LocalDateTime.now());
        stepMapper.insert(step);
        return step.getStepCode();
    }

    /**
     * 更新步骤。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(String stepCode, AdmissionPlanStep step, String operator) {
        AdmissionPlanStep current = stepMapper.selectOne(new LambdaQueryWrapper<AdmissionPlanStep>()
                .eq(AdmissionPlanStep::getStepCode, stepCode));
        if (current == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "步骤不存在");
        step.setId(current.getId());
        step.setStepCode(null);
        step.setModuleId(null);
        if (step.getJoinWithNext() != null) {
            step.setJoinWithNext(normalizeJoin(step.getJoinWithNext(), "joinWithNext"));
        }
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
     * FR-03 连接符结构校验（需求 §3.1 / §3.2）：在「执行激活」前拦截非法聚合配置。
     *
     * <p>校验项：
     * <ul>
     *   <li>模块 sort 唯一；步骤 stepSort 唯一（同模块内）；</li>
     *   <li>末位模块 {@code joinWithNextModule} 不可为 OR（悬空连接符无意义）；</li>
     *   <li>模块级禁止连续 OR（OR 仅相邻二元组，禁止 ≥3 模块 OR 组）；</li>
     *   <li>末位步骤 {@code joinWithNext} 不可为 OR（悬空，禁止）；</li>
     *   <li>步骤级连续 OR 允许（如 A OR B OR C 合法，等价于单 OR 组）。</li>
     * </ul>
     *
     * @param planId 计划 ID
     * @return 问题列表（空 = 通过）
     */
    public List<String> validatePlanStructure(Long planId) {
        List<String> issues = new ArrayList<>();
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>()
                        .eq(AdmissionPlanModule::getPlanId, planId)
                        .orderByAsc(AdmissionPlanModule::getSort));
        Set<Integer> seenSort = new HashSet<>();
        for (int i = 0; i < modules.size(); i++) {
            AdmissionPlanModule m = modules.get(i);
            if (m.getSort() != null && !seenSort.add(m.getSort())) {
                issues.add("模块 sort 重复: " + m.getSort());
            }
            boolean isLastModule = (i == modules.size() - 1);
            // 末位模块悬空 OR
            if (isLastModule && "OR".equalsIgnoreCase(m.getJoinWithNextModule())) {
                issues.add("末位模块「" + m.getModuleName() + "」joinWithNextModule 不可为 OR（悬空连接符）");
            }
            // 模块级连续 OR（≥3 模块 OR 组，违反 FR-02 二元组约束）
            if (!isLastModule && "OR".equalsIgnoreCase(m.getJoinWithNextModule())) {
                AdmissionPlanModule next = modules.get(i + 1);
                if ("OR".equalsIgnoreCase(next.getJoinWithNextModule())) {
                    issues.add("模块「" + m.getModuleName() + "」与「" + next.getModuleName()
                            + "」连续 OR：OR 仅支持相邻二元组，禁止 ≥3 模块 OR 组");
                }
            }
            // 步骤级校验
            List<AdmissionPlanStep> steps = stepMapper.selectList(
                    new LambdaQueryWrapper<AdmissionPlanStep>()
                            .eq(AdmissionPlanStep::getModuleId, m.getId())
                            .orderByAsc(AdmissionPlanStep::getStepSort));
            Set<Integer> seenStepSort = new HashSet<>();
            for (AdmissionPlanStep s : steps) {
                if (s.getStepSort() != null && !seenStepSort.add(s.getStepSort())) {
                    issues.add("模块「" + m.getModuleName() + "」步骤 stepSort 重复: " + s.getStepSort());
                }
            }
            for (int j = 0; j < steps.size(); j++) {
                AdmissionPlanStep s = steps.get(j);
                boolean isLastStep = (j == steps.size() - 1);
                // 末位步骤悬空 OR（步骤级连续 OR 允许，但末位悬空禁止）
                if (isLastStep && "OR".equalsIgnoreCase(s.getJoinWithNext())) {
                    issues.add("模块「" + m.getModuleName() + "」末位步骤 joinWithNext 不可为 OR（悬空连接符）");
                }
            }
        }
        return issues;
    }

    /**
     * 删除步骤。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(String stepCode) {
        AdmissionPlanStep current = stepMapper.selectOne(new LambdaQueryWrapper<AdmissionPlanStep>()
                .eq(AdmissionPlanStep::getStepCode, stepCode));
        if (current == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "步骤不存在");
        stepMapper.deleteById(current.getId());
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
    public String applyTemplate(String templateCode, String planCode, String planName, String operator) {
        StrategyTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<StrategyTemplate>()
                .eq(StrategyTemplate::getTemplateCode, templateCode));
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
                        .eq(StrategyTemplateModule::getTemplateId, template.getId())
                        .orderByAsc(StrategyTemplateModule::getSort));
        for (StrategyTemplateModule module : modules) {
            AdmissionPlanModule newModule = new AdmissionPlanModule();
            newModule.setPlanId(plan.getId());
            newModule.setModuleBizCode("module_" + UUID.randomUUID().toString().replace("-", ""));
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
                newStep.setStepCode("step_" + UUID.randomUUID().toString().replace("-", ""));
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
        return plan.getPlanCode();
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
    public String copyPlan(String planCode, String operator) {
        AdmissionExecutionPlan plan = findByCode(planCode);
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
                        .eq(AdmissionPlanModule::getPlanId, plan.getId())
                        .orderByAsc(AdmissionPlanModule::getSort));
        for (AdmissionPlanModule module : modules) {
            AdmissionPlanModule newModule = new AdmissionPlanModule();
            newModule.setPlanId(newPlan.getId());
            newModule.setModuleBizCode("module_" + UUID.randomUUID().toString().replace("-", ""));
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
                newStep.setStepCode("step_" + UUID.randomUUID().toString().replace("-", ""));
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
        return newPlan.getPlanCode();
    }
}
