package com.loan.partner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.partner.dto.PartnerProductSaveReq;
import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.mapper.PartnerProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 合作库有效期状态机单测（M3 L2）：不启动 Spring 上下文，mock Mapper。
 *
 * <p>覆盖：create 必填校验+重复码、renew 回 ACTIVE、updateStatus 仅 ACTIVE/OFFLINE、
 * listActive（ACTIVE 且 cooperate_until &gt; now）、expireOverdue（EXPIRING 且 ≤now→EXPIRED）、
 * markExpiring（ACTIVE 且 ≤now+30d→EXPIRING）、getByCode 异常分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerProductServiceTest {

    @Mock
    private PartnerProductMapper partnerProductMapper;

    private PartnerProductService service;

    @BeforeEach
    void setUp() {
        service = new PartnerProductService(partnerProductMapper);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create：必填齐全 → 落库 ACTIVE 并返回业务编码")
    void create_success() {
        PartnerProductSaveReq req = new PartnerProductSaveReq();
        req.setBankProductCode("bp_abc123");
        req.setCooperateUntil(LocalDateTime.now().plusDays(30));

        when(partnerProductMapper.selectCount(any())).thenReturn(0L);
        when(partnerProductMapper.insert(any(PartnerProduct.class))).thenReturn(1);

        String code = service.create(req, "op");

        assertEquals("bp_abc123", code);
        ArgumentCaptor<PartnerProduct> cap = ArgumentCaptor.forClass(PartnerProduct.class);
        verify(partnerProductMapper).insert(cap.capture());
        PartnerProduct saved = cap.getValue();
        assertEquals(PartnerProductService.STATUS_ACTIVE, saved.getStatus());
        assertEquals("bp_abc123", saved.getBankProductCode());
        assertEquals("op", saved.getCreatedBy());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("create：缺银行产品编码 → 参数异常")
    void create_missingBankCode() {
        PartnerProductSaveReq req = new PartnerProductSaveReq();
        req.setCooperateUntil(LocalDateTime.now().plusDays(30));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(partnerProductMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create：缺有效期 → 参数异常")
    void create_missingCooperateUntil() {
        PartnerProductSaveReq req = new PartnerProductSaveReq();
        req.setBankProductCode("bp_x");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("create：银行产品重复上架 → 参数异常")
    void create_duplicate() {
        PartnerProductSaveReq req = new PartnerProductSaveReq();
        req.setBankProductCode("bp_dup");
        req.setCooperateUntil(LocalDateTime.now().plusDays(30));
        when(partnerProductMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(partnerProductMapper, never()).insert(any());
    }

    // ---------- renew ----------

    @Test
    @DisplayName("renew：更新有效期并回到 ACTIVE")
    void renew_success() {
        PartnerProduct existing = new PartnerProduct();
        existing.setBankProductCode("bp_x");
        existing.setStatus(PartnerProductService.STATUS_EXPIRING);
        when(partnerProductMapper.selectOne(any())).thenReturn(existing);
        when(partnerProductMapper.updateById(any())).thenReturn(1);

        LocalDateTime newUntil = LocalDateTime.now().plusDays(60);
        service.renew("bp_x", newUntil, "op");

        ArgumentCaptor<PartnerProduct> cap = ArgumentCaptor.forClass(PartnerProduct.class);
        verify(partnerProductMapper).updateById(cap.capture());
        assertEquals(PartnerProductService.STATUS_ACTIVE, cap.getValue().getStatus());
        assertEquals(newUntil, cap.getValue().getCooperateUntil());
    }

    @Test
    @DisplayName("renew：有效期为空 → 参数异常")
    void renew_nullUntil() {
        PartnerProduct existing = new PartnerProduct();
        when(partnerProductMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.renew("bp_x", null, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ---------- updateStatus ----------

    @Test
    @DisplayName("updateStatus：ACTIVE / OFFLINE 合法")
    void updateStatus_legal() {
        PartnerProduct existing = new PartnerProduct();
        when(partnerProductMapper.selectOne(any())).thenReturn(existing);
        when(partnerProductMapper.updateById(any())).thenReturn(1);

        service.updateStatus("bp_x", PartnerProductService.STATUS_OFFLINE, "op");
        ArgumentCaptor<PartnerProduct> cap = ArgumentCaptor.forClass(PartnerProduct.class);
        verify(partnerProductMapper).updateById(cap.capture());
        assertEquals(PartnerProductService.STATUS_OFFLINE, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("updateStatus：EXPIRED 需走续签 → 参数异常")
    void updateStatus_illegal() {
        PartnerProduct existing = new PartnerProduct();
        when(partnerProductMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateStatus("bp_x", PartnerProductService.STATUS_EXPIRED, "op"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(partnerProductMapper, never()).updateById(any());
    }

    // ---------- listActive / listExpiring ----------

    @Test
    @DisplayName("listActive：返回 mapper 命中列表（ACTIVE 且未到期，由 wrapper 约束）")
    void listActive_returnsMapperResult() {
        PartnerProduct p = new PartnerProduct();
        p.setBankProductCode("bp_active");
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.singletonList(p));

        List<PartnerProduct> result = service.listActive();
        assertEquals(1, result.size());
        assertEquals("bp_active", result.get(0).getBankProductCode());
        verify(partnerProductMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listExpiring：返回临期集合")
    void listExpiring_returnsMapperResult() {
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.listExpiring().isEmpty());
    }

    // ---------- expireOverdue（到期 job） ----------

    @Test
    @DisplayName("expireOverdue：EXPIRING 且已到期 → 置 EXPIRED 并返回数量")
    void expireOverdue_transitions() {
        PartnerProduct overdue = new PartnerProduct();
        overdue.setBankProductCode("bp_o1");
        overdue.setStatus(PartnerProductService.STATUS_EXPIRING);
        overdue.setCooperateUntil(LocalDateTime.now().minusDays(1));
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.singletonList(overdue));
        when(partnerProductMapper.updateById(any())).thenReturn(1);

        int n = service.expireOverdue();
        assertEquals(1, n);
        ArgumentCaptor<PartnerProduct> cap = ArgumentCaptor.forClass(PartnerProduct.class);
        verify(partnerProductMapper).updateById(cap.capture());
        assertEquals(PartnerProductService.STATUS_EXPIRED, cap.getValue().getStatus());
        assertEquals("job", cap.getValue().getUpdatedBy());
    }

    @Test
    @DisplayName("expireOverdue：无临期 → 0，不更新")
    void expireOverdue_empty() {
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.expireOverdue());
        verify(partnerProductMapper, never()).updateById(any());
    }

    // ---------- markExpiring（临期预警 job） ----------

    @Test
    @DisplayName("markExpiring：ACTIVE 且进入 T-30 窗口 → 置 EXPIRING 并返回数量")
    void markExpiring_transitions() {
        PartnerProduct warn = new PartnerProduct();
        warn.setBankProductCode("bp_w1");
        warn.setStatus(PartnerProductService.STATUS_ACTIVE);
        warn.setCooperateUntil(LocalDateTime.now().plusDays(10)); // ≤ now+30
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.singletonList(warn));
        when(partnerProductMapper.updateById(any())).thenReturn(1);

        int n = service.markExpiring();
        assertEquals(1, n);
        ArgumentCaptor<PartnerProduct> cap = ArgumentCaptor.forClass(PartnerProduct.class);
        verify(partnerProductMapper).updateById(cap.capture());
        assertEquals(PartnerProductService.STATUS_EXPIRING, cap.getValue().getStatus());
    }

    @Test
    @DisplayName("markExpiring：无临期 → 0，不更新")
    void markExpiring_empty() {
        when(partnerProductMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.markExpiring());
        verify(partnerProductMapper, never()).updateById(any());
    }

    // ---------- getByCode ----------

    @Test
    @DisplayName("getByCode：命中返回实体")
    void getByCode_found() {
        PartnerProduct p = new PartnerProduct();
        p.setBankProductCode("bp_x");
        when(partnerProductMapper.selectOne(any())).thenReturn(p);
        assertSame(p, service.getByCode("bp_x"));
    }

    @Test
    @DisplayName("getByCode：编码为空 → 参数异常")
    void getByCode_blank() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByCode(" "));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getByCode：不存在 → 数据不存在")
    void getByCode_notFound() {
        when(partnerProductMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByCode("bp_no"));
        assertEquals(ResultCode.DATA_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- page（轻量） ----------

    @Test
    @DisplayName("page：委托 mapper 分页，返回非空结果")
    void page_delegates() {
        Page<PartnerProduct> pg = new Page<>(1, 10);
        pg.setRecords(Collections.singletonList(new PartnerProduct()));
        pg.setTotal(1);
        when(partnerProductMapper.selectPage(any(), any())).thenReturn(pg);

        PageResult<PartnerProduct> r = service.page(null, 1, 10);
        assertNotNull(r);
        verify(partnerProductMapper).selectPage(any(), any());
    }
}
