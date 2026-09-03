package com.loan.mini.service;

import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 角色校验守卫。
 *
 * <p>复刻 {@code MiniClientController} 既有 {@code requireStaff} / {@code requireApprover}
 * 校验逻辑，供审批中心统一调用，避免在各处重复书写权限校验。</p>
 *
 * <p>本类只做权限判定，不引入 Spring Security，保持与现有 Controller 一致的
 * {@link BusinessException} + {@link ResultCode} 报错风格。</p>
 */
@Service
public class MiniRoleGuard {

    /**
     * ALLOCATION 审批分配管理员角色白名单。
     *
     * <p>D39 业务变更：团队管理者（DEPT_MANAGER）纳入分配审批人，但仅可审批<b>本人团队</b>
     * （申请人部门 == 本人部门）的客户；跨团队待审单由 BOSS 等上级审批（见
     * {@code ClientAllocationService} 的团队范围校验）。</p>
     */
    private static final List<String> APPROVER_ROLES =
            Arrays.asList("OPERATOR", "SUPER_ADMIN", "SUPER", "BOSS", "DEPT_MANAGER");

    /** 普通审批（PRODUCT / DOWNLOAD 等）角色白名单，含部门经理。 */
    private static final List<String> APPROVAL_ROLES =
            Arrays.asList("BOSS", "DEPT_MANAGER", "OPERATOR", "SUPER_ADMIN", "SUPER");

    /**
     * 校验当前用户为已登录的企业员工。
     *
     * <p>渠道合作方、非员工、未登录均拒绝。</p>
     *
     * @param user 当前登录用户，允许为 {@code null}
     */
    public void requireStaff(LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可查看或分配客户");
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅企业员工可替客户操作");
        }
    }

    /**
     * 校验当前用户为审批分配管理员（运营管理员 / 超级管理员 / 部门经理）。
     *
     * @param user 当前登录用户，允许为 {@code null}
     */
    public void requireApprover(LoanUser user) {
        requireStaff(user);
        String code = user.getRoleCode() == null ? "" : String.valueOf(user.getRoleCode()).toUpperCase();
        if (!APPROVER_ROLES.contains(code)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理角色可审批分配");
        }
    }

    /**
     * 按审批类型校验权限。
     *
     * <ul>
     *   <li>{@code ALLOCATION}：仅审批分配管理员（{@link #requireApprover}）。</li>
     *   <li>其他（PRODUCT / DOWNLOAD 等）：企业员工 + 含部门经理的审批角色。</li>
     * </ul>
     *
     * @param type 审批类型，例如 {@code ALLOCATION} / {@code PRODUCT} / {@code DOWNLOAD}
     * @param user 当前登录用户，允许为 {@code null}
     */
    public void requireApproverFor(String type, LoanUser user) {
        if ("ALLOCATION".equals(type)) {
            requireApprover(user);
            return;
        }
        requireStaff(user);
        String code = user.getRoleCode() == null ? "" : String.valueOf(user.getRoleCode()).toUpperCase();
        if (!APPROVAL_ROLES.contains(code)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无审批权限");
        }
    }
}
