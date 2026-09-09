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
}
