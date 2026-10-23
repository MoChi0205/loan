package com.loan.channel.controller;

import com.loan.api.dto.PageResult;
import com.loan.channel.dto.ChannelBatchQueryRequest;
import com.loan.channel.service.ChannelDataScopeService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 渠道端报告只读接口契约测试（ChannelReportController，此前零覆盖）。
 *
 * <p>与 {@link ChannelClientControllerTest} 同理：数据边界由 {@link ChannelDataScopeService} 强制，
 * 本类验证 Controller 始终透传当前用户、分页参数被规范化、日期区间原样下传。</p>
 */
class ChannelReportControllerTest {

    private MockMvc mvc;
    private ChannelDataScopeService scopeService;

    @BeforeEach
    void setUp() {
        scopeService = mock(ChannelDataScopeService.class);
        ChannelReportController controller = new ChannelReportController(scopeService);
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
    @DisplayName("GET /page：报告分页固定在本渠道范围内，分页参数被规范化")
    void reportPageScopesToCurrentChannel() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.reportPage(eq("DONE"), eq("HIGH"), eq("张三"), anyInt(), anyInt(),
                any(), eq(channel), any(), any()))
                .thenReturn(PageResult.build(1, 10, 0L, Collections.emptyList()));

        mvc.perform(get("/api/channel/report/page")
                        .param("status", "DONE")
                        .param("grade", "HIGH")
                        .param("keyword", "张三")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1));

        verify(scopeService).reportPage(eq("DONE"), eq("HIGH"), eq("张三"), eq(1), anyInt(),
                isNull(), eq(channel), isNull(), isNull());
    }

    @Test
    @DisplayName("GET /page：日期区间原样下传给 Service 做真实过滤")
    void reportPagePassesDateRange() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.reportPage(any(), any(), any(), anyInt(), anyInt(), any(), eq(channel), any(), any()))
                .thenReturn(PageResult.build(1, 10, 0L, Collections.emptyList()));

        mvc.perform(get("/api/channel/report/page")
                        .param("createdAtStart", "2026-10-01")
                        .param("createdAtEnd", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scopeService).reportPage(isNull(), isNull(), isNull(), eq(1), eq(10), isNull(), eq(channel),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)));
    }

    @Test
    @DisplayName("GET /{reportNo}：越权读取他人报告被拒，Controller 不吞异常")
    void reportDetailPropagatesForbidden() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "无权查看该报告"))
                .when(scopeService).reportDetail(eq("R_OTHER"), eq(channel));

        mvc.perform(get("/api/channel/report/R_OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /batch：空请求体不 NPE，越权编码由 Service 静默丢弃")
    void reportBatchToleratesEmptyBody() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.reportBatch(isNull(), eq(channel))).thenReturn(Collections.emptyList());

        mvc.perform(post("/api/channel/report/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scopeService).reportBatch(isNull(), eq(channel));
    }

    @Test
    @DisplayName("员工身份访问渠道报告接口被拒（渠道端点不外泄全量报告）")
    void staffCannotUseChannelReportEndpoint() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅渠道合作方可访问"))
                .when(scopeService).reportPage(isNull(), isNull(), isNull(), anyInt(), anyInt(),
                        isNull(), eq(adviser), isNull(), isNull());

        mvc.perform(get("/api/channel/report/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("批量查询编码列表保序下传")
    void reportBatchPreservesOrder() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.reportBatch(any(), eq(channel))).thenReturn(Collections.emptyList());

        mvc.perform(post("/api/channel/report/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"R001\",\"R002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scopeService).reportBatch(eq(java.util.Arrays.asList("R001", "R002")), eq(channel));
    }

    @Test
    @DisplayName("批量请求 DTO 未设置编码时为 null，与空集合语义不同")
    void batchRequestNullByDefault() {
        ChannelBatchQueryRequest body = new ChannelBatchQueryRequest();
        org.junit.jupiter.api.Assertions.assertNull(body.getCodes());
    }
}
