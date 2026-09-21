package com.loan.mini.controller;

import com.loan.attachment.entity.ServiceAttachment;
import com.loan.attachment.mapper.ServiceAttachmentMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniMaterialService;
import com.loan.ocr.model.MaterialType;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.infrastructure.oss.OssObjectOpenResult;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 小程序端：经营 / 认证材料上传（C5 诊断与匹配材料真实数据流）。
 *
 * <p>契约：
 * <ul>
 *   <li>POST /api/mini/upload  —— 接收 MultipartFile（字段名 file），落盘到 {@code loan.upload.base-dir}，
 *       并返回 {fileKey, fileName, fileSize, url}；文件同时以 ServiceAttachment 持久化（失败仅告警，不影响上传）。</li>
 *   <li>GET  /api/mini/upload/{fileKey} —— 按 fileKey 前缀回传已存文件（inline 预览）。</li>
 * </ul>
 *
 * <p>业务 ID 遵循红线：fileKey = {@code att} + 32 位随机（BizIdGenerator 风格，避免主键直查）。
 */
@Slf4j
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniUploadController {

    private final ServiceAttachmentMapper attachmentMapper;
    private final MiniMaterialService materialService;
    private final ClientAllocationService clientAllocationService;
    private final OssStorageService ossStorageService;

    /**
     * 上传材料。
     *
     * @param file        文件（字段名 file）
     * @param bizType     资料类型（ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER）
     * @param clientCode  客户编码（不传则使用登录态客户编码）
     * @param user        当前登录用户
     * @return 文件元信息（fileKey / fileName / fileSize / url）
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false) String reportNo,
            @CurrentUser LoanUser user) {
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        String scopedClientCode = clientAllocationService.requireOperationClientCode(user, clientCode);
        String normalizedBizType = MaterialType.normalize(bizType);
        try {
            String fileKey = "att" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String suffix = "";
            int dot = original.lastIndexOf('.');
            if (dot > 0) {
                suffix = original.substring(dot);
            }
            String objectKey = fileKey + suffix;
            ossStorageService.upload(objectKey, file.getInputStream(), file.getSize());

            // 元数据持久化（失败不影响文件已落盘，仅告警）
            try {
                ServiceAttachment att = new ServiceAttachment();
                att.setFileKey(fileKey);
                att.setFileName(original);
                att.setFileSize(file.getSize());
                att.setAttachmentType(normalizedBizType);
                att.setClientProfileCode(scopedClientCode);
                att.setReportNo(reportNo);
                att.setUploadTime(LocalDateTime.now());
                attachmentMapper.insert(att);
            } catch (Exception e) {
                log.warn("附件元数据持久化失败（文件已存）: {}", e.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("fileKey", fileKey);
            data.put("fileName", original);
            data.put("fileSize", file.getSize());
            data.put("url", "/api/mini/upload/" + fileKey);

            // OCR 材料识别与复核：新报告生成前也必须可上传，先按客户绑定并进入材料复核；
            // 已有 reportNo 时额外关联既有报告，供补充材料诊断回灌。
            if (StringUtils.hasText(scopedClientCode)) {
                try {
                    Map<String, Object> ocr = materialService.ingest(
                            fileKey, normalizedBizType, scopedClientCode, reportNo, user);
                    data.put("ocrApplied", ocr.get("ocrApplied"));
                    data.put("extractedFields", ocr.get("extractedFields"));
                    data.put("mergedCount", ocr.get("mergedCount"));
                    data.put("ocrFileKey", ocr.get("ocrFileKey"));
                    data.put("recognitionProvider", ocr.get("recognitionProvider"));
                    data.put("aiRecognitionEnabled", ocr.get("aiRecognitionEnabled"));
                    data.put("recognitionStatus", ocr.get("recognitionStatus"));
                    // 审批门控：识别结果进入待复核时回传标识，前端据此提示「待我司审批，暂不可见」
                    data.put("pendingReview", ocr.get("pendingReview"));
                    data.put("reviewNo", ocr.get("reviewNo"));
                } catch (Exception e) {
                    log.warn("OCR 回灌失败（不影响上传主流程）: {}", e.getMessage());
                    data.put("ocrApplied", false);
                    data.put("extractedFields", new java.util.ArrayList<Map<String, Object>>());
                    data.put("mergedCount", 0);
                    data.put("ocrFileKey", null);
                    data.put("recognitionProvider", "unavailable");
                    data.put("aiRecognitionEnabled", false);
                    data.put("recognitionStatus", "FAILED");
                }
            }
            return Result.ok(data);
        } catch (IOException e) {
            log.error("材料上传失败", e);
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "材料上传失败");
        }
    }

    /**
     * 按 fileKey 回传已存文件（inline 预览）。
     *
     * @param fileKey 文件标识（仅允许字母数字，防目录穿越）
     * @param response HTTP 响应
     */
    @GetMapping("/upload/{fileKey}")
    public void download(@PathVariable String fileKey, HttpServletResponse response) throws IOException {
        if (!fileKey.matches("[a-zA-Z0-9]+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String objectKey = fileKey;
        // 业务 key 不含扩展名，兼容常见文件扩展名并避免目录穿越
        for (String ext : new String[]{"", ".pdf", ".jpg", ".jpeg", ".png", ".doc", ".docx", ".xls", ".xlsx"}) {
            if (ossStorageService.objectExists(fileKey + ext)) { objectKey = fileKey + ext; break; }
        }
        if (!ossStorageService.objectExists(objectKey)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        OssObjectOpenResult object = ossStorageService.openObject(objectKey);
        response.setContentType(object.getContentType() == null ? "application/octet-stream" : object.getContentType());
        response.setContentLengthLong(object.getContentLength());
        response.setHeader("Content-Disposition", "inline; filename=\"" + objectKey + "\"");
        try (java.io.InputStream in = object.getInputStream()) { org.springframework.util.StreamUtils.copy(in, response.getOutputStream()); }
    }
}
