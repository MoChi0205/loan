package com.loan.mini.controller;

import com.loan.attachment.entity.ServiceAttachment;
import com.loan.attachment.mapper.ServiceAttachmentMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.infrastructure.oss.OssObjectOpenResult;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.mini.service.MiniMaterialService;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序材料上传 / 下载接口契约测试（MiniUploadController，此前零覆盖）。
 *
 * <p>Controller 层负责：登录校验、空文件校验、材料类别白名单、把当前用户透传给
 * 归属校验与 OCR 回灌、以及下载接口的 IDOR 防护（fileKey 格式、登录态、归属校验）。</p>
 */
class MiniUploadControllerTest {

    private MockMvc mvc;
    private ServiceAttachmentMapper attachmentMapper;
    private MiniMaterialService materialService;
    private ClientAllocationService clientAllocationService;
    private OssStorageService ossStorageService;

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(ServiceAttachmentMapper.class);
        materialService = mock(MiniMaterialService.class);
        clientAllocationService = mock(ClientAllocationService.class);
        ossStorageService = mock(OssStorageService.class);
        MiniUploadController controller = new MiniUploadController(
                attachmentMapper, materialService, clientAllocationService, ossStorageService);
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
    @DisplayName("POST /upload：客户本人上传成功，当前用户透传给 OCR 且附件元数据归属本人")
    void uploadBindsAttachmentToCurrentUser() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(clientAllocationService.requireOperationClientCode(eq(customer), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(customer.getUserNo());
        when(materialService.ingest(anyString(), anyString(), anyString(), any(), eq(customer)))
                .thenReturn(ocrResult());

        mvc.perform(multipart("/api/mini/upload")
                        .file(new MockMultipartFile("file", "idcard.jpg", "image/jpeg", "IMG".getBytes()))
                        .param("bizType", "id_card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("idcard.jpg"))
                .andExpect(jsonPath("$.data.fileSize").value(3))
                .andExpect(jsonPath("$.data.ocrApplied").value(true));

        verify(materialService).ingest(anyString(), eq("ID_CARD"), eq(customer.getUserNo()), any(), eq(customer));
        ArgumentCaptor<ServiceAttachment> captor = ArgumentCaptor.forClass(ServiceAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        ServiceAttachment att = captor.getValue();
        assertEquals(customer.getUserNo(), att.getClientProfileCode());
        assertEquals("ID_CARD", att.getAttachmentType());
        assertEquals("", att.getOrderNo());
    }

    @Test
    @DisplayName("POST /upload：未登录被拒（UNAUTHORIZED 2000），不落 OSS")
    void uploadRejectsAnonymous() throws Exception {
        mvc.perform(multipart("/api/mini/upload")
                        .file(new MockMultipartFile("file", "idcard.jpg", "image/jpeg", "IMG".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        verify(ossStorageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(attachmentMapper, never()).insert(any(ServiceAttachment.class));
    }

    @Test
    @DisplayName("POST /upload：空文件被拒（PARAM_ERROR 1001）")
    void uploadRejectsEmptyFile() throws Exception {
        UserContext.setUser(TestUsers.customerUser());

        mvc.perform(multipart("/api/mini/upload")
                        .file(new MockMultipartFile("file", "idcard.jpg", "image/jpeg", new byte[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(ossStorageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("POST /upload：材料类别不在白名单被拒（bizType 非空即校验，不静默回落 OTHER）")
    void uploadRejectsUnknownBizType() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        when(clientAllocationService.requireOperationClientCode(any(LoanUser.class), any()))
                .thenReturn(customer.getUserNo());

        mvc.perform(multipart("/api/mini/upload")
                        .file(new MockMultipartFile("file", "evil.exe", "application/octet-stream", "MZ".getBytes()))
                        .param("bizType", "EXE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(ossStorageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("POST /upload：越权操作他人客户被拒（FORBIDDEN 2001），文件不落 OSS")
    void uploadRejectsForeignClient() throws Exception {
        LoanUser customer = TestUsers.customerUser();
        UserContext.setUser(customer);
        doThrow(new BusinessException(ResultCode.FORBIDDEN, "当前客户尚未归属本人，请先完成分配或转移审批"))
                .when(clientAllocationService).requireOperationClientCode(eq(customer), eq("CU_OTHER_001"));

        mvc.perform(multipart("/api/mini/upload")
                        .file(new MockMultipartFile("file", "idcard.jpg", "image/jpeg", "IMG".getBytes()))
                        .param("clientCode", "CU_OTHER_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));

        verify(ossStorageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(attachmentMapper, never()).insert(any(ServiceAttachment.class));
    }

    @Test
    @DisplayName("GET /upload/{fileKey}：fileKey 含非法字符（目录穿越尝试）被拒 400")
    void downloadRejectsMalformedFileKey() throws Exception {
        UserContext.setUser(TestUsers.customerUser());

        mvc.perform(get("/api/mini/upload/{fileKey}", "att-001"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/mini/upload/{fileKey}", "att_001"))
                .andExpect(status().isBadRequest());
        // 目录穿越尝试：fileKey 只允许字母数字，编码后的斜杠不得进入对象存储
        mvc.perform(get("/api/mini/upload/{fileKey}", "..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().is4xxClientError());

        verify(ossStorageService, never()).openObject(anyString());
    }

    @Test
    @DisplayName("GET /upload/{fileKey}：未登录下载被拒 401，不读取对象存储")
    void downloadRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/mini/upload/{fileKey}", "att001"))
                .andExpect(status().isUnauthorized());

        verify(ossStorageService, never()).openObject(anyString());
    }

    @Test
    @DisplayName("GET /upload/{fileKey}：他人 / 渠道下载被拒 403；元数据缺失时鉴权被跳过（当前实现按元数据行判定归属）")
    void downloadEnforcesOwnershipWhenMetadataExists() throws Exception {
        when(attachmentMapper.selectOne(any())).thenReturn(attachment("att001", "CU_TEST_001"));
        when(ossStorageService.objectExists("att001")).thenReturn(true);
        when(ossStorageService.openObject("att001"))
                .thenReturn(new OssObjectOpenResult(new ByteArrayInputStream("SECRET".getBytes(StandardCharsets.UTF_8)), 6L, "image/jpeg"));

        // 他人客户：拒绝
        UserContext.setUser(otherCustomer("CU_OTHER_001"));
        mvc.perform(get("/api/mini/upload/{fileKey}", "att001"))
                .andExpect(status().isForbidden());
        verify(ossStorageService, never()).openObject(anyString());

        // 渠道用户：即使文件存在也拒绝
        UserContext.setUser(TestUsers.channelUser());
        mvc.perform(get("/api/mini/upload/{fileKey}", "att001"))
                .andExpect(status().isForbidden());
        verify(ossStorageService, never()).openObject(anyString());

        // 元数据行缺失：当前实现跳过归属校验，任意登录用户可读（IDOR 风险，见交付说明）
        when(attachmentMapper.selectOne(any())).thenReturn(null);
        UserContext.setUser(otherCustomer("CU_OTHER_001"));
        mvc.perform(get("/api/mini/upload/{fileKey}", "att001"))
                .andExpect(status().isOk())
                .andExpect(content().string("SECRET"));
    }

    @Test
    @DisplayName("GET /upload/{fileKey}：本人下载成功并回传文件内容")
    void downloadServesOwnFile() throws Exception {
        UserContext.setUser(TestUsers.customerUser());
        when(attachmentMapper.selectOne(any())).thenReturn(attachment("att001", TestUsers.customerUser().getUserNo()));
        when(ossStorageService.objectExists("att001")).thenReturn(true);
        when(ossStorageService.openObject("att001")).thenReturn(new OssObjectOpenResult(
                new ByteArrayInputStream("OWN-FILE".getBytes(StandardCharsets.UTF_8)), 8L, "image/jpeg"));

        mvc.perform(get("/api/mini/upload/{fileKey}", "att001"))
                .andExpect(status().isOk())
                .andExpect(content().string("OWN-FILE"));
    }

    private LoanUser otherCustomer(String userNo) {
        LoanUser user = TestUsers.customerUser();
        user.setUserNo(userNo);
        return user;
    }

    private ServiceAttachment attachment(String fileKey, String clientCode) {
        ServiceAttachment att = new ServiceAttachment();
        att.setFileKey(fileKey);
        att.setFileName("idcard.jpg");
        att.setFileSize(6L);
        att.setClientProfileCode(clientCode);
        att.setAttachmentType("ID_CARD");
        att.setOrderNo("");
        return att;
    }

    private Map<String, Object> ocrResult() {
        Map<String, Object> ocr = new LinkedHashMap<>();
        ocr.put("ocrApplied", true);
        ocr.put("extractedFields", Collections.emptyList());
        ocr.put("mergedCount", 0);
        ocr.put("ocrFileKey", "att001");
        ocr.put("recognitionProvider", "tencent");
        ocr.put("aiRecognitionEnabled", true);
        ocr.put("recognitionStatus", "SUCCESS");
        ocr.put("pendingReview", false);
        ocr.put("reviewNo", null);
        return ocr;
    }
}
