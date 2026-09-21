package com.loan.report.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.mockito.Mockito;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.SafeDefaultAnswer;
import com.loan.test.TestUsers;
import com.loan.context.UserContext;
import com.loan.context.LoanUser;
import com.loan.exception.GlobalExceptionHandler;

import com.loan.report.service.ReportService;
import com.loan.report.service.StaffReportAggregationService;
import com.loan.report.service.ReportAnalyticsCacheService;
import com.loan.report.dto.StaffAggregatedReport;
import com.loan.report.dto.StaffReportDetail;

/**
 * L1 接口契约测试（自动生成，共 5 端点，其中 0 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class ReportControllerTest {

    private MockMvc mvc;
    private ReportService reportService;
    private StaffReportAggregationService staffReportAggregationService;
    private ReportAnalyticsCacheService reportAnalyticsCacheService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        reportService = Mockito.mock(ReportService.class, new SafeDefaultAnswer());
        staffReportAggregationService = Mockito.mock(StaffReportAggregationService.class, new SafeDefaultAnswer());
        reportAnalyticsCacheService = Mockito.mock(ReportAnalyticsCacheService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        ReportController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : ReportController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(ReportController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (ReportController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (ReportController) ReportController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "reportService", reportService);
        ReflectionTestUtils.setField(controller, "reportAnalyticsCacheService", reportAnalyticsCacheService);
        ReflectionTestUtils.setField(controller, "staffReportAggregationService", staffReportAggregationService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/report/overview")
    void get_api_admin_report_overview() throws Exception {
        mvc.perform(get("/api/admin/report/overview"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("GET /api/admin/report/operations")
    void get_api_admin_report_operations() throws Exception {
        mvc.perform(get("/api/admin/report/operations"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("GET /api/admin/report/order-trend")
    void get_api_admin_report_order_trend() throws Exception {
        mvc.perform(get("/api/admin/report/order-trend"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/admin/report/reward-trend")
    void get_api_admin_report_reward_trend() throws Exception {
        mvc.perform(get("/api/admin/report/reward-trend"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/admin/report/screening/page")
    void get_api_admin_report_screening_page() throws Exception {
        mvc.perform(get("/api/admin/report/screening/page"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/admin/report/screening/test")
    void get_api_admin_report_screening_test() throws Exception {
        mvc.perform(get("/api/admin/report/screening/test"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("Web 报告详情将当前员工交给服务端执行范围校验")
    void screening_detail_passes_current_staff_to_scope_check() throws Exception {
        StaffReportDetail detail = new StaffReportDetail();
        detail.setReportNo("R-WEB");
        detail.setProductCount(2);
        LoanUser staff = TestUsers.staffUser();
        Mockito.when(reportService.staffScreeningDetail("R-WEB", staff)).thenReturn(detail);
        try {
            UserContext.setUser(staff);
            mvc.perform(get("/api/admin/report/screening/R-WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportNo").value("R-WEB"))
                .andExpect(jsonPath("$.data.productCount").value(2));
            Mockito.verify(reportService).staffScreeningDetail("R-WEB", staff);
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("Web 聚合报告将当前员工交给聚合服务执行范围校验")
    void screening_aggregate_passes_current_staff_to_scope_check() throws Exception {
        StaffAggregatedReport detail = new StaffAggregatedReport();
        detail.setReportNo("R-AGG");
        LoanUser staff = TestUsers.staffUser();
        Mockito.when(staffReportAggregationService.aggregate("R-AGG", staff)).thenReturn(detail);
        try {
            UserContext.setUser(staff);
            mvc.perform(get("/api/admin/report/screening/R-AGG/aggregate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reportNo").value("R-AGG"));
            Mockito.verify(staffReportAggregationService).aggregate("R-AGG", staff);
        } finally {
            UserContext.clear();
        }
    }
}
