package com.loan.reward.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.entity.RewardRule;
import com.loan.reward.mapper.RewardRecordMapper;
import com.loan.reward.mapper.RewardRuleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 奖励计算单测（M3 L2）：不启动 Spring 上下文，mock 全部 Mapper。
 *
 * <p>覆盖：settleForOrder 条件链（订单 DEAL+金额→被推荐档案→CUSTOMER 邀请→ACTIVE 规则→L1，
 * indirectEnabled 时 L2）、settleLevel 金额上下限/手动金额/幂等、audit 发放/驳回/手动改额、
 * voidReward、saveRule 校验与缺省、disableRule、listRules。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardServiceTest {

    @Mock
    private RewardRecordMapper rewardRecordMapper;
    @Mock
    private RewardRuleMapper rewardRuleMapper;
    @Mock
    private ServiceOrderMapper orderMapper;
    @Mock
    private ClientProfileMapper clientProfileMapper;
    @Mock
    private InvitationMapper invitationMapper;
    @Mock
    private BankProductMapper bankProductMapper;

    private RewardService service;

    @BeforeEach
    void setUp() {
        service = new RewardService(rewardRecordMapper, rewardRuleMapper, orderMapper,
                clientProfileMapper, invitationMapper, bankProductMapper);
    }

    // ---------- settleForOrder 前置校验（返回 0 不结算） ----------

    @Test
    @DisplayName("settleForOrder：工单不存在 → 0")
    void settle_orderNotFound() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.settleForOrder("o1", "op", null));
        verify(rewardRecordMapper, never()).insert(any());
    }

    @Test
    @DisplayName("settleForOrder：工单非 DEAL → 0")
    void settle_orderNotDeal() {
        ServiceOrder order = new ServiceOrder();
        order.setStatus(ServiceOrder.STATUS_NEW);
        order.setDealAmount(new BigDecimal("100"));
        when(orderMapper.selectOne(any())).thenReturn(order);
        assertEquals(0, service.settleForOrder("o1", "op", null));
    }

    @Test
    @DisplayName("settleForOrder：成交金额为空 → 0")
    void settle_noDealAmount() {
        ServiceOrder order = new ServiceOrder();
        order.setStatus(ServiceOrder.STATUS_DEAL);
        order.setDealAmount(null);
        when(orderMapper.selectOne(any())).thenReturn(order);
        assertEquals(0, service.settleForOrder("o1", "op", null));
    }

    @Test
    @DisplayName("settleForOrder：被推荐客户档案缺失 → 0")
    void settle_refereeMissing() {
        ServiceOrder order = dealOrder("bp1", "cr");
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.settleForOrder("o1", "op", null));
    }

    @Test
    @DisplayName("settleForOrder：无 CUSTOMER 邀请关系 → 0")
    void settle_noInvitation() {
        ServiceOrder order = dealOrder("bp1", "cr");
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.settleForOrder("o1", "op", null));
    }

    @Test
    @DisplayName("settleForOrder：无生效奖励规则 → 0")
    void settle_noRule() {
        ServiceOrder order = dealOrder("bp1", "cr");
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        Invitation inv = customerInvitation(10L, 2L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(clientProfileMapper.selectById(anyLong())).thenReturn(profile("cRef", "ENTERPRISE", 10L));
        when(rewardRuleMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.settleForOrder("o1", "op", null));
    }

    // ---------- settleForOrder 成功（L1 比例） ----------

    @Test
    @DisplayName("settleForOrder：L1 比例结算 → 落 1 笔 PENDING_AUDIT，金额=基数×直推比例")
    void settle_l1Success() {
        ServiceOrder order = dealOrder("bp1", "cr"); // dealAmount 1000
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        Invitation inv = customerInvitation(10L, 2L);
        ClientProfile referrer = profile("cRef", "ENTERPRISE", 10L);
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.1"), null, null);

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(clientProfileMapper.selectById(anyLong())).thenReturn(referrer);
        when(rewardRuleMapper.selectOne(any())).thenReturn(rule);
        when(rewardRecordMapper.selectCount(any())).thenReturn(0L);
        when(rewardRecordMapper.insert(any())).thenReturn(1);

        int n = service.settleForOrder("o1", "op", null);
        assertEquals(1, n);

        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).insert(cap.capture());
        RewardRecord rec = cap.getValue();
        assertEquals(RewardRecord.STATUS_PENDING_AUDIT, rec.getStatus());
        assertEquals(1, rec.getLevel());
        assertEquals("cRef", rec.getReferrerClientCode());
        assertEquals("cr", rec.getRefereeClientCode());
        assertEquals(0, rec.getRewardAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(0, rec.getManualAdjustFlag());
    }

    @Test
    @DisplayName("settleForOrder：手动金额（L1 且仅 L1）→ 直接采用手动金额并标记人工调整")
    void settle_manualAmountL1() {
        ServiceOrder order = dealOrder("bp1", "cr");
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        Invitation inv = customerInvitation(10L, 2L);
        ClientProfile referrer = profile("cRef", "ENTERPRISE", 10L);
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.1"), null, null);

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(clientProfileMapper.selectById(anyLong())).thenReturn(referrer);
        when(rewardRuleMapper.selectOne(any())).thenReturn(rule);
        when(rewardRecordMapper.selectCount(any())).thenReturn(0L);
        when(rewardRecordMapper.insert(any())).thenReturn(1);

        int n = service.settleForOrder("o1", "op", new BigDecimal("150"));
        assertEquals(1, n);

        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).insert(cap.capture());
        assertEquals(0, cap.getValue().getRewardAmount().compareTo(new BigDecimal("150.00")));
        assertEquals(1, cap.getValue().getManualAdjustFlag());
        assertEquals("成交时手动指定金额", cap.getValue().getManualAdjustReason());
    }

    @Test
    @DisplayName("settleForOrder：比例结果受 minAmount 下限约束")
    void settle_minAmount() {
        ServiceOrder order = dealOrder("bp1", "cr");
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.01"), new BigDecimal("200"), null);
        stubSettleBaseline(order, rule);

        service.settleForOrder("o1", "op", null);
        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).insert(cap.capture());
        assertEquals(0, cap.getValue().getRewardAmount().compareTo(new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("settleForOrder：比例结果受 maxAmount 上限约束")
    void settle_maxAmount() {
        ServiceOrder order = dealOrder("bp1", "cr"); // 1000 * 0.5 = 500
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.5"), null, new BigDecimal("300"));
        stubSettleBaseline(order, rule);

        service.settleForOrder("o1", "op", null);
        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).insert(cap.capture());
        assertEquals(0, cap.getValue().getRewardAmount().compareTo(new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("settleForOrder：间推开关开启 → L1+L2 共 2 笔")
    void settle_l2Indirect() {
        ServiceOrder order = dealOrder("bp1", "cr");
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        Invitation inv1 = customerInvitation(10L, 2L);  // L1 推荐人
        Invitation inv2 = customerInvitation(20L, 10L); // L2 推荐人（推荐人的推荐人）
        ClientProfile refL1 = profile("cRef", "ENTERPRISE", 10L);
        ClientProfile refL2 = profile("l2Ref", "ENTERPRISE", 20L);
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.1"), null, null);
        rule.setIndirectEnabled(1);
        rule.setIndirectRate(new BigDecimal("0.05"));

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(inv1, inv2);
        when(clientProfileMapper.selectById(anyLong())).thenReturn(refL1, refL2);
        when(rewardRuleMapper.selectOne(any())).thenReturn(rule);
        when(rewardRecordMapper.selectCount(any())).thenReturn(0L);
        when(rewardRecordMapper.insert(any())).thenReturn(1);

        int n = service.settleForOrder("o1", "op", null);
        assertEquals(2, n);
        verify(rewardRecordMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("settleForOrder：已结算同工单+同推荐人+同层级 → 幂等跳过")
    void settle_idempotent() {
        ServiceOrder order = dealOrder("bp1", "cr");
        RewardRule rule = activeRule("bp1", "ENTERPRISE", new BigDecimal("0.1"), null, null);
        stubSettleBaseline(order, rule);
        when(rewardRecordMapper.selectCount(any())).thenReturn(1L); // 已存在

        int n = service.settleForOrder("o1", "op", null);
        assertEquals(0, n);
        verify(rewardRecordMapper, never()).insert(any());
    }

    // ---------- audit ----------

    @Test
    @DisplayName("audit：通过 → GRANTED + 结算人/时间")
    void audit_approve() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);
        when(rewardRecordMapper.updateById(any())).thenReturn(1);

        service.audit("r1", true, null, "op", null, null);

        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).updateById(cap.capture());
        assertEquals(RewardRecord.STATUS_GRANTED, cap.getValue().getStatus());
        assertEquals("op", cap.getValue().getSettleStaffCode());
        assertNotNull(cap.getValue().getSettleTime());
        assertNull(cap.getValue().getRejectReason());
    }

    @Test
    @DisplayName("audit：驳回 → REJECTED + 原因（原因必填）")
    void audit_rejectRequiresOpinion() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.audit("r1", false, null, "op", null, null));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(rewardRecordMapper, never()).updateById(any());

        when(rewardRecordMapper.updateById(any())).thenReturn(1);
        service.audit("r1", false, "资质不符", "op", null, null);
        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).updateById(cap.capture());
        assertEquals(RewardRecord.STATUS_REJECTED, cap.getValue().getStatus());
        assertEquals("资质不符", cap.getValue().getRejectReason());
    }

    @Test
    @DisplayName("audit：仅待审核可审核（已发放 → 参数异常）")
    void audit_onlyPending() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        rec.setStatus(RewardRecord.STATUS_GRANTED);
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.audit("r1", true, null, "op", null, null));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("audit：发放时手动改金额（与计算额不同）→ 覆盖金额，原因必填")
    void audit_manualAdjustRequiresReason() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.audit("r1", true, null, "op", new BigDecimal("120"), null));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());

        when(rewardRecordMapper.updateById(any())).thenReturn(1);
        service.audit("r1", true, null, "op", new BigDecimal("120"), "促销加成");
        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getRewardAmount().compareTo(new BigDecimal("120.00")));
        assertEquals(1, cap.getValue().getManualAdjustFlag());
        assertEquals("促销加成", cap.getValue().getManualAdjustReason());
    }

    // ---------- voidReward ----------

    @Test
    @DisplayName("voidReward：待审核可作废 → VOID")
    void voidReward_success() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);
        when(rewardRecordMapper.updateById(any())).thenReturn(1);

        service.voidReward("r1", "客户退款", "op");
        ArgumentCaptor<RewardRecord> cap = ArgumentCaptor.forClass(RewardRecord.class);
        verify(rewardRecordMapper).updateById(cap.capture());
        assertEquals(RewardRecord.STATUS_VOID, cap.getValue().getStatus());
        assertEquals("客户退款", cap.getValue().getRejectReason());
    }

    @Test
    @DisplayName("voidReward：原因必填")
    void voidReward_reasonRequired() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidReward("r1", "  ", "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("voidReward：已驳回/已作废不可再作废")
    void voidReward_invalidState() {
        RewardRecord rec = pendingRecord(new BigDecimal("100"));
        rec.setStatus(RewardRecord.STATUS_REJECTED);
        when(rewardRecordMapper.selectOne(any())).thenReturn(rec);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.voidReward("r1", "x", "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ---------- saveRule ----------

    @Test
    @DisplayName("saveRule：字段齐全 → 新增，状态缺省 ACTIVE、生效时间缺省 now")
    void saveRule_insert() {
        RewardRule rule = new RewardRule();
        rule.setProductCode("bp1");
        rule.setCustomerGroup("ENTERPRISE");
        rule.setDirectRate(new BigDecimal("0.1"));
        when(rewardRuleMapper.insert(any())).thenReturn(1);

        service.saveRule(rule, "op");
        ArgumentCaptor<RewardRule> cap = ArgumentCaptor.forClass(RewardRule.class);
        verify(rewardRuleMapper).insert(cap.capture());
        assertEquals("ACTIVE", cap.getValue().getStatus());
        assertNotNull(cap.getValue().getValidFrom());
        assertNotNull(cap.getValue().getRuleVersion());
    }

    @Test
    @DisplayName("saveRule：缺产品编码 / 客群 / 直推比例 → 参数异常")
    void saveRule_validation() {
        RewardRule base = new RewardRule();
        base.setCustomerGroup("ENTERPRISE");
        base.setDirectRate(new BigDecimal("0.1"));

        RewardRule noProduct = new RewardRule();
        noProduct.setCustomerGroup("ENTERPRISE");
        noProduct.setDirectRate(new BigDecimal("0.1"));
        assertThrows(BusinessException.class, () -> service.saveRule(noProduct, "op"));

        RewardRule noGroup = new RewardRule();
        noGroup.setProductCode("bp1");
        noGroup.setDirectRate(new BigDecimal("0.1"));
        assertThrows(BusinessException.class, () -> service.saveRule(noGroup, "op"));

        RewardRule noRate = new RewardRule();
        noRate.setProductCode("bp1");
        noRate.setCustomerGroup("ENTERPRISE");
        assertThrows(BusinessException.class, () -> service.saveRule(noRate, "op"));
    }

    @Test
    @DisplayName("saveRule：带业务版本 → 走更新")
    void saveRule_update() {
        RewardRule rule = new RewardRule();
        rule.setRuleVersion("rule-existing");
        rule.setProductCode("bp1");
        rule.setCustomerGroup("ENTERPRISE");
        rule.setDirectRate(new BigDecimal("0.1"));
        RewardRule existing = new RewardRule();
        existing.setId(5L);
        existing.setRuleVersion("rule-existing");
        when(rewardRuleMapper.selectOne(any())).thenReturn(existing);
        when(rewardRuleMapper.updateById(any())).thenReturn(1);

        service.saveRule(rule, "op");
        verify(rewardRuleMapper).updateById(any());
        verify(rewardRuleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("saveRule：重复产品与客群 → 返回可理解的业务异常")
    void saveRule_duplicateDimension() {
        RewardRule rule = new RewardRule();
        rule.setProductCode("bp1");
        rule.setCustomerGroup("ENTERPRISE");
        rule.setDirectRate(new BigDecimal("0.1"));
        when(rewardRuleMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_product_group"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveRule(rule, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已配置"));
    }

    // ---------- disableRule / listRules ----------

    @Test
    @DisplayName("disableRule：存在 → 置 DISABLED")
    void disableRule_success() {
        RewardRule rule = new RewardRule();
        rule.setStatus("ACTIVE");
        when(rewardRuleMapper.selectOne(any())).thenReturn(rule);
        when(rewardRuleMapper.updateById(any())).thenReturn(1);

        service.disableRule("rule-existing", "op");
        ArgumentCaptor<RewardRule> cap = ArgumentCaptor.forClass(RewardRule.class);
        verify(rewardRuleMapper).updateById(cap.capture());
        assertEquals("DISABLED", cap.getValue().getStatus());
    }

    @Test
    @DisplayName("disableRule：不存在 → 数据不存在")
    void disableRule_notFound() {
        when(rewardRuleMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.disableRule("rule-missing", "op"));
        assertEquals(ResultCode.DATA_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("listRules：返回规则列表")
    void listRules_returns() {
        when(rewardRuleMapper.selectList(any())).thenReturn(Collections.singletonList(new RewardRule()));
        assertEquals(1, service.listRules().size());
    }

    // ---------- helpers ----------

    private ServiceOrder dealOrder(String bankProductCode, String clientCode) {
        ServiceOrder order = new ServiceOrder();
        order.setOrderNo("o1");
        order.setStatus(ServiceOrder.STATUS_DEAL);
        order.setDealAmount(new BigDecimal("1000"));
        order.setBankProductCode(bankProductCode);
        order.setClientProfileCode(clientCode);
        return order;
    }

    private ClientProfile profile(String clientCode, String group, Long id) {
        ClientProfile p = new ClientProfile();
        p.setClientCode(clientCode);
        p.setCustomerGroup(group);
        p.setId(id);
        return p;
    }

    private Invitation customerInvitation(Long referrerId, Long usedByClientId) {
        Invitation inv = new Invitation();
        inv.setReferrerType("CUSTOMER");
        inv.setReferrerId(referrerId);
        inv.setUsedFlag(1);
        inv.setUsedByClientId(usedByClientId);
        return inv;
    }

    private RewardRule activeRule(String productCode, String group, BigDecimal directRate,
                                 BigDecimal min, BigDecimal max) {
        RewardRule rule = new RewardRule();
        rule.setStatus("ACTIVE");
        rule.setProductCode(productCode);
        rule.setCustomerGroup(group);
        rule.setDirectRate(directRate);
        rule.setMinAmount(min);
        rule.setMaxAmount(max);
        rule.setRuleVersion("rule_1");
        return rule;
    }

    /** 铺设 settleForOrder 成功链路所需的公共 mock（不含 selectCount / insert）。 */
    private void stubSettleBaseline(ServiceOrder order, RewardRule rule) {
        ClientProfile referee = profile("cr", "ENTERPRISE", 2L);
        Invitation inv = customerInvitation(10L, 2L);
        ClientProfile referrer = profile("cRef", "ENTERPRISE", 10L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(clientProfileMapper.selectOne(any())).thenReturn(referee);
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(clientProfileMapper.selectById(anyLong())).thenReturn(referrer);
        when(rewardRuleMapper.selectOne(any())).thenReturn(rule);
    }

    private RewardRecord pendingRecord(BigDecimal amount) {
        RewardRecord rec = new RewardRecord();
        rec.setRewardNo("r1");
        rec.setStatus(RewardRecord.STATUS_PENDING_AUDIT);
        rec.setRewardAmount(amount);
        return rec;
    }
}
