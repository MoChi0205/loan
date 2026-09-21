package com.loan.serviceops.model;

/** 预约状态。改期保留原记录为 RESCHEDULED，并创建新预约。 */
public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    ARRIVED,
    SERVING,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW || this == RESCHEDULED;
    }
}
