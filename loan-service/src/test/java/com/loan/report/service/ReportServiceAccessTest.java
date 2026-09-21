package com.loan.report.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.mapper.ClientRecycleConfigMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.report.dto.StaffReportDetail;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.reward.mapper.RewardRecordMapper;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceAccessTest {

    private ClientProfileMapper clientProfileMapper;
    private ClientScreeningMapper screeningMapper;
    private ReportQueryService reportQueryService;
    private ReportService service;

    @BeforeEach
    void setUp() {
        clientProfileMapper = mock(ClientProfileMapper.class);
        screeningMapper = mock(ClientScreeningMapper.class);
        reportQueryService = mock(ReportQueryService.class);
        service = new ReportService(
                mock(ServiceOrderMapper.class),
                mock(RewardRecordMapper.class),
                mock(LeadMapper.class),
                mock(LeadAllocationRecordMapper.class),
                clientProfileMapper,
                mock(ClientRecycleConfigMapper.class),
                mock(ClientLifecycleEventMapper.class),
                screeningMapper,
                mock(BankProductMapper.class),
                mock(StaffMapper.class),
                reportQueryService);
    }

    @Test
    @DisplayName("顾问只能读取本人归属客户的 Web 报告详情")
    void adviser_can_read_owned_client_report() {
        ClientScreening screening = screening("R-OWN", "CU-1");
        ClientProfile client = client("CU-1", "S-1");
        StaffReportDetail expected = new StaffReportDetail();
        expected.setReportNo("R-OWN");
        when(screeningMapper.selectOne(any())).thenReturn(screening);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(reportQueryService.staffDetail("R-OWN")).thenReturn(expected);

        StaffReportDetail actual = service.staffScreeningDetail("R-OWN", staff("S-1", "ADVISER", "D-1"));

        assertEquals("R-OWN", actual.getReportNo());
        verify(reportQueryService).staffDetail("R-OWN");
    }

    @Test
    @DisplayName("顾问读取他人归属客户报告时拒绝")
    void adviser_cannot_read_other_staff_client_report() {
        when(screeningMapper.selectOne(any())).thenReturn(screening("R-OTHER", "CU-2"));
        when(clientProfileMapper.selectOne(any())).thenReturn(client("CU-2", "S-2"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.staffScreeningDetail("R-OTHER", staff("S-1", "ADVISER", "D-1")));

        assertEquals("无权查看该客户报告", error.getMessage());
    }

    @Test
    @DisplayName("客户身份不能访问 Web 员工报告详情")
    void customer_cannot_read_staff_report() {
        LoanUser customer = staff("CU-1", "CUSTOMER", null);
        customer.setUserType(LoanUser.TYPE_CUSTOMER);

        assertThrows(BusinessException.class,
                () -> service.staffScreeningDetail("R-1", customer));
    }

    private ClientScreening screening(String reportNo, String clientCode) {
        ClientScreening screening = new ClientScreening();
        screening.setReportNo(reportNo);
        screening.setClientProfileCode(clientCode);
        return screening;
    }

    private ClientProfile client(String clientCode, String ownerStaffCode) {
        ClientProfile client = new ClientProfile();
        client.setClientCode(clientCode);
        client.setOwnerStaffCode(ownerStaffCode);
        return client;
    }

    private LoanUser staff(String userNo, String roleCode, String deptCode) {
        LoanUser user = new LoanUser();
        user.setUserNo(userNo);
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(roleCode);
        user.setDeptCode(deptCode);
        return user;
    }
}
