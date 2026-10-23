package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.serviceops.dto.ClientInsightDTO;
import com.loan.serviceops.service.ClientInsightService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户画像快照管理端接口契约测试（AdminClientInsightController，此前零覆盖）。
 *
 * <p>Controller 层只负责：分页参数规范化、把当前用户透传给 Service。
 * 业务红线（生成限归属顾问本人、复核禁自审、渠道账号一律拒绝）由 ClientInsightService
 * 承担；本类只验证「接口契约不被绕过」。</p>
 */
class AdminClientInsightControllerTest {

    private MockMvc mvc;
    private ClientInsightService insightService;

    @BeforeEach
    void setUp() {
        insightService = mock(ClientInsightService.class);
        AdminClientInsightController controller = new AdminClientInsightController(insightService);
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
    @DisplayName("GET /{clientCode}/insight：读取当前生效快照并透传当前用户")
    void insightReadsCurrentSnapshot() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(insightService.current("C001", adviser)).thenReturn(snapshot("INS001", 2));

        mvc.perform(get("/api/admin/client/C001/insight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.snapshotNo").value("INS001"))
                .andExpect(jsonPath("$.data.snapshotVersion").value(2))
                .andExpect(jsonPath("$.data.current").value(true));

        verify(insightService).current("C001", adviser);
    }

    @Test
    @DisplayName("POST /{clientCode}/insight/generate：生成画像时透传当前用户，返回新快照编号")
    void generatePassesCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(insightService.generate("C001", adviser)).thenReturn("INS002");

        mvc.perform(post("/api/admin/client/C001/insight/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("INS002"));

        verify(insightService).generate("C001", adviser);
    }

    @Test
    @DisplayName("POST /{clientCode}/insight/{snapshotNo}/review：复核结论与意见原样透传")
    void reviewPassesDecisionAndRemark() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);

        mvc.perform(post("/api/admin/client/C001/insight/INS001/review")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVE\",\"remark\":\"维度完整，可对外展示\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(insightService).review("C001", "INS001", "APPROVE", "维度完整，可对外展示", manager);
    }

    @Test
    @DisplayName("POST /{clientCode}/insight/{snapshotNo}/review：请求体为空时结论与意见按 null 透传")
    void reviewPassesNullDecisionWhenBodyEmpty() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);

        mvc.perform(post("/api/admin/client/C001/insight/INS001/review")
                        .contentType("application/json")
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(insightService).review(eq("C001"), eq("INS001"), isNull(), isNull(), eq(manager));
    }

    @Test
    @DisplayName("GET /{clientCode}/insight/history：分页越界被规范化且透传当前用户")
    void historyNormalizesPagingAndPassesCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(insightService.history(eq("C001"), anyInt(), anyInt(), eq(adviser)))
                .thenReturn(PageResult.<ClientInsightDTO>build(1, 100, 2L,
                        Arrays.asList(snapshot("INS002", 2), snapshot("INS001", 1))));

        mvc.perform(get("/api/admin/client/C001/insight/history")
                        .param("page", "0")
                        .param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].snapshotNo").value("INS002"))
                .andExpect(jsonPath("$.data.records[1].snapshotNo").value("INS001"));

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(insightService).history(eq("C001"), eq(1), sizeCaptor.capture(), eq(adviser));
        assertTrue(sizeCaptor.getValue() <= 100, "size 应被 PageParams 收敛到上限内");
    }

    @Test
    @DisplayName("POST /{clientCode}/insight/generate：非归属顾问被 Service 拒绝时返回 2001 而非 500")
    void generatePropagatesForbidden() throws Exception {
        LoanUser peer = TestUsers.adviserPeer();
        UserContext.setUser(peer);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "仅当前归属顾问可以生成客户画像"))
                .when(insightService).generate("C001", peer);

        mvc.perform(post("/api/admin/client/C001/insight/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /{clientCode}/insight/{snapshotNo}/review：结论不合法被 Service 拒绝时返回 1001 而非 500")
    void reviewPropagatesInvalidDecision() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "复核结论不合法"))
                .when(insightService).review(eq("C001"), eq("INS001"), any(), any(), eq(manager));

        mvc.perform(post("/api/admin/client/C001/insight/INS001/review")
                        .contentType("application/json")
                        .content("{\"decision\":\"UNKNOWN\",\"remark\":\"随手填的\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(insightService).review("C001", "INS001", "UNKNOWN", "随手填的", manager);
    }

    @Test
    @DisplayName("GET /{clientCode}/insight：无快照时返回空数据而不是报错（前端展示空态）")
    void insightAllowsEmptySnapshot() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(insightService.current("C001", adviser)).thenReturn(null);

        mvc.perform(get("/api/admin/client/C001/insight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(insightService).current("C001", adviser);
    }

    private ClientInsightDTO snapshot(String snapshotNo, int version) {
        ClientInsightDTO dto = new ClientInsightDTO();
        dto.setSnapshotNo(snapshotNo);
        dto.setClientCode("C001");
        dto.setSnapshotVersion(version);
        dto.setStatus(version == 2 ? "CURRENT" : "ARCHIVED");
        dto.setCurrent(version == 2);
        dto.setGeneratedBy("RULE");
        dto.setCustomerSummary("经营分析已更新至第 " + version + " 版，资料尚未提交；服务顾问将按约定与你确认下一步安排。");
        return dto;
    }
}
