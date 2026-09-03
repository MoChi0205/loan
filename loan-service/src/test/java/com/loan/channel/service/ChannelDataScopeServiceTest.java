package com.loan.channel.service;

import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.service.ClientService;
import com.loan.common.ResultCode;
import com.loan.common.service.BusinessNameService;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.entity.ClientScreening;
import com.loan.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 渠道客户与报告详情的越权隔离测试。 */
class ChannelDataScopeServiceTest {

    private ClientProfileMapper clientMapper;
    private ClientScreeningMapper screeningMapper;
    private ClientService clientService;
    private ReportService reportService;
    private ChannelDataScopeService service;
    private LoanUser channel;

    @BeforeEach
    void setUp() {
        clientMapper = mock(ClientProfileMapper.class);
        screeningMapper = mock(ClientScreeningMapper.class);
        clientService = mock(ClientService.class);
        reportService = mock(ReportService.class);
        service = new ChannelDataScopeService(clientMapper, screeningMapper, clientService, reportService,
                mock(BusinessNameService.class));
        channel = new LoanUser();
        channel.setUserType(LoanUser.TYPE_CHANNEL);
        channel.setUserNo("channel-user-no");
    }

    @Test
    void rejectsOtherChannelClientBeforeLoadingDetail() {
        when(clientMapper.countChannelOwnedClient("channel-user-no", "client-other")).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.clientDetail("client-other", channel));
        assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(clientService);
    }

    @Test
    void permitsOwnedClientDetail() {
        when(clientMapper.countChannelOwnedClient("channel-user-no", "client-own")).thenReturn(1);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ownerStaffCode", "ADV001");
        detail.put("ownerStaffName", "张顾问");
        detail.put("personal", new LinkedHashMap<>(Collections.singletonMap("clientProfileCode", "client-own")));
        when(clientService.getClientDetail("client-own")).thenReturn(detail);

        Map<String, Object> result = service.clientDetail("client-own", channel);

        assertEquals("张顾问", result.get("ownerStaffName"));
        assertEquals(false, result.containsKey("ownerStaffCode"));
        assertEquals(false, ((Map<?, ?>) result.get("personal")).containsKey("clientProfileCode"));
        verify(clientService).getClientDetail("client-own");
    }

    @Test
    void clientBatchKeepsRequestOrderAndDropsForeignCodes() {
        ClientProfile first = client("client-a");
        ClientProfile second = client("client-b");
        when(clientMapper.selectChannelOwnedByCodes("channel-user-no", Arrays.asList("client-b", "client-x", "client-a")))
                .thenReturn(Arrays.asList(second, first));

        List<Map<String, Object>> result = service.clientBatch(
                Arrays.asList("client-b", "client-x", "client-a", "client-b"), channel);

        assertEquals(2, result.size());
        assertEquals("client-b", result.get(0).get("clientCode"));
        assertEquals("client-a", result.get(1).get("clientCode"));
    }

    @Test
    void rejectsOtherChannelReportBeforeLoadingDetail() {
        when(screeningMapper.countChannelOwnedReport("channel-user-no", "report-other")).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reportDetail("report-other", channel));
        assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(reportService);
    }

    @Test
    void permitsOwnedReportAndRemovesInternalLinkFields() {
        when(screeningMapper.countChannelOwnedReport("channel-user-no", "report-own")).thenReturn(1);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("clientProfileCode", "client-own");
        detail.put("matchTraceUuid", "trace-internal");
        detail.put("templateCode", "template-internal");
        when(reportService.screeningDetail("report-own")).thenReturn(detail);
        ClientProfile client = client("client-own");
        client.setOwnerStaffCode("ADV001");
        when(clientMapper.selectOne(any())).thenReturn(client);
        BusinessNameService nameService = mock(BusinessNameService.class);
        when(nameService.staffNames(Collections.singletonList("ADV001")))
                .thenReturn(Collections.singletonMap("ADV001", "张顾问"));
        service = new ChannelDataScopeService(clientMapper, screeningMapper, clientService, reportService,
                nameService);

        Map<String, Object> result = service.reportDetail("report-own", channel);

        assertEquals("张顾问", result.get("ownerStaffName"));
        assertEquals(false, result.containsKey("clientProfileCode"));
        assertEquals(false, result.containsKey("matchTraceUuid"));
        assertEquals(false, result.containsKey("templateCode"));
    }

    @Test
    void reportBatchKeepsRequestOrderAndDropsForeignCodes() {
        ClientScreening first = report("report-a");
        ClientScreening second = report("report-b");
        when(screeningMapper.selectChannelOwnedByReportNos("channel-user-no",
                Arrays.asList("report-b", "report-x", "report-a"))).thenReturn(Arrays.asList(second, first));

        List<Map<String, Object>> result = service.reportBatch(
                Arrays.asList("report-b", "report-x", "report-a", "report-b"), channel);

        assertEquals(2, result.size());
        assertEquals("report-b", result.get(0).get("reportNo"));
        assertEquals("report-a", result.get(1).get("reportNo"));
    }

    @Test
    void rejectsStaffUsingChannelEndpoint() {
        LoanUser staff = new LoanUser();
        staff.setUserType(LoanUser.TYPE_STAFF);
        staff.setUserNo("ADV001");
        assertThrows(BusinessException.class, () -> service.requireChannel(staff));
    }

    @Test
    void batchRejectsMoreThanOneHundredCodes() {
        List<String> codes = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            codes.add("client-" + i);
        }
        assertThrows(BusinessException.class, () -> service.clientBatch(codes, channel));
        verifyNoInteractions(clientMapper);
    }

    private ClientProfile client(String code) {
        ClientProfile client = new ClientProfile();
        client.setClientCode(code);
        client.setContactName(code);
        return client;
    }

    private ClientScreening report(String reportNo) {
        ClientScreening report = new ClientScreening();
        report.setReportNo(reportNo);
        return report;
    }
}
