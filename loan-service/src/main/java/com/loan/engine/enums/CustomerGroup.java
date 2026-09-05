package com.loan.engine.enums;

import lombok.Getter;

/**
 * 客群枚举（第 4 章定稿：身份选择后锁定，严格硬分流）。
 *
 * @author loan-platform
 */
@Getter
public enum CustomerGroup {

    /** 企业客群（当前主链路：企业税贷） */
    ENTERPRISE("ENTERPRISE", "企业"),

    /** 个人客群（骨架占位：个人可贷产品暂缓，仅预留表结构与规则） */
    PERSONAL("PERSONAL", "个人");

    /** 客群编码 */
    private final String code;

    /** 客群名称 */
    private final String name;

    CustomerGroup(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按编码解析（未命中返回 null）。
     *
     * @param code 客群编码
     * @return 客群枚举，未命中为 null
     */
    public static CustomerGroup fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CustomerGroup group : values()) {
            if (group.code.equals(code)) {
                return group;
            }
        }
        return null;
    }
}
