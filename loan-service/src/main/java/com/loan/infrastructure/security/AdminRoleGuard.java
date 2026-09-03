package com.loan.infrastructure.security;

import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 管理端系统配置角色守卫。
 *
 * <p>业务全量角色（BOSS）与系统配置管理员必须分离：老板可查看全公司业务并完成业务审批，
 * 但角色、菜单、接口授权以及组织维护仅允许运营管理员和两种超级管理员角色操作。</p>
 */
public final class AdminRoleGuard {

    private static final Set<String> SYSTEM_CONFIG_ROLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("OPERATOR", "SUPER_ADMIN", "SUPER")));

    private AdminRoleGuard() {
    }

    /** 判断当前用户是否为系统配置管理员。 */
    public static boolean isSystemConfigAdmin(LoanUser user) {
        return user != null
                && LoanUser.TYPE_STAFF.equals(user.getUserType())
                && user.getRoleCode() != null
                && SYSTEM_CONFIG_ROLES.contains(user.getRoleCode().trim().toUpperCase());
    }

    /** 校验系统配置权限，失败时交由全局异常处理器返回统一 403 结果。 */
    public static void requireSystemConfigAdmin(LoanUser user) {
        if (!isSystemConfigAdmin(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅运营管理员或超级管理员可维护系统配置");
        }
    }
}
