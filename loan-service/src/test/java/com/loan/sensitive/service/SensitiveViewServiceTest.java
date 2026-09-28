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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客户手机号按“员工 + 客户 + 日期”的首次解锁计数。 */
@ExtendWith(MockitoExtension.class)
class SensitiveViewServiceTest {

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
        ClientProfile client = new ClientProfile();
        client.setClientCode("client-1");
        client.setOwnerStaffCode("staff-adviser");
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(clientProfileMapper.selectPhonePlain("client-1")).thenReturn("13900000001");
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
    }
}
