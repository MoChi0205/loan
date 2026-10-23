package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.controller.MiniServiceTimelineController;
import com.loan.serviceops.dto.ActivityTimelineDTO;
import com.loan.serviceops.service.ClientActivityService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户服务时间线契约测试（MiniServiceTimelineController，此前零覆盖）。
 *
 * <p>核心契约：<b>仅客户可看，且只能看自己的</b>——时间线的客户编号只取登录态
 * {@code userNo}，接口不接受任何可扩大范围的入参；{@code customerView} 恒为 true。</p>
 *
 * <p>离线 standalone MockMvc，不启动 Spring 上下文、不连库。</p>
 */
class MiniServiceTimelineControllerTest {

    private MockMvc mvc;
    private ClientActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = mock(ClientActivityService.class);
        MiniServiceTimelineController controller = new MiniServiceTimelineController(activityService);
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
    @DisplayName("GET /timeline：客户只看本人时间线，customerView 恒为 true")
    void timelineScopesToCurrentCustomer() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(activityService.timeline("CU_TEST_001", true, 1, 20))
                .thenReturn(PageResult.build(1, 20, 1, Collections.singletonList(event())));

        mvc.perform(get("/api/mini/service/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventType").value("APPOINTMENT_CREATED"))
                .andExpect(jsonPath("$.data.records[0].visibility").value("CUSTOMER"));

        verify(activityService).timeline("CU_TEST_001", true, 1, 20);
    }

    @Test
    @DisplayName("GET /timeline：传入他人 clientCode 无效，范围仍锁在当前登录客户")
    void timelineIgnoresClientCodeParam() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(activityService.timeline("CU_TEST_001", true, 1, 20))
                .thenReturn(PageResult.build(1, 20, 0, Collections.emptyList()));

        mvc.perform(get("/api/mini/service/timeline").param("clientCode", "CU_OTHER_002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(activityService).timeline("CU_TEST_001", true, 1, 20);
        verify(activityService, never()).timeline(eq("CU_OTHER_002"), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /timeline：分页参数越界被 PageParams 归一化为 1/100")
    void timelineNormalizesPaging() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(activityService.timeline("CU_TEST_001", true, 1, 100))
                .thenReturn(PageResult.build(1, 100, 0, Collections.emptyList()));

        mvc.perform(get("/api/mini/service/timeline").param("page", "0").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(activityService).timeline("CU_TEST_001", true, 1, 100);
    }

    @Test
    @DisplayName("GET /timeline：员工角色被拒（2001），Service 不被调用")
    void timelineRejectsStaff() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(get("/api/mini/service/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(activityService, never()).timeline(anyString(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /timeline：渠道合作方被拒（2001），Service 不被调用")
    void timelineRejectsChannelUser() throws Exception {
        UserContext.setUser(TestUsers.channelUser());

        mvc.perform(get("/api/mini/service/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(activityService, never()).timeline(anyString(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /timeline：未登录被拒（返回 2001 无权限，而非 2000 未登录）")
    void timelineRejectsAnonymous() throws Exception {
        UserContext.clear();

        mvc.perform(get("/api/mini/service/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(activityService, never()).timeline(anyString(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /timeline：Service 的业务异常原样透传，Controller 不吞")
    void timelinePropagatesBusinessException() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        doThrow(new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在"))
                .when(activityService).timeline(eq("CU_TEST_001"), anyBoolean(), anyInt(), anyInt());

        mvc.perform(get("/api/mini/service/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DATA_NOT_FOUND.getCode()));
    }

    private static ActivityTimelineDTO event() {
        ActivityTimelineDTO dto = new ActivityTimelineDTO();
        dto.setEventNo("act20261023001");
        dto.setEventType("APPOINTMENT_CREATED");
        dto.setSummary("预约已创建");
        dto.setVisibility("CUSTOMER");
        dto.setHappenedAt(LocalDateTime.now());
        dto.setStaffCode("A001");
        dto.setActorType("STAFF");
        return dto;
    }
}
