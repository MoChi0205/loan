package com.loan.serviceops.security;

import com.loan.context.LoanUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceOperationAccessPolicyTest {

    @Test
    void internalListScopeFollowsConfirmedMatrix() {
        assertEquals(ServiceListScope.SELF, ServiceOperationAccessPolicy.listScope(staff("ADVISER")));
        assertEquals(ServiceListScope.DEPARTMENT, ServiceOperationAccessPolicy.listScope(staff("DEPT_MANAGER")));
        assertEquals(ServiceListScope.COMPANY, ServiceOperationAccessPolicy.listScope(staff("BOSS")));
        assertEquals(ServiceListScope.COMPANY, ServiceOperationAccessPolicy.listScope(staff("OPERATOR")));
        assertEquals(ServiceListScope.COMPANY, ServiceOperationAccessPolicy.listScope(staff("SUPER_ADMIN")));
        assertEquals(ServiceListScope.COMPANY, ServiceOperationAccessPolicy.listScope(staff("SUPER")));
    }

    @Test
    void channelAndCustomerAreExcludedFromInternalLists() {
        assertEquals(ServiceListScope.NONE, ServiceOperationAccessPolicy.listScope(user("CHANNEL", null, "channel01")));
        assertEquals(ServiceListScope.NONE, ServiceOperationAccessPolicy.listScope(user("CUSTOMER", null, "client01")));
        assertFalse(ServiceOperationAccessPolicy.canAccessInternalLists(user("CHANNEL", null, "channel01")));
    }

    @Test
    void onlyOutingOwnerCanCheckInOrReturn() {
        LoanUser adviser = user("STAFF", "ADVISER", "staff01");
        assertTrue(ServiceOperationAccessPolicy.canCheckOuting(adviser, "staff01"));
        assertFalse(ServiceOperationAccessPolicy.canCheckOuting(adviser, "staff02"));
        assertFalse(ServiceOperationAccessPolicy.canCheckOuting(user("CHANNEL", null, "staff01"), "staff01"));
    }

    @Test
    void listVisibilityDoesNotGrantWritePermission() {
        LoanUser manager = user("STAFF", "DEPT_MANAGER", "manager01");
        LoanUser boss = user("STAFF", "BOSS", "boss01");
        LoanUser customer = user("CUSTOMER", null, "client01");

        assertEquals(ServiceListScope.DEPARTMENT, ServiceOperationAccessPolicy.listScope(manager));
        assertEquals(ServiceListScope.COMPANY, ServiceOperationAccessPolicy.listScope(boss));
        assertFalse(ServiceOperationAccessPolicy.canOperateAppointment(manager, "client01", "staff01"));
        assertFalse(ServiceOperationAccessPolicy.canOperateAppointment(boss, "client01", "staff01"));
        assertTrue(ServiceOperationAccessPolicy.canOperateAppointment(customer, "client01", "staff01"));
        assertTrue(ServiceOperationAccessPolicy.canOperateAppointment(
                user("STAFF", "ADVISER", "staff01"), "client01", "staff01"));
    }

    private LoanUser staff(String role) {
        return user("STAFF", role, "staff01");
    }

    private LoanUser user(String type, String role, String no) {
        LoanUser user = new LoanUser();
        user.setUserType(type);
        user.setRoleCode(role);
        user.setUserNo(no);
        user.setDeptCode("dept01");
        return user;
    }
}
