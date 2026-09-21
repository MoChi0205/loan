package com.loan.auth.controller;

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
import static org.mockito.Mockito.verify;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.SafeDefaultAnswer;
import com.loan.test.TestUsers;
import com.loan.context.UserContext;
import com.loan.context.LoanUser;
import com.loan.exception.GlobalExceptionHandler;

import com.loan.auth.service.AuthService;
import com.loan.auth.service.AuthCaptchaService;
import com.loan.infrastructure.security.LoginRsaCrypto;

/**
 * L1 接口契约测试（共 7 条契约，其中 2 个需登录，1 个验证旧入口已关闭）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class AuthControllerTest {

    private MockMvc mvc;
    private AuthService authService;
    private LoginRsaCrypto loginRsaCrypto;
    private AuthCaptchaService authCaptchaService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        authService = Mockito.mock(AuthService.class, new SafeDefaultAnswer());
        loginRsaCrypto = Mockito.mock(LoginRsaCrypto.class, new SafeDefaultAnswer());
        authCaptchaService = Mockito.mock(AuthCaptchaService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        AuthController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : AuthController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(AuthController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (AuthController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (AuthController) AuthController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "loginRsaCrypto", loginRsaCrypto);
        ReflectionTestUtils.setField(controller, "authCaptchaService", authCaptchaService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /api/auth/health")
    void get_api_auth_health() throws Exception {
        mvc.perform(get("/api/auth/health"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("GET /api/auth/public-key")
    void get_api_auth_public_key() throws Exception {
        mvc.perform(get("/api/auth/public-key"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login 已关闭")
    void post_api_auth_login_removed() throws Exception {
        mvc.perform(post("/api/auth/login").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/auth/code-login")
    void post_api_auth_code_login() throws Exception {
        mvc.perform(post("/api/auth/code-login").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/auth/captcha")
    void get_api_auth_captcha() throws Exception {
        mvc.perform(get("/api/auth/captcha"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("POST /api/auth/password-login")
    void post_api_auth_password_login() throws Exception {
        mvc.perform(post("/api/auth/password-login").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { if (result.getResponse().getStatus() >= 500) throw new AssertionError("password login 5xx"); });
    }

    @Test
    @DisplayName("POST /api/auth/reset-password")
    void post_api_auth_reset_password() throws Exception {
        mvc.perform(post("/api/auth/reset-password").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { if (result.getResponse().getStatus() >= 500) throw new AssertionError("reset password 5xx"); });
    }

    @Test
    @DisplayName("POST /api/auth/channel-login")
    void post_api_auth_channel_login() throws Exception {
        mvc.perform(post("/api/auth/channel-login").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("POST /api/auth/logout [auth]")
    void post_api_auth_logout() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/auth/logout"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
            verify(authService).logout(TestUsers.staffUser().getUserType(), TestUsers.staffUser().getUserId());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/auth/me [auth]")
    void get_api_auth_me() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/auth/me"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }
}
