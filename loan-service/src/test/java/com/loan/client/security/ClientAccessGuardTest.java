package com.loan.client.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientAccessGuardTest {

    private ClientProfileMapper clientMapper;
    private StaffMapper staffMapper;
    private ClientAccessGuard guard;

    @BeforeEach
    void setUp() {
        clientMapper = mock(ClientProfileMapper.class);
        staffMapper = mock(StaffMapper.class);
        guard = new ClientAccessGuard(clientMapper, staffMapper);
    }

    @Test
    @DisplayName("顾问只能读写本人已归属客户")
    void adviserCanOnlyReadAndWriteOwnedClient() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", "A001", null, null));

        assertDoesNotThrow(() -> guard.requireReadable(staff("ADVISER", "A001", "D1"), "C001"));
        assertDoesNotThrow(() -> guard.requireWritable(staff("ADVISER", "A001", "D1"), "C001"));

        BusinessException readDenied = assertThrows(BusinessException.class,
                () -> guard.requireReadable(staff("ADVISER", "A002", "D1"), "C001"));
        BusinessException writeDenied = assertThrows(BusinessException.class,
                () -> guard.requireWritable(staff("ADVISER", "A002", "D1"), "C001"));
        assertEquals(2001, readDenied.getCode());
        assertEquals(2001, writeDenied.getCode());
    }

    @Test
    @DisplayName("顾问可读公司公海但不能直接编辑")
    void adviserCanReadEnterpriseSeaButCannotWrite() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", null, "ENTERPRISE", null));

        assertDoesNotThrow(() -> guard.requireReadable(staff("ADVISER", "A001", "D1"), "C001"));
        BusinessException denied = assertThrows(BusinessException.class,
                () -> guard.requireWritable(staff("ADVISER", "A001", "D1"), "C001"));
        assertEquals(2001, denied.getCode());
    }

    @Test
    @DisplayName("非员工身份在查询客户前即拒绝")
    void nonStaffIsDeniedBeforeClientLookup() {
        LoanUser customer = staff(null, "C001", null);
        customer.setUserType(LoanUser.TYPE_CUSTOMER);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> guard.requireReadable(customer, "C001"));

        assertEquals(2001, denied.getCode());
        verify(clientMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    @DisplayName("未知员工角色即使是客户归属人也默认拒绝")
    void unknownStaffRoleFailsClosed() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", "X001", null, null));

        LoanUser unknown = staff("UNKNOWN", "X001", "D1");
        assertThrows(BusinessException.class, () -> guard.requireReadable(unknown, "C001"));
        assertThrows(BusinessException.class, () -> guard.requireWritable(unknown, "C001"));
    }

    @Test
    @DisplayName("团队公海只允许对应部门经理读取")
    void teamSeaIsLimitedToMatchingManager() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", null, "TEAM", "D1"));

        assertDoesNotThrow(() -> guard.requireReadable(staff("DEPT_MANAGER", "M001", "D1"), "C001"));
        assertThrows(BusinessException.class,
                () -> guard.requireReadable(staff("DEPT_MANAGER", "M002", "D2"), "C001"));
        assertThrows(BusinessException.class,
                () -> guard.requireReadable(staff("ADVISER", "A001", "D1"), "C001"));
    }

    @Test
    @DisplayName("经理只读写本部门已归属客户")
    void managerIsLimitedToDepartment() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", "A001", null, null));
        when(staffMapper.selectOne(any(Wrapper.class))).thenReturn(owner("A001", "D1"));

        assertDoesNotThrow(() -> guard.requireReadable(staff("DEPT_MANAGER", "M001", "D1"), "C001"));
        assertDoesNotThrow(() -> guard.requireWritable(staff("DEPT_MANAGER", "M001", "D1"), "C001"));
        assertThrows(BusinessException.class,
                () -> guard.requireReadable(staff("DEPT_MANAGER", "M002", "D2"), "C001"));
        assertThrows(BusinessException.class,
                () -> guard.requireWritable(staff("DEPT_MANAGER", "M002", "D2"), "C001"));
    }

    @Test
    @DisplayName("公司管理角色可读写全公司客户")
    void companyRolesCanReadAndWriteAllClients() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", "A001", null, null));
        when(staffMapper.selectOne(any(Wrapper.class))).thenReturn(owner("A001", "D1"));

        for (String role : new String[]{"BOSS", "OPERATOR", "SUPER_ADMIN", "SUPER"}) {
            LoanUser user = staff(role, role + "001", "HQ");
            assertDoesNotThrow(() -> guard.requireReadable(user, "C001"));
            assertDoesNotThrow(() -> guard.requireWritable(user, "C001"));
        }
    }

    @Test
    @DisplayName("渠道和客户身份不能访问管理端客户对象")
    void nonStaffIsDenied() {
        when(clientMapper.selectOne(any(Wrapper.class))).thenReturn(client("C001", null, "ENTERPRISE", null));
        LoanUser channel = staff("CHANNEL", "CH001", null);
        channel.setUserType(LoanUser.TYPE_CHANNEL);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> guard.requireReadable(channel, "C001"));
        assertEquals(2001, denied.getCode());
    }

    private ClientProfile client(String code, String owner, String seaLevel, String seaDept) {
        ClientProfile client = new ClientProfile();
        client.setClientCode(code);
        client.setOwnerStaffCode(owner);
        client.setSeaLevel(seaLevel);
        client.setSeaDeptCode(seaDept);
        return client;
    }

    private Staff owner(String code, String dept) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setDeptCode(dept);
        return staff;
    }

    private LoanUser staff(String role, String code, String dept) {
        LoanUser user = new LoanUser();
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(role);
        user.setUserNo(code);
        user.setDeptCode(dept);
        return user;
    }
}
