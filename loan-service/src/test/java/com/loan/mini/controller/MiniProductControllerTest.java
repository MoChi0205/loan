package com.loan.mini.controller;

import com.loan.channel.dto.ChannelProductReq;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.mini.service.MiniProductService;
import com.loan.mini.service.MiniRoleGuard;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序端渠道产品管理契约测试（MiniProductController，此前零覆盖）。
 *
 * <p>安全重点：<b>渠道侧每个写接口都必须先过 {@code requireChannel}</b>——
 * 即「渠道用户只能操作本人 / 本渠道录入的产品」。Controller 只做角色门禁，
 * 对象级归属（{@code channelUserId == user.userId}）由 MiniProductService 兜底，
 * 本类用「Service 抛 BusinessException → 2001」验证该兜底不被吞掉。</p>
 *
 * <p>离线 standalone MockMvc，不启动 Spring 上下文、不连库。</p>
 */
class MiniProductControllerTest {

    private MockMvc mvc;
    private MiniProductService productService;
    private LoanUser channel;

    @BeforeEach
    void setUp() {
        productService = mock(MiniProductService.class);
        channel = TestUsers.channelUser();
        MiniProductController controller = new MiniProductController(productService, new MiniRoleGuard());
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /product/list：渠道用户只取本渠道产品，当前渠道用户被透传")
    void myProductsPassesCurrentChannelUser() throws Exception {
        UserContext.setUser(channel);
        when(productService.myProducts(channel))
                .thenReturn(Collections.singletonList(row("PAPR20261023001", "经营贷-A")));

        mvc.perform(get("/api/mini/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].code").value("PAPR20261023001"))
                .andExpect(jsonPath("$.data[0].productName").value("经营贷-A"));

        verify(productService).myProducts(channel);
    }

    @Test
    @DisplayName("GET /product/list：员工角色被拒（2001）、未登录被拒（2000），渠道数据不外泄")
    void myProductsRejectsStaffAndAnonymous() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        mvc.perform(get("/api/mini/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        UserContext.clear();
        mvc.perform(get("/api/mini/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        verify(productService, never()).myProducts(any());
    }

    @Test
    @DisplayName("POST /product：渠道用户新建草稿，产品归属由后端推导并透传当前渠道用户")
    void saveCreatesDraftForCurrentChannel() throws Exception {
        UserContext.setUser(channel);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("code", "PAPR20261023002");
        created.put("action", "CREATED");
        when(productService.save(any(ChannelProductReq.class), eq(channel))).thenReturn(created);

        mvc.perform(post("/api/mini/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"经营贷-B\",\"customerGroup\":\"ENTERPRISE\","
                                + "\"amountMin\":100000,\"amountMax\":5000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("CREATED"))
                .andExpect(jsonPath("$.data.code").value("PAPR20261023002"));

        verify(productService).save(any(ChannelProductReq.class), eq(channel));
    }

    @Test
    @DisplayName("员工调用渠道侧任一写接口均被拒（2001），且全部不落到 Service")
    void allChannelWriteEndpointsRejectStaff() throws Exception {
        UserContext.setUser(TestUsers.operator());

        mvc.perform(post("/api/mini/product").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(put("/api/mini/product/PAPR20261023001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(post("/api/mini/product/PAPR20261023001/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(post("/api/mini/product/PAPR20261023001/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(post("/api/mini/product/PAPR20261023001/delete-apply")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"不再合作\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(post("/api/mini/product/PAPR20261023001/delete-cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
        mvc.perform(get("/api/mini/product/PAPR20261023001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(productService, never()).save(any(), any());
        verify(productService, never()).update(anyString(), any(), any());
        verify(productService, never()).submit(anyString(), any());
        verify(productService, never()).revoke(anyString(), any());
        verify(productService, never()).applyDelete(anyString(), anyString(), any());
        verify(productService, never()).cancelDelete(anyString(), any());
        verify(productService, never()).detail(anyString(), any());
    }

    @Test
    @DisplayName("PUT /product/{code}：跨渠道越权编辑他人产品被 Service 拒绝（2001，不是 500）")
    void updateRejectsCrossChannelOwnership() throws Exception {
        UserContext.setUser(channel);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "只能操作本渠道录入的产品"))
                .when(productService).update(eq("PAPR_OTHER_CHANNEL"), any(ChannelProductReq.class), eq(channel));

        mvc.perform(put("/api/mini/product/PAPR_OTHER_CHANNEL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"被篡改的产品\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(productService).update(eq("PAPR_OTHER_CHANNEL"), any(ChannelProductReq.class), eq(channel));
    }

    @Test
    @DisplayName("POST /product/{code}/submit：按审批单号 + 当前渠道用户提交审批")
    void submitPassesApprovalNoAndCurrentUser() throws Exception {
        UserContext.setUser(channel);

        mvc.perform(post("/api/mini/product/PAPR20261023001/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(productService).submit("PAPR20261023001", channel);
    }

    @Test
    @DisplayName("POST /product/{code}/delete-apply：未传 body 时删除原因规范化为空串")
    void applyDeleteNormalizesMissingReason() throws Exception {
        UserContext.setUser(channel);

        mvc.perform(post("/api/mini/product/PAPR20261023001/delete-apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(productService).applyDelete("PAPR20261023001", "", channel);
    }

    @Test
    @DisplayName("POST /partner-product/delete/{no}/audit：老板终审透传审批人；运营角色被拒（2001）")
    void auditDeleteRequiresFinalApprover() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);

        mvc.perform(post("/api/mini/partner-product/delete/PAPR20261023001/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"opinion\":\"同意下架\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(productService).auditDelete("PAPR20261023001", true, "同意下架", "B001");

        // 运营属普通审批角色，但不是渠道内容终审人
        UserContext.setUser(TestUsers.operator());
        mvc.perform(get("/api/mini/partner-product/delete/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(productService, never()).pendingDeleteList();
    }

    private static Map<String, Object> row(String code, String productName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("productName", productName);
        m.put("status", "DRAFT");
        return m;
    }
}
