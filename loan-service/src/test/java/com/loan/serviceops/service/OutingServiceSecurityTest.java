package com.loan.serviceops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.context.LoanUser;
import com.loan.infrastructure.security.AesUtils;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutingServiceSecurityTest {

    private StaffOutingMapper outingMapper;
    private AppointmentService appointmentService;
    private OutingService service;

    @BeforeEach
    void setUp() {
        new AesUtils().setConfiguredKey("test-key-serviceops");
        outingMapper = mock(StaffOutingMapper.class);
        appointmentService = mock(AppointmentService.class);
        service = new OutingService(outingMapper, mock(StaffMapper.class), appointmentService,
                mock(ServiceOperationScopeService.class), mock(ClientActivityService.class), new ObjectMapper());
    }

    @Test
    void departureStoresEncryptedPayloadAndStartsHomeVisit() {
        StaffOuting outing = outing("outing01", "staff01");
        when(outingMapper.selectOne(any())).thenReturn(outing);
        when(outingMapper.depart(eq("outing01"), any(), any(), eq("员工"))).thenReturn(1);

        service.depart("outing01", location(), staff("staff01"));

        ArgumentCaptor<String> ciphertext = ArgumentCaptor.forClass(String.class);
        verify(outingMapper).depart(eq("outing01"), any(), ciphertext.capture(), eq("员工"));
        String stored = ciphertext.getValue();
        assertFalse(stored.contains("121.4737"));
        String decrypted = AesUtils.decrypt(stored);
        assertTrue(decrypted.contains("121.4737"));
        assertTrue(decrypted.contains("上海市测试位置"));
        verify(appointmentService).startService("appt01", staff("staff01"));
    }

    private LocationCheckInRequest location() {
        LocationCheckInRequest request = new LocationCheckInRequest();
        request.setLatitude(new BigDecimal("31.2304"));
        request.setLongitude(new BigDecimal("121.4737"));
        request.setAccuracyMeters(new BigDecimal("18.5"));
        request.setLocationText("上海市测试位置");
        request.setCollectedAt(LocalDateTime.now());
        return request;
    }

    private StaffOuting outing(String no, String staffCode) {
        StaffOuting outing = new StaffOuting();
        outing.setOutingNo(no);
        outing.setStaffCode(staffCode);
        outing.setClientCode("client01");
        outing.setAppointmentNo("appt01");
        outing.setStatus("READY");
        return outing;
    }

    private LoanUser staff(String no) {
        LoanUser user = new LoanUser();
        user.setUserType("STAFF");
        user.setRoleCode("ADVISER");
        user.setUserNo(no);
        user.setName("员工");
        return user;
    }
}
