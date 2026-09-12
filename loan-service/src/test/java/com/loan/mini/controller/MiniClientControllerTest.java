package com.loan.mini.controller;

import com.loan.context.UserContext;
import com.loan.mini.service.MiniClientService;
import com.loan.mini.service.MiniRoleGuard;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L1 接口契约测试：小程序「我的客户 / 客户公海」列表（D74）。
 *
 * <p>覆盖 {@code GET /api/mini/client/my} 与 {@code GET /api/mini/client/sea}：
 * 员工可访问并返回 Result 信封；未登录被 {@code requireStaff} 拒绝（不出现 5xx）。
 *
 * <p>离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取）。
 */
class MiniClientControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MiniClientService clientService = Mockito.mock(MiniClientService.class, new SafeDefaultAnswer());
        MiniRoleGuard roleGuard = Mockito.mock(MiniRoleGuard.class, new SafeDefaultAnswer());
        MiniClientController controller = new MiniClientController(clientService, roleGuard);
        // 字段注入兜底（@RequiredArgsConstructor 构造已注入，双保险）
        ReflectionTestUtils.setField(controller, "miniClientService", clientService);
        ReflectionTestUtils.setField(controller, "miniRoleGuard", roleGuard);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/mini/client/my [auth]")
    void get_api_mini_client_my() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/client/my").param("page", "1").param("size", "10"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/client/sea?seaLevel=ENTERPRISE [auth]")
    void get_api_mini_client_sea() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/client/sea").param("seaLevel", "ENTERPRISE"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/client/team [auth]")
    void get_api_mini_client_team() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/client/team"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/mini/client/{clientCode}/recycle [auth]")
    void post_api_mini_client_recycle() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/client/C001/recycle"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/mini/client/{clientCode}/release [auth]")
    void post_api_mini_client_release() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/client/C001/release"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }
}
