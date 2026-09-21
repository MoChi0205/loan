package com.loan.serviceops.security;

/** 内部名单查询范围；NONE 表示不能访问内部名单。 */
public enum ServiceListScope {
    NONE,
    SELF,
    DEPARTMENT,
    COMPANY
}
