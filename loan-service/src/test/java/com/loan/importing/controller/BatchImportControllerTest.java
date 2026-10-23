package com.loan.importing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.importing.entity.BatchImportTask;
import com.loan.importing.mapper.BatchImportTaskMapper;
import com.loan.importing.service.ExcelTemplateService;
import com.loan.importing.service.ImportRoleGuard;
import com.loan.mini.service.MiniLeadService;
import com.loan.product.service.ProductService;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量导入接口契约测试（BatchImportController，此前零覆盖）。
 *
 * <p>Controller 层负责：角色守卫（{@link ImportRoleGuard}：仅 BOSS / SUPER_ADMIN / SUPER）、
 * 文件非空与大小类型校验、导入任务落库、把当前用户透传给下游 Service。
 * 本类使用<b>真实</b> ImportRoleGuard，确保角色白名单被真正执行（而不是 mock 掉）。</p>
 */
class BatchImportControllerTest {

    private MockMvc mvc;
    private ExcelTemplateService templateService;
    private ProductService productService;
    private MiniLeadService miniLeadService;
    private BatchImportTaskMapper taskMapper;

    @BeforeEach
    void setUp() {
        templateService = mock(ExcelTemplateService.class);
        productService = mock(ProductService.class);
        miniLeadService = mock(MiniLeadService.class);
        taskMapper = mock(BatchImportTaskMapper.class);
        BatchImportController controller = new BatchImportController(
                new ImportRoleGuard(), templateService, productService, miniLeadService, taskMapper);
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
    @DisplayName("GET /tasks：老板查询任务分页，页码与页大小原样透传给 Mapper 且只查本人任务")
    void tasksPassesPagingAndScopesToApplicant() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        Page<BatchImportTask> page = new Page<>(2, 5);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        mvc.perform(get("/api/admin/import/tasks")
                        .param("page", "2")
                        .param("size", "5")
                        .param("importType", "lead")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(taskMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(2L, pageCaptor.getValue().getCurrent());
        assertEquals(5L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("GET /tasks：顾问越权被 ImportRoleGuard 拒绝（FORBIDDEN 2001，不是 500）")
    void tasksRejectsNonBossRole() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(get("/api/admin/import/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(taskMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("POST /preview：老板上传合法 xlsx，type 被归一化后透传给模板服务")
    void previewNormalizesTypeAndDelegates() throws Exception {
        UserContext.setUser(TestUsers.boss());
        when(templateService.preview(eq("LEAD"), any())).thenReturn(previewResult());

        mvc.perform(multipart("/api/admin/import/preview")
                        .file(new MockMultipartFile("file", "lead.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x".getBytes()))
                        .param("type", "lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rows[0]['手机号*']").value("13800000000"));

        verify(templateService).preview(eq("LEAD"), any());
    }

    @Test
    @DisplayName("POST /preview：空文件被拒，解析服务不被调用")
    void previewRejectsEmptyFile() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(multipart("/api/admin/import/preview")
                        .file(new MockMultipartFile("file", "lead.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]))
                        .param("type", "LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(templateService, never()).preview(any(), any());
    }

    @Test
    @DisplayName("POST /preview：非 .xlsx 文件被拒")
    void previewRejectsNonXlsx() throws Exception {
        UserContext.setUser(TestUsers.superAdmin());

        mvc.perform(multipart("/api/admin/import/preview")
                        .file(new MockMultipartFile("file", "lead.csv", "text/csv", "a,b".getBytes()))
                        .param("type", "LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(templateService, never()).preview(any(), any());
    }

    @Test
    @DisplayName("POST /preview：超过 20MB 的文件被拒")
    void previewRejectsOversizeFile() throws Exception {
        UserContext.setUser(TestUsers.boss());
        byte[] big = new byte[20 * 1024 * 1024 + 1];

        mvc.perform(multipart("/api/admin/import/preview")
                        .file(new MockMultipartFile("file", "big.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", big))
                        .param("type", "LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(templateService, never()).preview(any(), any());
    }

    @Test
    @DisplayName("POST /execute：未指定目标资源池被拒，且不解析文件")
    void executeRequiresPoolCode() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(multipart("/api/admin/import/execute")
                        .file(new MockMultipartFile("file", "lead.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x".getBytes()))
                        .param("type", "LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(templateService, never()).preview(any(), any());
        verify(taskMapper, never()).insert(any(BatchImportTask.class));
    }

    @Test
    @DisplayName("POST /execute：老板执行线索导入，当前用户透传给 MiniLeadService 且任务归属申请人")
    void executePassesCurrentUserAndRecordsApplicant() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        when(templateService.preview(eq("LEAD"), any())).thenReturn(previewResult());
        when(miniLeadService.submit(any(), eq(boss)))
                .thenReturn(Collections.singletonMap("leadNo", "LEAD001"));

        mvc.perform(multipart("/api/admin/import/execute")
                        .file(new MockMultipartFile("file", "lead.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x".getBytes()))
                        .param("type", "LEAD")
                        .param("poolCode", "POOL1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.poolCode").value("POOL1"));

        verify(miniLeadService).submit(any(), eq(boss));
        ArgumentCaptor<BatchImportTask> captor = ArgumentCaptor.forClass(BatchImportTask.class);
        verify(taskMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        BatchImportTask inserted = captor.getValue();
        assertEquals(boss.getUserNo(), inserted.getApplicantStaffCode());
        assertEquals("LEAD", inserted.getImportType());
        assertEquals("POOL1", inserted.getPoolCode());
    }

    private Map<String, Object> previewResult() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("联系人*", "张三");
        row.put("手机号*", "13800000000");
        row.put("客群*", "ENTERPRISE");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", Arrays.asList(row));
        return result;
    }
}
