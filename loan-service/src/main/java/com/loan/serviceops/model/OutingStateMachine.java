package com.loan.serviceops.model;

/** 员工上门外出状态机：无需审批，但必须完成出发和返回双打卡。 */
public final class OutingStateMachine {

    private OutingStateMachine() {
    }

    public static boolean canTransition(OutingStatus current, OutingStatus target) {
        if (current == null || target == null || current.isTerminal()) {
            return false;
        }
        switch (current) {
            case DRAFT:
                return target == OutingStatus.READY || target == OutingStatus.CANCELLED;
            case READY:
                return target == OutingStatus.IN_PROGRESS || target == OutingStatus.CANCELLED;
            case IN_PROGRESS:
                return target == OutingStatus.COMPLETED || target == OutingStatus.CANCELLED;
            default:
                return false;
        }
    }
}
