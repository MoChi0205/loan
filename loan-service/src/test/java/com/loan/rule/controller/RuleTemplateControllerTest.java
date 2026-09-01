package com.loan.rule.controller;

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

import com.loan.rule.service.RuleTemplateService;

/**
 * L1 接口契约测试（自动生成，共 11 端点，其中 5 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class RuleTemplateControllerTest {

    private MockMvc mvc;
    private RuleTemplateService templateService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        templateService = Mockito.mock(RuleTemplateService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        RuleTemplateController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : RuleTemplateController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(RuleTemplateController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (RuleTemplateController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (RuleTemplateController) RuleTemplateController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "templateService", templateService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/rule-template/page")
    void get_api_admin_rule_template_page() throws Exception {
        mvc.perform(get("/api/admin/rule-template/page"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("GET /api/admin/rule-template/categories [auth]")
    void get_api_admin_rule_template_categories() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/admin/rule-template/categories"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("PUT /api/admin/rule-template/test [auth]")
    void put_api_admin_rule_template_test() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(put("/api/admin/rule-template/test").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("DELETE /api/admin/rule-template/test")
    void delete_api_admin_rule_template_test() throws Exception {
        mvc.perform(delete("/api/admin/rule-template/test"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("POST /api/admin/rule-template/test/publish [auth]")
    void post_api_admin_rule_template_test_publish() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/admin/rule-template/test/publish"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/rule-template/test/offline [auth]")
    void post_api_admin_rule_template_test_offline() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/admin/rule-template/test/offline"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/admin/rule-template/test/detail")
    void get_api_admin_rule_template_test_detail() throws Exception {
        mvc.perform(get("/api/admin/rule-template/test/detail"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("POST /api/admin/rule-template/test/import [auth]")
    void post_api_admin_rule_template_test_import() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(post("/api/admin/rule-template/test/import"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/rule-template/field")
    void post_api_admin_rule_template_field() throws Exception {
        mvc.perform(post("/api/admin/rule-template/field").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("PUT /api/admin/rule-template/field/test")
    void put_api_admin_rule_template_field_test() throws Exception {
        mvc.perform(put("/api/admin/rule-template/field/test").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("DELETE /api/admin/rule-template/field/test")
    void delete_api_admin_rule_template_field_test() throws Exception {
        mvc.perform(delete("/api/admin/rule-template/field/test"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }
}
