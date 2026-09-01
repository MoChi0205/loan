package com.loan.engine.execute;

import com.loan.engine.enums.TotalResult;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个产品的匹配执行结果（汇总步骤明细，供档位聚合与审计落库）。
 *
 * @author loan-platform
 */
@Getter
@Builder
public class PlanExecutionResult {

    /** 产品 ID */
    private final Long productId;

    /** 产品编码 */
    private final String productCode;

    /** 产品名称（仅管理端可见，客户端屏蔽） */
    private final String productName;

    /** 产品匹配总结果（PASS/CONDITION/REJECT/SKIP_SEGMENT_MISMATCH/ERROR） */
    private final TotalResult totalResult;

    /** 命中 PASS 步骤数 */
    private final int passStepCount;

    /** FAIL 步骤数 */
    private final int failStepCount;

    /** 步骤执行明细 */
    @Builder.Default
    private final List<StepExecutionRecord> stepRecords = new ArrayList<>();

    /**
     * 追加步骤记录。
     *
     * @param record 步骤记录
     */
    public void addStepRecord(StepExecutionRecord record) {
        if (record != null) {
            this.stepRecords.add(record);
        }
    }

    /**
     * 是否可进件（PASS）。
     *
     * @return true 可进件
     */
    public boolean isPass() {
        return TotalResult.PASS == totalResult;
    }
}
