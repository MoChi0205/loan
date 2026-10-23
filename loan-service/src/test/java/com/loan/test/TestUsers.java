package com.loan.test;

import com.loan.common.constant.RoleCode;
import com.loan.context.LoanUser;

import java.lang.reflect.Field;

/**
 * 测试用登录态构造器：生成"真实"的 LoanUser（非 mock），避免以下问题：
 * <ul>
 *   <li>端点回显 @CurrentUser 对象时 Jackson 序列化 mock 内部字段导致 500；</li>
 *   <li>mock getter 返回 null 触发控制器"请先登录 / 仅员工可操作"等误拦。</li>
 * </ul>
 *
 * <p><b>角色必须用 {@link RoleCode} 里的真实编码</b>。历史版本把员工默认角色写成 {@code "STAFF"}，
 * 但该值不在 {@code t_role.role_code} 与 {@link RoleCode} 中，导致所有用它构造的用例实际在测一个
 * 不存在的角色——角色白名单（如 BOSS/SUPER_ADMIN/SUPER）全部判定为「不匹配」，
 * 权限分支测的是错的路径。已统一收敛为真实角色编码。</p>
 *
 * <p>部门约定：顾问与经理同属 {@code D001}（可测「经理看本部门」），
 * {@link #adviserOtherDept()} 属 {@code D002}（用于测跨部门越权）。</p>
 */
public class TestUsers {

    /** 部门：顾问与部门经理同属，便于验证「经理可见本部门」。 */
    public static final String DEPT_MAIN = "D001";

    /** 部门：与 DEPT_MAIN 不同，用于验证跨部门越权被拒。 */
    public static final String DEPT_OTHER = "D002";

    /**
     * 默认员工（工号 T001，顾问，D001 部门）。
     *
     * <p>保留该方法以兼容存量用例；角色已从历史错误值 "STAFF" 修正为 {@link RoleCode#ADVISER}。</p>
     */
    public static LoanUser staffUser() {
        LoanUser u = staff("T001", RoleCode.ADVISER, DEPT_MAIN);
        u.setName("tester");
        return u;
    }

    /** 顾问（本人归属视角）。 */
    public static LoanUser adviser() {
        return staff("A001", RoleCode.ADVISER, DEPT_MAIN);
    }

    /** 另一名顾问，同部门——用于验证「他人数据不可读写」。 */
    public static LoanUser adviserPeer() {
        return staff("A002", RoleCode.ADVISER, DEPT_MAIN);
    }

    /** 跨部门顾问（D002）——用于验证跨部门越权被拒。 */
    public static LoanUser adviserOtherDept() {
        return staff("A003", RoleCode.ADVISER, DEPT_OTHER);
    }

    /** 部门经理（与顾问同部门 D001）。 */
    public static LoanUser manager() {
        return staff("M001", RoleCode.DEPT_MANAGER, DEPT_MAIN);
    }

    /** 跨部门经理（D002）——用于验证经理也不可跨部门。 */
    public static LoanUser managerOtherDept() {
        return staff("M002", RoleCode.DEPT_MANAGER, DEPT_OTHER);
    }

    /** 老板。 */
    public static LoanUser boss() {
        return staff("B001", RoleCode.BOSS, "BOSS_DIRECT");
    }

    /** 运营。 */
    public static LoanUser operator() {
        return staff("O001", RoleCode.OPERATOR, "OPERATION");
    }

    /** 超级管理员。 */
    public static LoanUser superAdmin() {
        return staff("S001", RoleCode.SUPER_ADMIN, "BOSS_DIRECT");
    }

    /** 超级（历史兼容编码）。 */
    public static LoanUser superRole() {
        return staff("SU001", RoleCode.SUPER, "BOSS_DIRECT");
    }

    /**
     * 通用员工构造器。
     *
     * @param staffNo  工号
     * @param roleCode {@link RoleCode} 中的真实角色编码
     * @param deptCode 部门编码
     */
    public static LoanUser staff(String staffNo, String roleCode, String deptCode) {
        LoanUser u = new LoanUser();
        u.setUserId(1L);
        u.setUserNo(staffNo);
        u.setPhone("13800000000");
        u.setName("tester-" + staffNo);
        u.setUserType(LoanUser.TYPE_STAFF);
        u.setClientType("WEB");
        u.setRoleCode(roleCode);
        u.setRegion("cn");
        u.setReferrerNo("R001");
        u.setReferrerName("referrer");
        u.setAvatar("");
        u.setDeptCode(deptCode);
        u.setInvitedFlag(false);
        return u;
    }

    /** 渠道合作方（数据范围按 bankChannel 硬隔离）。 */
    public static LoanUser channelUser() {
        LoanUser u = new LoanUser();
        u.setUserId(3L);
        u.setUserNo("CH001");
        u.setPhone("13911112222");
        u.setName("渠道甲");
        u.setUserType(LoanUser.TYPE_CHANNEL);
        u.setClientType("WEB");
        u.setRoleCode(RoleCode.CHANNEL);
        u.setRegion("cn");
        u.setBankChannelId(1L);
        u.setBankChannelCode("BC001");
        return u;
    }

    /** 客户（H5 / 小程序端）。 */
    public static LoanUser customerUser() {
        LoanUser u = new LoanUser();
        u.setUserId(2L);
        u.setUserNo("CU_TEST_001");
        u.setPhone("13900000000");
        u.setName("customer");
        u.setUserType(LoanUser.TYPE_CUSTOMER);
        u.setClientType("MINI_APP");
        u.setRoleCode(RoleCode.CUSTOMER);
        u.setRegion("cn");
        return u;
    }

    /** 全部真实员工角色，供权限矩阵类用例参数化遍历。 */
    public static LoanUser[] allStaffRoles() {
        return new LoanUser[]{adviser(), manager(), boss(), operator(), superAdmin(), superRole()};
    }

    private static void set(Object o, String field, Object val) {
        try {
            Field f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(o, val);
        } catch (Exception ignored) {
            // 字段不存在则跳过
        }
    }
}
