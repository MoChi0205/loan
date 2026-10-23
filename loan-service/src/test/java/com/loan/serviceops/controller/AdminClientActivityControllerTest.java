package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.dto.ActivityTimelineDTO;
import com.loan.serviceops.dto.FollowRecordCreateRequest;
import com.loan.serviceops.service.ClientActivityService;
import com.loan.serviceops.service.FollowRecordService;
import com.loan.serviceops.service.ServiceOperationScopeService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户跟进与活动回放管理端接口契约测试（AdminClientActivityController，此前零覆盖）。
 *
 * <p>Controller 层负责：分页参数规范化、先做对象级鉴权再查时间线、把当前用户透传给 Service。
 * 业务红线（仅归属顾问可写跟进、跨部门不可见）由 FollowRecordService 与
 * ServiceOperationScopeService 承担；本类只验证「接口契约不被绕过」。</p>
 */
class AdminClientActivityControllerTest {

    private MockMvc mvc;
    private FollowRecordService followRecordService;
    private ClientActivityService activityService;
    private ServiceOperationScopeService scopeService;

    @BeforeEach
    void setUp() {
        followRecordService = mock(FollowRecordService.class);
        activityService = mock(ClientActivityService.class);
        scopeService = mock(ServiceOperationScopeService.class);
        AdminClientActivityController controller =
                new AdminClientActivityController(followRecordService, activityService, scopeService);
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
    @DisplayName("POST /{clientCode}/follow-record：跟进内容与当前用户原样透传，返回跟进编号")
    void createFollowPassesBodyAndCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(followRecordService.create(eq("C001"), any(FollowRecordCreateRequest.class), eq(adviser)))
                .thenReturn("F20261023001");

        mvc.perform(post("/api/admin/client/C001/follow-record")
                        .contentType("application/json")
                        .content("{\"channelType\":\"PHONE\",\"resultCode\":\"INTENTION\","
                                + "\"content\":\"电话沟通经营情况\",\"visibility\":\"STAFF_ONLY\","
                                + "\"nextAction\":\"二周后二次回访\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("F20261023001"));

        ArgumentCaptor<FollowRecordCreateRequest> captor =
                ArgumentCaptor.forClass(FollowRecordCreateRequest.class);
        verify(followRecordService).create(eq("C001"), captor.capture(), eq(adviser));
        FollowRecordCreateRequest passed = captor.getValue();
        assertEquals("PHONE", passed.getChannelType());
        assertEquals("INTENTION", passed.getResultCode());
        assertEquals("电话沟通经营情况", passed.getContent());
        assertEquals("STAFF_ONLY", passed.getVisibility());
        assertEquals("二周后二次回访", passed.getNextAction());
    }

    @Test
    @DisplayName("POST /{clientCode}/follow-record：非归属顾问被 Service 拒绝时返回 2001 而非 500")
    void createFollowPropagatesForbidden() throws Exception {
        LoanUser peer = TestUsers.adviserPeer();
        UserContext.setUser(peer);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅当前归属顾问可以新增跟进记录"))
                .when(followRecordService)
                .create(eq("C001"), any(FollowRecordCreateRequest.class), eq(peer));

        mvc.perform(post("/api/admin/client/C001/follow-record")
                        .contentType("application/json")
                        .content("{\"channelType\":\"PHONE\",\"resultCode\":\"INTENTION\",\"content\":\"试图越权跟进\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /{clientCode}/activity-timeline：分页越界被规范化，且固定按员工视角回放（customerView=false）")
    void timelineNormalizesPagingAndKeepsStaffView() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        ClientProfile client = client("C001", "A001");
        when(scopeService.requireClient("C001")).thenReturn(client);
        when(activityService.timeline(eq("C001"), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(PageResult.<ActivityTimelineDTO>build(1, 100, 1L,
                        Collections.singletonList(event("EV001"))));

        mvc.perform(get("/api/admin/client/C001/activity-timeline")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.records[0].eventNo").value("EV001"));

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(activityService).timeline(eq("C001"), eq(false), eq(1), sizeCaptor.capture());
        assertTrue(sizeCaptor.getValue() <= 100, "size 应被 PageParams 收敛到上限内");
    }

    @Test
    @DisplayName("GET /{clientCode}/activity-timeline：先校验客户存在再做对象级可见性校验，最后才查时间线")
    void timelineRequiresObjectAccessBeforeQuery() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        ClientProfile client = client("C001", "A001");
        when(scopeService.requireClient("C001")).thenReturn(client);
        when(activityService.timeline(anyString(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(PageResult.<ActivityTimelineDTO>build(1, 20, 0L, Collections.emptyList()));

        mvc.perform(get("/api/admin/client/C001/activity-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        InOrder ordered = inOrder(scopeService, activityService);
        ordered.verify(scopeService).requireClient("C001");
        ArgumentCaptor<ClientProfile> clientCaptor = ArgumentCaptor.forClass(ClientProfile.class);
        ordered.verify(scopeService).requireClientVisibleToStaff(eq(manager), clientCaptor.capture());
        assertSame(client, clientCaptor.getValue());
        ordered.verify(activityService).timeline(eq("C001"), eq(false), eq(1), eq(20));
    }

    @Test
    @DisplayName("GET /{clientCode}/activity-timeline：客户不存在时返回 1002 且不查询时间线")
    void timelineStopsWhenClientMissing() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        doThrow(new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在"))
                .when(scopeService).requireClient("C_MISSING");

        mvc.perform(get("/api/admin/client/C_MISSING/activity-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DATA_NOT_FOUND.getCode()));

        verify(activityService, never()).timeline(anyString(), anyBoolean(), anyInt(), anyInt());
        verify(scopeService, never()).requireClientVisibleToStaff(any(), any());
    }

    @Test
    @DisplayName("GET /{clientCode}/activity-timeline：跨部门越权返回 2001 且不查询时间线")
    void timelineStopsWhenClientNotVisible() throws Exception {
        LoanUser otherDeptAdviser = TestUsers.adviserOtherDept();
        UserContext.setUser(otherDeptAdviser);
        when(scopeService.requireClient("C001")).thenReturn(client("C001", "A001"));
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "只能访问本部门客户"))
                .when(scopeService).requireClientVisibleToStaff(eq(otherDeptAdviser), any(ClientProfile.class));

        mvc.perform(get("/api/admin/client/C001/activity-timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(activityService, never()).timeline(anyString(), anyBoolean(), anyInt(), anyInt());
    }

    private ClientProfile client(String clientCode, String ownerStaffCode) {
        ClientProfile profile = new ClientProfile();
        profile.setClientCode(clientCode);
        profile.setOwnerStaffCode(ownerStaffCode);
        return profile;
    }

    private ActivityTimelineDTO event(String eventNo) {
        ActivityTimelineDTO dto = new ActivityTimelineDTO();
        dto.setEventNo(eventNo);
        dto.setEventType("FOLLOW_RECORDED");
        dto.setSummary("员工已记录客户跟进");
        return dto;
    }
}
