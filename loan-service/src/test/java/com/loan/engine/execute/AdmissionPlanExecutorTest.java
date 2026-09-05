package com.loan.engine.execute;

import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.enums.StepResult;
import com.loan.engine.enums.TotalResult;
import com.loan.engine.evaluate.RuleConditionEvaluator;
import com.loan.engine.rule.AdmissionRuleRegistry;
import com.loan.engine.rule.RuleStepConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 准入执行器单测：验证 AND 段 + OR 组 分段解析（修复 AND/OR 聚合错配，对齐 mds §3.1/§3.2）。
 *
 * <p>用例覆盖：
 * <ul>
 *   <li>A OR B AND C：A=PASS 时 C 仍须执行（修复旧左结合短路跳过 C 的 bug）；</li>
 *   <li>模块级 M1(OR) M2(AND) M3(OR) M4：M1=PASS 不得短路跳过 M2/M3/M4，全 4 模块执行；</li>
 *   <li>AND 链断裂：B FAIL 后 C 短路 SKIP；</li>
 *   <li>OR 逻辑模块：任一 PASS 即模块通过；</li>
 *   <li>空模块：视为通过。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdmissionPlanExecutorTest {

    @Mock
    private AdmissionRuleRegistry ruleRegistry;
    @Mock
    private RuleConditionEvaluator conditionEvaluator;
    @Mock
    private SegmentRouter segmentRouter;

    @InjectMocks
    private AdmissionPlanExecutor executor;

    private AdmissionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AdmissionContext.builder()
                .customerGroup(CustomerGroup.ENTERPRISE)
                .channelCode("CH01")
                .fieldValues(new HashMap<>())
                .build();
        // 全部步骤走通用条件求值器（无专用 Handler），避免 Handler 注册依赖
        when(ruleRegistry.getHandler(anyString())).thenReturn(null);
        // 客群恒匹配，专注聚合逻辑
        when(segmentRouter.matches(any(CustomerGroup.class), any(CustomerGroup.class))).thenReturn(true);
        // 字段码即结果语义：PASS->true / FAIL->false
        when(conditionEvaluator.evaluate(any(), eq("PASS"), any(), any(), any())).thenReturn(true);
        when(conditionEvaluator.evaluate(any(), eq("FAIL"), any(), any(), any())).thenReturn(false);
    }

    private RuleStepConfig step(String field, String joinWithNext) {
        return RuleStepConfig.builder()
                .planId(1L).moduleId(1L).stepId(1L)
                .moduleCode("M").moduleName("M")
                .logicType("AND").globalPre(false)
                .ruleCode("R_" + field).fieldCode(field).fieldName(field)
                .operator("EQ").valueType("STRING").valueText("x")
                .stepSort(1).joinWithNext(joinWithNext)
                .isDryRun(0)
                .build();
    }

    private PlanModule module(String code, String logicType, boolean globalPre, int sort,
                              String joinWithNextModule, RuleStepConfig... steps) {
        PlanModule m = new PlanModule(1L, code, code, logicType, globalPre, sort, joinWithNextModule);
        for (RuleStepConfig s : steps) {
            m.addStep(s);
        }
        return m;
    }

    private ProductPlan plan(PlanModule... modules) {
        AdmissionPlan p = new AdmissionPlan(1L, "P1", "P1", CustomerGroup.ENTERPRISE);
        for (PlanModule m : modules) {
            p.addModule(m);
        }
        return new ProductPlan(1L, "PRD", "PRD", p);
    }

    private List<StepExecutionRecord> records(PlanExecutionResult r) {
        return r.getStepRecords();
    }

    private boolean hasSkip(PlanExecutionResult r) {
        return records(r).stream().anyMatch(x -> x.getStepResult() == StepResult.SKIP_SHORT_CIRCUIT);
    }

    @Test
    @DisplayName("步骤 A OR B AND C：A=PASS 时 C 仍须执行并通过 → 修复左结合短路跳过 C")
    void stepOrThenAnd_aPass_runsC_andPasses() {
        // A OR B AND C：段 {A,B} || {C} → (A ‖ B) && C
        PlanModule m = module("M1", "AND", false, 0, "AND",
                step("PASS", "OR"),   // A
                step("FAIL", "AND"),  // B
                step("PASS", "AND")); // C
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.PASS, r.getTotalResult(), "A 通过、C 通过 → 计划 PASS");
        assertEquals(2, r.getPassStepCount(), "A 与 C 通过，应 2 个 PASS");
        assertFalse(hasSkip(r), "旧逻辑会在 A 通过后短路跳过 C；修复后 C 必须被执行");
    }

    @Test
    @DisplayName("步骤 A OR B AND C：A=PASS 但 C=FAIL → 段 {C} 失败 → CONDITION（C 仍须执行）")
    void stepOrThenAnd_aPass_cFail_conditionsAndRunsC() {
        PlanModule m = module("M1", "AND", false, 0, "AND",
                step("PASS", "OR"),
                step("FAIL", "AND"),
                step("FAIL", "AND"));
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.CONDITION, r.getTotalResult(), "C 失败 → 需补料 CONDITION");
        assertEquals(1, r.getPassStepCount(), "仅 A 通过");
        assertEquals(2, r.getFailStepCount(), "B 与 C 失败");
        assertFalse(hasSkip(r), "C 必须被执行（旧逻辑会被短路跳过，给出错误的 PASS）");
    }

    @Test
    @DisplayName("模块级 M1(OR) M2(AND) M3(OR) M4：M1=PASS 不得短路跳过 M2/M3/M4，全 4 模块执行")
    void moduleOrGroup_m1Pass_runsAllModules() {
        // (M1 ‖ M2) && (M3 ‖ M4)
        PlanModule m1 = module("M1", "AND", false, 0, "OR", step("PASS", "AND"));
        PlanModule m2 = module("M2", "AND", false, 1, "AND", step("FAIL", "AND"));
        PlanModule m3 = module("M3", "AND", false, 2, "OR", step("FAIL", "AND"));
        PlanModule m4 = module("M4", "AND", false, 3, "AND", step("FAIL", "AND"));
        PlanExecutionResult r = executor.executePlan(ctx, plan(m1, m2, m3, m4));

        assertEquals(TotalResult.CONDITION, r.getTotalResult(), "段2 (M3‖M4) 全失败 → CONDITION");
        assertEquals(1, r.getPassStepCount(), "仅 M1 通过");
        assertEquals(3, r.getFailStepCount(), "M2/M3/M4 失败");
        assertFalse(hasSkip(r), "旧逻辑会在 M1 通过后 OR 短路跳过 M2/M3/M4；修复后全 4 模块执行");
    }

    @Test
    @DisplayName("AND 链断裂：A PASS AND B FAIL → C 短路 SKIP（预期行为，验证短路方向正确）")
    void andChainBreak_shortCircuitsTrailingSegment() {
        // A AND B AND C：段 {A},{B},{C}
        PlanModule m = module("M1", "AND", false, 0, "AND",
                step("PASS", "AND"),
                step("FAIL", "AND"),
                step("PASS", "AND"));
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.CONDITION, r.getTotalResult(), "B 失败 → 需补料");
        assertTrue(hasSkip(r), "B 失败应短路跳过 C（AND 链断裂）");
        // C 须为 SKIP_SHORT_CIRCUIT（非 PASS），证明其未执行
        boolean cSkipped = records(r).stream()
                .anyMatch(x -> x.getStepResult() == StepResult.SKIP_SHORT_CIRCUIT
                        && "PASS".equals(x.getFieldCode()));
        assertTrue(cSkipped, "C(step fieldCode=PASS) 应被短路跳过，而非被执行");
    }

    @Test
    @DisplayName("OR 逻辑模块：任一步 PASS 即模块通过")
    void orLogicModule_anyPass_passes() {
        PlanModule m = module("M1", "OR", false, 0, "AND",
                step("FAIL", "AND"),
                step("PASS", "AND"));
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.PASS, r.getTotalResult(), "OR 模块：B 通过即模块通过");
        assertEquals(1, r.getPassStepCount());
        assertFalse(hasSkip(r), "OR 模块内步骤应全执行（OR 组不短路）");
    }

    @Test
    @DisplayName("空模块：视为通过，不产生步骤记录")
    void emptyModule_passesWithoutRecords() {
        PlanModule m = module("M1", "AND", false, 0, "AND");
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.PASS, r.getTotalResult(), "空模块不影响结果");
        assertTrue(records(r).isEmpty(), "空模块不应产生步骤记录");
    }

    @Test
    @DisplayName("客群不匹配：直接 SKIP_SEGMENT_MISMATCH")
    void segmentMismatch_skips() {
        when(segmentRouter.matches(any(CustomerGroup.class), any(CustomerGroup.class))).thenReturn(false);
        PlanModule m = module("M1", "AND", false, 0, "AND", step("PASS", "AND"));
        PlanExecutionResult r = executor.executePlan(ctx, plan(m));

        assertEquals(TotalResult.SKIP_SEGMENT_MISMATCH, r.getTotalResult());
        assertTrue(records(r).isEmpty());
    }
}
