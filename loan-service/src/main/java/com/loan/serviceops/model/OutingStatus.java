package com.loan.serviceops.model;

/** 无审批的员工上门外出状态。 */
public enum OutingStatus {
    DRAFT,
    READY,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
