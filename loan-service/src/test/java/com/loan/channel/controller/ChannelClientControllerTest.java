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
 * 渠道端客户只读接口契约测试（ChannelClientController，此前零覆盖）。
 *
 * <p>渠道的数据边界由 {@link ChannelDataScopeService} 强制（跨渠道客户直接 403、批次越权编码静默丢弃），
 * 已由 {@code ChannelDataScopeServiceTest} 覆盖。本类负责验证 Controller 不会绕过该边界：
 * 无论传什么参数，都会把<b>当前登录用户</b>透传下去，由 Service 决定可见范围。</p>
 */
class ChannelClientControllerTest {

    private MockMvc mvc;
    private ChannelDataScopeService scopeService;

    @BeforeEach
    void setUp() {
        scopeService = mock(ChannelDataScopeService.class);
        ChannelClientController controller = new ChannelClientController(scopeService);
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
    @DisplayName("GET /page：渠道只能看到本人客户，分页参数越界被规范化")
    void clientPageNormalizesPagingAndScopesToCurrentChannel() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.clientPage(eq("张三"), anyInt(), anyInt(), any(), eq(channel)))
                .thenReturn(PageResult.build(1, 10, 0L, Collections.emptyList()));

        mvc.perform(get("/api/channel/client/page")
                        .param("keyword", "张三")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1));

        verify(scopeService).clientPage(eq("张三"), eq(1), anyInt(), isNull(), eq(channel));
    }

    @Test
    @DisplayName("GET /{clientCode}：越权访问他人客户被 Service 拒绝，Controller 不吞异常")
    void clientDetailPropagatesForbidden() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "无权查看该客户"))
                .when(scopeService).clientDetail(eq("C_OTHER"), eq(channel));

        mvc.perform(get("/api/channel/client/C_OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /batch：请求体为空时不 NPE，按 null 编码集下传")
    void clientBatchToleratesEmptyBody() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.clientBatch(isNull(), eq(channel))).thenReturn(Collections.emptyList());

        mvc.perform(post("/api/channel/client/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scopeService).clientBatch(isNull(), eq(channel));
    }

    @Test
    @DisplayName("POST /batch：编码列表原样下传，越权项由 Service 静默丢弃")
    void clientBatchPassesCodesThrough() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        when(scopeService.clientBatch(any(), eq(channel))).thenReturn(Collections.emptyList());

        mvc.perform(post("/api/channel/client/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"C001\",\"C002\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(scopeService).clientBatch(eq(java.util.Arrays.asList("C001", "C002")), eq(channel));
    }

    @Test
    @DisplayName("非渠道身份访问渠道客户接口被 Service 拒绝")
    void nonChannelIdentityIsRejected() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅渠道合作方可访问"))
                .when(scopeService).clientPage(isNull(), anyInt(), anyInt(), isNull(), eq(adviser));

        mvc.perform(get("/api/channel/client/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("批量请求 DTO 的编码为空集合时不被当成 null")
    void batchRequestKeepsEmptyListDistinctFromNull() {
        ChannelBatchQueryRequest body = new ChannelBatchQueryRequest();
        body.setCodes(Collections.emptyList());
        org.junit.jupiter.api.Assertions.assertNotNull(body.getCodes());
        org.junit.jupiter.api.Assertions.assertTrue(body.getCodes().isEmpty());
    }
}
