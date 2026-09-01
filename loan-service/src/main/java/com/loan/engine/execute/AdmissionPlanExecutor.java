package com.loan.engine.execute;

import com.loan.engine.enums.StepResult;
import com.loan.engine.enums.TotalResult;
import com.loan.engine.evaluate.RuleConditionEvaluator;
import com.loan.engine.rule.AdmissionRuleHandler;
import com.loan.engine.rule.AdmissionRuleRegistry;
import com.loan.engine.rule.RuleHandlerResult;
import com.loan.engine.rule.RuleStepConfig;
import com.loan.engine.rule.StepConditionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 准入执行计划执行器（核心编排，第 15 章定稿 + V2 OR 组/空跑增强）。
 *
 * <p>执行结构：计划 → 模块（顺序，模块间 AND/OR 链）→ 步骤（单条规则，步骤间 AND/OR 链）。
 * <ul>
 *   <li>模块间：默认 AND 链，`join_with_next_module=OR` 组成 OR 组——前序失败且 AND 连接则
 *       后续短路 SKIP_SHORT_CIRCUIT；前序通过且 OR 连接则后续短路（OR 组已满足）；</li>
 *   <li>模块内：默认 AND 链，`join_with_next=OR` 组成步骤 OR 组——FAIL 且 OR 连接等下一步补救，
 *       PASS 且 OR 连接则短路后续；模块 `logic_type=OR` 时任一步 PASS 即模块通过；</li>
 *   <li>空跑：步骤 `is_dry_run=1` 且 Handler 返回 FAIL（拒绝）→ 链上 `step_result` 改写 PASS，
 *       保留 `handler_step_result=FAIL`（mismatchFlag=true）；</li>
 *   <li>全局前置风控模块命中直接 REJECT，不可补料改善，终止后续模块；</li>
 *   <li>非风控模块 FAIL 记为 CONDITION（需补料，可改善）；</li>
 *   <li>全部模块通过记 PASS（可进件）。</li>
 * </ul>
 *
 * <p>步骤执行分两类：行为/风控专用规则走 {@link AdmissionRuleRegistry} 定位 Handler；
 * 通用条件规则走 {@link RuleConditionEvaluator} 表达式求值。
 *
 * @author loan-platform
 */
@Slf4j
@Service
public class AdmissionPlanExecutor {

    /** 规则处理器注册表 */
    @Resource
    private AdmissionRuleRegistry ruleRegistry;

    /** 条件表达式求值器 */
    @Resource
    private RuleConditionEvaluator conditionEvaluator;

    /** 客群分流器 */
    @Resource
    private SegmentRouter segmentRouter;

    /**
     * 批量执行多个产品计划，返回每个产品的匹配结果。
     *
     * @param context 执行上下文
     * @param plans   产品-计划绑定列表
     * @return 产品匹配结果列表
     */
    public List<PlanExecutionResult> execute(AdmissionContext context, List<ProductPlan> plans) {
        List<PlanExecutionResult> results = new ArrayList<>();
        if (plans == null || plans.isEmpty()) {
            return results;
        }
        for (ProductPlan productPlan : plans) {
            results.add(executePlan(context, productPlan));
        }
        return results;
    }

