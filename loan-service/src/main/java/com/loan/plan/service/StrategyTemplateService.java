package com.loan.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.plan.entity.AdmissionExecutionPlan;
import com.loan.plan.entity.AdmissionPlanModule;
import com.loan.plan.entity.AdmissionPlanStep;
import com.loan.plan.entity.ProductStrategy;
import com.loan.plan.entity.StrategyTemplate;
import com.loan.plan.entity.StrategyTemplateModule;
import com.loan.plan.entity.StrategyTemplateStep;
import com.loan.plan.mapper.AdmissionExecutionPlanMapper;
import com.loan.plan.mapper.AdmissionPlanModuleMapper;
import com.loan.plan.mapper.AdmissionPlanStepMapper;
import com.loan.plan.mapper.ProductStrategyMapper;
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
 * 策略模版服务：模版 CRUD + 模块/步骤编排 + 上线/下线（对齐 mds 功能策略模版）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class StrategyTemplateService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final StrategyTemplateMapper templateMapper;
    private final StrategyTemplateModuleMapper moduleMapper;
    private final StrategyTemplateStepMapper stepMapper;
    private final RuleMapper ruleMapper;
    private final AdmissionExecutionPlanMapper planMapper;
    private final AdmissionPlanModuleMapper planModuleMapper;
    private final AdmissionPlanStepMapper planStepMapper;
    private final ProductStrategyMapper strategyMapper;

    /**
     * 分页查询模版。
     */
    public PageResult<StrategyTemplate> page(String customerGroup, String keyword, int page, int size) {
        LambdaQueryWrapper<StrategyTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(StrategyTemplate::getCustomerGroup, customerGroup);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(StrategyTemplate::getTemplateCode, keyword)
                    .or().like(StrategyTemplate::getTemplateName, keyword));
        }
        wrapper.orderByDesc(StrategyTemplate::getId);
        Page<StrategyTemplate> result = templateMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 新建模版（草稿）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(StrategyTemplate template, String operator) {
        if (!StringUtils.hasText(template.getTemplateCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版编码必填");
        }
        template.setId(null);
        template.setStatus(STATUS_DISABLED);
        template.setCreatedBy(operator);
        template.setUpdatedBy(operator);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        return template.getId();
    }

    /**
     * 编辑模版。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(StrategyTemplate template, String operator) {
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 删除模版（级联模块/步骤）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        List<StrategyTemplateModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<StrategyTemplateModule>().eq(StrategyTemplateModule::getTemplateId, id));
        for (StrategyTemplateModule module : modules) {
            stepMapper.delete(new LambdaQueryWrapper<StrategyTemplateStep>()
                    .eq(StrategyTemplateStep::getTemplateModuleId, module.getId()));
        }
        moduleMapper.delete(new LambdaQueryWrapper<StrategyTemplateModule>()
                .eq(StrategyTemplateModule::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    /**
     * 上线（发布供渠道导入）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, String operator) {
        StrategyTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模版不存在");
        }
        template.setStatus(STATUS_ACTIVE);
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 下线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, String operator) {
        StrategyTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模版不存在");
        }
        template.setStatus(STATUS_DISABLED);
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 模版详情（模版 + 模块 + 步骤，含规则信息）。
     */
    public Map<String, Object> detail(Long id) {
        StrategyTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模版不存在");
        }
        List<StrategyTemplateModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<StrategyTemplateModule>()
                        .eq(StrategyTemplateModule::getTemplateId, id)
                        .orderByAsc(StrategyTemplateModule::getSort));
        List<Long> moduleIds = modules.stream().map(StrategyTemplateModule::getId).collect(Collectors.toList());
        Map<Long, List<StrategyTemplateStep>> moduleSteps = new LinkedHashMap<>();
        List<Long> ruleIds = new ArrayList<>();
        if (!moduleIds.isEmpty()) {
            for (StrategyTemplateStep step : stepMapper.selectList(
                    new LambdaQueryWrapper<StrategyTemplateStep>()
                            .in(StrategyTemplateStep::getTemplateModuleId, moduleIds)
                            .orderByAsc(StrategyTemplateStep::getStepSort))) {
                moduleSteps.computeIfAbsent(step.getTemplateModuleId(), k -> new ArrayList<>()).add(step);
                if (step.getRuleId() != null) {
                    ruleIds.add(step.getRuleId());
                }
            }
        }
        Map<Long, Rule> ruleMap = ruleIds.isEmpty() ? Collections.emptyMap()
                : ruleMapper.selectBatchIds(ruleIds).stream().collect(Collectors.toMap(Rule::getId, Function.identity()));

        List<Map<String, Object>> moduleVOs = new ArrayList<>();
        for (StrategyTemplateModule module : modules) {
            Map<String, Object> mvo = new LinkedHashMap<>();
            mvo.put("id", module.getId());
            mvo.put("moduleCode", module.getModuleCode());
            mvo.put("moduleName", module.getModuleName());
            mvo.put("logicType", module.getLogicType());
            mvo.put("sort", module.getSort());
            mvo.put("joinWithNextModule", module.getJoinWithNextModule());
            List<Map<String, Object>> stepVOs = new ArrayList<>();
            for (StrategyTemplateStep step : moduleSteps.getOrDefault(module.getId(), Collections.emptyList())) {
                Rule rule = step.getRuleId() == null ? null : ruleMap.get(step.getRuleId());
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
        result.put("template", template);
        result.put("modules", moduleVOs);
        return result;
    }

    /** 新建模块 */
    @Transactional(rollbackFor = Exception.class)
    public Long createModule(StrategyTemplateModule module) {
        module.setId(null);
        if (module.getSort() == null) {
            module.setSort(0);
        }
        if (module.getLogicType() == null) {
            module.setLogicType("AND");
        }
        module.setCreatedAt(LocalDateTime.now());
        moduleMapper.insert(module);
        return module.getId();
    }

    /** 更新模块 */
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(StrategyTemplateModule module) {
        moduleMapper.updateById(module);
    }

    /** 删除模块（级联步骤） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(Long moduleId) {
        stepMapper.delete(new LambdaQueryWrapper<StrategyTemplateStep>()
                .eq(StrategyTemplateStep::getTemplateModuleId, moduleId));
        moduleMapper.deleteById(moduleId);
    }

    /** 新建步骤 */
    @Transactional(rollbackFor = Exception.class)
    public Long createStep(StrategyTemplateStep step) {
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
        step.setCreatedAt(LocalDateTime.now());
        stepMapper.insert(step);
        return step.getId();
    }

    /** 更新步骤 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStep(StrategyTemplateStep step) {
        stepMapper.updateById(step);
    }

    /** 删除步骤 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(Long stepId) {
        stepMapper.deleteById(stepId);
    }

    /**
     * 执行计划另存为策略模版（对齐 mds v2 save-as-template）。
     *
     * <p>把执行计划（模块 + 步骤）深拷贝为草稿态模版；模版编码唯一且不可改。
     *
     * @param planId       源执行计划 ID
     * @param templateCode 目标模版编码（唯一）
     * @param templateName 模版名称
     * @param operator     操作人
     * @return 新模版 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveAsTemplateFromPlan(Long planId, String templateCode, String templateName, String operator) {
        if (!StringUtils.hasText(templateCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版编码必填");
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<StrategyTemplate>()
                .eq(StrategyTemplate::getTemplateCode, templateCode)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版编码已存在: " + templateCode);
        }
        AdmissionExecutionPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "执行计划不存在");
        }

        StrategyTemplate template = new StrategyTemplate();
        template.setTemplateCode(templateCode);
        template.setTemplateName(StringUtils.hasText(templateName) ? templateName : plan.getPlanName());
        template.setCustomerGroup(plan.getCustomerGroup());
        template.setStatus(STATUS_DISABLED);
        template.setCreatedBy(operator);
        template.setUpdatedBy(operator);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        copyPlanTreeToTemplate(plan, template.getId(), operator);
        return template.getId();
    }

    /**
     * 渠道策略生成模版快照（对齐 mds v2 snapshot-from-channel）。
     *
     * <p>把渠道下某策略关联的执行计划深拷贝为草稿态模版，供复用沉淀；模版编码唯一。
     *
     * @param channelCode  源渠道编码
     * @param strategyCode 源策略编码
     * @param templateCode 目标模版编码（唯一）
     * @param templateName 模版名称
     * @param operator     操作人
     * @return 新模版 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long snapshotFromChannel(String channelCode, String strategyCode, String templateCode,
                                    String templateName, String operator) {
        if (!StringUtils.hasText(channelCode) || !StringUtils.hasText(strategyCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "源渠道与策略编码必填");
        }
        if (!StringUtils.hasText(templateCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版编码必填");
        }
        ProductStrategy strategy = strategyMapper.selectOne(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getChannelCode, channelCode)
                .eq(ProductStrategy::getStrategyCode, strategyCode));
        if (strategy == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "源渠道策略不存在");
        }
        if (!StringUtils.hasText(strategy.getExecutionPlanCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "源策略未绑定执行计划，无法生成快照");
        }
        AdmissionExecutionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                .eq(AdmissionExecutionPlan::getPlanCode, strategy.getExecutionPlanCode()));
        if (plan == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "源策略关联的执行计划不存在");
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<StrategyTemplate>()
                .eq(StrategyTemplate::getTemplateCode, templateCode)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版编码已存在: " + templateCode);
        }

        StrategyTemplate template = new StrategyTemplate();
        template.setTemplateCode(templateCode);
        template.setTemplateName(StringUtils.hasText(templateName) ? templateName
                : (strategy.getStrategyName() + "快照"));
        template.setCustomerGroup(strategy.getCustomerGroup());
        template.setDescription("由渠道 " + channelCode + " / " + strategyCode + " 快照生成");
        template.setStatus(STATUS_DISABLED);
        template.setCreatedBy(operator);
        template.setUpdatedBy(operator);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        copyPlanTreeToTemplate(plan, template.getId(), operator);
        return template.getId();
    }

    /** 深拷贝执行计划树（模块 + 步骤，含 AND/OR 链 / 空跑 / 步骤参数 / 前置条件）到策略模版 */
    private void copyPlanTreeToTemplate(AdmissionExecutionPlan plan, Long templateId, String operator) {
        List<AdmissionPlanModule> modules = planModuleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>()
                        .eq(AdmissionPlanModule::getPlanId, plan.getId())
                        .orderByAsc(AdmissionPlanModule::getSort));
        for (AdmissionPlanModule module : modules) {
            StrategyTemplateModule newModule = new StrategyTemplateModule();
            newModule.setTemplateId(templateId);
            newModule.setModuleCode(module.getModuleCode());
            newModule.setModuleName(module.getModuleName());
            newModule.setLogicType(module.getLogicType());
            newModule.setSort(module.getSort());
            newModule.setJoinWithNextModule(module.getJoinWithNextModule());
            newModule.setCreatedAt(LocalDateTime.now());
            moduleMapper.insert(newModule);

            List<AdmissionPlanStep> steps = planStepMapper.selectList(
                    new LambdaQueryWrapper<AdmissionPlanStep>()
                            .eq(AdmissionPlanStep::getModuleId, module.getId())
                            .orderByAsc(AdmissionPlanStep::getStepSort));
            for (AdmissionPlanStep step : steps) {
                StrategyTemplateStep newStep = new StrategyTemplateStep();
                newStep.setTemplateModuleId(newModule.getId());
                newStep.setRuleId(step.getRuleId());
                newStep.setStepSort(step.getStepSort());
                newStep.setJoinWithNext(step.getJoinWithNext());
                newStep.setIsDryRun(step.getIsDryRun());
                newStep.setStepConfigJson(step.getStepConfigJson());
                newStep.setConditionField(step.getConditionField());
                newStep.setConditionOperator(step.getConditionOperator());
                newStep.setConditionValue(step.getConditionValue());
                newStep.setCreatedAt(LocalDateTime.now());
                stepMapper.insert(newStep);
            }
        }
    }
}
