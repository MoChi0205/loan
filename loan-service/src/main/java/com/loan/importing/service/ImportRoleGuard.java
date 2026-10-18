package com.loan.importing.service;

import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 批量导入、模板下载和结果导出的唯一角色守卫。 */
@Service
public class ImportRoleGuard {
    private static final Set<String> ROLES = new HashSet<>(Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER"));

    public void requireImportAdmin(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())
                || user.getRoleCode() == null || !ROLES.contains(user.getRoleCode().toUpperCase())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅老板或超级管理员可执行批量导入导出");
        }
    }

    /** 资源池下拉等只读查询：全体员工可用（渠道/小程序端除外）。 */
    public void requireStaff(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅公司员工可访问资源池信息");
        }
    }
}
