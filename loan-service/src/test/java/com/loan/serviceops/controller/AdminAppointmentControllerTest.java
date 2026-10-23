package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.dto.AppointmentChangeRequest;
import com.loan.serviceops.dto.AppointmentCreateRequest;
import com.loan.serviceops.dto.StaffAppointmentDTO;
import com.loan.serviceops.service.AppointmentService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户预约与履约管理端接口契约测试（AdminAppointmentController，此前零覆盖）。
 *
 * <p>Controller 层只负责三件事：分页参数规范化、把请求体原样透传、把当前登录人透传给 Service。
 * 业务红线（状态机合法性、仅主服务顾问可操作、5 分钟内不可自助变更、异常流程原因必填、
 * 数据范围收敛）全部由 AppointmentService 承担；本类只验证「接口契约不被绕过」。</p>
 */
class AdminAppointmentControllerTest {

    private MockMvc mvc;
    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        AdminAppointmentController controller = new AdminAppointmentController(appointmentService);
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
    @DisplayName("GET /day：分页越界被规范化（page=0→1、size=9999→100）且始终透传当前用户")
    void dayNormalizesPagingAndPassesCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(appointmentService.day(any(), any(), any(), eq(adviser), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(PageResult.<StaffAppointmentDTO>build(1, 100, 1L,
                        Collections.singletonList(appointment("AP20261023001"))));

        mvc.perform(get("/api/admin/appointment/day")
                        .param("page", "0")
                        .param("size", "9999")
                        .param("staffCode", "A001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.records[0].appointmentNo").value("AP20261023001"));

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(appointmentService).day(any(), any(), any(), eq(adviser), eq(1), sizeCaptor.capture(),
                any(), any(), eq("A001"), any(), any(), eq(false), any());
        assertTrue(sizeCaptor.getValue() <= 100, "size 应被 PageParams 收敛到上限内");
    }

    @Test
    @DisplayName("POST /：创建预约时请求体与当前用户原样透传，返回新预约编号")
    void createPassesBodyAndCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(appointmentService.createForStaff(any(AppointmentCreateRequest.class), eq(adviser)))
                .thenReturn("AP20261023002");

        mvc.perform(post("/api/admin/appointment")
                        .contentType("application/json")
                        .content("{\"clientCode\":\"C001\",\"serviceMethod\":\"COMPANY_ON_SITE\","
                                + "\"scheduledStart\":\"2026-11-01T10:00:00\","
                                + "\"scheduledEnd\":\"2026-11-01T11:00:00\","
                                + "\"locationName\":\"公司会议室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("AP20261023002"));

        ArgumentCaptor<AppointmentCreateRequest> captor =
                ArgumentCaptor.forClass(AppointmentCreateRequest.class);
        verify(appointmentService).createForStaff(captor.capture(), eq(adviser));
        AppointmentCreateRequest passed = captor.getValue();
        assertEquals("C001", passed.getClientCode());
        assertEquals("COMPANY_ON_SITE", passed.getServiceMethod());
        assertEquals("公司会议室", passed.getLocationName());
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), passed.getScheduledStart());
        assertEquals(LocalDateTime.of(2026, 11, 1, 11, 0), passed.getScheduledEnd());
    }

