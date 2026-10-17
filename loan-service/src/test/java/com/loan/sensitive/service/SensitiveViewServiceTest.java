package com.loan.sensitive.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.lead.mapper.LeadMapper;
import com.loan.notification.service.NotificationService;
import com.loan.sensitive.dto.SensitiveApplyViewResp;
import com.loan.sensitive.mapper.SensitiveViewApprovalMapper;
import com.loan.sensitive.mapper.SensitiveViewGrantMapper;
import com.loan.sensitive.mapper.SensitiveViewLogMapper;
import com.loan.serviceops.service.ServiceOperationScopeService;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import com.loan.exception.BusinessException;
import com.loan.sensitive.entity.SensitiveViewApproval;

/** 客户手机号按“员工 + 客户 + 日期”的首次解锁计数。 */
@ExtendWith(MockitoExtension.class)
class SensitiveViewServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void metadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), SensitiveViewApproval.class);
    }

    @Mock private SensitiveViewGrantMapper grantMapper;
    @Mock private SensitiveViewLogMapper logMapper;
    @Mock private SensitiveViewApprovalMapper approvalMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private ClientProfileMapper clientProfileMapper;
    @Mock private StaffMapper staffMapper;
    @Mock private NotificationService notificationService;
    @Mock private ServiceOperationScopeService scopeService;

    private SensitiveViewService service;
    private LoanUser adviser;

    @BeforeEach
    void setUp() {
        service = new SensitiveViewService(grantMapper, logMapper, approvalMapper, leadMapper,
                clientProfileMapper, staffMapper, notificationService, scopeService);
        adviser = new LoanUser();
        adviser.setUserNo("staff-adviser");
        adviser.setUserType(LoanUser.TYPE_STAFF);
        adviser.setRoleCode("ADVISER");
        adviser.setDeptCode("dept-1");
        lenient().when(staffMapper.lockByStaffCode("staff-adviser")).thenReturn(1L);
        ClientProfile client = new ClientProfile();
        client.setClientCode("client-1");
        client.setOwnerStaffCode("staff-adviser");
        lenient().when(clientProfileMapper.selectOne(any())).thenReturn(client);
        lenient().when(clientProfileMapper.selectPhonePlain("client-1")).thenReturn("13900000001");
    }

    @Test
    void firstClientUnlockTodayConsumesOneQuotaEvenWhenHistoricalGrantExists() {
        when(logMapper.countTodayClientView("staff-adviser", LocalDate.now(), "client-1"))
                .thenReturn(0L);
        when(logMapper.countTodayClientViews("staff-adviser", LocalDate.now()))
                .thenReturn(0L, 1L);

        SensitiveApplyViewResp response = service.applyClientView("client-1", adviser);

        assertTrue(response.isRevealed());
        assertEquals(1, response.getUsed());
        assertEquals(29, response.getRemaining());
        ArgumentCaptor<com.loan.sensitive.entity.SensitiveViewLog> captor =
                ArgumentCaptor.forClass(com.loan.sensitive.entity.SensitiveViewLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals("client-1", captor.getValue().getClientCode());
        verify(staffMapper).lockByStaffCode("staff-adviser");
        org.mockito.InOrder order = inOrder(staffMapper, clientProfileMapper, logMapper);
        order.verify(staffMapper).lockByStaffCode("staff-adviser");
        order.verify(clientProfileMapper).selectOne(any());
        order.verify(logMapper).countTodayClientViews("staff-adviser", LocalDate.now());
    }

    @Test
    void repeatedClientUnlockOnSameDayDoesNotConsumeQuotaAgain() {
        when(logMapper.countTodayClientView("staff-adviser", LocalDate.now(), "client-1"))
                .thenReturn(1L);
        when(logMapper.countTodayClientViews("staff-adviser", LocalDate.now())).thenReturn(1L);

        SensitiveApplyViewResp response = service.applyClientView("client-1", adviser);

        assertEquals(1, response.getUsed());
        assertEquals(29, response.getRemaining());
        verify(logMapper, never()).insert(any());
        verify(staffMapper).lockByStaffCode("staff-adviser");
    }

    @Test
    void foreignClientScopeIsRejectedBeforeReadingPlainPhone() {
        doThrow(new BusinessException(com.loan.common.ResultCode.FORBIDDEN, "只能访问本人负责的客户"))
                .when(scopeService).requireClientVisibleToStaff(eq(adviser), any());

        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.applyClientView("client-1", adviser));

        verify(clientProfileMapper, never()).selectPhonePlain(any());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void adviserOverLimitCreatesManagerApprovalWithoutRevealingPhone() {
        when(logMapper.countTodayClientView("staff-adviser", LocalDate.now(), "client-1")).thenReturn(0L);
        when(logMapper.countTodayClientViews("staff-adviser", LocalDate.now())).thenReturn(30L);

        SensitiveApplyViewResp response = service.applyClientView("client-1", adviser);

        org.junit.jupiter.api.Assertions.assertFalse(response.isRevealed());
        assertEquals("PENDING", response.getApprovalStatus());
        ArgumentCaptor<SensitiveViewApproval> approval = ArgumentCaptor.forClass(SensitiveViewApproval.class);
        verify(approvalMapper).insert(approval.capture());
        assertEquals("MANAGER_REVIEW", approval.getValue().getApprovalStage());
        assertEquals("staff-adviser", approval.getValue().getApplicantStaffCode());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void bossIsUnlimitedButStillLeavesDailyAuditLog() {
        adviser.setRoleCode("BOSS");

        SensitiveApplyViewResp response = service.applyClientView("client-1", adviser);

        assertTrue(response.isRevealed());
        assertEquals(-1, response.getLimit());
        verify(staffMapper).lockByStaffCode("staff-adviser");
        verify(logMapper).insert(any());
    }

    @Test
    void bossRepeatedViewDoesNotDuplicateDailyAudit() {
        adviser.setRoleCode("BOSS");
        when(logMapper.countTodayClientView("staff-adviser", LocalDate.now(), "client-1")).thenReturn(1L);
        assertTrue(service.applyClientView("client-1", adviser).isRevealed());
        verify(staffMapper).lockByStaffCode("staff-adviser");
        verify(logMapper, never()).insert(any());
    }

    @Test
    void malformedPhoneCannotBeRevealedOrConsumeQuota() {
        when(clientProfileMapper.selectPhonePlain("client-1")).thenReturn("encrypted-not-a-phone");
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.applyClientView("client-1", adviser));
        verify(logMapper, never()).insert(any());
    }

    @Test
    void managerOverLimitRequiresBossApproval() {
        adviser.setRoleCode("DEPT_MANAGER");
        when(logMapper.countTodayClientViews("staff-adviser", LocalDate.now())).thenReturn(30L);
        SensitiveApplyViewResp response = service.applyClientView("client-1", adviser);
        assertFalse(response.isRevealed());
        ArgumentCaptor<SensitiveViewApproval> approval = ArgumentCaptor.forClass(SensitiveViewApproval.class);
        verify(approvalMapper).insert(approval.capture());
        assertEquals("BOSS_REVIEW", approval.getValue().getApprovalStage());
        verify(logMapper, never()).insert(any());
    }

    @Test
    void approvalCannotBeSelfAuditedOrCrossDepartmentAudited() {
        SensitiveViewApproval approval = pendingApproval();
        when(approvalMapper.selectOne(any())).thenReturn(approval);
        adviser.setRoleCode("DEPT_MANAGER");
        assertThrows(BusinessException.class, () -> service.auditApproval("approval", true, null, adviser));
        adviser.setUserNo("other-manager"); adviser.setDeptCode("other-dept");
        assertThrows(BusinessException.class, () -> service.auditApproval("approval", true, null, adviser));
        verify(approvalMapper, never()).update(any(), any());
    }

    @Test
    void racingApprovalCannotNotifySuccessAfterStateChanged() {
        when(approvalMapper.selectOne(any())).thenReturn(pendingApproval());
        adviser.setRoleCode("DEPT_MANAGER"); adviser.setUserNo("manager");
        when(approvalMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.auditApproval("approval", true, null, adviser));
        verify(notificationService, never()).send(any());
    }

    private SensitiveViewApproval pendingApproval() {
        SensitiveViewApproval approval = new SensitiveViewApproval(); approval.setApprovalNo("approval");
        approval.setApplicantStaffCode("staff-adviser"); approval.setApplicantDeptCode("dept-1");
        approval.setApprovalStage("MANAGER_REVIEW"); approval.setApproveStatus("PENDING"); return approval;
    }
}
