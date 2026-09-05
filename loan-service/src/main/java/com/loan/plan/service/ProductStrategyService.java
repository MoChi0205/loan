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
import com.loan.plan.service.PlanOrchestrationService;
import com.loan.product.entity.BankChannel;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 渠道准入策略服务：策略 CRUD + 写锁 + 上线校验 + 跨渠道复制 + 级联删除。
 *
 * <p>生命周期：DISABLED（草稿，可编辑）→ ACTIVE（上线，写锁只读）。
 * 对齐 mds v2 的写锁护栏（V2ChannelConfigWriteGuard）与上线校验（validate-before-enable）。
 *
 * <p>数据库规范：产品/计划关联均使用业务 key 字符串（bankProductCode / executionPlanCode）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ProductStrategyService {

    /** 上线态（写锁触发条件） */
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String MESSAGE_READONLY = "策略已上线，配置只读；请先下线后再修改";

    private final ProductStrategyMapper strategyMapper;
    private final AdmissionExecutionPlanMapper planMapper;
    private final AdmissionPlanModuleMapper moduleMapper;
    private final AdmissionPlanStepMapper stepMapper;
    private final StrategyTemplateMapper templateMapper;
    private final StrategyTemplateModuleMapper templateModuleMapper;
    private final StrategyTemplateStepMapper templateStepMapper;
    private final BankChannelMapper channelMapper;
    private final BankProductMapper bankProductMapper;
    private final PlanOrchestrationService planOrchestrationService;

    /**
     * 分页查询策略。
     *
     * @param channelCode   渠道编码（可选）
     * @param customerGroup 客群（可选）
     * @param keyword       关键字（策略编码/名称）
     * @param page          页码
     * @param size          每页大小
     * @return 策略分页
     */
    public PageResult<ProductStrategy> page(String channelCode, String customerGroup, String keyword, int page, int size) {
        LambdaQueryWrapper<ProductStrategy> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channelCode)) {
            wrapper.eq(ProductStrategy::getChannelCode, channelCode);
        }
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(ProductStrategy::getCustomerGroup, customerGroup);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ProductStrategy::getStrategyCode, keyword)
                    .or().like(ProductStrategy::getStrategyName, keyword));
        }
        wrapper.orderByDesc(ProductStrategy::getId);
        Page<ProductStrategy> result = strategyMapper.selectPage(new Page<>(page, size), wrapper);
        enrichProductNames(result.getRecords());
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /** 当前分页批量补齐产品名称，避免前端拉取伪全量字典及逐行查询。 */
    private void enrichProductNames(List<ProductStrategy> strategies) {
        List<String> codes = strategies.stream().map(ProductStrategy::getBankProductCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) return;
        Map<String, String> names = bankProductMapper.selectList(new LambdaQueryWrapper<BankProduct>()
                        .in(BankProduct::getProductCode, codes)).stream()
                .collect(Collectors.toMap(BankProduct::getProductCode, BankProduct::getProductName, (a, b) -> a));
        strategies.forEach(strategy -> strategy.setBankProductName(names.get(strategy.getBankProductCode())));
    }

    /** 判断执行计划是否被任一渠道策略引用，供删除保护精确校验。 */
    public boolean existsByExecutionPlanCode(String planCode) {
        if (!StringUtils.hasText(planCode)) return false;
        Long count = strategyMapper.selectCount(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getExecutionPlanCode, planCode));
        return count != null && count > 0;
    }

    /**
     * 渠道配置摘要：按渠道返回策略数/已上线数/计划数/最近更新，供「渠道配置列表」每渠道一行展示。
     *
     * @return 每个启用渠道一行的配置摘要
     */
    public List<Map<String, Object>> channelSummary() {
        List<BankChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<BankChannel>().eq(BankChannel::getStatus, "ACTIVE").orderByAsc(BankChannel::getId));
        List<ProductStrategy> strategies = strategyMapper.selectList(null);
        Map<String, List<ProductStrategy>> byChannel = strategies.stream()
                .collect(Collectors.groupingBy(ProductStrategy::getChannelCode));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BankChannel channel : channels) {
            List<ProductStrategy> list = byChannel.getOrDefault(channel.getChannelCode(), Collections.emptyList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelCode", channel.getChannelCode());
            m.put("bankName", channel.getBankName());
            m.put("strategyCount", list.size());
            m.put("activeCount", list.stream().filter(s -> STATUS_ACTIVE.equals(s.getStatus())).count());
            Set<String> planCodes = list.stream().map(ProductStrategy::getExecutionPlanCode)
                    .filter(StringUtils::hasText).collect(Collectors.toSet());
            m.put("planCount", planCodes.size());
            LocalDateTime latest = list.stream().map(ProductStrategy::getUpdatedAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            m.put("updatedAt", latest);
            result.add(m);
        }
        return result;
    }

    /**
     * 新建策略（草稿态）。
     *
     * @param strategy 策略
     * @return 策略业务编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(ProductStrategy strategy, String operator) {
        if (!StringUtils.hasText(strategy.getChannelCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "渠道编码必填");
        }
        if (!StringUtils.hasText(strategy.getStrategyCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "策略编码必填");
        }
        validateAssociations(strategy);
        // 渠道内策略编码唯一
        Long cnt = strategyMapper.selectCount(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getChannelCode, strategy.getChannelCode())
                .eq(ProductStrategy::getStrategyCode, strategy.getStrategyCode()));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "策略编码在该渠道已存在");
        }
        strategy.setId(null);
        strategy.setStatus(STATUS_DISABLED);
        strategy.setCreatedBy(operator);
        strategy.setUpdatedBy(operator);
        strategy.setCreatedAt(LocalDateTime.now());
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.insert(strategy);
        return strategy.getStrategyCode();
    }

    /**
     * 编辑策略（写锁校验：上线态只读；草稿态可全量编辑）。
     *
     * @param strategyCode 策略业务编码
     * @param strategy     策略
     * @param operator     操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(String strategyCode, ProductStrategy strategy, String operator) {
        ProductStrategy existing = requireEditable(strategyCode);
        validateAssociations(strategy);
        // 草稿态：允许全量编辑；仅保留主键与审计字段，避免被篡改
        strategy.setId(existing.getId());
        strategy.setStrategyCode(existing.getStrategyCode());
        strategy.setStatus(existing.getStatus());
        strategy.setCreatedBy(null);
        strategy.setCreatedAt(null);
        strategy.setUpdatedBy(operator);
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(strategy);
    }

    /**
     * 删除策略（级联删除计划树）。
     *
     * @param strategyCode 策略业务编码
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String strategyCode) {
        ProductStrategy strategy = findByCode(strategyCode);
        if (strategy == null) {
            return;
        }
        if (STATUS_ACTIVE.equals(strategy.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "策略已上线，请先下线后再删除");
        }
        cascadeDeletePlan(strategy.getExecutionPlanCode());
        strategyMapper.deleteById(strategy.getId());
    }

    /**
     * 上线（先校验计划可执行）。
     *
     * @param strategyCode 策略业务编码
     * @param operator     操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void enable(String strategyCode, String operator) {
        ProductStrategy strategy = findByCode(strategyCode);
        if (strategy == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "策略不存在");
        }
        List<String> issues = validateBeforeEnable(strategy);
        if (!issues.isEmpty()) {
            throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "全链路配置未完成，无法上线：" + String.join("；", issues));
        }
        strategy.setStatus(STATUS_ACTIVE);
        strategy.setUpdatedBy(operator);
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(strategy);
    }

    /**
     * 下线。
     *
     * @param strategyCode 策略业务编码
     * @param operator     操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(String strategyCode, String operator) {
        ProductStrategy strategy = findByCode(strategyCode);
        if (strategy == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "策略不存在");
        }
        strategy.setStatus(STATUS_DISABLED);
        strategy.setUpdatedBy(operator);
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(strategy);
    }

    /**
     * 上线前校验：计划结构完整可执行（模块有步骤、步骤有规则）。
     *
     * @param strategyCode 策略业务编码
     * @return 校验问题列表（空 = 通过）
     */
    public List<String> validateBeforeEnable(String strategyCode) {
        ProductStrategy strategy = findByCode(strategyCode);
        if (strategy == null) {
            return Collections.singletonList("策略不存在");
        }
        return validateBeforeEnable(strategy);
    }

    private List<String> validateBeforeEnable(ProductStrategy strategy) {
        List<String> issues = new ArrayList<>();
        if (!StringUtils.hasText(strategy.getBankProductCode())) {
            issues.add("未绑定产品");
        }
        if (!StringUtils.hasText(strategy.getExecutionPlanCode())) {
            issues.add("未绑定执行计划");
            return issues;
        }
        AdmissionExecutionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                .eq(AdmissionExecutionPlan::getPlanCode, strategy.getExecutionPlanCode()));
        if (plan == null) {
            issues.add("执行计划不存在");
            return issues;
        }
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>().eq(AdmissionPlanModule::getPlanId, plan.getId()));
        if (modules.isEmpty()) {
            issues.add("计划无模块");
            return issues;
        }
        for (AdmissionPlanModule module : modules) {
            Long stepCnt = stepMapper.selectCount(
                    new LambdaQueryWrapper<AdmissionPlanStep>().eq(AdmissionPlanStep::getModuleId, module.getId()));
            if (stepCnt == null || stepCnt == 0) {
                issues.add("模块「" + module.getModuleName() + "」无步骤");
            }
        }
        // FR-03 连接符结构校验（AND/OR 聚合配置合法性，执行激活前拦截）
        issues.addAll(planOrchestrationService.validatePlanStructure(plan.getId()));
        return issues;
    }

    /**
     * 跨渠道复制：源策略深拷贝（计划 + 模块 + 步骤）到目标渠道。
     *
     * @param sourceStrategyCode 源策略业务编码
     * @param targetChannelCode 目标渠道编码
     * @param targetStrategyCode 目标策略编码
     * @param operator          操作人
     * @return 新策略业务编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String importFromChannel(String sourceStrategyCode, String targetChannelCode,
                                    String targetStrategyCode, String operator) {
        ProductStrategy source = findByCode(sourceStrategyCode);
        if (source == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "源策略不存在");
        }
        if (!StringUtils.hasText(targetChannelCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标渠道必填");
        }
        Long cnt = strategyMapper.selectCount(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getChannelCode, targetChannelCode)
                .eq(ProductStrategy::getStrategyCode, targetStrategyCode));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标渠道已存在该策略编码");
        }

        // 深拷贝计划
        String newPlanCode = deepCopyPlan(source.getExecutionPlanCode(), operator);

        ProductStrategy target = new ProductStrategy();
        target.setChannelCode(targetChannelCode);
        target.setStrategyCode(targetStrategyCode);
        target.setStrategyName(source.getStrategyName());
        target.setDescription(source.getDescription());
        target.setBankProductCode(source.getBankProductCode());
        target.setCustomerGroup(source.getCustomerGroup());
        target.setExecutionPlanCode(newPlanCode);
        target.setStatus(STATUS_DISABLED);
        target.setCreatedBy(operator);
        target.setUpdatedBy(operator);
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        strategyMapper.insert(target);
        return target.getStrategyCode();
    }

    /**
     * 模版导入到策略：把已上线的策略模版（模块→步骤→规则）实例化为一个新的执行计划，并绑定到渠道准入策略。
     *
     * @param templateCode   策略模版业务编码
     * @param channelCode    目标渠道编码
     * @param strategyCode   目标策略编码
     * @param strategyName   目标策略名称（可选，缺省用模版名）
     * @param bankProductCode 目标产品编码（业务 key）
     * @param customerGroup  目标客群（可选，缺省用模版客群）
     * @param operator       操作人
     * @return 新策略业务编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String importFromTemplate(String templateCode, String channelCode, String strategyCode,
                                  String strategyName, String bankProductCode, String customerGroup, String operator) {
        StrategyTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<StrategyTemplate>()
                .eq(StrategyTemplate::getTemplateCode, templateCode));
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "策略模版不存在");
        }
        if (!STATUS_ACTIVE.equals(template.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模版未上线，无法导入");
        }
        if (!StringUtils.hasText(channelCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标渠道必填");
        }
        if (!StringUtils.hasText(strategyCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "策略编码必填");
        }
        if (!StringUtils.hasText(bankProductCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码必填");
        }
        if (bankProductMapper.selectCount(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, bankProductCode)) == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品不存在：" + bankProductCode);
        }
        Long cnt = strategyMapper.selectCount(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getChannelCode, channelCode)
                .eq(ProductStrategy::getStrategyCode, strategyCode));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标渠道已存在该策略编码");
        }

        String targetGroup = StringUtils.hasText(customerGroup) ? customerGroup : template.getCustomerGroup();

        // 1. 创建执行计划（草稿态）
        AdmissionExecutionPlan plan = new AdmissionExecutionPlan();
        plan.setPlanCode(template.getTemplateCode() + "_" + System.currentTimeMillis());
        plan.setPlanName(template.getTemplateName());
        plan.setCustomerGroup(targetGroup);
        plan.setVersion(1);
        plan.setStatus("0");
        plan.setCreatedBy(operator);
        plan.setUpdatedBy(operator);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        // 2. 复制模块 + 步骤（ruleId 复用）
        List<StrategyTemplateModule> modules = templateModuleMapper.selectList(
                new LambdaQueryWrapper<StrategyTemplateModule>()
                        .eq(StrategyTemplateModule::getTemplateId, template.getId())
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

        // 3. 创建策略（草稿态）
        ProductStrategy strategy = new ProductStrategy();
        strategy.setChannelCode(channelCode);
        strategy.setStrategyCode(strategyCode);
        strategy.setStrategyName(StringUtils.hasText(strategyName) ? strategyName : template.getTemplateName());
        strategy.setDescription(template.getDescription());
        strategy.setBankProductCode(bankProductCode);
        strategy.setCustomerGroup(targetGroup);
        strategy.setExecutionPlanCode(plan.getPlanCode());
        strategy.setStatus(STATUS_DISABLED);
        strategy.setCreatedBy(operator);
        strategy.setUpdatedBy(operator);
        strategy.setCreatedAt(LocalDateTime.now());
        strategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.insert(strategy);
        return strategy.getStrategyCode();
    }

    /** 关联业务 key 校验：产品编码须存在，计划编码（若填）须存在 */
    private void validateAssociations(ProductStrategy strategy) {
        if (StringUtils.hasText(strategy.getBankProductCode())) {
            if (bankProductMapper.selectCount(new LambdaQueryWrapper<BankProduct>()
                    .eq(BankProduct::getProductCode, strategy.getBankProductCode())) == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "产品不存在：" + strategy.getBankProductCode());
            }
        }
        if (StringUtils.hasText(strategy.getExecutionPlanCode())) {
            if (planMapper.selectCount(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                    .eq(AdmissionExecutionPlan::getPlanCode, strategy.getExecutionPlanCode())) == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "执行计划不存在：" + strategy.getExecutionPlanCode());
            }
        }
    }

    /** 写锁校验：上线态只读 */
    private ProductStrategy requireEditable(String strategyCode) {
        ProductStrategy strategy = findByCode(strategyCode);
        if (strategy == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "策略不存在");
        }
        if (STATUS_ACTIVE.equals(strategy.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, MESSAGE_READONLY);
        }
        return strategy;
    }

    /** 按对外业务编码查找策略。 */
    private ProductStrategy findByCode(String strategyCode) {
        if (!StringUtils.hasText(strategyCode)) {
            return null;
        }
        return strategyMapper.selectOne(new LambdaQueryWrapper<ProductStrategy>()
                .eq(ProductStrategy::getStrategyCode, strategyCode));
    }

    /** 深拷贝计划树，返回新计划编码 */
    private String deepCopyPlan(String planCode, String operator) {
        if (!StringUtils.hasText(planCode)) {
            return null;
        }
        AdmissionExecutionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                .eq(AdmissionExecutionPlan::getPlanCode, planCode));
        if (plan == null) {
            return null;
        }
        AdmissionExecutionPlan newPlan = new AdmissionExecutionPlan();
        newPlan.setPlanCode(plan.getPlanCode() + "_copy_" + System.currentTimeMillis());
        newPlan.setPlanName(plan.getPlanName());
        newPlan.setVersion(1);
        newPlan.setStatus("0");
        newPlan.setCreatedBy(operator);
        newPlan.setUpdatedBy(operator);
        newPlan.setCreatedAt(LocalDateTime.now());
        newPlan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(newPlan);

        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>().eq(AdmissionPlanModule::getPlanId, plan.getId()));
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
                    new LambdaQueryWrapper<AdmissionPlanStep>().eq(AdmissionPlanStep::getModuleId, module.getId()));
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
        return newPlan.getPlanCode();
    }

    /** 级联删除计划树（按计划编码） */
    private void cascadeDeletePlan(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            return;
        }
        AdmissionExecutionPlan plan = planMapper.selectOne(new LambdaQueryWrapper<AdmissionExecutionPlan>()
                .eq(AdmissionExecutionPlan::getPlanCode, planCode));
        if (plan == null) {
            return;
        }
        List<AdmissionPlanModule> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<AdmissionPlanModule>().eq(AdmissionPlanModule::getPlanId, plan.getId()));
        for (AdmissionPlanModule module : modules) {
            stepMapper.delete(new LambdaQueryWrapper<AdmissionPlanStep>()
                    .eq(AdmissionPlanStep::getModuleId, module.getId()));
        }
        moduleMapper.delete(new LambdaQueryWrapper<AdmissionPlanModule>()
                .eq(AdmissionPlanModule::getPlanId, plan.getId()));
        planMapper.deleteById(plan.getId());
    }
}
