package com.loan.engine.execute;

import com.loan.engine.enums.CustomerGroup;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 准入执行计划结构模型（一个产品的完整匹配计划）。
 *
 * <p>包含计划标识、适用客群与模块列表（含全局前置风控模块置前）。
 *
 * @author loan-platform
 */
@Getter
public class AdmissionPlan {

    /** 计划 ID */
    private final Long planId;

    /** 计划编码 */
    private final String planCode;

    /** 计划名称 */
    private final String planName;

    /** 计划适用客群 */
    private final CustomerGroup customerGroup;

    /** 模块列表（按 sort 升序，全局前置风控模块置前） */
    private final List<PlanModule> modules = new ArrayList<>();

    /**
     * 构造计划。
     *
     * @param planId        计划 ID
     * @param planCode      计划编码
     * @param planName      计划名称
     * @param customerGroup 适用客群
     */
    public AdmissionPlan(Long planId, String planCode, String planName, CustomerGroup customerGroup) {
        this.planId = planId;
        this.planCode = planCode;
        this.planName = planName;
        this.customerGroup = customerGroup;
    }

    /**
     * 追加模块。
     *
     * @param module 模块
     */
    public void addModule(PlanModule module) {
        if (module != null) {
            this.modules.add(module);
        }
    }
}
