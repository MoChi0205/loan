package com.loan.serviceops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.infrastructure.security.AesUtils;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.serviceops.model.ServiceMethod;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工外出的安全边界：不接受代录、禁止自审、审核范围限本部门、打卡必须有照片。
 */
class OutingServiceSecurityTest {

    /** 合法照片 fileKey（att + 32 位十六进制）。 */
    private static final String PHOTO_KEY = "att0123456789abcdef0123456789abcdef";

    private StaffOutingMapper outingMapper;
    private StaffMapper staffMapper;
    private AppointmentService appointmentService;
    private ServiceOperationScopeService scopeService;
    private ClientActivityService activityService;
    private OssStorageService ossStorageService;
    private OutingService service;

    @BeforeEach
    void setUp() {
        new AesUtils().setConfiguredKey("test-key-serviceops");
        outingMapper = mock(StaffOutingMapper.class);
        staffMapper = mock(StaffMapper.class);
        appointmentService = mock(AppointmentService.class);
        scopeService = mock(ServiceOperationScopeService.class);
        activityService = mock(ClientActivityService.class);
        ossStorageService = mock(OssStorageService.class);
        service = new OutingService(outingMapper, staffMapper, appointmentService,
                scopeService, activityService, new ObjectMapper(), ossStorageService);
    }

    @Test
    void departureStoresEncryptedPayloadAndPhotoThenStartsHomeVisit() {
        StaffOuting outing = outing("outing01", "staff01", "READY");
        when(outingMapper.selectOne(any())).thenReturn(outing);
        when(ossStorageService.objectExists(anyString())).thenReturn(true);
        when(outingMapper.depart(eq("outing01"), any(), any(), any(), eq("员工"))).thenReturn(1);

        service.depart("outing01", location(PHOTO_KEY), staff("staff01", "ADVISER"));

        ArgumentCaptor<String> ciphertext = ArgumentCaptor.forClass(String.class);
        verify(outingMapper).depart(eq("outing01"), any(), ciphertext.capture(), eq(PHOTO_KEY), eq("员工"));
        String stored = ciphertext.getValue();
        assertFalse(stored.contains("121.4737"));
        String decrypted = AesUtils.decrypt(stored);
        assertTrue(decrypted.contains("121.4737"));
        assertTrue(decrypted.contains("上海市测试位置"));
        verify(appointmentService).startService("appt01", staff("staff01", "ADVISER"));
    }

