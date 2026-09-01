package com.loan.report.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表中心 HTTP 接口（Web 管理端）：经营总览 / 月度趋势 / 初筛报告查询。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 经营总览。
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@CurrentUser LoanUser user) {
        return Result.ok(reportService.overview(user));
    }

    /**
     * 成交趋势（按月）。
     *
     * @param months 近 N 个月（默认 12）
     */
    @GetMapping("/order-trend")
    public Result<List<Map<String, Object>>> orderTrend(@RequestParam(defaultValue = "12") int months,
                                                       @CurrentUser LoanUser user) {
        return Result.ok(reportService.orderTrend(months, user));
    }

    /**
     * 奖励趋势（按月）。
     *
     * @param months 近 N 个月（默认 12）
     */
    @GetMapping("/reward-trend")
    public Result<List<Map<String, Object>>> rewardTrend(@RequestParam(defaultValue = "12") int months,
                                                         @CurrentUser LoanUser user) {
        return Result.ok(reportService.rewardTrend(months, user));
    }

    /**
     * 初筛报告分页。
     */
    @GetMapping("/screening/page")
    public Result<PageResult<Map<String, Object>>> screeningPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @CurrentUser LoanUser user) {
        return Result.ok(reportService.screeningPage(status, grade, keyword,
                page <= 0 ? 1 : page, size <= 0 ? 10 : Math.min(size, 100), orderBy, orderDir, user));
    }

    /**
     * 初筛报告详情。
     */
    @GetMapping("/screening/{reportNo}")
    public Result<Map<String, Object>> screeningDetail(@PathVariable String reportNo) {
        return Result.ok(reportService.screeningDetail(reportNo));
    }
}
