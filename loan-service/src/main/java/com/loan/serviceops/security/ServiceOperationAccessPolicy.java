package com.loan.serviceops.security;

import com.loan.context.LoanUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 每日来访、预约、外出、待回访和工单名单的数据范围策略。
 * 列表可见范围不授予取消、打卡或异常处理等写权限。
 */
public final class ServiceOperationAccessPolicy {

    private static final Set<String> COMPANY_ROLES = new HashSet<>(Arrays.asList(
            "BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER"));

    private ServiceOperationAccessPolicy() {
    }

    public static ServiceListScope listScope(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())) {
            return ServiceListScope.NONE;
        }
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (COMPANY_ROLES.contains(role)) {
            return ServiceListScope.COMPANY;
        }
        if ("DEPT_MANAGER".equals(role)) {
            return ServiceListScope.DEPARTMENT;
        }
        if ("ADVISER".equals(role)) {
            return ServiceListScope.SELF;
        }
        return ServiceListScope.NONE;
    }

    public static boolean canAccessInternalLists(LoanUser user) {
        return listScope(user) != ServiceListScope.NONE;
    }

    /** 客户只能读取自己的预约；员工和渠道不能借用客户接口。 */
    public static boolean canReadCustomerAppointment(LoanUser user, String appointmentClientCode) {
        return user != null
                && LoanUser.TYPE_CUSTOMER.equalsIgnoreCase(user.getUserType())
                && user.getUserNo() != null
                && user.getUserNo().equals(appointmentClientCode);
    }

    /**
     * 普通预约操作只允许预约客户本人或主服务顾问本人。
     * 经理和全公司角色的列表可见范围不会自动转化成写权限。
     */
    public static boolean canOperateAppointment(LoanUser user,
                                                String appointmentClientCode,
                                                String hostStaffCode) {
        if (canReadCustomerAppointment(user, appointmentClientCode)) {
            return true;
        }
        return user != null
                && LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())
                && user.getUserNo() != null
                && user.getUserNo().equals(hostStaffCode);
    }

    /** 只有外出员工本人可以执行出发/返回打卡。 */
    public static boolean canCheckOuting(LoanUser user, String outingStaffCode) {
        return user != null
                && LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())
                && user.getUserNo() != null
                && user.getUserNo().equals(outingStaffCode);
    }
}
