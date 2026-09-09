package com.loan.approval.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.approval.mapper.ContentApprovalMapper;
import com.loan.sms.mapper.SmsTemplateMapper;
import com.loan.report.mapper.ReportTemplateMapper;
import com.loan.notification.service.NotificationService;
import com.loan.common.service.BusinessNameService;
import com.loan.mini.service.MiniClientService;
import com.loan.partner.service.PartnerProductService;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.staff.mapper.StaffMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 产品审批与合作库联动回归。 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceProductTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ProductApproval.class);
        TableInfoHelper.initTableInfo(assistant, BankProduct.class);
    }

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
    void setUp() {
        service = new ApprovalService(productApprovalMapper, downloadApprovalMapper, materialReviewMapper,
                materialReviewService, bankProductMapper, miniClientService, staffMapper,
                businessNameService, partnerProductService, contentApprovalMapper, smsTemplateMapper,
                reportTemplateMapper, notificationService);
    }

    @Test
    @DisplayName("产品终审通过：CAS 更新后同步全量库并激活合作库")
    void productAudit_activatesPartnerProduct() {
        ProductApproval approval = new ProductApproval();
        approval.setApprovalNo("papr001");
        approval.setBankProductCode("product001");
        approval.setApproveStatus("PENDING");
        approval.setAfterSnapshotJson("{\"cooperateUntil\":\"2027-09-30\"}");
        when(productApprovalMapper.selectOne(any())).thenReturn(approval);
        when(productApprovalMapper.update(isNull(), any())).thenReturn(1);
        BankProduct product = new BankProduct();
        product.setProductCode("product001");
        product.setStatus("PENDING");
        when(bankProductMapper.selectOne(any())).thenReturn(product);

        service.productAudit("papr001", true, "通过", "boss001");

        verify(bankProductMapper).updateById(product);
        verify(partnerProductService).activateByApproval(
                org.mockito.ArgumentMatchers.eq("product001"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq("boss001"));
    }
}
