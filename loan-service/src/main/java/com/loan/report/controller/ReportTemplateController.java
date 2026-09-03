package com.loan.report.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.report.entity.ReportTemplate;
import com.loan.report.service.ReportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报告模板 HTTP 接口（Web 管理端）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/report/template")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportTemplateService templateService;

    /**
     * 模板分页。
     */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(templateService.page(keyword,
                PageParams.page(page), PageParams.size(size)));
    }

    /**
     * 新增 / 编辑模板。
     */
    @PostMapping("/save")
    @OpLog(bizType = "报告模板", action = "SAVE")
    public Result<Void> save(@RequestBody ReportTemplate req, @CurrentUser LoanUser user) {
        templateService.save(req, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 发布 / 停用。
     */
    @PostMapping("/toggle")
    @OpLog(bizType = "报告模板", action = "TOGGLE")
    public Result<Void> toggle(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        templateService.toggle((String) body.get("templateCode"),
                body.get("versionNo") == null ? null : Integer.valueOf(body.get("versionNo").toString()),
                Boolean.TRUE.equals(body.get("active")),
                user == null ? "system" : user.getName());
        return Result.ok();
    }
}
