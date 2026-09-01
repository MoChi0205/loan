package com.loan.plan.controller;

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

import com.loan.plan.service.ChannelUserListService;
import com.loan.plan.entity.ChannelUserList;

/**
 * L1 接口契约测试（自动生成，共 3 端点，其中 1 个需登录）。
 * 离线 standalone MockMvc：不启动 Spring 上下文（规避 Nacos 远程配置拉取），
 * 手工构造 Controller + 深桩 mock 依赖 + 自定义 @CurrentUser 解析器。
 * 断言：GET 返回 Result 信封(code 存在)；写操作/含必填参数的 GET 不出现 5xx。
 */
class ChannelUserListControllerTest {

    private MockMvc mvc;
    private ChannelUserListService listService;

    @BeforeEach
    void setUp() {
        // 1) 每个依赖创建深桩 mock（返回安全默认值，避免 NPE）
        listService = Mockito.mock(ChannelUserListService.class, new SafeDefaultAnswer());
        // 2) 构造控制器（优先构造函数，否则无参 + 字段注入兜底）
        ChannelUserListController controller;
        try {
            Constructor<?> ctor = null;
            for (Constructor<?> c : ChannelUserListController.class.getDeclaredConstructors()) {
                if (c.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) { ctor = c; break; }
            }
            if (ctor == null) {
                ctor = Arrays.stream(ChannelUserListController.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            }
            if (ctor != null && ctor.getParameterCount() > 0) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = Mockito.mock(ctor.getParameterTypes()[i], new SafeDefaultAnswer());
                }
                ctor.setAccessible(true);
                controller = (ChannelUserListController) ctor.newInstance(args);
            } else {
                // 反射式无参实例化（编译期不依赖无参构造器存在）
                controller = (ChannelUserListController) ChannelUserListController.class.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 3) 字段注入兜底（@Resource/@Autowired 字段）
        ReflectionTestUtils.setField(controller, "listService", listService);
        // 4) standalone MockMvc：注册全局异常处理器 + 自定义 @CurrentUser 解析器（镜像生产切面）
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("GET /api/admin/channel-user-list/page [auth]")
    void get_api_admin_channel_user_list_page() throws Exception {
        try {
            UserContext.setUser(TestUsers.staffUser());
            mvc.perform(get("/api/admin/channel-user-list/page"))
                .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @DisplayName("DELETE /api/admin/channel-user-list/test")
    void delete_api_admin_channel_user_list_test() throws Exception {
        mvc.perform(delete("/api/admin/channel-user-list/test"))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        Mockito.verify(listService).delete("test");
    }

    @Test
    @DisplayName("POST /api/admin/channel-user-list/batch-delete")
    void post_api_admin_channel_user_list_batch_delete() throws Exception {
        mvc.perform(post("/api/admin/channel-user-list/batch-delete")
                .content("{\"listCodes\":[\"culist0000000001\"]}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(result -> { int s = result.getResponse().getStatus(); if (s >= 500) throw new AssertionError("HTTP status >= 500: " + s); });
        Mockito.verify(listService).batchDelete(Mockito.eq(java.util.Collections.singletonList("culist0000000001")));
    }

    @Test
    @DisplayName("POST /api/admin/channel-user-list/batch-query")
    void post_api_admin_channel_user_list_batch_query() throws Exception {
        mvc.perform(post("/api/admin/channel-user-list/batch-query")
                .content("{\"listCodes\":[\"culist0000000001\"]}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        Mockito.verify(listService).batchQuery(Mockito.eq(java.util.Collections.singletonList("culist0000000001")));
    }

    @Test
    @DisplayName("PUT /api/admin/channel-user-list/{listCode}")
    void put_api_admin_channel_user_list_by_business_code() throws Exception {
        mvc.perform(put("/api/admin/channel-user-list/culist0000000001")
                .content("{\"channelCode\":\"CH002\",\"customerGroup\":\"PERSONAL\","
                        + "\"listType\":\"LOCAL_WHITE\",\"listKey\":\"13900139000\"}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.code").exists());
        Mockito.verify(listService).update(Mockito.eq("culist0000000001"), Mockito.any());
    }

    @Test
    @DisplayName("GET 详情响应隐藏物理 id")
    void detailHidesPhysicalId() throws Exception {
        ChannelUserList item = new ChannelUserList();
        item.setId(99L);
        item.setListCode("culist0000000001");
        Mockito.when(listService.detail("culist0000000001")).thenReturn(item);

        mvc.perform(get("/api/admin/channel-user-list/culist0000000001"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.data.listCode").value("culist0000000001"))
            .andExpect(jsonPath("$.data.id").doesNotExist());
    }
}
