package com.loan.infrastructure.security;

import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 系统配置管理员角色边界测试。 */
class AdminRoleGuardTest {

    @Test
    void operatorAndBothSuperRolesCanManageSystemConfig() {
        assertTrue(AdminRoleGuard.isSystemConfigAdmin(staff("OPERATOR")));
        assertTrue(AdminRoleGuard.isSystemConfigAdmin(staff("SUPER_ADMIN")));
        assertTrue(AdminRoleGuard.isSystemConfigAdmin(staff("SUPER")));
        assertDoesNotThrow(() -> AdminRoleGuard.requireSystemConfigAdmin(staff("SUPER")));
    }

    @Test
    void bossAndBusinessRolesCannotManageSystemConfig() {
        assertFalse(AdminRoleGuard.isSystemConfigAdmin(staff("BOSS")));
        assertFalse(AdminRoleGuard.isSystemConfigAdmin(staff("DEPT_MANAGER")));
        assertFalse(AdminRoleGuard.isSystemConfigAdmin(staff("ADVISER")));
        assertThrows(BusinessException.class,
                () -> AdminRoleGuard.requireSystemConfigAdmin(staff("BOSS")));
    }

    @Test
    void nonStaffCannotManageSystemConfig() {
        LoanUser channel = staff("OPERATOR");
        channel.setUserType(LoanUser.TYPE_CHANNEL);
        assertFalse(AdminRoleGuard.isSystemConfigAdmin(channel));
        assertThrows(BusinessException.class,
                () -> AdminRoleGuard.requireSystemConfigAdmin(channel));
    }

    private LoanUser staff(String roleCode) {
        LoanUser user = new LoanUser();
        user.setUserNo("test-user");
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(roleCode);
        return user;
    }
}
