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

    @Value("${loan.upload.base-dir:./uploads}")
    private String baseDir;

    private final ServiceAttachmentMapper attachmentMapper;
    private final MiniMaterialService materialService;
    private final ClientAllocationService clientAllocationService;

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
        try {
            // 绝对路径化（与 OcrController 同款修复，D28：避免 Servlet 容器下解析到 Tomcat work 临时目录）
            Path dir = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileKey = "att" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String suffix = "";
            int dot = original.lastIndexOf('.');
            if (dot > 0) {
                suffix = original.substring(dot);
            }
            Path target = dir.resolve(fileKey + suffix);
            file.transferTo(target.toFile());

            // 元数据持久化（失败不影响文件已落盘，仅告警）
            try {
                ServiceAttachment att = new ServiceAttachment();
                att.setFileKey(fileKey);
                att.setFileName(original);
                att.setFileSize(file.getSize());
                att.setAttachmentType(bizType == null ? "OTHER" : bizType);
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

            // OCR 材料识别与诊断回灌（T2）：仅当 reportNo 非空时触发；
            // 识别/回灌失败仅告警，绝不阻塞上传主流程（向后兼容原有 4 字段返回）。
            if (StringUtils.hasText(reportNo)) {
                try {
                    Map<String, Object> ocr = materialService.ingest(
                            fileKey, bizType, scopedClientCode, reportNo, user);
                    data.put("ocrApplied", ocr.get("ocrApplied"));
                    data.put("extractedFields", ocr.get("extractedFields"));
                    data.put("mergedCount", ocr.get("mergedCount"));
                    data.put("ocrRecordId", ocr.get("ocrRecordId"));
                } catch (Exception e) {
                    log.warn("OCR 回灌失败（不影响上传主流程）: {}", e.getMessage());
                    data.put("ocrApplied", false);
                    data.put("extractedFields", new java.util.ArrayList<Map<String, Object>>());
                    data.put("mergedCount", 0);
                    data.put("ocrRecordId", null);
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
        Path dir = Paths.get(baseDir);
        Path found = null;
        try (Stream<Path> stream = Files.list(dir)) {
            found = stream
                    .filter(p -> p.getFileName().toString().startsWith(fileKey))
                    .findFirst()
                    .orElse(null);
        }
        if (found == null || !Files.exists(found)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = Files.probeContentType(found);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename=\"" + found.getFileName().toString() + "\"");
        Files.copy(found, response.getOutputStream());
    }
}
