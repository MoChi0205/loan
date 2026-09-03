package com.loan.org.controller;

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

import com.loan.org.service.OrgService;
import com.loan.org.service.OrgWriteService;

/**
 * L1 接口契约测试（自动生成，共 10 端点，其中 5 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class OrgControllerTest {

    private MockMvc mvc;
    private OrgService orgService;
    private OrgWriteService orgWriteService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        orgService = Mockito.mock(OrgService.class, new SafeDefaultAnswer());
        orgWriteService = Mockito.mock(OrgWriteService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        OrgController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : OrgController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(OrgController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (OrgController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (OrgController) OrgController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "orgService", orgService);
        ReflectionTestUtils.setField(controller, "orgWriteService", orgWriteService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/org/menu/tree")
    void get_api_admin_org_menu_tree() throws Exception {
        try {
            LoanUser adviser = TestUsers.staffUser();
            adviser.setRoleCode("ADVISER");
            UserContext.setUser(adviser);
            mvc.perform(get("/api/admin/org/menu/tree").param("roleCode", "BOSS"))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value(0));
            Mockito.verify(orgService).listMenusByRole("ADVISER");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("BOSS 不能借 roleCode 参数查看其他角色菜单")
    void bossCannotInspectOtherRoleMenu() throws Exception {
        try {
            LoanUser boss = TestUsers.staffUser();
            boss.setRoleCode("BOSS");
            UserContext.setUser(boss);
            mvc.perform(get("/api/admin/org/menu/tree").param("roleCode", "SUPER"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value(0));
            Mockito.verify(orgService).listMenusByRole("BOSS");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("OPERATOR 可在组织配置页查看其他角色菜单")
    void operatorCanInspectOtherRoleMenu() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(get("/api/admin/org/menu/tree").param("roleCode", "ADVISER"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value(0));
            Mockito.verify(orgService).listMenusByRole("ADVISER");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/admin/org/department/tree")
    void get_api_admin_org_department_tree() throws Exception {
        mvc.perform(get("/api/admin/org/department/tree"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("GET /api/admin/org/role/list")
    void get_api_admin_org_role_list() throws Exception {
        mvc.perform(get("/api/admin/org/role/list"))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("GET /api/admin/org/permission/list")
    void get_api_admin_org_permission_list() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(get("/api/admin/org/permission/list").param("roleCode", "ADVISER"))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").value(0));
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("GET /api/admin/org/staff/page")
    void get_api_admin_org_staff_page() throws Exception {
        mvc.perform(get("/api/admin/org/staff/page"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
    }

    @Test
    @DisplayName("POST /api/admin/org/department/save [auth]")
    void post_api_admin_org_department_save() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(post("/api/admin/org/department/save").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/org/department/disable [auth]")
    void post_api_admin_org_department_disable() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(post("/api/admin/org/department/disable").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/org/staff/save [auth]")
    void post_api_admin_org_staff_save() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(post("/api/admin/org/staff/save").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/org/staff/disable [auth]")
    void post_api_admin_org_staff_disable() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(post("/api/admin/org/staff/disable").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("POST /api/admin/org/permission/save [auth]")
    void post_api_admin_org_permission_save() throws Exception {
        try {
            LoanUser operator = TestUsers.staffUser();
            operator.setRoleCode("OPERATOR");
            UserContext.setUser(operator);
            mvc.perform(post("/api/admin/org/permission/save").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }
}
