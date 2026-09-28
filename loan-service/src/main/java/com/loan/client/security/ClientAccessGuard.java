package com.loan.client.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 管理端客户对象级权限守卫。
 *
 * <p>页面菜单和接口权限只决定“能否使用客户模块”，本守卫进一步决定当前员工能否
 * 读取或修改指定客户。所有按 {@code clientCode} 查询的管理端详情接口必须复用这里，
 * 不能依赖前端列表已经过滤。</p>
 */
@Service
@RequiredArgsConstructor
public class ClientAccessGuard {

    private static final Set<String> COMPANY_ROLES = new HashSet<>(Arrays.asList(
            "BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER"));

    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;

    /** 要求当前员工对客户具有只读权限，并返回已经校验过的客户。 */
    public ClientProfile requireReadable(LoanUser user, String clientCode) {
        requireStaffIdentity(user);
        ClientProfile client = requireClient(clientCode, false);
        if (!canRead(user, client, ownerDepartmentIfNeeded(user, client))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该客户资料");
        }
        return client;
    }

    /**
     * 要求当前员工对客户具有编辑权限，并锁定客户记录直到当前事务结束。
     * 调用方必须在事务中执行后续更新，避免权限校验后归属被并发改变。
     */
    public ClientProfile requireWritable(LoanUser user, String clientCode) {
        requireStaffIdentity(user);
        ClientProfile client = requireClient(clientCode, true);
        if (!canWrite(user, client, ownerDepartmentIfNeeded(user, client))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改该客户资料");
        }
        return client;
    }

    boolean canRead(LoanUser user, ClientProfile client, String ownerDeptCode) {
        String role = normalizedRole(user);
        if (COMPANY_ROLES.contains(role)) {
            return true;
        }
        if (!"ADVISER".equals(role) && !"DEPT_MANAGER".equals(role)) {
            return false;
        }
        if (isOwnedBy(user, client)) {
            return true;
        }
        if (!StringUtils.hasText(client.getOwnerStaffCode())) {
            // 未分配或公司公海对内部员工可读；团队公海只给对应部门经理。
            if ("TEAM".equalsIgnoreCase(client.getSeaLevel())) {
                return "DEPT_MANAGER".equals(role)
                        && sameDepartment(user.getDeptCode(), client.getSeaDeptCode());
            }
            return "ADVISER".equals(role) || "DEPT_MANAGER".equals(role);
        }
        return "DEPT_MANAGER".equals(role)
                && sameDepartment(user.getDeptCode(), ownerDeptCode);
    }

    boolean canWrite(LoanUser user, ClientProfile client, String ownerDeptCode) {
        String role = normalizedRole(user);
        if (COMPANY_ROLES.contains(role)) {
            return true;
        }
        if (!"ADVISER".equals(role) && !"DEPT_MANAGER".equals(role)) {
            return false;
        }
        if (isOwnedBy(user, client)) {
            return true;
        }
        // 公海客户必须先通过认领/分配流程建立归属，不能直接编辑档案。
        return StringUtils.hasText(client.getOwnerStaffCode())
                && "DEPT_MANAGER".equals(role)
                && sameDepartment(user.getDeptCode(), ownerDeptCode);
    }

    private ClientProfile requireClient(String clientCode, boolean lockForUpdate) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户编码必填");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode.trim())
                .last(lockForUpdate ? "limit 1 for update" : "limit 1"));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        return client;
    }

    private void requireStaffIdentity(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equalsIgnoreCase(user.getUserType())
                || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前身份无权访问客户资料");
        }
    }

    private String ownerDepartmentIfNeeded(LoanUser user, ClientProfile client) {
        if (!"DEPT_MANAGER".equals(normalizedRole(user))
                || isOwnedBy(user, client)
                || !StringUtils.hasText(client.getOwnerStaffCode())) {
            return null;
        }
        Staff owner = staffMapper.selectOne(new QueryWrapper<Staff>()
                .select("staff_code", "dept_code")
                .eq("staff_code", client.getOwnerStaffCode())
                .last("limit 1"));
        return owner == null ? null : owner.getDeptCode();
    }

    private boolean isOwnedBy(LoanUser user, ClientProfile client) {
        return user != null && StringUtils.hasText(user.getUserNo())
                && user.getUserNo().equals(client.getOwnerStaffCode());
    }

    private boolean sameDepartment(String left, String right) {
        return StringUtils.hasText(left) && left.equalsIgnoreCase(right);
    }

    private String normalizedRole(LoanUser user) {
        return user == null || user.getRoleCode() == null ? "" : user.getRoleCode().trim().toUpperCase();
    }
}
