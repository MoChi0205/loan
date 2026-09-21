package com.loan.serviceops.model;

/**
 * 员工上门外出状态机：本人提交申请 → 主管审核 → 出发/返回双打卡。
 *
 * <p>合法流转：
 * <ul>
 *   <li>DRAFT → PENDING_REVIEW / CANCELLED</li>
 *   <li>PENDING_REVIEW → READY（审核通过）/ REJECTED（驳回）/ CANCELLED</li>
 *   <li>REJECTED → PENDING_REVIEW（修改后重提）/ CANCELLED</li>
 *   <li>READY → IN_PROGRESS（出发打卡）/ CANCELLED</li>
 *   <li>IN_PROGRESS → COMPLETED（返回打卡）/ CANCELLED</li>
 * </ul>
 */
public final class OutingStateMachine {

    private OutingStateMachine() {
    }

    /**
     * 是否允许从当前状态流转到目标状态。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return 允许返回 true
     */
    public static boolean canTransition(OutingStatus current, OutingStatus target) {
        if (current == null || target == null || current.isTerminal()) {
            return false;
        }
        switch (current) {
            case DRAFT:
                return target == OutingStatus.PENDING_REVIEW || target == OutingStatus.CANCELLED;
            case PENDING_REVIEW:
                return target == OutingStatus.READY || target == OutingStatus.REJECTED
                        || target == OutingStatus.CANCELLED;
            case REJECTED:
                return target == OutingStatus.PENDING_REVIEW || target == OutingStatus.CANCELLED;
            case READY:
                return target == OutingStatus.IN_PROGRESS || target == OutingStatus.CANCELLED;
            case IN_PROGRESS:
                return target == OutingStatus.COMPLETED || target == OutingStatus.CANCELLED;
            default:
                return false;
        }
    }

    /** 仅「待审核」可被审核（通过或驳回）。 */
    public static boolean canReview(OutingStatus current) {
        return current == OutingStatus.PENDING_REVIEW;
    }

    /** 仅「待出发」可出发打卡。 */
    public static boolean canDepart(OutingStatus current) {
        return current == OutingStatus.READY;
    }

    /** 仅「外出中」可返回打卡。 */
    public static boolean canReturn(OutingStatus current) {
        return current == OutingStatus.IN_PROGRESS;
    }
}
