package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniLeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小程序端：线索录入（融资需求提交）。渠道/客户/员工均可提交，渠道录入进公海（沙箱隔离）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/lead")
@RequiredArgsConstructor
public class MiniLeadController {

    private final MiniLeadService miniLeadService;

    /**
     * 提交融资需求（登录后可提交）。
     *
     * <p>企业字段（entName/creditCode/industry/foundYears/annualTaxAmount/annualInvoiceAmount）可选；
     * source 由后端按用户类型派生，前端不可传。响应 {@code {leadNo, duplicated}}。
     *
     * @param body 提交内容
     * @param user 当前登录用户
     * @return {leadNo, duplicated}
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, String> body, @CurrentUser LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        Map<String, Object> result = miniLeadService.submit(body, user);
        if (Boolean.TRUE.equals(result.get("duplicated"))) {
            // 唯一索引冲突：友好文案，不泄露归属人（沙箱隔离）
            return Result.build(ResultCode.SUCCESS.getCode(), "该客户已被录入，请联系运营", result, null);
        }
        return Result.ok(result);
    }

    /**
     * 我录入的线索（仅本人，沙箱脱敏）。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前登录用户
     * @return 分页（leadNo/contactName脱敏/entName/phone掩码/followStatus/createdAt）
     */
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> my(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser LoanUser user) {
        if (user == null || user.getUserNo() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.ok(miniLeadService.myLeads(page, size, user));
    }
}
