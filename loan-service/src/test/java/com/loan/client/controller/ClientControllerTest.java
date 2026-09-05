package com.loan.client.controller;

import com.loan.api.dto.PageResult;
import com.loan.client.service.ClientAllocationService;
import com.loan.client.service.ClientService;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.mini.service.MiniRoleGuard;
import com.loan.test.CurrentUserArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 客户档案与客户归属管理端接口契约测试。 */
class ClientControllerTest {

    private MockMvc mvc;
    private ClientAllocationService allocationService;
    private MiniRoleGuard roleGuard;

    @BeforeEach
    void setUp() {
        ClientService clientService = mock(ClientService.class);
        allocationService = mock(ClientAllocationService.class);
        roleGuard = mock(MiniRoleGuard.class);
        ClientController controller = new ClientController(clientService, allocationService, roleGuard);
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
    @DisplayName("未分配客户分页规范化参数并返回统一信封")
    void unassignedPage() throws Exception {
        LoanUser adviser = staff("ADVISER", "S001");
        UserContext.setUser(adviser);
        when(allocationService.pageUnassigned("张三", 1, 100))
                .thenReturn(PageResult.build(1, 100, 0, Collections.emptyList()));

        mvc.perform(get("/api/admin/client/unassigned/page")
                        .param("keyword", "张三")
                        .param("page", "0")
                        .param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100));

        verify(roleGuard).requireStaff(adviser);
        verify(allocationService).pageUnassigned("张三", 1, 100);
    }

    @Test
    @DisplayName("顾问按客户业务编码申请认领")
    void claim() throws Exception {
        LoanUser adviser = staff("ADVISER", "S001");
        UserContext.setUser(adviser);
        when(allocationService.applyTransfer("client001", "S001", adviser))
                .thenReturn(approvalResult("alloc001"));

        mvc.perform(post("/api/admin/client/client001/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.approvalNo").value("alloc001"));

        verify(roleGuard).requireStaff(adviser);
        verify(allocationService).applyTransfer("client001", "S001", adviser);
    }

    @Test
    @DisplayName("管理者选择顾问后直接生效")
    void assign() throws Exception {
        LoanUser manager = staff("DEPT_MANAGER", "M001");
        UserContext.setUser(manager);
        when(allocationService.directAssign("client001", "S002", manager))
                .thenReturn(directAssignResult());

        mvc.perform(post("/api/admin/client/client001/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adviserStaffCode\":\"S002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.direct").value(true))
                .andExpect(jsonPath("$.data.needApproval").value(false));

        verify(roleGuard).requireStaff(manager);
        verify(allocationService).directAssign("client001", "S002", manager);
    }

    @Test
    @DisplayName("非顾问不能从未分配客户池申请认领")
    void claimRejectsNonAdviser() throws Exception {
        UserContext.setUser(staff("DEPT_MANAGER", "M001"));

        mvc.perform(post("/api/admin/client/client001/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("仅顾问可从未分配客户池申请认领"));
    }

    private LoanUser staff(String roleCode, String staffCode) {
        LoanUser user = new LoanUser();
        user.setUserId(1L);
        user.setUserNo(staffCode);
        user.setName("测试人员");
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(roleCode);
        return user;
    }

    private Map<String, Object> approvalResult(String approvalNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalNo", approvalNo);
        result.put("status", "PENDING");
        return result;
    }

    private Map<String, Object> directAssignResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", "client001");
        result.put("ownerStaffName", "李顾问");
        result.put("status", "APPROVED");
        result.put("direct", true);
        result.put("needApproval", false);
        return result;
    }
}
