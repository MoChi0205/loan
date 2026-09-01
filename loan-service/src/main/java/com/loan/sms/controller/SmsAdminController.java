package com.loan.sms.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.log.annotation.OpLog;
import com.loan.sms.entity.SmsTemplate;
import com.loan.sms.service.SmsAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 短信中心 HTTP 接口（Web 管理端）：模板管理 / 发送记录 / 手动发送。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/sms")
@RequiredArgsConstructor
public class SmsAdminController {

    private final SmsAdminService smsAdminService;

    /**
     * 模板分页。
     */
    @GetMapping("/template/page")
    public Result<PageResult<Map<String, Object>>> templatePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(smsAdminService.templatePage(keyword,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 模板全量（下拉）。
     */
    @GetMapping("/template/list")
    public Result<List<Map<String, Object>>> templateList() {
        return Result.ok(smsAdminService.templateList());
    }

    /**
     * 新增 / 编辑模板。
     */
    @PostMapping("/template/save")
    @OpLog(bizType = "短信中心", action = "TEMPLATE_SAVE")
    public Result<Void> saveTemplate(@RequestBody SmsTemplate req, @CurrentUser LoanUser user) {
        smsAdminService.saveTemplate(req, user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 启停模板。
     */
    @PostMapping("/template/toggle")
    @OpLog(bizType = "短信中心", action = "TEMPLATE_TOGGLE")
    public Result<Void> toggleTemplate(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        smsAdminService.toggleTemplate((String) body.get("templateCode"),
                Boolean.TRUE.equals(body.get("enabled")),
                user == null ? "system" : user.getName());
        return Result.ok();
    }

    /**
     * 发送记录分页。
     */
    @GetMapping("/record/page")
    public Result<PageResult<Map<String, Object>>> recordPage(
            @RequestParam(required = false) String smsType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir) {
        return Result.ok(smsAdminService.recordPage(smsType, status, phone,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir));
    }

    /**
     * 手动发送短信。
     *
     * @param body { phone, templateCode, content? }
     * @param user 当前用户
     * @return 成功标记
     */
    @PostMapping("/send")
    @OpLog(bizType = "短信中心", action = "SEND")
    public Result<Void> send(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        smsAdminService.sendManual((String) body.get("phone"),
                (String) body.get("templateCode"),
                (String) body.get("content"),
                user == null ? "system" : user.getName());
        return Result.ok();
    }
}
