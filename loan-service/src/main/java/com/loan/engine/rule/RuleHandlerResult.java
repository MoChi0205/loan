package com.loan.engine.rule;

import com.loan.engine.enums.StepResult;
import lombok.Builder;
import lombok.Getter;

/**
 * 规则处理器执行结果（参考 mds RuleHandlerResult）。
 *
 * <p>携带步骤结果态、拒绝/跳过原因、取值快照；{@code chainTerminate} 用于整链短路放行。
 *
 * @author loan-platform
 */
@Getter
@Builder
public class RuleHandlerResult {

    /** 步骤结果态 */
    private final StepResult stepResult;

    /** 是否终止整条链（如白名单命中整链放行） */
    private final boolean chainTerminate;

    /** 取值快照 JSON（审计用：记录本步取到的实际值） */
    private final String querySnapshotJson;

    /** 拒绝/跳过原因 */
    private final String rejectReason;

    /** 通过 */
    public static RuleHandlerResult pass() {
        return RuleHandlerResult.builder().stepResult(StepResult.PASS).build();
    }

    /** 通过（携带取值快照） */
    public static RuleHandlerResult pass(String snapshotJson) {
        return RuleHandlerResult.builder().stepResult(StepResult.PASS).querySnapshotJson(snapshotJson).build();
    }

    /** 不通过 */
    public static RuleHandlerResult fail(String reason) {
        return RuleHandlerResult.builder().stepResult(StepResult.FAIL).rejectReason(reason).build();
    }

    /** 跳过 */
    public static RuleHandlerResult skip(String reason) {
        return RuleHandlerResult.builder().stepResult(StepResult.SKIP).rejectReason(reason).build();
    }

    /** 客群串访跳过 */
    public static RuleHandlerResult segmentMismatch(String reason) {
        return RuleHandlerResult.builder().stepResult(StepResult.SKIP_SEGMENT_MISMATCH).rejectReason(reason).build();
    }

    /** 执行异常 */
    public static RuleHandlerResult error(String reason) {
        return RuleHandlerResult.builder().stepResult(StepResult.ERROR).rejectReason(reason).build();
    }

    /** 整链短路放行（白名单等：命中即 PASS 并终止后续步骤） */
    public static RuleHandlerResult chainPass(String reason) {
        return RuleHandlerResult.builder().stepResult(StepResult.PASS).chainTerminate(true).rejectReason(reason).build();
    }
}