    @Test
    @DisplayName("POST /{no}/{confirm|arrive|start|complete|no-show}：履约状态机五步均透传当前用户且不吞异常")
    void lifecycleTransitionsPassCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);

        mvc.perform(post("/api/admin/appointment/AP001/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/admin/appointment/AP001/arrive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/admin/appointment/AP001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/admin/appointment/AP001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/admin/appointment/AP002/no-show"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).confirmByStaff("AP001", adviser);
        verify(appointmentService).arrive("AP001", adviser);
        verify(appointmentService).startService("AP001", adviser);
        verify(appointmentService).complete("AP001", adviser);
        verify(appointmentService).markNoShow("AP002", adviser);
    }

    @Test
    @DisplayName("POST /{no}/cancel：常规取消原样透传原因，且不走异常流程")
    void cancelPassesReasonWithoutExceptionFlow() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);

        mvc.perform(post("/api/admin/appointment/AP001/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"客户临时有事\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).cancel(eq("AP001"), eq("客户临时有事"), eq(adviser), eq(false));
    }

    @Test
    @DisplayName("POST /{no}/exception-cancel：异常取消原因透传且必须走异常流程")
    void exceptionCancelPassesReasonWithExceptionFlow() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);

        mvc.perform(post("/api/admin/appointment/AP001/exception-cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"顾问临时外出，无法接待\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(appointmentService).cancel(eq("AP001"), eq("顾问临时外出，无法接待"), eq(manager), eq(true));
    }

    @Test
    @DisplayName("POST /{no}/reschedule 与 /exception-reschedule：改期按路径区分异常流程并返回新预约编号")
    void rescheduleBranchesPassFlags() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(appointmentService.reschedule(eq("AP001"), any(AppointmentChangeRequest.class), eq(adviser), eq(false)))
                .thenReturn("AP20261023003");
        when(appointmentService.reschedule(eq("AP001"), any(AppointmentChangeRequest.class), eq(adviser), eq(true)))
                .thenReturn("AP20261023004");

        mvc.perform(post("/api/admin/appointment/AP001/reschedule")
                        .contentType("application/json")
                        .content("{\"scheduledStart\":\"2026-11-02T09:00:00\","
                                + "\"scheduledEnd\":\"2026-11-02T10:00:00\","
                                + "\"reason\":\"客户改约\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("AP20261023003"));

        mvc.perform(post("/api/admin/appointment/AP001/exception-reschedule")
                        .contentType("application/json")
                        .content("{\"scheduledStart\":\"2026-11-03T09:00:00\","
                                + "\"scheduledEnd\":\"2026-11-03T10:00:00\","
                                + "\"reason\":\"顾问时间冲突\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("AP20261023004"));

        ArgumentCaptor<AppointmentChangeRequest> normalCaptor =
                ArgumentCaptor.forClass(AppointmentChangeRequest.class);
        verify(appointmentService).reschedule(eq("AP001"), normalCaptor.capture(), eq(adviser), eq(false));
        assertEquals("客户改约", normalCaptor.getValue().getReason());
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 0), normalCaptor.getValue().getScheduledStart());

        ArgumentCaptor<AppointmentChangeRequest> exceptionCaptor =
                ArgumentCaptor.forClass(AppointmentChangeRequest.class);
        verify(appointmentService).reschedule(eq("AP001"), exceptionCaptor.capture(), eq(adviser), eq(true));
        assertEquals("顾问时间冲突", exceptionCaptor.getValue().getReason());
        assertEquals(LocalDateTime.of(2026, 11, 3, 10, 0), exceptionCaptor.getValue().getScheduledEnd());
    }

    @Test
    @DisplayName("POST /{no}/confirm：非主服务顾问被 Service 拒绝时返回 2001 而非 500")
    void confirmPropagatesForbidden() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅主服务顾问可以执行该操作"))
                .when(appointmentService).confirmByStaff("AP001", adviser);

        mvc.perform(post("/api/admin/appointment/AP001/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /day：日期区间倒置被 Service 拒绝，Controller 不吞异常")
    void dayPropagatesInvalidDateRange() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "结束日期不能早于开始日期"))
                .when(appointmentService).day(any(), any(), any(), any(), anyInt(), anyInt(),
                        any(), any(), any(), any(), any(), anyBoolean(), any());

        mvc.perform(get("/api/admin/appointment/day")
                        .param("dateFrom", "2026-10-23")
                        .param("dateTo", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    private StaffAppointmentDTO appointment(String appointmentNo) {
        StaffAppointmentDTO dto = new StaffAppointmentDTO();
        dto.setAppointmentNo(appointmentNo);
        dto.setClientCode("C001");
        dto.setStatus("CONFIRMED");
        return dto;
    }
}
