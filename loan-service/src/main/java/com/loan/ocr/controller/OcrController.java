package com.loan.ocr.controller;

import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.ocr.entity.ExtractFieldDef;
import com.loan.ocr.entity.OcrRecord;
import com.loan.ocr.model.OcrResult;
import com.loan.ocr.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * OCR 提取 HTTP 接口。
 *
 * @author loan-platform
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @Value("${loan.upload.base-dir:./uploads}")
    private String baseDir;

    /**
     * 提取字段定义列表。
     *
     * @param customerGroup 客群（可选）
     * @return 字段定义列表
     */
    @GetMapping("/field-defs")
    public Result<List<ExtractFieldDef>> fieldDefs(@RequestParam(required = false) String customerGroup) {
        return Result.ok(ocrService.listFieldDefs(customerGroup));
    }

    /**
     * 保存识别记录（阶段一模拟）。
     *
     * @param record 识别记录
     * @return 记录 ID
     */
    @PostMapping("/record")
    public Result<Long> saveRecord(@RequestBody OcrRecord record) {
        ocrService.saveRecord(record);
        return Result.ok(record.getId());
    }

    /**
     * Web 端材料识别（T16/D28）：上传文件 → 落盘 → 提取 → 映射 facts → 落 t_ocr_record。
     *
     * <p>文件命名沿用红线：fileKey = {@code att} + 32 位随机（BizIdGenerator 风格），
     * 落盘到 {@code loan.upload.base-dir}（与 /api/mini/upload 同目录约定）。
     *
     * @param file          材料文件（字段名 file）
     * @param bizType       资料类型（ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER）
     * @param customerGroup 客群（可选，ENTERPRISE / PERSONAL）
     * @return 识别结果（facts / confidenceAvg / ocrRecordId / extractedFields / rulesMissing）
     */
    @PostMapping("/recognize")
    public Result<OcrResult> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String customerGroup) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        String fileKey;
        try {
            // 绝对路径化，避免相对路径在 Servlet 容器下解析到 Tomcat work 临时目录（D28 修复：原 FileNotFoundException）
            Path dir = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            fileKey = "att" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String suffix = "";
            int dot = original.lastIndexOf('.');
            if (dot > 0) {
                suffix = original.substring(dot);
            }
            Path target = dir.resolve(fileKey + suffix);
            file.transferTo(target.toFile());
        } catch (Exception e) {
            log.warn("OCR 文件落盘失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }
        OcrResult result = ocrService.recognize(fileKey,
                StringUtils.hasText(bizType) ? bizType : "OTHER", customerGroup);
        return Result.ok(result);
    }
}
