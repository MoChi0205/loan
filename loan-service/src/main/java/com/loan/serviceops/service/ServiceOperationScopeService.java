package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.org.entity.Department;
import com.loan.org.mapper.DepartmentMapper;
import com.loan.serviceops.security.ServiceListScope;
import com.loan.serviceops.security.ServiceOperationAccessPolicy;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 服务运营领域的数据范围校验，所有查询和写操作复用同一真值。 */
@Service
@RequiredArgsConstructor
public class ServiceOperationScopeService {

    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;
    private final DepartmentMapper departmentMapper;

    public ClientProfile requireClient(String clientCode) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户不能为空");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        return client;
    }

    public Staff requireActiveStaff(String staffCode) {
        Staff staff = findStaff(staffCode);
        if (staff == null || !"ACTIVE".equals(staff.getStatus())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "服务顾问不存在或已停用");
        }
        return staff;
    }

    public Staff findStaff(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }
        return staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode));
    }

    public Staff requireStaff(String staffCode) {
        Staff staff = findStaff(staffCode);
        if (staff == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在");
        }
        return staff;
    }

    public java.util.Map<String, ClientProfile> clientsByCodes(java.util.Collection<String> clientCodes) {
        if (clientCodes == null || clientCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                        .in(ClientProfile::getClientCode, clientCodes)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ClientProfile::getClientCode, item -> item, (a, b) -> a));
    }

    /** 业务 DTO 只向页面返回部门名称，部门编码仅保留作内部关联。 */
    public java.util.Map<String, String> departmentNames(java.util.Collection<String> deptCodes) {
        if (deptCodes == null || deptCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .in(Department::getDeptCode, deptCodes)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Department::getDeptCode, Department::getDeptName, (a, b) -> a));
    }

    public void requireStaffListAccess(LoanUser user) {
        if (!ServiceOperationAccessPolicy.canAccessInternalLists(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问公司内部服务名单");
        }
    }

    public void requireClientVisibleToStaff(LoanUser user, ClientProfile client) {
        requireStaffListAccess(user);
        ServiceListScope scope = ServiceOperationAccessPolicy.listScope(user);
        if (scope == ServiceListScope.COMPANY) {
            return;
        }
        if (scope == ServiceListScope.SELF) {
            // 公海客户没有归属人：只要该员工有内部名单权限即可申请查看，团队公海另校验部门。
            if (!StringUtils.hasText(client.getOwnerStaffCode())) {
                if ("DEPT_MANAGER".equalsIgnoreCase(user.getRoleCode())
                        && "TEAM".equalsIgnoreCase(client.getSeaLevel())
                        && StringUtils.hasText(client.getSeaDeptCode())
                        && !client.getSeaDeptCode().equals(user.getDeptCode())) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "只能访问本部门客户");
                }
                return;
            }
            if (!user.getUserNo().equals(client.getOwnerStaffCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能访问本人负责的客户");
            }
            return;
        }
        if (!StringUtils.hasText(client.getOwnerStaffCode())) {
            if (scope == ServiceListScope.DEPARTMENT
                    && "TEAM".equalsIgnoreCase(client.getSeaLevel())
                    && StringUtils.hasText(client.getSeaDeptCode())
                    && !client.getSeaDeptCode().equals(user.getDeptCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能访问本部门客户");
            }
            return;
        }
        Staff owner = requireStaff(client.getOwnerStaffCode());
        if (!StringUtils.hasText(user.getDeptCode()) || !user.getDeptCode().equals(owner.getDeptCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能访问本部门客户");
        }
    }

    public void applyAppointmentListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientAppointment> wrapper,
                                          LoanUser user) {
        applyAppointmentListScope(wrapper, user, null);
    }

    public void applyAppointmentListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientAppointment> wrapper,
                                          LoanUser user, String requestedScope) {
        applyAppointmentListScope(wrapper, user, requestedScope, null, null);
    }

    public void applyAppointmentListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientAppointment> wrapper,
                                          LoanUser user, String requestedScope,
                                          String requestedDeptCode, String requestedStaffCode) {
        requireStaffListAccess(user);
        ServiceListScope scope = resolveScope(user, requestedScope);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.ClientAppointment::getHostStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.ClientAppointment::getHostStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
        applyStaffFilter(wrapper, com.loan.serviceops.entity.ClientAppointment::getHostStaffCode,
                user, scope, requestedDeptCode, requestedStaffCode);
    }

    public void applyOutingListScope(LambdaQueryWrapper<com.loan.serviceops.entity.StaffOuting> wrapper,
                                     LoanUser user) {
        applyOutingListScope(wrapper, user, null);
    }

    public void applyOutingListScope(LambdaQueryWrapper<com.loan.serviceops.entity.StaffOuting> wrapper,
                                     LoanUser user, String requestedScope) {
        applyOutingListScope(wrapper, user, requestedScope, null, null);
    }

    public void applyOutingListScope(LambdaQueryWrapper<com.loan.serviceops.entity.StaffOuting> wrapper,
                                     LoanUser user, String requestedScope,
                                     String requestedDeptCode, String requestedStaffCode) {
        requireStaffListAccess(user);
        ServiceListScope scope = resolveScope(user, requestedScope);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.StaffOuting::getStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.StaffOuting::getStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
        applyStaffFilter(wrapper, com.loan.serviceops.entity.StaffOuting::getStaffCode,
                user, scope, requestedDeptCode, requestedStaffCode);
    }

    public void applyFollowListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientFollowRecord> wrapper,
                                     LoanUser user) {
        applyFollowListScope(wrapper, user, null);
    }

    public void applyFollowListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientFollowRecord> wrapper,
                                     LoanUser user, String requestedScope) {
        applyFollowListScope(wrapper, user, requestedScope, null, null);
    }

    public void applyFollowListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientFollowRecord> wrapper,
                                     LoanUser user, String requestedScope,
                                     String requestedDeptCode, String requestedStaffCode) {
        requireStaffListAccess(user);
        ServiceListScope scope = resolveScope(user, requestedScope);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.ClientFollowRecord::getStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.ClientFollowRecord::getStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
        applyStaffFilter(wrapper, com.loan.serviceops.entity.ClientFollowRecord::getStaffCode,
                user, scope, requestedDeptCode, requestedStaffCode);
    }

    public void applyOrderListScope(LambdaQueryWrapper<com.loan.order.entity.ServiceOrder> wrapper,
                                    LoanUser user) {
        applyOrderListScope(wrapper, user, null);
    }

    public void applyOrderListScope(LambdaQueryWrapper<com.loan.order.entity.ServiceOrder> wrapper,
                                    LoanUser user, String requestedScope) {
        applyOrderListScope(wrapper, user, requestedScope, null, null);
    }

    public void applyOrderListScope(LambdaQueryWrapper<com.loan.order.entity.ServiceOrder> wrapper,
                                    LoanUser user, String requestedScope,
                                    String requestedDeptCode, String requestedStaffCode) {
        requireStaffListAccess(user);
        ServiceListScope scope = resolveScope(user, requestedScope);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.order.entity.ServiceOrder::getOwnerStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.order.entity.ServiceOrder::getOwnerStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
        applyStaffFilter(wrapper, com.loan.order.entity.ServiceOrder::getOwnerStaffCode,
                user, scope, requestedDeptCode, requestedStaffCode);
    }

    /** 管理列表的部门/员工下钻筛选；服务端再次校验，禁止用查询参数越权。 */
    private <T> void applyStaffFilter(LambdaQueryWrapper<T> wrapper,
                                      com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> staffColumn,
                                      LoanUser user, ServiceListScope scope,
                                      String requestedDeptCode, String requestedStaffCode) {
        String deptCode = StringUtils.hasText(requestedDeptCode) ? requestedDeptCode.trim() : null;
        String staffCode = StringUtils.hasText(requestedStaffCode) ? requestedStaffCode.trim() : null;
        if (scope == ServiceListScope.SELF) {
            if ((staffCode != null && !staffCode.equals(user.getUserNo())) || deptCode != null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "本人数据范围不能筛选其他员工或团队");
            }
            return;
        }
        if (scope == ServiceListScope.DEPARTMENT) {
            if (deptCode != null && !deptCode.equals(user.getDeptCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能筛选本人所在团队");
            }
            deptCode = user.getDeptCode();
        }
        if (staffCode != null) {
            Staff staff = requireActiveStaff(staffCode);
            if (deptCode != null && !deptCode.equals(staff.getDeptCode())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "所选员工不属于所选团队");
            }
            wrapper.eq(staffColumn, staffCode);
        } else if (deptCode != null) {
            wrapper.inSql(staffColumn, "SELECT staff_code FROM t_staff WHERE dept_code='"
                    + safeSqlLiteral(deptCode) + "'");
        }
    }

    private String safeSqlLiteral(String value) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "部门数据范围无效");
        }
        return value;
    }

    /**
     * 页面可请求更窄的数据范围，但不能突破角色上限：顾问只能本人，经理可本人/本部门，
     * 公司管理角色可本人/本部门/全公司。非法或未授权范围直接拒绝，避免仅靠前端隐藏控件。
     */
    public ServiceListScope resolveScope(LoanUser user, String requestedScope) {
        ServiceListScope maximum = ServiceOperationAccessPolicy.listScope(user);
        if (!StringUtils.hasText(requestedScope)) return maximum;
        String value = requestedScope.trim().toUpperCase();
        ServiceListScope requested;
        try {
            requested = ServiceListScope.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "数据范围不合法");
        }
        if (requested == ServiceListScope.NONE || !isAllowedScope(maximum, requested)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色无权查看该数据范围");
        }
        return requested;
    }

    private boolean isAllowedScope(ServiceListScope maximum, ServiceListScope requested) {
        if (maximum == ServiceListScope.SELF) return requested == ServiceListScope.SELF;
        if (maximum == ServiceListScope.DEPARTMENT) return requested == ServiceListScope.SELF || requested == ServiceListScope.DEPARTMENT;
        return maximum == ServiceListScope.COMPANY
                && (requested == ServiceListScope.SELF || requested == ServiceListScope.DEPARTMENT || requested == ServiceListScope.COMPANY);
    }
}