    /**
     * 执行单个产品计划。
     *
     * @param context     执行上下文
     * @param productPlan 产品-计划绑定
     * @return 产品匹配结果
     */
    public PlanExecutionResult executePlan(AdmissionContext context, ProductPlan productPlan) {
        AdmissionPlan plan = productPlan.getPlan();
        // 1. 客群硬分流：串客群直接 SKIP_SEGMENT_MISMATCH
        if (!segmentRouter.matches(context.getCustomerGroup(), plan.getCustomerGroup())) {
            return PlanExecutionResult.builder()
                    .productId(productPlan.getProductId())
                    .productCode(productPlan.getProductCode())
                    .productName(productPlan.getProductName())
                    .totalResult(TotalResult.SKIP_SEGMENT_MISMATCH)
                    .build();
        }

        boolean riskReject = false;
        boolean anyFail = false;
        int passCount = 0;
        int failCount = 0;
        List<StepExecutionRecord> stepRecords = new ArrayList<>();

        // 模块间 OR 组短路状态（需求 §3.2：join_with_next_module）
        boolean shortCircuit = false;
        PlanModule prevModule = null;
        boolean prevFailed = false;
        boolean prevPassed = true;

        // 2. 遍历模块（按 sort 升序）
        for (PlanModule module : plan.getModules()) {
            // 短路判定：前序失败且 AND 连接 → 链断裂；前序通过且 OR 连接 → 链已满足
            if (prevModule != null && !shortCircuit) {
                if ((prevFailed && !prevModule.isOrJoinNextModule())
                        || (prevPassed && prevModule.isOrJoinNextModule())) {
                    shortCircuit = true;
                }
            }
            if (shortCircuit) {
                // 模块被短路：全部步骤写 SKIP_SHORT_CIRCUIT，不再执行
                for (RuleStepConfig step : module.getSteps()) {
                    stepRecords.add(buildSkipRecord(step));
                }
                continue;
            }

            ModuleExecution moduleExecution = executeModule(context, module);
            for (StepExecutionRecord record : moduleExecution.records) {
                stepRecords.add(record);
                if (record.getStepResult() == StepResult.PASS) {
                    passCount++;
                } else if (record.getStepResult() == StepResult.FAIL) {
                    failCount++;
                }
            }
            if (moduleExecution.failed) {
                if (module.isGlobalPre()) {
                    // 全局前置风控命中：直接 REJECT，不可改善，终止后续模块
                    riskReject = true;
                    break;
                }
                anyFail = true;
                prevFailed = true;
                prevPassed = false;
            } else {
                prevPassed = true;
                prevFailed = false;
            }
            prevModule = module;
        }

        // 3. 汇总产品总结果
        TotalResult total;
        if (riskReject) {
            total = TotalResult.REJECT;
        } else if (anyFail) {
            total = TotalResult.CONDITION;
        } else {
            total = TotalResult.PASS;
        }
        return PlanExecutionResult.builder()
                .productId(productPlan.getProductId())
                .productCode(productPlan.getProductCode())
                .productName(productPlan.getProductName())
                .totalResult(total)
                .passStepCount(passCount)
                .failStepCount(failCount)
                .stepRecords(stepRecords)
                .build();
    }

    /**
     * 执行单个模块（步骤级 AND/OR 链 + 空跑）。
     *
     * <p>步骤间默认 AND 链：FAIL 且与下一步 OR 连接则等待下一步补救；FAIL 且 AND 连接则
     * 链断裂，后续短路 SKIP。PASS 且与下一步 OR 连接则 OR 组满足，后续短路 SKIP。
     * 模块 `logic_type=OR` 时任一步 PASS 即模块通过并短路后续。
     *
     * @param context 执行上下文
     * @param module  模块
     * @return 模块执行结果
     */
    private ModuleExecution executeModule(AdmissionContext context, PlanModule module) {
        ModuleExecution execution = new ModuleExecution();
        boolean moduleOr = module.isOrLogic();
        boolean anyFail = false;      // 未补救 FAIL 标记
        boolean shortCircuit = false; // 步骤短路（AND 断裂 / OR 组满足）
        boolean passed = false;       // OR 模块出现过 PASS

        for (RuleStepConfig step : module.getSteps()) {
            if (shortCircuit || (moduleOr && passed)) {
                execution.records.add(buildSkipRecord(step));
                continue;
            }
            StepExecutionRecord record = executeStep(context, step);
            execution.records.add(record);

            StepResult sr = record.getStepResult();
            if (moduleOr) {
                // OR 模块：遇 PASS 短路（任一步 PASS 即模块通过）
                if (sr == StepResult.PASS) {
                    passed = true;
                } else if (sr == StepResult.FAIL) {
                    anyFail = true;
                }
            } else if (sr == StepResult.FAIL) {
                anyFail = true;
                if (step.isOrJoinNext()) {
                    // OR 组内：等待下一步骤补救
                    continue;
                }
                // AND 链断裂：短路后续
                shortCircuit = true;
            } else if (sr == StepResult.PASS) {
                anyFail = false; // 补救前面 OR 组内的 FAIL
                if (step.isOrJoinNext()) {
                    // OR 组已满足：短路后续
                    shortCircuit = true;
                }
            }
        }
        execution.failed = moduleOr ? !passed : anyFail;
        return execution;
    }

