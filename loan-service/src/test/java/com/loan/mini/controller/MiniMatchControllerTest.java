package com.loan.mini.controller;

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

import com.loan.mini.service.MiniMatchService;
import com.loan.mini.dto.CustomerRiskAnalysisResult;
import com.loan.mini.dto.MiniMatchResult;
import com.loan.report.dto.CustomerReportDetail;
import com.loan.report.dto.StaffReportDetail;

/**
 * L1 接口契约测试（自动生成，共 4 端点，其中 4 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class MiniMatchControllerTest {

    private MockMvc mvc;
    private MiniMatchService miniMatchService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        miniMatchService = Mockito.mock(MiniMatchService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        MiniMatchController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : MiniMatchController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(MiniMatchController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (MiniMatchController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (MiniMatchController) MiniMatchController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "miniMatchService", miniMatchService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("POST /api/mini/match/run [auth]")
    void post_api_mini_match_run() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/match/run").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/mini/match/run 缺少申请城市时返回参数错误")
    void post_api_mini_match_run_requires_apply_city() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/match/run")
                    .content("{\"facts\":{\"annualTaxAmount\":1},\"clientCode\":\"cu_test\"}")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("请选择申请城市"));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("客户执行响应只包含风险分析字段")
    void customer_match_run_hides_internal_match_fields() throws Exception {
        CustomerRiskAnalysisResult result = new CustomerRiskAnalysisResult();
        result.setReportNo("R001");
        result.setAnalysisStatus("STABLE");
        result.setAnalysisLabel("资料结构较完整");
        result.setRiskSummary("已完成资质与经营风险分析");
        Mockito.when(miniMatchService.runForMini(Mockito.anyString(), Mockito.anyMap(),
                Mockito.any(LoanUser.class), Mockito.anyString(), Mockito.nullable(String.class)))
                .thenReturn(result);
        try {
            UserContext.setUser(TestUsers.customerUser());
            mvc.perform(post("/api/mini/match/run")
                    .content("{\"facts\":{\"annualTaxAmount\":10000},\"applyCity\":\"杭州市\"}")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("STABLE"))
                .andExpect(jsonPath("$.data.productCount").doesNotExist())
                .andExpect(jsonPath("$.data.totalResult").doesNotExist())
                .andExpect(jsonPath("$.data.grade").doesNotExist())
                .andExpect(jsonPath("$.data.ruleLogs").doesNotExist());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("员工执行响应保留内部匹配字段")
    void staff_match_run_keeps_internal_match_fields() throws Exception {
        MiniMatchResult result = new MiniMatchResult();
        result.setReportNo("R002");
        result.setTotalResult("PASS");
        result.setProductCount(2);
        Mockito.when(miniMatchService.runForMini(Mockito.anyString(), Mockito.anyMap(),
                Mockito.any(LoanUser.class), Mockito.anyString(), Mockito.nullable(String.class)))
                .thenReturn(result);
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/match/run")
                    .content("{\"facts\":{\"annualTaxAmount\":10000},\"applyCity\":\"杭州市\",\"clientCode\":\"CU_TEST_001\"}")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalResult").value("PASS"))
                .andExpect(jsonPath("$.data.productCount").value(2));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/match/history [auth]")
    void get_api_mini_match_history() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/match/history"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/report/list [auth]")
    void get_api_mini_report_list() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/report/list"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/report/test [auth]")
    void get_api_mini_report_test() throws Exception {
        StaffReportDetail detail = new StaffReportDetail();
        detail.setReportNo("test");
        Mockito.when(miniMatchService.staffReportDetail("test")).thenReturn(detail);
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/report/test"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("客户报告详情使用独立 DTO 且不包含内部字段")
    void customer_report_detail_hides_internal_fields() throws Exception {
        CustomerReportDetail detail = new CustomerReportDetail();
        detail.setReportNo("R-CUSTOMER");
        detail.setAnalysisStatus("ATTENTION");
        detail.setRiskSummary("建议补充经营流水");
        detail.setDataSourceNotice("本报告仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口。");
        Mockito.when(miniMatchService.customerReportDetail("R-CUSTOMER", "CU_TEST_001")).thenReturn(detail);
        try {
            UserContext.setUser(TestUsers.customerUser());
            mvc.perform(get("/api/mini/report/R-CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisStatus").value("ATTENTION"))
                .andExpect(jsonPath("$.data.dataSourceNotice").exists())
                .andExpect(jsonPath("$.data.bankCount").doesNotExist())
                .andExpect(jsonPath("$.data.productCount").doesNotExist())
                .andExpect(jsonPath("$.data.grade").doesNotExist())
                .andExpect(jsonPath("$.data.totalResult").doesNotExist())
                .andExpect(jsonPath("$.data.ruleLogs").doesNotExist());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("员工报告详情使用独立 DTO 并保留内部字段")
    void staff_report_detail_keeps_internal_fields() throws Exception {
        StaffReportDetail detail = new StaffReportDetail();
        detail.setReportNo("R-STAFF");
        detail.setGrade("HIGH");
        detail.setTotalResult("PASS");
        detail.setBankCount(2);
        detail.setProductCount(3);
        Mockito.when(miniMatchService.staffReportDetail("R-STAFF")).thenReturn(detail);
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/report/R-STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("HIGH"))
                .andExpect(jsonPath("$.data.totalResult").value("PASS"))
                .andExpect(jsonPath("$.data.bankCount").value(2))
                .andExpect(jsonPath("$.data.productCount").value(3));
        } finally {
            UserContext.clear();
        }
    }
}
