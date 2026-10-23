package com.loan.mini.controller;

import com.loan.approval.service.ApprovalService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序端审批中心统一入口契约测试（MiniApprovalController，此前零覆盖）。
 *
 * <p>Controller 负责三件事：角色门禁（{@link MiniRoleGuard}）→ 类型白名单
 * （{@code ApprovalService#requireTypeEnabled}）→ 把当前用户原样透传给 Service。
 * 对象级审批范围（如部门经理仅可审本团队）由 Service 承担，本类只验证「门禁不被绕过」。</p>
 *
 * <p>离线 standalone MockMvc，不启动 Spring 上下文、不连库。</p>
 */
class MiniApprovalControllerTest {

    private MockMvc mvc;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        // 真实守卫：让角色白名单分支被真正执行，而不是被 mock 短路掉
        MiniRoleGuard roleGuard = new MiniRoleGuard();
        MiniApprovalController controller = new MiniApprovalController(approvalService, roleGuard);
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
    @DisplayName("GET /counts：按渠道终审人身份分流（老板 includeProduct=true / 运营 false）")
    void countsVariesByChannelFinalApprover() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("PRODUCT", 2);
        counts.put("ALLOCATION", 1);
        counts.put("TOTAL", 3);
        when(approvalService.pendingCounts(true)).thenReturn(counts);

        mvc.perform(get("/api/mini/approval/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.PRODUCT").value(2))
                .andExpect(jsonPath("$.data.TOTAL").value(3));

        // 运营属审批角色但不是渠道终审人，产品待审数不应进入其统计
        UserContext.setUser(TestUsers.operator());
        when(approvalService.pendingCounts(false)).thenReturn(Collections.singletonMap("TOTAL", 1));

        mvc.perform(get("/api/mini/approval/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.TOTAL").value(1));

        verify(approvalService).pendingCounts(true);
        verify(approvalService).pendingCounts(false);
    }

    @Test
    @DisplayName("GET /counts：越权角色被拒（顾问 2001 / 渠道 2001 / 未登录 2000），统计不落到 Service")
    void countsRejectsUnauthorizedRoles() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        mvc.perform(get("/api/mini/approval/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        UserContext.setUser(TestUsers.channelUser());
        mvc.perform(get("/api/mini/approval/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        UserContext.clear();
        mvc.perform(get("/api/mini/approval/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        verify(approvalService, never()).pendingCounts(anyBoolean());
    }

    @Test
    @DisplayName("GET /pending：type=ALL 按 ALLOCATION 权限校验，默认分页 1/10 原样透传")
    void pendingAllUsesAllocationGuard() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        when(approvalService.unifiedPending("ALL", 1, 10, true))
                .thenReturn(Collections.singletonMap("total", 0));

        mvc.perform(get("/api/mini/approval/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(approvalService).unifiedPending("ALL", 1, 10, true);
    }

    @Test
    @DisplayName("GET /pending：部门经理可审产品（非终审人）；顾问请求审批列表被拒（2001）")
    void pendingRoleMatrix() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        when(approvalService.unifiedPending("PRODUCT", 1, 10, false))
                .thenReturn(Collections.singletonMap("total", 0));

        mvc.perform(get("/api/mini/approval/pending").param("type", "PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(approvalService).unifiedPending("PRODUCT", 1, 10, false);

        UserContext.setUser(TestUsers.adviser());
        mvc.perform(get("/api/mini/approval/pending").param("type", "DOWNLOAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(approvalService, never()).unifiedPending(eq("DOWNLOAD"), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("POST /{type}/{no}/audit：驳回意见与 approve=false 原样透传，审批人取当前登录用户")
    void auditPassesOpinionAndCurrentUser() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        when(approvalService.requireTypeEnabled("PRODUCT")).thenReturn("PRODUCT");

        mvc.perform(post("/api/mini/approval/PRODUCT/PAPR20261023001/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"opinion\":\"利率区间超出合作范围\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(approvalService).requireTypeEnabled("PRODUCT");
        verify(approvalService).unifiedAudit("PRODUCT", "PAPR20261023001", false, "利率区间超出合作范围", boss);
    }

    @Test
    @DisplayName("POST /{type}/{no}/audit：无请求体时默认通过、意见为 null，审批人仍为当前登录人")
    void auditDefaultsToApproveWhenBodyMissing() throws Exception {
        LoanUser superAdmin = TestUsers.superAdmin();
        UserContext.setUser(superAdmin);
        when(approvalService.requireTypeEnabled("ALLOCATION")).thenReturn("ALLOCATION");

        mvc.perform(post("/api/mini/approval/ALLOCATION/ALOC20261023001/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(approvalService).unifiedAudit(eq("ALLOCATION"), eq("ALOC20261023001"), eq(true), eq(null), eq(superAdmin));
    }

    @Test
    @DisplayName("POST /{type}/{no}/audit：白名单外类型与渠道用户均被拒（2001），审批动作不执行")
    void auditBlockedBeforeAction() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "审批类型未开放：OUTING"))
                .when(approvalService).requireTypeEnabled("OUTING");

        mvc.perform(post("/api/mini/approval/OUTING/OUT20261023001/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        UserContext.setUser(TestUsers.channelUser());
        mvc.perform(post("/api/mini/approval/PRODUCT/PAPR20261023001/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(approvalService, never()).unifiedAudit(anyString(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("POST /{type}/{no}/audit：跨团队审批由 Service 拒绝，Controller 不吞异常")
    void auditPropagatesCrossTeamRejection() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        when(approvalService.requireTypeEnabled("ALLOCATION")).thenReturn("ALLOCATION");
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "只能审批本团队的客户分配申请"))
                .when(approvalService).unifiedAudit(eq("ALLOCATION"), eq("ALOC20261023002"),
                        anyBoolean(), any(), eq(manager));

        mvc.perform(post("/api/mini/approval/ALLOCATION/ALOC20261023002/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(approvalService).unifiedAudit(eq("ALLOCATION"), eq("ALOC20261023002"),
                eq(true), eq(null), eq(manager));
    }
}
