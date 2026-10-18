package com.loan.common.constant;

/**
 * 角色编码常量（集中定义，避免散落魔法字符串导致 typo / 口径不一致）。
 *
 * <p>真源：{@code t_role.role_code} 与各 RoleGuard 的角色集合；
 * 本类只收口编码字面量，不替代各 Guard 的权限语义（审批人集合 / 可见范围仍以 Guard 为准）。</p>
 *
 * @author loan-platform
 */
public final class RoleCode {

    private RoleCode() {
    }

    /** 顾问 */
    public static final String ADVISER = "ADVISER";

    /** 部门经理 */
    public static final String DEPT_MANAGER = "DEPT_MANAGER";

    /** 老板 */
    public static final String BOSS = "BOSS";

    /** 运营 */
    public static final String OPERATOR = "OPERATOR";

    /** 超级管理员 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 超级（历史兼容编码） */
    public static final String SUPER = "SUPER";

    /** 渠道合作方 */
    public static final String CHANNEL = "CHANNEL";

    /** 客户（双客群） */
    public static final String CUSTOMER = "CUSTOMER";
}
