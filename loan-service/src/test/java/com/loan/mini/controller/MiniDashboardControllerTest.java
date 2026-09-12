package com.loan.mini.controller;

import com.loan.context.UserContext;
import com.loan.dashboard.service.DashboardService;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.SafeDefaultAnswer;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L1 接口契约测试：小程序首页统计（GET /api/mini/dashboard/stats）。
 *
 * <p>离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock DashboardService + 自定义 @CurrentUser 解析器。
 * 断言：返回 Result 信封（code 存在），不出现 5xx。
 */
class MiniDashboardControllerTest {

    private MockMvc mvc;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        // 深桩 mock：返回安全默认值，避免 NPE
        dashboardService = Mockito.mock(DashboardService.class, new SafeDefaultAnswer());
        MiniDashboardController controller = new MiniDashboardController(dashboardService);
        // 字段注入兜底（@RequiredArgsConstructor 构造已注入，双保险）
        ReflectionTestUtils.setField(controller, "dashboardService", dashboardService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/mini/dashboard/stats [auth]")
    void get_api_mini_dashboard_stats() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/dashboard/stats"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }
}
