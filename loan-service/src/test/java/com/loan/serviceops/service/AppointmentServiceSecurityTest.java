package com.loan.serviceops.service;

import com.loan.client.entity.ClientProfile;
import com.loan.context.LoanUser;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceSecurityTest {

    private ClientAppointmentMapper appointmentMapper;
    private StaffMapper staffMapper;
    private ServiceOperationScopeService scopeService;
    private ClientActivityService activityService;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        appointmentMapper = mock(ClientAppointmentMapper.class);
        staffMapper = mock(StaffMapper.class);
        ServiceOrderMapper orderMapper = mock(ServiceOrderMapper.class);
        scopeService = mock(ServiceOperationScopeService.class);
        activityService = mock(ClientActivityService.class);
        service = new AppointmentService(appointmentMapper, staffMapper, orderMapper, scopeService, activityService);
    }

    @Test
    void customerCannotOverrideClientHostOrInternalNote() {
        LoanUser user = customer("client-real");
        ClientProfile client = new ClientProfile();
        client.setClientCode("client-real");
        client.setOwnerStaffCode("staff-owner");
        Staff staff = new Staff();
        staff.setStaffCode("staff-owner");
        staff.setStatus("ACTIVE");
        when(scopeService.requireClient("client-real")).thenReturn(client);
        when(scopeService.requireActiveStaff("staff-owner")).thenReturn(staff);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);

        AppointmentCreateRequest request = validRequest();
        request.setClientCode("client-forged");
        request.setHostStaffCode("staff-forged");
        request.setOrderNo("order-forged");
        request.setInternalNote("should-not-pass");
        service.createForCustomer(request, user, "MINI_APP");

        ArgumentCaptor<ClientAppointment> captor = ArgumentCaptor.forClass(ClientAppointment.class);
        verify(appointmentMapper).insert(captor.capture());
        ClientAppointment inserted = captor.getValue();
        assertEquals("client-real", inserted.getClientCode());
        assertEquals("staff-owner", inserted.getHostStaffCode());
        assertEquals("CUSTOMER", inserted.getCreatedByType());
        assertEquals("CONFIRMED", inserted.getCustomerConfirmStatus());
        assertNull(inserted.getOrderNo());
        assertNull(inserted.getInternalNote());
    }

    @Test
    void staffCreatedAppointmentIsImmediatelyConfirmed() {
        ClientProfile client = new ClientProfile();
        client.setClientCode("client-real");
        client.setOwnerStaffCode("staff-owner");
        Staff staff = new Staff();
        staff.setStaffCode("staff-owner");
        staff.setStatus("ACTIVE");
        when(scopeService.requireClient("client-real")).thenReturn(client);
        org.mockito.Mockito.doNothing().when(scopeService).requireClientVisibleToStaff(any(), eq(client));
        when(scopeService.requireActiveStaff("staff-owner")).thenReturn(staff);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);

        LoanUser employee = new LoanUser();
        employee.setUserNo("staff-owner");
        employee.setUserType("STAFF");
        employee.setRoleCode("ADVISER");
        AppointmentCreateRequest request = validRequest();
        request.setClientCode("client-real");
        request.setHostStaffCode("staff-owner");

        service.createForStaff(request, employee);

        ArgumentCaptor<ClientAppointment> captor = ArgumentCaptor.forClass(ClientAppointment.class);
        verify(appointmentMapper).insert(captor.capture());
        assertEquals("CONFIRMED", captor.getValue().getStatus());
        assertEquals("CONFIRMED", captor.getValue().getCustomerConfirmStatus());
    }

    @Test
    void customerDtoUsesAdviserNameAndNoStaffCode() {
        LoanUser user = customer("client-real");
        ClientAppointment appointment = new ClientAppointment();
        appointment.setAppointmentNo("appt01");
        appointment.setClientCode("client-real");
        appointment.setHostStaffCode("staff-owner");
        appointment.setAppointmentType("PHONE_CONSULT");
        appointment.setStatus("CONFIRMED");
        appointment.setScheduledStart(LocalDateTime.now().plusHours(1));
        appointment.setScheduledEnd(LocalDateTime.now().plusHours(2));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ClientAppointment> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(Collections.singletonList(appointment));
        page.setTotal(1);
        when(appointmentMapper.selectPage(any(), any())).thenReturn(page);
        Staff staff = new Staff();
        staff.setStaffCode("staff-owner");
        staff.setStaffName("顾问张老师");
        when(staffMapper.selectList(any())).thenReturn(Collections.singletonList(staff));

        assertEquals("顾问张老师", service.mine(user, 1, 20).getRecords().get(0).getAdviserName());
    }

    private AppointmentCreateRequest validRequest() {
        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setServiceMethod("PHONE_CONSULT");
        request.setScheduledStart(LocalDateTime.now().plusDays(1));
        request.setScheduledEnd(LocalDateTime.now().plusDays(1).plusHours(1));
        return request;
    }

    private LoanUser customer(String no) {
        LoanUser user = new LoanUser();
        user.setUserNo(no);
        user.setUserType("CUSTOMER");
        user.setName("客户");
        return user;
    }
}
