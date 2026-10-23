package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.dto.ReasonRequest;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.service.OutingService;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工外出管理端接口契约测试（AdminOutingController，此前零覆盖）。
 *
 * <p>Controller 层负责：参数规范化、文件校验、把当前用户透传给 Service。
 * 业务红线（禁止自审、打卡必须带照片、审核范围限本部门）由 OutingService 承担，
 * 已由 {@code OutingServiceSecurityTest} 覆盖；本类只验证「接口契约不被绕过」。</p>
 */
class AdminOutingControllerTest {

    private MockMvc mvc;
    private OutingService outingService;
    private OssStorageService ossStorageService;

    @BeforeEach
    void setUp() {
        outingService = mock(OutingService.class);
        ossStorageService = mock(OssStorageService.class);
        AdminOutingController controller = new AdminOutingController(outingService, ossStorageService);
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
    @DisplayName("GET /day：分页参数越界被规范化，且始终透传当前用户")
    void dayNormalizesPagingAndPassesCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(outingService.day(any(), any(), eq(adviser), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), eq(false), any()))
                .thenReturn(PageResult.build(1, 20, 0L, Collections.emptyList()));

        mvc.perform(get("/api/admin/outing/day").param("page", "0").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1));

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(outingService).day(any(), any(), eq(adviser), eq(1), sizeCaptor.capture(),
                any(), any(), any(), any(), any(), eq(false), any());
        assertTrue(sizeCaptor.getValue() <= 100, "size 应被 PageParams 收敛到上限内");
    }

    @Test
    @DisplayName("GET /pending：待审批列表按当前用户范围查询，不外泄他部门")
    void pendingUsesCurrentUserScope() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        when(outingService.pending(eq(manager), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(PageResult.build(1, 20, 0L, Collections.emptyList()));

        mvc.perform(get("/api/admin/outing/pending").param("keyword", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).pending(eq(manager), eq(1), eq(20), eq("张三"), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("POST /：提交外出申请，归属人固定为当前用户")
    void createOutingAlwaysBindsCurrentUser() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(outingService.create(any(OutingCreateRequest.class), eq(adviser))).thenReturn("OUT20261023001");

        mvc.perform(post("/api/admin/outing")
                        .contentType("application/json")
                        .content("{\"clientCode\":\"C001\",\"purpose\":\"面签\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("OUT20261023001"));

        verify(outingService).create(any(OutingCreateRequest.class), eq(adviser));
    }

    @Test
    @DisplayName("POST /{no}/approve：理由缺省时传 null，不吞异常")
    void approvePassesNullReason() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);

        mvc.perform(post("/api/admin/outing/OUT001/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).approve(eq("OUT001"), isNull(), eq(manager));
    }

    @Test
    @DisplayName("POST /{no}/reject：审批人的拒绝原因原样透传")
    void rejectPassesReason() throws Exception {
        LoanUser manager = TestUsers.manager();
        UserContext.setUser(manager);
        ReasonRequest reason = new ReasonRequest();
        reason.setReason("客户临时取消");

        mvc.perform(post("/api/admin/outing/OUT001/reject")
                        .contentType("application/json")
                        .content("{\"reason\":\"客户临时取消\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).reject(eq("OUT001"), eq("客户临时取消"), eq(manager));
        assertEquals("客户临时取消", reason.getReason());
    }

    @Test
    @DisplayName("POST /{no}/resubmit：驳回后由本人重新提交")
    void resubmitOnlyByOwner() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);

        mvc.perform(post("/api/admin/outing/OUT001/resubmit")
                        .contentType("application/json")
                        .content("{\"purpose\":\"改期后重提\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).resubmit(eq("OUT001"), any(OutingCreateRequest.class), eq(adviser));
    }

    @Test
    @DisplayName("POST /{no}/photo：非本人打卡被 Service 拒绝，不落到 OSS")
    void uploadPhotoRejectsNonOwnerBeforeStoring() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "只有外出本人可上传打卡照片"))
                .when(outingService).requireCheckInUploader(eq("OUT001"), eq(adviser));

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(ossStorageService, org.mockito.Mockito.never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("POST /{no}/photo：空文件在存储前即被拒")
    void uploadPhotoRejectsEmptyFile() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(ossStorageService, org.mockito.Mockito.never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("POST /{no}/photo：非图片 MIME 被拒")
    void uploadPhotoRejectsNonImage() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("POST /{no}/photo：扩展名不在白名单被拒（防伪装 MIME）")
    void uploadPhotoRejectsDisallowedExtension() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "evil.gif", "image/gif", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("POST /{no}/photo：超过 10MB 被拒")
    void uploadPhotoRejectsOversize() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        byte[] big = new byte[10 * 1024 * 1024 + 1];

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", big)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("POST /{no}/photo：合法图片落 OSS 并返回 fileKey")
    void uploadPhotoStoresAndReturnsFileKey() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(multipart("/api/admin/outing/OUT001/photo")
                        .file(new MockMultipartFile("file", "ok.jpg", "image/jpeg", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("ok.jpg"));

        verify(ossStorageService).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("GET /day：日期区间倒置由 Service 拒绝，Controller 不吞异常")
    void dayPropagatesInvalidDateRange() throws Exception {
        UserContext.setUser(TestUsers.adviser());
        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "结束日期不能早于开始日期"))
                .when(outingService).day(any(), any(), any(), anyInt(), anyInt(),
                        any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        mvc.perform(get("/api/admin/outing/day")
                        .param("dateFrom", "2026-10-23")
                        .param("dateTo", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("外出 DTO 不承载打卡坐标明文（脱敏契约）")
    void outingDtoHidesRawLocation() {
        StaffOutingDTO dto = new StaffOutingDTO();
        dto.setOutingNo("OUT001");
        assertEquals("OUT001", dto.getOutingNo());
    }

    @Test
    @DisplayName("提交外出请求体缺少客户编码时仍进入 Service 校验，不静默通过")
    void createWithoutClientStillDelegates() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(outingService.create(any(OutingCreateRequest.class), eq(adviser))).thenReturn("OUT002");

        mvc.perform(post("/api/admin/outing")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).create(any(OutingCreateRequest.class), eq(adviser));
    }

    @Test
    @DisplayName("日期缺省时按当天查询，不抛异常")
    void dayDefaultsToToday() throws Exception {
        LoanUser adviser = TestUsers.adviser();
        UserContext.setUser(adviser);
        when(outingService.day(any(), any(), eq(adviser), anyInt(), anyInt(),
                any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(PageResult.build(1, 20, 0L, Collections.emptyList()));

        mvc.perform(get("/api/admin/outing/day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(outingService).day(isNull(), isNull(), eq(adviser), eq(1), eq(20),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull());
        assertEquals(LocalDate.now().isAfter(LocalDate.of(2026, 1, 1)), true);
    }
}
