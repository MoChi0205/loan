package com.loan.serviceops.service;

import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.org.mapper.DepartmentMapper;
import com.loan.serviceops.security.ServiceListScope;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ServiceOperationScopeServiceTest {

    private final ServiceOperationScopeService service = new ServiceOperationScopeService(
            mock(ClientProfileMapper.class), mock(StaffMapper.class), mock(DepartmentMapper.class));

    @Test
    void adviserCanOnlySelectSelf() {
        LoanUser user = staff("ADVISER");
        assertEquals(ServiceListScope.SELF, service.resolveScope(user, "SELF"));
        assertThrows(BusinessException.class, () -> service.resolveScope(user, "DEPARTMENT"));
        assertThrows(BusinessException.class, () -> service.resolveScope(user, "COMPANY"));
    }

    @Test
    void managerCanSelectSelfOrDepartment() {
        LoanUser user = staff("DEPT_MANAGER");
        assertEquals(ServiceListScope.SELF, service.resolveScope(user, "SELF"));
        assertEquals(ServiceListScope.DEPARTMENT, service.resolveScope(user, "DEPARTMENT"));
        assertThrows(BusinessException.class, () -> service.resolveScope(user, "COMPANY"));
    }

    @Test
    void companyRoleCanNarrowItsScope() {
        LoanUser user = staff("BOSS");
        assertEquals(ServiceListScope.SELF, service.resolveScope(user, "SELF"));
        assertEquals(ServiceListScope.DEPARTMENT, service.resolveScope(user, "DEPARTMENT"));
        assertEquals(ServiceListScope.COMPANY, service.resolveScope(user, "COMPANY"));
    }

    @Test
    void invalidScopeIsRejected() {
        assertThrows(BusinessException.class, () -> service.resolveScope(staff("BOSS"), "OTHER"));
    }

    private LoanUser staff(String role) {
        LoanUser user = new LoanUser();
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setUserNo("STAFF001");
        user.setRoleCode(role);
        user.setDeptCode("CONSULT");
        return user;
    }
}