    /**
     * 执行单个步骤（步骤前置条件 → 专用 Handler 优先，否则通用条件求值；支持空跑改写）。
     *
     * @param context 执行上下文
     * @param step    步骤配置
     * @return 步骤执行记录
     */
    private StepExecutionRecord executeStep(AdmissionContext context, RuleStepConfig step) {
        // 步骤前置条件（conditionOperator 非空时生效）：不满足直接跳过本步（对齐 mds v2）
        if (StepConditionEvaluator.hasCondition(step)) {
            try {
                if (!StepConditionEvaluator.evaluate(step, context)) {
                    return buildSkipRecord(step, "步骤前置条件不满足");
                }
            } catch (IllegalArgumentException e) {
                // 配置错误（未知字段/运算符）：保存时已被拒绝；存量脏数据运行时按跳过处理并告警，绝不静默通过
                log.warn("[准入执行][步骤前置条件] 规则={} 条件配置错误，按条件不满足跳过: {}",
                        step.getRuleCode(), e.getMessage());
                return buildSkipRecord(step, "条件配置错误，按条件不满足跳过");
            }
        }

        String expression = step.getFieldCode() + " " + step.getOperator() + " " + step.getValueText();
        AdmissionRuleHandler handler = ruleRegistry.getHandler(step.getRuleCode());
        StepResult stepResult;
        StepResult handlerStepResult;
        String detail;

        if (handler != null) {
            // 专用 Handler（行为/风控）
            RuleHandlerResult hr = handler.handle(context, step);
            StepResult raw = hr.getStepResult();
            handlerStepResult = raw;
            // 空跑：Handler 拒绝(FAIL)时链上 stepResult 改写 PASS，保留 handlerStepResult（需求 §3.2-5）
            if (step.isDryRun() && raw == StepResult.FAIL) {
                stepResult = StepResult.PASS;
            } else {
                stepResult = raw;
            }
            detail = hr.getRejectReason() != null ? hr.getRejectReason() : hr.getQuerySnapshotJson();
        } else {
            // 通用条件规则：表达式求值
            boolean hit = conditionEvaluator.evaluate(context, step.getFieldCode(), step.getOperator(),
                    step.getValueText(), step.getValueType());
            stepResult = hit ? StepResult.PASS : StepResult.FAIL;
            handlerStepResult = stepResult;
            detail = hit ? "命中" : "未命中";
        }

        return StepExecutionRecord.builder()
                .planId(step.getPlanId())
                .moduleId(step.getModuleId())
                .moduleCode(step.getModuleCode())
                .moduleName(step.getModuleName())
                .stepId(step.getStepId())
                .ruleCode(step.getRuleCode())
                .fieldCode(step.getFieldCode())
                .expression(expression)
                .stepResult(stepResult)
                .handlerStepResult(handlerStepResult)
                .mismatchFlag(stepResult != handlerStepResult)
                .detail(detail)
                .build();
    }

    /**
     * 构建短路跳过记录（不执行 Handler/求值器，直接记 SKIP_SHORT_CIRCUIT）。
     *
     * @param step 步骤配置
     * @return 跳过记录
     */
    private StepExecutionRecord buildSkipRecord(RuleStepConfig step) {
        return buildSkipRecord(step, "短路跳过", StepResult.SKIP_SHORT_CIRCUIT);
    }

    /**
     * 构建步骤前置条件跳过记录（记 SKIP，条件不适用语义）。
     *
     * @param step   步骤配置
     * @param detail 跳过原因
     * @return 跳过记录
     */
    private StepExecutionRecord buildSkipRecord(RuleStepConfig step, String detail) {
        return buildSkipRecord(step, detail, StepResult.SKIP);
    }

    /**
     * 构建跳过记录（短路 / 步骤前置条件不满足）。
     *
     * @param step       步骤配置
     * @param detail     跳过原因
     * @param skipResult 跳过结果态
     * @return 跳过记录
     */
    private StepExecutionRecord buildSkipRecord(RuleStepConfig step, String detail, StepResult skipResult) {
        return StepExecutionRecord.builder()
                .planId(step.getPlanId())
                .moduleId(step.getModuleId())
                .moduleCode(step.getModuleCode())
                .moduleName(step.getModuleName())
                .stepId(step.getStepId())
                .ruleCode(step.getRuleCode())
                .fieldCode(step.getFieldCode())
                .expression(step.getFieldCode() + " " + step.getOperator() + " " + step.getValueText())
                .stepResult(skipResult)
                .handlerStepResult(skipResult)
                .mismatchFlag(false)
                .detail(detail)
                .build();
    }

    /**
     * 模块执行中间结果。
     */
    private static class ModuleExecution {
        /** 步骤执行记录 */
        private final List<StepExecutionRecord> records = new ArrayList<>();
        /** 模块是否失败 */
        private boolean failed;
    }
}
