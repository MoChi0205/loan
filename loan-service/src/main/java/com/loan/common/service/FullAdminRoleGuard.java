package com.loan.common.service;

import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/** 老板/超级管理员全权限动作守卫。 */
@Service
public class FullAdminRoleGuard {
    public boolean isFullAdmin(LoanUser user) {
        return user != null && LoanUser.TYPE_STAFF.equals(user.getUserType()) && user.getRoleCode() != null
                && Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER").contains(user.getRoleCode().toUpperCase());
    }
    public void requireFullAdmin(LoanUser user) {
        if (!isFullAdmin(user)) throw new BusinessException(ResultCode.FORBIDDEN, "该操作需老板或超级管理员审批");
    }
}
