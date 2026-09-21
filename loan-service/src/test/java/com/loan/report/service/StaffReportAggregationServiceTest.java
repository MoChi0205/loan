package com.loan.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.approval.entity.MaterialReview;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniMatchService;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.report.dto.StaffAggregatedReport;
import com.loan.report.dto.StaffReportDetail;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientSubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffReportAggregationServiceTest {

    private ReportService reportService;
    private MiniMatchService miniMatchService;
    private ClientScreeningMapper screeningMapper;
    private ClientProfileMapper profileMapper;
    private PersonalProfileMapper personalProfileMapper;
    private ClientSubmissionMapper submissionMapper;
    private MaterialReviewMapper materialReviewMapper;
    private StaffReportAggregationService service;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        miniMatchService = mock(MiniMatchService.class);
        screeningMapper = mock(ClientScreeningMapper.class);
        profileMapper = mock(ClientProfileMapper.class);
        personalProfileMapper = mock(PersonalProfileMapper.class);
        submissionMapper = mock(ClientSubmissionMapper.class);
        materialReviewMapper = mock(MaterialReviewMapper.class);
        service = new StaffReportAggregationService(reportService, miniMatchService, screeningMapper,
                profileMapper, personalProfileMapper, submissionMapper, materialReviewMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("按报告编号聚合五个内部模块并复用现有匹配结果")
    void aggregates_internal_report_modules() {
        LoanUser user = staff("S-1");
        StaffReportDetail summary = new StaffReportDetail();
        summary.setReportNo("R-1");
        summary.setClientProfileCode("CU-1");
        summary.setProductCount(1);
        when(reportService.staffScreeningDetail("R-1", user)).thenReturn(summary);

        ClientScreening screening = new ClientScreening();
        screening.setReportNo("R-1");
        screening.setClientProfileCode("CU-1");
        screening.setMatchTraceUuid("TRACE-1");
        when(screeningMapper.selectOne(any())).thenReturn(screening);

        ClientProfile profile = new ClientProfile();
        profile.setClientCode("CU-1");
        profile.setEnterpriseName("测试企业");
        profile.setPhone("13812345678");
        profile.setCreditCode("913100001234567890");
        when(profileMapper.selectOne(any())).thenReturn(profile);

        ClientSubmission submission = new ClientSubmission();
        submission.setSubmissionNo("SUB-1");
        submission.setStatus("MATCHED");
        submission.setDataJson("{\"_ocrMeta\":{\"version\":2}}");
        when(submissionMapper.selectList(any())).thenReturn(Collections.singletonList(submission));

        MaterialReview review = new MaterialReview();
        review.setReviewStatus("APPROVED");
        review.setReviewTime(LocalDateTime.now());
        when(materialReviewMapper.selectList(any())).thenReturn(Collections.singletonList(review));

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("reportNo", "R-1");
        when(miniMatchService.reportDiagnosis("R-1", null)).thenReturn(analysis);
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("productCode", "P-1");
        product.put("productName", "内部产品");
        product.put("hitResult", "CONDITION");
        when(miniMatchService.reportProducts("R-1")).thenReturn(Collections.singletonList(product));

        StaffAggregatedReport result = service.aggregate("R-1", user);

        assertEquals("R-1", result.getReportSummary().getReportNo());
        assertEquals("测试企业", result.getClientProfile().getEnterpriseName());
        assertEquals("138****5678", result.getClientProfile().getContactPhoneMasked());
        assertEquals("v2", result.getMaterialStatus().getMaterialVersion());
        assertEquals(1, result.getMaterialStatus().getApprovedCount());
        assertEquals("R-1", result.getBusinessAnalysis().get("reportNo"));
        assertEquals("P-1", result.getMatchedProducts().get(0).getProductCode());
        assertTrue(result.getDataSourceNotice().contains("未调用外部个人信息查询接口"));
        verify(miniMatchService).reportProducts("R-1");
    }

    @Test
    @DisplayName("权限校验失败后不得查询任何聚合数据")
    void rejects_before_loading_aggregate_data() {
        LoanUser user = staff("S-2");
        when(reportService.staffScreeningDetail("R-DENY", user))
                .thenThrow(new BusinessException(com.loan.common.ResultCode.FORBIDDEN, "无权查看该客户报告"));

        assertThrows(BusinessException.class, () -> service.aggregate("R-DENY", user));

        verifyNoInteractions(screeningMapper, profileMapper, submissionMapper, materialReviewMapper, miniMatchService);
    }

    private LoanUser staff(String staffCode) {
        LoanUser user = new LoanUser();
        user.setUserNo(staffCode);
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode("ADVISER");
        return user;
    }
}
