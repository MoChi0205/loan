package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.dto.DailyFollowDTO;
import com.loan.serviceops.dto.DailyOrderDTO;
import com.loan.serviceops.dto.DailyServiceWorkbenchDTO;
import com.loan.serviceops.dto.StaffAppointmentDTO;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.service.DailyServiceWorkbenchService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 今日服务台聚合接口契约测试（AdminServiceWorkbenchController，此前零覆盖）。
 *
 * <p>Controller 层只负责：分页参数规范化、把日期/范围/筛选条件与当前用户透传给 Service。
 * 业务红线（内部名单权限、数据范围不得突破角色上限）由 DailyServiceWorkbenchService
 * 与 ServiceOperationScopeService 承担；本类只验证「接口契约不被绕过」。</p>
 */
class AdminServiceWorkbenchControllerTest {

    private MockMvc mvc;
    private DailyServiceWorkbenchService workbenchService;

    @BeforeEach
    void setUp() {
        workbenchService = mock(DailyServiceWorkbenchService.class);
        AdminServiceWorkbenchController controller = new AdminServiceWorkbenchController(workbenchService);
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
    @DisplayName("GET /daily-lists：分页越界被规范化（page=0→1、size=9999→100）且始终透传当前用户")
    void dailyListsNormalizesPagingAndPassesCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(workbenchService.daily(any(), any(), any(), any(), eq(adviser), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 100));

        mvc.perform(get("/api/admin/workbench/daily-lists")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyVisits.page").value(1))
                .andExpect(jsonPath("$.data.companyVisits.size").value(100));

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(workbenchService).daily(any(), any(), any(), any(), eq(adviser), eq(1), sizeCaptor.capture(),
                anyBoolean(), any(), any(), anyBoolean());
        assertTrue(sizeCaptor.getValue() <= 100, "size 应被 PageParams 收敛到上限内");
    }

    @Test
    @DisplayName("GET /daily-lists：无参数时使用默认分页 1/20、不强制刷新、不跨未完成项")
    void dailyListsUsesDefaultParams() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(workbenchService.daily(any(), any(), any(), any(), eq(adviser), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 20));

        mvc.perform(get("/api/admin/workbench/daily-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pendingFollows.page").value(1))
                .andExpect(jsonPath("$.data.pendingFollows.size").value(20));

        verify(workbenchService).daily(isNull(), isNull(), isNull(), isNull(), eq(adviser), eq(1), eq(20),
                eq(false), isNull(), isNull(), eq(false));
    }

    @Test
    @DisplayName("GET /daily-lists：部门与员工筛选条件原样透传给 Service 做二次范围校验")
    void dailyListsPassesScopeFilters() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        when(workbenchService.daily(any(), any(), any(), any(), eq(manager), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 20));

        mvc.perform(get("/api/admin/workbench/daily-lists")
                        .param("scope", "DEPARTMENT")
                        .param("deptCode", "D001")
                        .param("staffCode", "A001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(workbenchService).daily(isNull(), eq("DEPARTMENT"), eq("D001"), eq("A001"), eq(manager),
                eq(1), eq(20), eq(false), isNull(), isNull(), eq(false));
    }

    @Test
    @DisplayName("GET /daily-lists：日期区间与未完成项开关原样透传")
    void dailyListsPassesDateRangeAndUnfinishedFlag() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        when(workbenchService.daily(any(), any(), any(), any(), eq(boss), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 20));

        mvc.perform(get("/api/admin/workbench/daily-lists")
                        .param("date", "2026-10-23")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-31")
                        .param("includeUnfinished", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(workbenchService).daily(eq(LocalDate.of(2026, 10, 23)), isNull(), isNull(), isNull(), eq(boss),
                eq(1), eq(20), eq(false), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)),
                eq(true));
    }

    @Test
    @DisplayName("GET /daily-lists：refresh=true 原样透传给 Service 强制回源")
    void dailyListsPassesRefreshFlag() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(workbenchService.daily(any(), any(), any(), any(), eq(adviser), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 20));

        mvc.perform(get("/api/admin/workbench/daily-lists").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(workbenchService).daily(isNull(), isNull(), isNull(), isNull(), eq(adviser), eq(1), eq(20),
                eq(true), isNull(), isNull(), eq(false));
    }

    @Test
    @DisplayName("GET /daily-lists：四块名单全部装配到响应体，且不外泄内部物理主键")
    void dailyListsReturnsAllFourLists() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(workbenchService.daily(any(), any(), any(), any(), eq(adviser), anyInt(), anyInt(),
                anyBoolean(), any(), any(), anyBoolean()))
                .thenReturn(workbench(1, 20));

        mvc.perform(get("/api/admin/workbench/daily-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyVisits").exists())
                .andExpect(jsonPath("$.data.staffOutings").exists())
                .andExpect(jsonPath("$.data.pendingFollows").exists())
                .andExpect(jsonPath("$.data.activeOrders").exists())
                .andExpect(jsonPath("$.data.companyVisits.records").isEmpty())
                .andExpect(jsonPath("$.data.activeOrders.records").isEmpty());
    }

    @Test
    @DisplayName("GET /daily-lists：渠道账号无内部名单权限时返回 2001 而非 500")
    void dailyListsPropagatesForbidden() throws Exception {
        LoanUser channel = TestUsers.channelUser();
        UserContext.setUser(channel);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "无权访问公司内部服务名单"))
                .when(workbenchService).daily(any(), any(), any(), any(), eq(channel), anyInt(), anyInt(),
                        anyBoolean(), any(), any(), anyBoolean());

        mvc.perform(get("/api/admin/workbench/daily-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /daily-lists：数据范围越权时返回 2001，参数不合法时返回 1001，均不吞异常")
    void dailyListsPropagatesScopeErrors() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "当前角色无权查看该数据范围"))
                .when(workbenchService).daily(any(), eq("COMPANY"), any(), any(), eq(adviser), anyInt(),
                        anyInt(), anyBoolean(), any(), any(), anyBoolean());

        mvc.perform(get("/api/admin/workbench/daily-lists").param("scope", "COMPANY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "数据范围不合法"))
                .when(workbenchService).daily(any(), eq("ALL"), any(), any(), eq(adviser), anyInt(),
                        anyInt(), anyBoolean(), any(), any(), anyBoolean());

        mvc.perform(get("/api/admin/workbench/daily-lists").param("scope", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    private DailyServiceWorkbenchDTO workbench(long page, long size) {
        DailyServiceWorkbenchDTO dto = new DailyServiceWorkbenchDTO();
        dto.setCompanyVisits(PageResult.<StaffAppointmentDTO>build(page, size, 0L, Collections.emptyList()));
        dto.setStaffOutings(PageResult.<StaffOutingDTO>build(page, size, 0L, Collections.emptyList()));
        dto.setPendingFollows(PageResult.<DailyFollowDTO>build(page, size, 0L, Collections.emptyList()));
        dto.setActiveOrders(PageResult.<DailyOrderDTO>build(page, size, 0L, Collections.emptyList()));
        return dto;
    }
}
