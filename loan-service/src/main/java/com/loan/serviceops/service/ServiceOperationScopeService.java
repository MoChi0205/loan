package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
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
            if (!user.getUserNo().equals(client.getOwnerStaffCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能访问本人负责的客户");
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
        requireStaffListAccess(user);
        ServiceListScope scope = ServiceOperationAccessPolicy.listScope(user);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.ClientAppointment::getHostStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.ClientAppointment::getHostStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
    }

    public void applyOutingListScope(LambdaQueryWrapper<com.loan.serviceops.entity.StaffOuting> wrapper,
                                     LoanUser user) {
        requireStaffListAccess(user);
        ServiceListScope scope = ServiceOperationAccessPolicy.listScope(user);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.StaffOuting::getStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.StaffOuting::getStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
    }

    public void applyFollowListScope(LambdaQueryWrapper<com.loan.serviceops.entity.ClientFollowRecord> wrapper,
                                     LoanUser user) {
        requireStaffListAccess(user);
        ServiceListScope scope = ServiceOperationAccessPolicy.listScope(user);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.serviceops.entity.ClientFollowRecord::getStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.serviceops.entity.ClientFollowRecord::getStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
    }

    public void applyOrderListScope(LambdaQueryWrapper<com.loan.order.entity.ServiceOrder> wrapper,
                                    LoanUser user) {
        requireStaffListAccess(user);
        ServiceListScope scope = ServiceOperationAccessPolicy.listScope(user);
        if (scope == ServiceListScope.SELF) {
            wrapper.eq(com.loan.order.entity.ServiceOrder::getOwnerStaffCode, user.getUserNo());
        } else if (scope == ServiceListScope.DEPARTMENT) {
            wrapper.inSql(com.loan.order.entity.ServiceOrder::getOwnerStaffCode,
                    "SELECT staff_code FROM t_staff WHERE dept_code='"
                            + safeSqlLiteral(user.getDeptCode()) + "'");
        }
    }

    private String safeSqlLiteral(String value) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "部门数据范围无效");
        }
        return value;
    }
}
