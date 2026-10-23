package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.controller.MiniAppointmentController;
import com.loan.serviceops.dto.AppointmentChangeRequest;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.dto.CustomerAppointmentDTO;
import com.loan.serviceops.service.AppointmentService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
 * 小程序 / H5 客户预约接口契约测试（MiniAppointmentController，此前零覆盖）。
 *
 * <p>核心契约：<b>客户只能看 / 改自己的预约</b>——客户编号始终取登录态，接口不接受
 * {@code clientCode} 入参；归属员工（host）由 Service 按客户档案的归属顾问推导，
 * Controller 不提供任何改写入口。</p>
 *
 * <p>离线 standalone MockMvc，不启动 Spring 上下文、不连库。</p>
 */
class MiniAppointmentControllerTest {

    private MockMvc mvc;
    private AppointmentService appointmentService;
    private LoanUser customer;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        customer = TestUsers.customerUser();
        MiniAppointmentController controller = new MiniAppointmentController(appointmentService);
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
    @DisplayName("GET /mine：只返回本人预约，分页参数被 PageParams 归一化为 1/100")
    void mineScopesToCurrentCustomer() throws Exception {
        UserContext.setUser(customer);
        when(appointmentService.mine(customer, 1, 100))
                .thenReturn(PageResult.build(1, 100, 1, Collections.singletonList(appointment())));

        mvc.perform(get("/api/mini/appointment/mine").param("page", "0").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].appointmentNo").value("appt20261023001"))
                .andExpect(jsonPath("$.data.records[0].adviserName").value("李顾问"));

        verify(appointmentService).mine(customer, 1, 100);
    }

    @Test
    @DisplayName("GET /mine：员工角色被 Service 拒绝（2001），返回业务码而非 500")
    void mineRejectsStaffRole() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅客户可查看本人预约"))
                .when(appointmentService).mine(eq(adviser), anyInt(), anyInt());

        mvc.perform(get("/api/mini/appointment/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /request：H5/WEB 端识别为 H5，缺省端类型为 MINI_APP，当前客户被透传")
    void requestMapsTerminalAndPassesCurrentUser() throws Exception {
        UserContext.setUser(customer);
        when(appointmentService.createForCustomer(any(AppointmentCreateRequest.class), eq(customer), eq("H5")))
                .thenReturn("appt20261023002");

        mvc.perform(post("/api/mini/appointment/request")
                        .header("X-Client-Type", "H5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceMethod\":\"COMPANY_ON_SITE\","
                                + "\"scheduledStart\":\"2026-10-24T10:00:00\","
                                + "\"scheduledEnd\":\"2026-10-24T11:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("appt20261023002"));

        verify(appointmentService).createForCustomer(any(AppointmentCreateRequest.class), eq(customer), eq("H5"));

        when(appointmentService.createForCustomer(any(AppointmentCreateRequest.class), eq(customer), eq("MINI_APP")))
                .thenReturn("appt20261023003");
        mvc.perform(post("/api/mini/appointment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceMethod\":\"COMPANY_ON_SITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).createForCustomer(any(AppointmentCreateRequest.class), eq(customer), eq("MINI_APP"));
    }

    @Test
    @DisplayName("POST /request：客户无法自行指定 host（归属员工），请求原样交给 Service 推导")
    void requestDoesNotTrustHostFromClient() throws Exception {
        UserContext.setUser(customer);
        when(appointmentService.createForCustomer(any(AppointmentCreateRequest.class), eq(customer), anyString()))
                .thenReturn("appt20261023004");

        mvc.perform(post("/api/mini/appointment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientCode\":\"CU_OTHER_002\",\"hostStaffCode\":\"A999\","
                                + "\"serviceMethod\":\"COMPANY_ON_SITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<AppointmentCreateRequest> captor =
                ArgumentCaptor.forClass(AppointmentCreateRequest.class);
        verify(appointmentService).createForCustomer(captor.capture(), eq(customer), eq("MINI_APP"));
        // Controller 不做归属推导，仅原样透传；host/clientCode 由 Service 的 copyForCustomer 覆盖
        AppointmentCreateRequest forwarded = captor.getValue();
        assertEquals("CU_OTHER_002", forwarded.getClientCode());
        assertEquals("A999", forwarded.getHostStaffCode());
    }

    @Test
    @DisplayName("POST /{no}/confirm：客户确认本人预约，预约单号与当前用户一并透传")
    void confirmPassesCurrentUser() throws Exception {
        UserContext.setUser(customer);

        mvc.perform(post("/api/mini/appointment/appt20261023001/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).confirmByCustomer("appt20261023001", customer);
    }

    @Test
    @DisplayName("POST /{no}/cancel：取消原因原样透传，exceptionFlow 固定为 false（客户自助）")
    void cancelPassesReasonAndSelfServiceFlag() throws Exception {
        UserContext.setUser(customer);

        mvc.perform(post("/api/mini/appointment/appt20261023001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"临时有事\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 无请求体时原因为 null，仍走客户自助流（exceptionFlow=false）
        mvc.perform(post("/api/mini/appointment/appt20261023001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).cancel(eq("appt20261023001"), isNull(), eq(customer), eq(false));
    }

    @Test
    @DisplayName("POST /{no}/cancel：取消他人预约被 Service 拒绝（2001），不吞异常")
    void cancelRejectsOtherCustomersAppointment() throws Exception {
        UserContext.setUser(customer);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "只能操作本人预约"))
                .when(appointmentService).cancel(eq("appt_other_001"), any(), eq(customer), anyBoolean());

        mvc.perform(post("/api/mini/appointment/appt_other_001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"越权测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /{no}/reschedule 与 /{no}/check-in：请求体与当前客户一并透传")
    void rescheduleAndCheckInPassCurrentUser() throws Exception {
        UserContext.setUser(customer);
        when(appointmentService.reschedule(eq("appt20261023001"), any(AppointmentChangeRequest.class),
                eq(customer), eq(false))).thenReturn("appt20261023009");

        mvc.perform(post("/api/mini/appointment/appt20261023001/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledStart\":\"2026-10-25T10:00:00\","
                                + "\"scheduledEnd\":\"2026-10-25T11:00:00\",\"reason\":\"改期\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("appt20261023009"));

        verify(appointmentService).reschedule(eq("appt20261023001"), any(AppointmentChangeRequest.class),
                eq(customer), eq(false));

        mvc.perform(post("/api/mini/appointment/appt20261023001/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).checkInByCustomer("appt20261023001", customer);
        verify(appointmentService, never()).arrive(anyString(), any());
    }

    private static CustomerAppointmentDTO appointment() {
        CustomerAppointmentDTO dto = new CustomerAppointmentDTO();
        dto.setAppointmentNo("appt20261023001");
        dto.setServiceMethod("COMPANY_ON_SITE");
        dto.setAdviserName("李顾问");
        dto.setStatus("CONFIRMED");
        dto.setScheduledStart(LocalDateTime.of(2026, 10, 24, 10, 0));
        return dto;
    }
}
