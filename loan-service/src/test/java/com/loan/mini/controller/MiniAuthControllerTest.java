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

import com.loan.auth.service.AuthService;
import com.loan.mini.service.MiniAuthService;
import org.springframework.core.env.Environment;

/**
 * L1 接口契约测试（自动生成，共 4 端点，其中 2 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class MiniAuthControllerTest {

    private MockMvc mvc;
    private Environment environment;
    private AuthService authService;
    private MiniAuthService miniAuthService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        environment = Mockito.mock(Environment.class, new SafeDefaultAnswer());
        authService = Mockito.mock(AuthService.class, new SafeDefaultAnswer());
        miniAuthService = Mockito.mock(MiniAuthService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        MiniAuthController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : MiniAuthController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(MiniAuthController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (MiniAuthController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (MiniAuthController) MiniAuthController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "environment", environment);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "miniAuthService", miniAuthService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("POST /api/mini/auth/login")
    void post_api_mini_auth_login() throws Exception {
        mvc.perform(post("/api/mini/auth/login").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/mini/me [auth]")
    void get_api_mini_me() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/mini/me"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/mini/auth/enterprise [auth]")
    void post_api_mini_auth_enterprise() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/mini/auth/enterprise").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/mini/wecom/qrcode")
    void get_api_mini_wecom_qrcode() throws Exception {
        mvc.perform(get("/api/mini/wecom/qrcode"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }
}
