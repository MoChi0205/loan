package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序端站内消息中心契约测试（MiniNotificationController，此前零覆盖）。
 *
 * <p>核心契约：<b>三个接口一律按登录态 {@code userNo} 收口</b>，不接受任何可扩大范围的入参，
 * 且响应经 {@code MiniNotificationVO} 脱敏、不含业务单号（合规红线 02-#7）。</p>
 *
 * <p>离线 standalone MockMvc，不启动 Spring 上下文、不连库。</p>
 */
class MiniNotificationControllerTest {

    private MockMvc mvc;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        MiniNotificationController controller = new MiniNotificationController(notificationService);
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
    @DisplayName("GET /mine：只查本人通知，且响应脱敏不含业务单号")
    void mineReturnsOwnDesensitizedNotifications() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(notificationService.page("CU_TEST_001", 1, 10))
                .thenReturn(PageResult.build(1, 10, 1, Collections.singletonList(productApprovalNotice())));
        when(notificationService.countUnread("CU_TEST_001")).thenReturn(3);

        mvc.perform(get("/api/mini/notification/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.records[0].source").value("审核"))
                .andExpect(jsonPath("$.data.records[0].matter").value("产品审核结果"))
                .andExpect(jsonPath("$.data.records[0].unread").value(true))
                // 合规：不出现任何业务单号
                .andExpect(jsonPath("$.data.records[0].notificationId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].relatedId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].userNo").doesNotExist());

        verify(notificationService).page("CU_TEST_001", 1, 10);
        verify(notificationService).countUnread("CU_TEST_001");
    }

    @Test
    @DisplayName("GET /mine：试图用 userNo 入参读他人通知无效，范围仍锁在当前登录人")
    void mineIgnoresUserNoParam() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(notificationService.page("CU_TEST_001", 1, 10))
                .thenReturn(PageResult.build(1, 10, 0, Collections.emptyList()));

        mvc.perform(get("/api/mini/notification/mine").param("userNo", "CU_OTHER_002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(notificationService).page("CU_TEST_001", 1, 10);
        verify(notificationService, never()).page(eq("CU_OTHER_002"), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /mine：员工登录时同样只取本人（工号维度隔离）")
    void mineScopesToStaffNo() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(notificationService.page("A001", 1, 10))
                .thenReturn(PageResult.build(1, 10, 0, Collections.emptyList()));

        mvc.perform(get("/api/mini/notification/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(notificationService).page("A001", 1, 10);
        verify(notificationService).countUnread("A001");
    }

    @Test
    @DisplayName("GET /mine：未登录时返回空页（code=0），且不触碰 Service")
    void mineReturnsEmptyPageWhenAnonymous() throws Exception {
        UserContext.clear();

        mvc.perform(get("/api/mini/notification/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        verify(notificationService, never()).page(anyString(), anyInt(), anyInt());
        verify(notificationService, never()).countUnread(anyString());
    }

    @Test
    @DisplayName("GET /mine/unread-count：未读数按当前登录人统计，无入参可篡改")
    void unreadCountUsesCurrentUser() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(notificationService.countUnread("CU_TEST_001")).thenReturn(5);

        mvc.perform(get("/api/mini/notification/mine/unread-count").param("userNo", "CU_OTHER_002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));

        verify(notificationService).countUnread("CU_TEST_001");
        verify(notificationService, never()).countUnread("CU_OTHER_002");
    }

    @Test
    @DisplayName("POST /mine/read-all：只清当前登录人的红点，不影响他人")
    void readAllOnlyMarksCurrentUser() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(notificationService.markAllAsRead("CU_TEST_001")).thenReturn(4);

        mvc.perform(post("/api/mini/notification/mine/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(4));

        verify(notificationService).markAllAsRead("CU_TEST_001");
        verify(notificationService, never()).markAllAsRead("CU_OTHER_002");
    }

    @Test
    @DisplayName("POST /mine/read-all：未登录时返回 0 且不触碰 Service")
    void readAllSkipsServiceWhenAnonymous() throws Exception {
        UserContext.clear();

        mvc.perform(post("/api/mini/notification/mine/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));

        verify(notificationService, never()).markAllAsRead(anyString());
    }

    @Test
    @DisplayName("GET /mine/unread-count：未登录时把 null 传给 Service（不抛异常，返回 0）")
    void unreadCountPassesNullWhenAnonymous() throws Exception {
        UserContext.clear();

        mvc.perform(get("/api/mini/notification/mine/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));

        // 契约现状：该接口未做空用户短路，直接把 null 透传到 Service
        verify(notificationService).countUnread(isNull());
    }

    private static Notification productApprovalNotice() {
        Notification n = new Notification();
        n.setNotificationId("noti-20261023001");
        n.setUserNo("CU_TEST_001");
        n.setType(Notification.TYPE_PRODUCT_APPROVAL);
        n.setTitle("产品审核结果");
        n.setContent("您提交的产品已通过审核");
        n.setRelatedId("PAPR20261023001");
        n.setReadStatus(0);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
