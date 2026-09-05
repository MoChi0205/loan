package com.loan.engine.execute;

import com.loan.engine.rule.RuleStepConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 准入执行计划的结构化模型（从 t_admission_execution_plan → module → step 组装）。
 *
 * <p>与 {@link RuleStepConfig}（单步）配合，构成「计划 → 模块（顺序）→ 步骤」三层结构，
 * 供 {@link AdmissionPlanExecutor} 执行。模块级 AND 遇 FAIL 短路、OR 遇 PASS 短路。
 *
 * @author loan-platform
 */
@Getter
public class PlanModule {

    /** 模块 ID */
    private final Long moduleId;

    /** 模块编码 */
    private final String moduleCode;

    /** 模块名称 */
    private final String moduleName;

    /** 模块逻辑（AND/OR） */
    private final String logicType;

    /** 是否全局前置风控模块（命中直接 REJECT） */
    private final boolean globalPre;

    /** 模块顺序 */
    private final Integer sort;

    /** 与下一模块连接（AND/OR，支持模块间 OR 组短路） */
    private final String joinWithNextModule;

    /** 模块内步骤（按 stepSort 升序） */
    private final List<RuleStepConfig> steps;

    /**
     * 构造计划模块。
     *
     * @param moduleId           模块 ID
     * @param moduleCode         模块编码
     * @param moduleName         模块名称
     * @param logicType          模块逻辑
     * @param globalPre          是否全局前置风控
     * @param sort               模块顺序
     * @param joinWithNextModule 与下一模块连接（AND/OR）
     */
    public PlanModule(Long moduleId, String moduleCode, String moduleName, String logicType,
                      boolean globalPre, Integer sort, String joinWithNextModule) {
        this.moduleId = moduleId;
        this.moduleCode = moduleCode;
        this.moduleName = moduleName;
        this.logicType = logicType;
        this.globalPre = globalPre;
        this.sort = sort;
        this.joinWithNextModule = joinWithNextModule;
        this.steps = new ArrayList<>();
    }

    /**
     * 追加步骤。
     *
     * @param step 单步配置
     */
    public void addStep(RuleStepConfig step) {
        if (step != null) {
            this.steps.add(step);
        }
    }

    /**
     * 是否 OR 逻辑（遇 PASS 短路）。
     *
     * @return true OR
     */
    public boolean isOrLogic() {
        return "OR".equalsIgnoreCase(logicType);
    }

    /**
     * 与下一模块是否 OR 连接（组成模块间 OR 组）。
     *
     * @return true OR
     */
    public boolean isOrJoinNextModule() {
        return "OR".equalsIgnoreCase(joinWithNextModule);
    }
}
