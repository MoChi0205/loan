package com.loan.importing.controller;

import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.importing.service.ExcelTemplateService;
import com.loan.importing.service.ImportRoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import com.loan.common.Result;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RestController;

/** 产品/客户线索批量导入公共入口。 */
@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
public class BatchImportController {
    private final ImportRoleGuard roleGuard;
    private final ExcelTemplateService templateService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String type, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + normalized.toLowerCase() + "-import-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateService.template(normalized));
    }

    /** 上传预览：仅解析与校验，不写库；正式导入任务后续复用同一解析结果。 */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam String type,
                                               @RequestParam("file") MultipartFile file,
                                               @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        if (file == null || file.isEmpty()) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "请选择导入文件");
        }
        if (file.getSize() > 20 * 1024 * 1024L) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "文件不能超过 20MB");
        }
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        return Result.ok(templateService.preview(normalized, file));
    }
}
