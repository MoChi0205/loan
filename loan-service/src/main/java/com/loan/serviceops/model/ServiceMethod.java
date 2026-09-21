package com.loan.serviceops.model;

/** 客户预约与履约的四种服务方式。 */
public enum ServiceMethod {
    COMPANY_ON_SITE,
    HOME_VISIT,
    VIDEO_MEETING,
    PHONE_CONSULT;

    /** 只有客户到公司现场才计入来访/到店。 */
    public boolean countsAsOnSiteVisit() {
        return this == COMPANY_ON_SITE;
    }

    /** 只有员工上门拜访才计入员工外出。 */
    public boolean countsAsStaffOuting() {
        return this == HOME_VISIT;
    }
}
