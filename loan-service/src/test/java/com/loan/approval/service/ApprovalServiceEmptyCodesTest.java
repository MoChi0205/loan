package com.loan.approval.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.approval.entity.ContentApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.ContentApprovalMapper;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.approval.service.MaterialReviewService;
import com.loan.common.service.BusinessNameService;
import com.loan.mini.service.MiniClientService;
import com.loan.notification.service.NotificationService;
import com.loan.partner.service.PartnerProductService;
import com.loan.product.mapper.BankProductMapper;
import com.loan.report.mapper.ReportTemplateMapper;
import com.loan.sms.mapper.SmsTemplateMapper;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：待审切片（pendingSlice）在无待审记录时，codes 为空，
 * 必须跳过 smsTemplateMapper / reportTemplateMapper 的 .in(templateCode, 空codes) 查询，
 * 避免生成 WHERE template_code IN () 触发 MySQL SQLSyntaxErrorException。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceEmptyCodesTest {

    @Mock private ProductApprovalMapper productApprovalMapper;
    @Mock private AttachmentDownloadApprovalMapper downloadApprovalMapper;
    @Mock private MaterialReviewMapper materialReviewMapper;
    @Mock private MaterialReviewService materialReviewService;
    @Mock private BankProductMapper bankProductMapper;
    @Mock private MiniClientService miniClientService;
    @Mock private StaffMapper staffMapper;
    @Mock private BusinessNameService businessNameService;
    @Mock private PartnerProductService partnerProductService;
    @Mock private ContentApprovalMapper contentApprovalMapper;
    @Mock private SmsTemplateMapper smsTemplateMapper;
    @Mock private ReportTemplateMapper reportTemplateMapper;
    @Mock private NotificationService notificationService;

    private ApprovalService service;

    @BeforeEach
    void setUp() throws Exception {
        // @RequiredArgsConstructor 注入 final 依赖；enabledTypes 由 @Value 注入，
        // Mockito 不会设置，使用反射显式放开 SMS/REPORT 类型以便走公开入口验证。
        service = new ApprovalService(productApprovalMapper, downloadApprovalMapper,
                materialReviewMapper, materialReviewService, bankProductMapper, miniClientService,
                staffMapper, businessNameService, partnerProductService, contentApprovalMapper,
                smsTemplateMapper, reportTemplateMapper, notificationService);

        Field enabled = ApprovalService.class.getDeclaredField("enabledTypes");
        enabled.setAccessible(true);
        enabled.set(service, Arrays.asList(
                ApprovalService.TYPE_SMS_TEMPLATE,
                ApprovalService.TYPE_REPORT_TEMPLATE,
                ApprovalService.TYPE_ALLOCATION));
    }

    private void stubEmptyContentPage() {
        // 模拟该类型无待审记录：records 为空、total=0
        when(contentApprovalMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        // 空集合走 staffNames 仍须返回空 Map（证明 BusinessNameService 自身对空集已安全）
        when(businessNameService.staffNames(any())).thenReturn(Collections.emptyMap());
    }

    @Test
    void pendingEmptySmsTemplateSkipsInClause() {
        stubEmptyContentPage();

        Map<String, Object> result = service.unifiedPending(ApprovalService.TYPE_SMS_TEMPLATE, 1, 10);

        assertNotNull(result);
        assertEquals(0L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertTrue(records.isEmpty());

        // 关键断言：空 codes 时绝不应调用 .in(templateCode, 空) 查询
        verify(smsTemplateMapper, never()).selectList(any());
        verify(reportTemplateMapper, never()).selectList(any());
    }

    @Test
    void pendingEmptyReportTemplateSkipsInClause() {
        stubEmptyContentPage();

        Map<String, Object> result = service.unifiedPending(ApprovalService.TYPE_REPORT_TEMPLATE, 1, 10);

        assertNotNull(result);
        assertEquals(0L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertTrue(records.isEmpty());

        verify(smsTemplateMapper, never()).selectList(any());
        verify(reportTemplateMapper, never()).selectList(any());
    }
}
