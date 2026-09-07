package com.loan.mini.service;

import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.channel.entity.ChannelUser;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.notification.service.NotificationService;
import com.loan.partner.service.PartnerProductService;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.entity.BankChannel;
import com.loan.channel.dto.ChannelProductReq;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 渠道产品列表批量装配与删除审批幂等回归。 */
@ExtendWith(MockitoExtension.class)
class MiniProductServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ProductApproval.class);
        TableInfoHelper.initTableInfo(assistant, BankProduct.class);
    }

    @Mock private ProductApprovalMapper approvalMapper;
    @Mock private BankProductMapper bankProductMapper;
    @Mock private PartnerProductService partnerProductService;
    @Mock private NotificationService notificationService;
    @Mock private ChannelUserMapper channelUserMapper;
    @Mock private BankChannelMapper bankChannelMapper;

    private MiniProductService service;

    @BeforeEach
    void setUp() {
        service = new MiniProductService(approvalMapper, bankProductMapper,
                partnerProductService, notificationService, channelUserMapper, bankChannelMapper);
    }

    @Test
    @DisplayName("我的产品：多审批单仅批量查询一次产品并兼容历史 OK")
    void myProducts_batchesProductNames() {
        ProductApproval first = approval("a1", "p1", "OK");
        ProductApproval second = approval("a2", "p2", "PENDING");
        when(approvalMapper.selectList(any())).thenReturn(Arrays.asList(first, second));
        BankProduct p1 = product("p1", "产品甲");
        BankProduct p2 = product("p2", "产品乙");
        when(bankProductMapper.selectList(any())).thenReturn(Arrays.asList(p1, p2));

        List<Map<String, Object>> rows = service.myProducts(channelUser());

        assertEquals(2, rows.size());
        assertEquals("APPROVED", rows.get(0).get("status"));
        assertEquals("产品甲", rows.get(0).get("productName"));
        verify(bankProductMapper, times(1)).selectList(any());
        verify(bankProductMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("删除终审：批准保留审批记录、合作库下架并通知渠道稳定账号")
    void auditDelete_approvesWithoutPhysicalDelete() {
        ProductApproval approval = approval("a1", "p1", "PENDING_DELETE");
        approval.setApplyType("DELETE");
        when(approvalMapper.selectOne(any())).thenReturn(approval);
        when(approvalMapper.update(any(), any())).thenReturn(1);
        ChannelUser channel = new ChannelUser();
        channel.setPhoneHash("phone_hash_1");
        when(channelUserMapper.selectById(7L)).thenReturn(channel);

        service.auditDelete("a1", true, "同意下架", "boss001");

        verify(partnerProductService).offlineByApproval("p1", "boss001");
        verify(approvalMapper, never()).deleteById(any());
        verify(notificationService).sendOnce(any());
    }

    @Test
    @DisplayName("我的产品：员工即使物理 ID 与渠道账号相同也禁止访问")
    void myProducts_rejectsNonChannelIdCollision() {
        LoanUser staff = channelUser();
        staff.setUserType(LoanUser.TYPE_STAFF);

        BusinessException error = assertThrows(BusinessException.class, () -> service.myProducts(staff));

        assertEquals(2001, error.getCode());
        verify(approvalMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("渠道新建产品：内部编码和所属银行由后端生成")
    void save_generatesInternalProductCodeAndChannelScope() {
        ChannelUser channel = new ChannelUser();
        channel.setBankChannelId(9L);
        when(channelUserMapper.selectById(7L)).thenReturn(channel);
        BankChannel bank = new BankChannel();
        bank.setChannelCode("bank_demo");
        when(bankChannelMapper.selectById(9L)).thenReturn(bank);
        when(bankProductMapper.insert(any())).thenReturn(1);
        when(approvalMapper.insert(any())).thenReturn(1);
        ChannelProductReq req = productReq();

        Map<String, Object> result = service.save(req, channelUser());

        assertEquals("CREATED", result.get("action"));
        org.mockito.ArgumentCaptor<BankProduct> cap = org.mockito.ArgumentCaptor.forClass(BankProduct.class);
        verify(bankProductMapper).insert(cap.capture());
        assertEquals("bank_demo", cap.getValue().getBankChannelCode());
        assertEquals(Long.valueOf(7L), cap.getValue().getChannelUserId());
        assertEquals(new BigDecimal("1000000"), cap.getValue().getAmountMin());
        org.junit.jupiter.api.Assertions.assertTrue(cap.getValue().getProductCode().startsWith("product"));
    }

    @Test
    @DisplayName("渠道产品：额度下限大于上限时统一业务异常")
    void save_rejectsInvalidRange() {
        ChannelProductReq req = productReq();
        req.setAmountMin(new BigDecimal("200"));
        req.setAmountMax(new BigDecimal("100"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.save(req, channelUser()));

        assertEquals(1001, error.getCode());
        verify(bankProductMapper, never()).insert(any());
    }

    private ProductApproval approval(String no, String productCode, String status) {
        ProductApproval approval = new ProductApproval();
        approval.setApprovalNo(no);
        approval.setBankProductCode(productCode);
        approval.setChannelUserId(7L);
        approval.setApplyType("CREATE");
        approval.setApproveStatus(status);
        return approval;
    }

    private BankProduct product(String code, String name) {
        BankProduct product = new BankProduct();
        product.setProductCode(code);
        product.setProductName(name);
        return product;
    }

    private LoanUser channelUser() {
        LoanUser user = new LoanUser();
        user.setUserId(7L);
        user.setUserNo("phone_hash_1");
        user.setUserType(LoanUser.TYPE_CHANNEL);
        user.setName("渠道用户");
        return user;
    }

    private ChannelProductReq productReq() {
        ChannelProductReq req = new ChannelProductReq();
        req.setProductName("渠道经营贷");
        req.setCustomerGroup("ENTERPRISE");
        req.setAmountMin(new BigDecimal("1000000"));
        req.setAmountMax(new BigDecimal("5000000"));
        req.setBizTermsJson("{\"description\":\"正常经营满一年\"}");
        return req;
    }
}
