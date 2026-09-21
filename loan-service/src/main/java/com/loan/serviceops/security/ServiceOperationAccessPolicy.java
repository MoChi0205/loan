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

    /** 只有外出员工本人可以修改并重新提交被驳回的申请。 */
    public static boolean canResubmitOuting(LoanUser user, String outingStaffCode) {
        return canCheckOuting(user, outingStaffCode);
    }

    /**
     * 外出申请的审核资格。
     *
     * <p>两条硬规则：
     * <ol>
     *   <li><b>禁止自审</b>：任何人都不能审核自己提交的外出申请；</li>
     *   <li><b>范围限定</b>：部门经理只能审本部门，老板/运营/超管可审全公司；顾问无审核权。</li>
     * </ol>
     *
     * @param user            当前用户
     * @param outingStaffCode 申请人员工工号
     * @param outingDeptCode  申请人员工所属部门编码（可能为空，为空时部门经理不可审）
     * @return 允许审核返回 true
     */
    public static boolean canReviewOuting(LoanUser user, String outingStaffCode, String outingDeptCode) {
        if (user == null || !LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())) {
            return false;
        }
        String userNo = user.getUserNo();
        if (userNo == null || userNo.equals(outingStaffCode)) {
            return false;
        }
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (COMPANY_ROLES.contains(role)) {
            return true;
        }
        if ("DEPT_MANAGER".equals(role)) {
            String dept = user.getDeptCode();
            return dept != null && !dept.isEmpty() && dept.equals(outingDeptCode);
        }
        return false;
    }

    /**
     * 客户画像快照的复核资格。
     *
     * <p>画像复核是管理动作，与「生成」分离：生成由当前归属顾问本人完成，
     * 复核只给部门经理（本部门）与全公司角色，顾问不能自复核自己的画像。
     *
     * @param user          当前用户
     * @param ownerStaffCode 客户当前归属顾问工号
     * @param ownerDeptCode  归属顾问所属部门编码（可能为空，为空时部门经理不可复核）
     * @return 允许复核返回 true
     */
    public static boolean canReviewInsight(LoanUser user, String ownerStaffCode, String ownerDeptCode) {
        if (user == null || !LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())) {
            return false;
        }
        String userNo = user.getUserNo();
        if (userNo == null || userNo.equals(ownerStaffCode)) {
            return false;
        }
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        if (COMPANY_ROLES.contains(role)) {
            return true;
        }
        if ("DEPT_MANAGER".equals(role)) {
            String dept = user.getDeptCode();
            return dept != null && !dept.isEmpty() && dept.equals(ownerDeptCode);
        }
        return false;
    }
}