    @Test
    void checkInWithoutPhotoIsRejected() {
        StaffOuting outing = outing("outing01", "staff01", "READY");
        when(outingMapper.selectOne(any())).thenReturn(outing);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.depart("outing01", location(null), staff("staff01", "ADVISER")));
        assertTrue(e.getMessage().contains("现场照片"));
        verify(outingMapper, never()).depart(anyString(), any(), any(), any(), anyString());
    }

    @Test
    void checkInWithPhotoMissingInStorageIsRejected() {
        StaffOuting outing = outing("outing01", "staff01", "READY");
        when(outingMapper.selectOne(any())).thenReturn(outing);
        when(ossStorageService.objectExists(anyString())).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.depart("outing01", location(PHOTO_KEY), staff("staff01", "ADVISER")));
        assertTrue(e.getMessage().contains("打卡照片不存在"));
        verify(outingMapper, never()).depart(anyString(), any(), any(), any(), anyString());
    }

    @Test
    void checkInWithMalformedPhotoKeyIsRejected() {
        StaffOuting outing = outing("outing01", "staff01", "READY");
        when(outingMapper.selectOne(any())).thenReturn(outing);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.depart("outing01", location("../../etc/passwd"), staff("staff01", "ADVISER")));
        assertTrue(e.getMessage().contains("标识不合法"));
    }

    @Test
    void createRejectsProxySubmissionByAnotherStaff() {
        ClientAppointment appointment = appointment("staff01");
        when(appointmentService.requireAppointment("appt01")).thenReturn(appointment);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createRequest(), staff("staff02", "DEPT_MANAGER")));
        assertTrue(e.getMessage().contains("不接受他人代录"));
        verify(outingMapper, never()).insert(any());
    }

    @Test
    void createCreatesPendingReviewForHostStaff() {
        when(appointmentService.requireAppointment("appt01")).thenReturn(appointment("staff01"));
        when(outingMapper.selectCount(any())).thenReturn(0L);

        service.create(createRequest(), staff("staff01", "ADVISER"));

        ArgumentCaptor<StaffOuting> saved = ArgumentCaptor.forClass(StaffOuting.class);
        verify(outingMapper).insert(saved.capture());
        assertEquals("PENDING_REVIEW", saved.getValue().getStatus());
        assertEquals("staff01", saved.getValue().getStaffCode());
        assertTrue(saved.getValue().getSubmittedAt() != null);
    }

    @Test
    void reviewRejectsSelfApproval() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "PENDING_REVIEW"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.approve("outing01", "ok", staff("staff01", "DEPT_MANAGER")));
        assertTrue(e.getMessage().contains("不能审核本人提交的申请"));
        verify(outingMapper, never()).approve(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void reviewRejectsManagerOfAnotherDepartment() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "PENDING_REVIEW"));
        when(scopeService.findStaff("staff01")).thenReturn(staffEntity("staff01", "D1"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.approve("outing01", "ok", managerOf("staff09", "D2")));
        assertTrue(e.getMessage().contains("仅可审核本部门"));
        verify(outingMapper, never()).approve(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void reviewPassesForManagerOfSameDepartment() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "PENDING_REVIEW"));
        when(scopeService.findStaff("staff01")).thenReturn(staffEntity("staff01", "D1"));
        when(outingMapper.approve(eq("outing01"), eq("staff09"), anyString(), any(), any())).thenReturn(1);

        service.approve("outing01", "同意", managerOf("staff09", "D1"));

        verify(outingMapper).approve(eq("outing01"), eq("staff09"), eq("主管"), eq("同意"), any());
    }

    @Test
    void reviewRejectedWhenNotPendingReview() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "COMPLETED"));
        when(scopeService.findStaff("staff01")).thenReturn(staffEntity("staff01", "D1"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.approve("outing01", "ok", managerOf("staff09", "D1")));
        assertTrue(e.getMessage().contains("当前状态不可审核"));
    }

    @Test
    void rejectRequiresReason() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "PENDING_REVIEW"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.reject("outing01", "  ", managerOf("staff09", "D1")));
        assertTrue(e.getMessage().contains("驳回原因必填"));
    }

    @Test
    void resubmitOnlyForApplicantAndOnlyWhenRejected() {
        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "REJECTED"));

        BusinessException notOwner = assertThrows(BusinessException.class,
                () -> service.resubmit("outing01", createRequest(), staff("staff02", "ADVISER")));
        assertTrue(notOwner.getMessage().contains("只有申请人本人可以重新提交"));

        when(outingMapper.selectOne(any())).thenReturn(outing("outing01", "staff01", "READY"));
        BusinessException notRejected = assertThrows(BusinessException.class,
                () -> service.resubmit("outing01", createRequest(), staff("staff01", "ADVISER")));
        assertTrue(notRejected.getMessage().contains("只有被驳回的申请可以重新提交"));
    }

    private LocationCheckInRequest location(String photoKey) {
        LocationCheckInRequest request = new LocationCheckInRequest();
        request.setLatitude(new BigDecimal("31.2304"));
        request.setLongitude(new BigDecimal("121.4737"));
        request.setAccuracyMeters(new BigDecimal("18.5"));
        request.setLocationText("上海市测试位置");
        request.setCollectedAt(LocalDateTime.now());
        request.setPhotoFileKey(photoKey);
        return request;
    }

    private OutingCreateRequest createRequest() {
        OutingCreateRequest request = new OutingCreateRequest();
        request.setAppointmentNo("appt01");
        request.setDestination("客户公司");
        request.setPurpose("经营资料梳理");
        return request;
    }

    private ClientAppointment appointment(String hostStaffCode) {
        ClientAppointment appointment = new ClientAppointment();
        appointment.setAppointmentNo("appt01");
        appointment.setClientCode("client01");
        appointment.setHostStaffCode(hostStaffCode);
        appointment.setAppointmentType(ServiceMethod.HOME_VISIT.name());
        appointment.setStatus("CONFIRMED");
        appointment.setScheduledStart(LocalDateTime.now().plusHours(1));
        appointment.setScheduledEnd(LocalDateTime.now().plusHours(2));
        return appointment;
    }

    private StaffOuting outing(String no, String staffCode, String status) {
        StaffOuting outing = new StaffOuting();
        outing.setId(1L);
        outing.setOutingNo(no);
        outing.setStaffCode(staffCode);
        outing.setClientCode("client01");
        outing.setAppointmentNo("appt01");
        outing.setStatus(status);
        return outing;
    }

    private Staff staffEntity(String code, String deptCode) {
        Staff staff = new Staff();
        staff.setStaffCode(code);
        staff.setStaffName("员工");
        staff.setDeptCode(deptCode);
        return staff;
    }

    private LoanUser staff(String no, String roleCode) {
        LoanUser user = new LoanUser();
        user.setUserType("STAFF");
        user.setRoleCode(roleCode);
        user.setUserNo(no);
        user.setName("员工");
        return user;
    }

    private LoanUser managerOf(String no, String deptCode) {
        LoanUser user = staff(no, "DEPT_MANAGER");
        user.setName("主管");
        user.setDeptCode(deptCode);
        return user;
    }
}
